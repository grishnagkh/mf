/*
 * SessionManager.java
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import mf.bloomfilter.BloomFilter;
import mf.sync.SyncI;
import mf.sync.utils.log.SyncLogger;
import android.annotation.SuppressLint;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.support.v4.util.ArrayMap;
import android.text.format.Formatter;
import android.util.Log;

public class SessionInfo {

	/**
	 * singleton method
	 */
	public static SessionInfo getInstance() {
		if (instance == null) {
			instance = new SessionInfo();
		}
		return instance;
	}

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

	/** singleton instance */
	private static SessionInfo instance;
	/** used to store data about self */
	private Peer mySelf;
	/** Map of known peers */
	private Map<Integer, Peer> peers;
	/** id of the session, not used atm */
	private String sessionId;
	/** sequence number for resynchronizations (fine) */
	private int seqN;

	/** time stamp until which the session is valid, not used atm */
	private long validThru;

	/** logger for debugging */
	private SyncLogger log;
	private SyncLogger sLog;
	private SyncLogger rLog;

	private boolean cSynced = false;

	private BloomFilter bloom;

	private List<BloomFilter> bloomList;

	/** time when the last avgTs update */
	long lastAvgUpdateTs;

	/** average time stamp at time oldTs */
	long avgTs;

	/**
	 * constructor
	 */
	private SessionInfo() {
		peers = new ArrayMap<Integer, Peer>();
		log = new SyncLogger(20);
		rLog = new SyncLogger(5);
		sLog = new SyncLogger(5);
		seqN = 0;
		bloomList = new ArrayList<BloomFilter>();
	}

	/**
	 * align the average playback timestamp stored to a specific time stamp
	 *
	 * @param alignTo
	 *            the time stamp to align to
	 * @return the aligned time stamp
	 */
	public long alignedAvgTs(long alignTo) {
		return avgTs + alignTo - lastAvgUpdateTs;
	}

	public void clearSessionData() {
		instance = null;
	}

	public BloomFilter getBloom() {
		return bloom;
	}

	public List<BloomFilter> getBloomList() {
		return bloomList;
	}

	public long getLastAvgUpdateTs() {
		return lastAvgUpdateTs;
	}

	/**
	 *
	 * @return
	 */
	public SyncLogger getLog() {
		return log;
	}

	/**
	 *
	 * @return
	 */
	public Peer getMySelf() {
		return mySelf;
	}

	/**
	 *
	 * @return
	 */
	@SuppressLint("UseSparseArrays")
	public Map<Integer, Peer> getPeers() {
		return peers;
	}

	public SyncLogger getRcvLog() {
		return rLog;
	}

	public SyncLogger getSendLog() {
		return sLog;
	}

	/**
	 *
	 * @return
	 */
	public int getSeqN() {
		return seqN;
	}

	/**
	 *
	 * @return
	 */
	public String getSessionId() {
		return sessionId;
	}

	/**
	 *
	 * @return
	 */
	public long getValidThru() {
		return validThru;
	}

	public boolean isCSynced() {
		return cSynced;
	}

	/**
	 *
	 * @param s
	 */
	public void log(String s) {
		log.append(s);
	}

	public void resetFSyncData() {
		bloom = new BloomFilter(SyncI.BLOOM_FILTER_LEN_BYTE, SyncI.N_HASHES);
		if (mySelf != null) {
			bloom.add(mySelf.getId());
		}
		if (bloomList != null) {
			bloomList.clear();
			bloomList.add(bloom);
		}
	}

	public void setBloom(BloomFilter bloom) {
		this.bloom = bloom;
	}

	public void setCSynced() {
		cSynced = true;
		SessionInfo.getInstance().updateAvgTs(PlayerControl.getPlaybackTime(),
				Clock.getTime());
	}

	/**
	 *
	 * @param mySelf
	 */
	public void setMySelf(Peer mySelf) {
		this.mySelf = mySelf;
		bloom = new BloomFilter(SyncI.BLOOM_FILTER_LEN_BYTE, SyncI.N_HASHES);
		bloom.add(mySelf.getId());
	}

	/**
	 *
	 * @param peers
	 */
	public void setPeers(Map<Integer, Peer> peers) {
		this.peers = peers;
	}

	/**
	 *
	 * @param sn
	 */
	public void setSeqN(int sn) {
		seqN = sn;
	}

	/**
	 *
	 * @param sessionId
	 */
	public void setSessionId(String sessionId) {
		this.sessionId = sessionId;
	}

	/**
	 *
	 * @param validThru
	 */
	public void setValidThru(String validThru) {
		this.validThru = Long.parseLong(validThru);
	}

	/**
	 * update the average timstamp and reset the last update time stampt
	 *
	 * @param newValue
	 *            the value to be set
	 */
	public synchronized void updateAvgTs(long newValue, long updateTime) {
		avgTs = newValue;
		lastAvgUpdateTs = updateTime;
	}

}
