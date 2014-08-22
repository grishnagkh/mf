package mf.sync.utils;

import mf.com.google.android.exoplayer.ExoPlayer;
import android.widget.MediaController.MediaPlayerControl;

public class PlayerControl implements MediaPlayerControl {

	/**
	 * ensure we have enough data in buffer
	 *
	 * @param time
	 */

	public static void ensureBuffered(long time) {
		// to avoid so much buffer that the app will not buffer
		time = time < 2000 ? time : 2000;
		// simply busy wait until we have buffered more
		while (getBufferPos() - getPlaybackTime() < time)
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}
	}

	/**
	 * ensure, playback is after target+padding
	 *
	 * @param l
	 */
	public static void ensureTime(long target, long padding) {
		while (getPlaybackTime() < target + padding)
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}
		SessionInfo.getInstance().log(
				"we definetly are after playback time " + (target + padding));
	}

	/**
	 * Gets an estimate of the absolute position in milliseconds up to which
	 * data is buffered.
	 *
	 * @return An estimate of the absolute position in milliseconds up to which
	 *         data is buffered, or {@link ExoPlayer#UNKNOWN_TIME} if no
	 *         estimate is available.
	 */
	public static int getBufferPos() {
		return player.getBufferedPosition();
	}

	/**
	 *
	 * @return the duration of the current track in milliseconds,
	 *         {@link ExoPlayer#UNKNOWN_TIME} if the duration is not known or
	 * @link{Utils#PLAYER_NOT_INITIALIZED if the player is not initialized
	 */
	public static int getCurTrackDuration() {
		if (player == null)
			return PlayerControl.PLAYER_NOT_INITIALIZED;
		return player.getDuration();
	}

	/**
	 *
	 * @return the actual playback position in milliseconds or
	 *         PLAYER_NOT_INITIALIZED if the player is not initialized
	 */
	public static int getPlaybackTime() {
		if (player == null)
			return PlayerControl.PLAYER_NOT_INITIALIZED;
		// return player.getCurrentPosition();
		return (int) player.getPositionUs() / 1000;
	}

	public static float getSpeed() {
		return player.getPlaybackRate();
	}

	/**
	 * initializes the player
	 *
	 * @param newPlayer
	 */

	public static void initPlayer(ExoPlayer newPlayer) {
		player = newPlayer;
	}

	/**
	 * pauses the player for a specific time
	 *
	 * @param duration
	 *            how long to pause
	 */
	public static void pause(int duration) {
		player.setPlayWhenReady(false);
		try {
			Thread.sleep(duration);
		} catch (InterruptedException e) {

		}
		player.setPlayWhenReady(true);

	}

	public static void setPlaybackRate(float f) {
		player.setPlaybackRate(f);
	}

	/**
	 * Seeks to a position specified in milliseconds.
	 *
	 * @param positionMs
	 *            The seek position.
	 */
	public static void setPlaybackTime(int positionMs) {

		SessionInfo.getInstance().log("setting playback time to " + positionMs);
		if (player == null)
			return;
		player.seekTo(positionMs);
	}

	static final int PLAYER_NOT_INITIALIZED = -2;

	/** player instance for playback control */
	private static ExoPlayer player;

	public PlayerControl(ExoPlayer player) {
		PlayerControl.player = player;
	}

	@Override
	public boolean canPause() {
		return true;
	}

	@Override
	public boolean canSeekBackward() {
		return true;
	}

	@Override
	public boolean canSeekForward() {
		return true;
	}

	// not supported
	@Override
	public int getAudioSessionId() {
		throw new UnsupportedOperationException();
	}

	@Override
	public int getBufferPercentage() {
		return player.getBufferedPercentage();
	}

	@Override
	public int getCurrentPosition() {
		return player.getCurrentPosition();
	}

	@Override
	public int getDuration() {
		return player.getDuration();
	}

	@Override
	public boolean isPlaying() {
		return player.getPlayWhenReady();
	}

	@Override
	public void pause() {
		player.setPlayWhenReady(false);
	}

	@Override
	public void seekTo(int timeMillis) {
		// MediaController arrow keys generate unbounded values.
		player.seekTo(Math.min(Math.max(0, timeMillis), getDuration()));
	}

	@Override
	public void start() {
		player.setPlayWhenReady(true);
	}
}
