/*
 * Utils.java
 *
 * Copyright (c) 2014, Stefan Petscharnig. All rights reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301 USA
 */
package mf.sync.utils;

import java.net.InetAddress;
import java.net.UnknownHostException;

import mf.com.google.android.exoplayer.ExoPlayer;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;
import android.util.Log;

/**
 * 
 * Utility class
 * 
 * @author stefan petscahrnig
 *
 */
public class Utils {

	private static final int PLAYER_NOT_INITIALIZED = -2;

	/** player instance for playback control */
	private static ExoPlayer player;

	public static void setPlaybackRate(float f) {
		player.setPlaybackRate(f);
	}

	/**
	 * @param c
	 *            the application cotnext
	 * @return the wifi address
	 */
	@SuppressWarnings("deprecation")
	public static InetAddress getWifiAddress(Context c) {
		if (c == null)
			throw new RuntimeException("Context is null");
		WifiManager wm = (WifiManager) c.getSystemService(Context.WIFI_SERVICE);
		try {
			return InetAddress.getByName(Formatter.formatIpAddress(wm
					.getConnectionInfo().getIpAddress()));
		} catch (UnknownHostException e) {
			Log.e("sync utils", "cannot get wifi address, returning null...");
		}
		return null;
	}

	/**
	 * 
	 * @return the duration of the current track in milliseconds,
	 *         {@link ExoPlayer#UNKNOWN_TIME} if the duration is not known or
	 * @link{Utils#PLAYER_NOT_INITIALIZED if the player is not initialized
	 */
	public static int getCurTrackDuration() {
		if (player == null)
			return PLAYER_NOT_INITIALIZED;
		return player.getDuration();
	}

	/**
	 * 
	 * @return the actual playback position in milliseconds or
	 * PLAYER_NOT_INITIALIZED if the player is not initialized
	 */
	public static int getPlaybackTime() {
		if (player == null)
			return PLAYER_NOT_INITIALIZED;
		// return player.getCurrentPosition();
		return (int) player.getPositionUs() / 1000;
	}

	/**
	 * Seeks to a position specified in milliseconds.
	 *
	 * @param positionMs
	 *            The seek position.
	 */
	public static void setPlaybackTime(int positionMs) {
		if (player == null)
			return;
		player.seekTo(positionMs);
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

}
