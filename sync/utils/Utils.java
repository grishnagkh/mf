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

	static final boolean TEST_LOCAL = false;

	public static String[] NTP_HOSTS = new String[] {
			"time1srv.sci.uni-klu.ac.at", "0.at.pool.ntp.org", "0.pool.ntp.org" };

	// private static long nts = 0, updateTime = 0;
	private static ExoPlayer player;

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

	public static int getCurTrackDuration() {
		if (player == null)
			return -1;
		return player.getDuration();
	}

	public static int getPlaybackTime() {
		if (player == null)
			return -1;
		return player.getCurrentPosition();
	}

	public static void setPlaybackTime(int avgPts) {
		if (player == null)
			return;
		player.seekTo(avgPts);
	}

	public static void initPlayer(ExoPlayer newPlayer) {
		player = newPlayer;
	}

	public static int getBufferPos() {
		return player.getBufferedPosition();
	}

	public static void pause(int duration) {
		player.setPlayWhenReady(false);
		try {
			Thread.sleep(duration);
		} catch (InterruptedException e) {

		}
		player.setPlayWhenReady(true);

	}

}
