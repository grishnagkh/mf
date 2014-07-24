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
 */package at.itec.mf;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.BitSet;

import org.videolan.vlc.gui.MainActivity;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;
import android.util.Log;
import at.itec.mf.bloomfilter.BloomFilter;

/**
 * 
 * Utility class providing methods for timing, message building and bloom filter
 * comparison
 * 
 * @author stefan petscahrnig
 *
 */
public class Utils {

	// TODO: implement NTP query ?
	public static long getTimestamp() {
		return System.currentTimeMillis();
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
		return type + delim + myIP + delim + myPort + delim + pts + delim + nts
				+ delim + myId;
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
		return type + delim + avgTS + delim + nts + delim + myId + delim
				+ bloomFilterRep + delim + maxId;
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
		// FIXME hack alert: global variable
		WifiManager wm = (WifiManager) MainActivity.c
				.getSystemService(Context.WIFI_SERVICE);
		try {
			return InetAddress.getByName(Formatter.formatIpAddress(wm
					.getConnectionInfo().getIpAddress()));
		} catch (UnknownHostException e) {
			Log.e("sync utils", "cannot get wifi address, returning null...");
		}
		return null;
	}

}
