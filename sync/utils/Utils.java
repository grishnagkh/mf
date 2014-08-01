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
import java.util.BitSet;

import mf.bloomfilter.BloomFilter;
import mf.com.google.android.exoplayer.ExoPlayer;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

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

	public static String[] NTP_HOSTS = new String[] {
			"time1srv.sci.uni-klu.ac.at", "0.at.pool.ntp.org", "0.pool.ntp.org" };

	private static long oldNtp = 0, oldUpdateTime = 0;
	private static boolean refreshNtp = false;

	public static long getTimestamp() {

		NTPUDPClient client = new NTPUDPClient();
		client.setDefaultTimeout(1000);

		long ret = -1;
		if (System.currentTimeMillis() - oldUpdateTime > 5000) {
			refreshNtp = true;
		}
		for (int i = 0; refreshNtp && i < NTP_HOSTS.length; i++) {
			try {
				InetAddress hostAddr = InetAddress.getByName(NTP_HOSTS[i]);

				TimeInfo info = client.getTime(hostAddr);
				SessionInfo.getInstance().log(
						"update ntp time from " + NTP_HOSTS[i]);
				ret = info.getReturnTime();
				oldNtp = ret;
				oldUpdateTime = System.currentTimeMillis();
				client.close();
				refreshNtp = false;
				break;
			} catch (Exception e) {
				// does not matter
			}
		}
		if (ret < 0) {
			if (oldUpdateTime <= 0) {
				oldNtp = System.currentTimeMillis();
				oldUpdateTime = System.currentTimeMillis();
			}
			return oldNtp + System.currentTimeMillis() - oldUpdateTime;
		}

		return ret;

	}

	/**
	 * 
	 * builds a coarse sync message
	 * 
	 * @param delim
	 * @param type
	 * @param myIP
	 * @param myPort
	 * @param pts
	 * @param nts
	 * @param myId
	 * @return
	 */
	public static String buildMessage(String delim, int type, String myIP,
			int myPort, long pts, long nts, int myId) {
		String msg = type + delim + myIP + delim + myPort + delim + pts + delim
				+ nts + delim + myId;
		return msg;
	}

	/**
	 * builds a fine sync message
	 * 
	 * @param delim
	 * @param type
	 * @param avgTS
	 * @param nts
	 * @param myId
	 * @param bloomFilterRep
	 * @param maxId
	 * @return
	 */
	public static String buildMessage(String delim, int type, long avgTS,
			long nts, int myId, String bloomFilterRep, int maxId) {

		String msg = type + delim + avgTS + delim + nts + delim + myId + delim
				+ bloomFilterRep + delim + maxId;

		return msg;
	}

	/**
	 * 
	 * @param b
	 *            the bitset to convert
	 * @return a string representation of a bitset
	 */
	public static String toString(BitSet b) {
		StringBuffer ret = new StringBuffer();
		for (int i = 0; i < b.size(); i += 1) {
			ret.append(b.get(i) ? 1 : 0);
		}
		return ret.toString();
	}

	/**
	 * 
	 * @param str
	 *            a bit string containing 1's and 0's
	 * @return a bitset corresponding to the input string
	 */

	public static BitSet fromString(String str) {
		BitSet ret = new BitSet(str.length());
		for (int i = 0; i < str.length(); i++) {
			ret.set(i, str.charAt(i) == '1' ? true : false);
		}
		return ret;
	}

	/**
	 * @param bf1
	 * @param bf2
	 * @return true if and result contains at least 1 true value (assuming bloom
	 *         filters have equal length)
	 */
	public static boolean and(BloomFilter<?> bf1, BloomFilter<?> bf2) {
		BitSet b1 = bf1.getBitSet();
		BitSet b2 = bf2.getBitSet();
		for (int i = 0; i < b1.length(); i++)
			if (b1.get(i) && b2.get(i))
				return true;
		return false;
	}

	/**
	 * 
	 * @param rcvBF
	 * @param bloom
	 * @return true if xor result contains at least 1 true value
	 */
	public static boolean xor(BloomFilter<?> bf1, BloomFilter<?> bf2) {
		BitSet b1 = bf1.getBitSet();
		BitSet b2 = bf2.getBitSet();
		for (int i = 0; i < b1.length(); i++)
			if (b1.get(i) ^ b2.get(i))
				return true;
		return false;
	}

	/**
	 * 
	 * @param bloom
	 * @param maxId
	 * @return the (maximum) number of peers which can be in a given bloom
	 *         filter, up to a given maximum id
	 */
	public static int getN(BloomFilter<Integer> bloom, int maxId) {
		int ctr = 0;
		for (int i = 1; i <= maxId; i++) {
			if (bloom.contains(i))
				ctr++;
		}
		return ctr;
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

	private static ExoPlayer player;

	public static void initPlayer(ExoPlayer newPlayer) {
		player = newPlayer;

	}

}
