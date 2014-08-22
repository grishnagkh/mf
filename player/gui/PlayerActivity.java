/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mf.player.gui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import mf.com.google.android.exoplayer.ExoPlaybackException;
import mf.com.google.android.exoplayer.ExoPlayer;
import mf.com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import mf.com.google.android.exoplayer.MediaCodecTrackRenderer.DecoderInitializationException;
import mf.com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import mf.com.google.android.exoplayer.VideoSurfaceView;
import mf.sync.coarse.CSync;
import mf.sync.net.MessageHandler;
import mf.sync.utils.Clock;
import mf.sync.utils.Peer;
import mf.sync.utils.PlayerControl;
import mf.sync.utils.SessionInfo;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.media.MediaCodec.CryptoException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;

/**
 * An activity that plays media using {@link ExoPlayer}.
 */
public class PlayerActivity extends Activity implements SurfaceHolder.Callback,
		ExoPlayer.Listener, MediaCodecVideoTrackRenderer.EventListener {

	/**
	 * Builds renderers for the player.
	 */
	public interface RendererBuilder {
		void buildRenderers(RendererBuilderCallback callback);
	}

	/* package */
	final class RendererBuilderCallback {

		public void onRenderers(MediaCodecVideoTrackRenderer videoRenderer,
				MediaCodecAudioTrackRenderer audioRenderer) {
			PlayerActivity.this.onRenderers(this, videoRenderer, audioRenderer);
		}

		public void onRenderersError(Exception e) {
			PlayerActivity.this.onRenderersError(this, e);
		}
	}

	public static final int RENDERER_COUNT = 2;
	public static final int TYPE_VIDEO = 0;

	public static final int TYPE_AUDIO = 1;

	private static final String TAG = "PlayerActivity";
	public static final int TYPE_DASH_VOD = 0;
	public static final int TYPE_SS_VOD = 1;

	public static final int TYPE_OTHER = 2;
	private MediaController mediaController;
	private Handler mainHandler;
	private View shutterView;

	private VideoSurfaceView surfaceView;
	private ExoPlayer player;
	private RendererBuilder builder;
	private RendererBuilderCallback callback;

	private MediaCodecVideoTrackRenderer videoRenderer;
	private boolean autoPlay = true;

	private int playerPosition;
	private Uri contentUri;
	private int contentType;

	private String contentId;

	public static final boolean DEBUG = true;

	public static final boolean DBOX_ONLY = false;

	public Handler getMainHandler() {
		return mainHandler;
	}

	private RendererBuilder getRendererBuilder() {
		String userAgent = DemoUtil.getUserAgent(this);
		switch (contentType) {
		case TYPE_SS_VOD:
			return new SmoothStreamingRendererBuilder(this, userAgent,
					contentUri.toString(), contentId);
		case TYPE_DASH_VOD:
			return new DashVodRendererBuilder(this, userAgent,
					contentUri.toString(), contentId);

		default:
			return new DefaultRendererBuilder(this, contentUri);
		}
	}

	// Public methods

	private void maybeStartPlayback() {
		Surface surface = surfaceView.getHolder().getSurface();
		if (videoRenderer == null || surface == null || !surface.isValid())
			// We're not ready yet.
			return;
		player.sendMessage(videoRenderer,
				MediaCodecVideoTrackRenderer.MSG_SET_SURFACE, surface);
		if (autoPlay) {
			player.setPlayWhenReady(true);
			autoPlay = false;
		}

	}

	// Internal methods

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (DEBUG)
			new Thread(new Runnable() {

				@Override
				public void run() {
					// update debug pane
					for (int i = 0; i < 1000000; i++) {
						try {
							Thread.sleep(500);
						} catch (InterruptedException e) {
						}
						try {
							runOnUiThread(new Runnable() {

								@Override
								public void run() {
									updateDebugViews();
								}
							});
						} catch (Exception e) {
						}
					}
				}
			}).start();

		Intent intent = getIntent();
		contentUri = intent.getData();
		contentType = intent.getIntExtra(DemoUtil.CONTENT_TYPE_EXTRA,
				TYPE_OTHER);
		contentId = intent.getStringExtra(DemoUtil.CONTENT_ID_EXTRA);

		mainHandler = new Handler(getMainLooper());
		builder = getRendererBuilder();

		setContentView(R.layout.player_activity_simple);
		View root = findViewById(R.id.root);
		root.setOnTouchListener(new OnTouchListener() {
			@SuppressLint("ClickableViewAccessibility")
			@Override
			public boolean onTouch(View arg0, MotionEvent arg1) {
				if (arg1.getAction() == MotionEvent.ACTION_DOWN)
					toggleControlsVisibility();
				return true;
			}
		});

		mediaController = new MediaController(this);
		mediaController.setAnchorView(root);
		shutterView = findViewById(R.id.shutter);
		surfaceView = (VideoSurfaceView) findViewById(R.id.surface_view);
		surfaceView.getHolder().addCallback(this);

	}

	@Override
	public void onCryptoError(CryptoException e) {
		// This is for informational purposes only. Do nothing.
	}

	@Override
	public void onDecoderInitializationError(DecoderInitializationException e) {
		// This is for informational purposes only. Do nothing.
	}

	@Override
	public void onDrawnToSurface(Surface surface) {
		shutterView.setVisibility(View.GONE);
	}

	@Override
	public void onDroppedFrames(int count, long elapsed) {
		Log.d(TAG, "Dropped frames: " + count);
	}

	private void onError(Exception e) {
		Log.e(TAG, "Playback failed", e);

		Toast.makeText(
				this,
				"could not play the selected video, check your internet connection and try again",
				Toast.LENGTH_LONG).show();

		finish();
	}

	// ExoPlayer.Listener implementation

	@Override
	public void onPause() {
		super.onPause();
		// Release the player
		if (player != null) {
			playerPosition = player.getCurrentPosition();
			player.release();
			player = null;
		}
		callback = null;
		videoRenderer = null;
		shutterView.setVisibility(View.VISIBLE);

		MessageHandler.getInstance().stopHandling(true);
	}

	@Override
	public void onPlayerError(ExoPlaybackException e) {
		onError(e);
	}

	@Override
	public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
		// Do nothing.
	}

	// MediaCodecVideoTrackRenderer.Listener

	@Override
	public void onPlayWhenReadyCommitted() {
		// Do nothing.

		if (player.getPlayWhenReady()) {
			// MessageHandler.getInstance().resumeHandling();
			if (CSync.getInstance().getState() != Thread.State.NEW)
				CSync.getInstance().interrupt();
			CSync.getInstance().start();
		} else {
			// MessageHandler.getInstance().pauseHandling();
			// CSync.getInstance().stopSync(); // if we do a coarse sync, stop
			// it
			// FSync.getInstance().stopSync(); // if we do a fine sync, stop it
		}
	}

	private void onRenderers(RendererBuilderCallback callback,
			MediaCodecVideoTrackRenderer videoRenderer,
			MediaCodecAudioTrackRenderer audioRenderer) {
		if (this.callback != callback)
			return;
		this.callback = null;
		this.videoRenderer = videoRenderer;
		player.prepare(videoRenderer, audioRenderer);
		maybeStartPlayback();
	}

	private void onRenderersError(RendererBuilderCallback callback, Exception e) {
		if (this.callback != callback)
			return;
		this.callback = null;
		onError(e);
	}

	@Override
	public void onResume() {
		super.onResume();
		// Setup the player
		player = ExoPlayer.Factory.newInstance(RENDERER_COUNT, 1000, 5000);
		player.addListener(this);
		player.seekTo(playerPosition);
		// Build the player controls
		mediaController.setMediaPlayer(new PlayerControl(player));
		mediaController.setEnabled(true);
		// Request the renderers
		callback = new RendererBuilderCallback();
		builder.buildRenderers(callback);

		// here the mpd *should* be parsed,
		// at least, the parsing should have been initiated

		PlayerControl.initPlayer(player);

	}

	@Override
	public void onVideoSizeChanged(int width, int height) {
		surfaceView.setVideoWidthHeightRatio(height == 0 ? 1 : (float) width
				/ height);
	}

	// SurfaceHolder.Callback implementation

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		// Do nothing.
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		maybeStartPlayback();
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		if (videoRenderer != null)
			player.blockingSendMessage(videoRenderer,
					MediaCodecVideoTrackRenderer.MSG_SET_SURFACE, null);
	}

	private void toggleControlsVisibility() {
		// no controls visible for now...

		// if (mediaController.isShowing())
		// mediaController.hide();
		// else
		// mediaController.show(0);
	}

	public void updateDebugViews() {

		TextView dBox = (TextView) findViewById(R.id.debug_box);
		String dText = "DEBUG\n";

		long now = Clock.getTime();
		String speed = "0";

		try {
			speed = "" + PlayerControl.getSpeed();
		} catch (Exception e) {
			// if the sample rate is not initialized, then, we get an
			// arithmetic
			// exception by dividing by zero
		}

		dText += "Ntp Time: " + new Date(now) + "(" + now + ") Playback time:"
				+ (PlayerControl.getPlaybackTime()) + " speed x" + speed
				+ " Csync complete: " + SessionInfo.getInstance().isCSynced()
				+ "\n" + SessionInfo.getInstance().getLog().toString();
		dBox.setText(dText);
		if (!DBOX_ONLY) {
			TextView dRcv = (TextView) findViewById(R.id.debug_view_rcv);
			TextView dSen = (TextView) findViewById(R.id.debug_view_send);
			TextView dPee = (TextView) findViewById(R.id.debug_view_peers);

			String rcvStr = "message received\n";
			String senStr = "messages sent\n";
			String peeStr = "known peers\n";

			senStr += SessionInfo.getInstance().getSendLog().toString();
			rcvStr += SessionInfo.getInstance().getRcvLog().toString();

			Map<Integer, Peer> map = SessionInfo.getInstance().getPeers();

			if (map != null) {
				Collection<Peer> l2 = map.values();
				List<Peer> l1 = new ArrayList<Peer>();
				for (Peer p : l2)
					l1.add(p);
				for (int i = l1.size(); i > 0; i--)
					peeStr += l1.get(i - 1).toString() + "\n";
			}
			dRcv.setText(rcvStr);
			dSen.setText(senStr);
			dPee.setText(peeStr);
		}

	}
}