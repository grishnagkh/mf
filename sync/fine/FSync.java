/*
 * FSync.java
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

package mf.sync.fine;

import java.util.ArrayList;
import java.util.List;

import mf.bloomfilter.BloomFilter;
import mf.sync.SyncI;
import mf.sync.net.FSyncMsg;
import mf.sync.net.MessageHandler;
import mf.sync.utils.Clock;
import mf.sync.utils.Peer;
import mf.sync.utils.PlayerControl;
import mf.sync.utils.SessionInfo;

/**
 * 
 * Class handling the fine synchronization using mf algorithm
 * 
 * @author stefan petscharnig
 *
 */
public class FSync {

	/** actual bloom filter */
	private BloomFilter bloom;
	/** a list of seen bloom filters */
	private List<BloomFilter> bloomList;
	/** time when the last avgTs update */
	long lastAvgUpdateTs;
	/** average time stamp at time oldTs */
	long avgTs;
	/** maximum peer id seen so far */
	private int maxId;
	/** own peer id */
	private int myId;
	/** singleton instance */
	private static FSync instance;
	/** periodical broadcast server */
	private Thread workerThread;
	/** monitor for the avg value */
	private Object avgMonitor;

	public static final boolean DEBUG = false;

	/**
	 * Constructor
	 */
	private FSync() {

		avgMonitor = new Object();
		bloomList = new ArrayList<BloomFilter>();
		myId = SessionInfo.getInstance().getMySelf().getId();
		maxId = myId;
		avgMonitor = this;
		bloom = new BloomFilter(SyncI.BLOOM_FILTER_LEN_BYTE, SyncI.N_HASHES);
		bloom.add(SessionInfo.getInstance().getMySelf().getId());
		bloomList.add(bloom);
		initAvgTs();
	}

	/**
	 * singleton method
	 * 
	 * @return
	 */
	public static FSync getInstance() {
		instance = instance == null ? new FSync() : instance;
		return instance;
	}

	/**
	 * start fine sync message sending in a new thread
	 */
	public void startSync() {
		SessionInfo.getInstance().log("start fine sync (with reset)");

		bloomList.clear();
		initAvgTs();
		bloom = new BloomFilter(SyncI.BLOOM_FILTER_LEN_BYTE, SyncI.N_HASHES);

		bloom.add(SessionInfo.getInstance().getMySelf().getId());
		bloomList.add(bloom);

		maxId = myId;
		workerThread = new FSyncServer(this);
		workerThread.start();

	}

	/**
	 * start the fine synchronization without doing a reset, performed when new
	 * peers come into play
	 */
	public void startWoReset() {
		stopSync();
		SessionInfo.getInstance().log("start fine sync (without reset)");
		initAvgTs();
		workerThread = new FSyncServer(this);
		workerThread.start();
	}

	/**
	 * hard resync, does reset everything (new synchronization round)
	 */
	public void reSync() {
		SessionInfo.getInstance().log("starting resynchronization");
		stopSync();
		startSync();
	}

	/**
	 * stop the fine synchronization
	 */
	public void stopSync() {
		if (serverRunning())
			workerThread.interrupt();
	}

	/**
	 * do process a fine sync request in a new thread
	 */
	public void processRequest(FSyncMsg fSyncMsg) {
		new FSResponseHandler(fSyncMsg, this, maxId).start();
	}

	/**
	 * align the average playback timestamp stored to a specific time stamp
	 * 
	 * @param alignTo
	 *            the time stamp to align to
	 * @return the aligned time stamp
	 */
	long alignedAvgTs(long alignTo) {
		return avgTs + alignTo - lastAvgUpdateTs;
	}

	/**
	 * initialize the avergae time stamp to the playback time
	 * 
	 * @return
	 */
	long initAvgTs() {
		avgTs = PlayerControl.getPlaybackTime();
		lastAvgUpdateTs = Clock.getTime();

		return avgTs;
	}

	/**
	 * update the average timstamp and reset the last update time stampt
	 * 
	 * @param newValue
	 *            the value to be set
	 */
	void updateAvgTs(long newValue, long updateTime) {
		SessionInfo.getInstance().log("waiting for avgMonitor");
		synchronized (avgMonitor) {
			SessionInfo.getInstance().log("got avgMonitor");
			avgTs = newValue;
			lastAvgUpdateTs = updateTime;
		}
		SessionInfo.getInstance().log("release avgMonitor");
	}

	/**
	 * flood the information so far to the known peers
	 * 
	 * @param nts
	 */
	void broadcastToPeers() {
		/* broadcast to known peers */
		long nts = Clock.getTime();
		FSyncMsg m = new FSyncMsg(alignedAvgTs(nts), nts, myId, bloom, maxId,
				SessionInfo.getInstance().getSeqN());

		if (DEBUG) {
			SessionInfo.getInstance().log("avg" + m.avg + "@" + m.nts);
			SessionInfo.getInstance()
					.log("pts" + PlayerControl.getPlaybackTime() + "@"
							+ Clock.getTime());
		}

		for (Peer p : SessionInfo.getInstance().getPeers().values()) {
			m.destAddress = p.getAddress();
			m.destPort = p.getPort();
			MessageHandler.getInstance().sendMsg(m);
		}
	}

	/**
	 * 
	 * @return
	 */
	BloomFilter getBloom() {
		return bloom;
	}

	/**
	 * 
	 * @return
	 */
	List<BloomFilter> getBloomList() {
		return bloomList;
	}

	/**
	 * 
	 * @return
	 */
	int getMaxId() {
		return maxId;
	}

	/**
	 * 
	 * @param maxId
	 */
	void setMaxId(int maxId) {
		this.maxId = maxId;
	}

	/**
	 * 
	 * @return
	 */
	public boolean serverRunning() {
		return workerThread != null && workerThread.isAlive();
	}

}
