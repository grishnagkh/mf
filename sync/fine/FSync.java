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

import java.io.IOException;
import java.net.SocketException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import mf.bloomfilter.BloomFilter;
import mf.sync.SyncI;
import mf.sync.net.FSyncMsg;
import mf.sync.net.MessageHandler;
import mf.sync.utils.Clock;
import mf.sync.utils.Peer;
import mf.sync.utils.SessionInfo;
import mf.sync.utils.Utils;

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
	private long lastAvgUpdateTs;
	/** average time stamp at time oldTs */
	private long avgTs;
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

	/**
	 * Constructor
	 */
	private FSync() {

		avgMonitor = new Object();
		bloomList = new ArrayList<BloomFilter>();
		myId = SessionInfo.getInstance().getMySelf().getId();
		maxId = myId;
		avgMonitor = this;
		try {
			bloom = new BloomFilter(SyncI.BLOOM_FILTER_LEN_BYTE, SyncI.N_HASHES);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		bloom.add(SessionInfo.getInstance().getMySelf().getId());
		bloomList.add(bloom);

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
	 * 
	 * @throws NoSuchAlgorithmException
	 */
	public void startSync() {

		initAvgTs();
		bloomList.clear();
		try {
			bloom = new BloomFilter(SyncI.BLOOM_FILTER_LEN_BYTE, SyncI.N_HASHES);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		bloom.add(SessionInfo.getInstance().getMySelf().getId());
		bloomList.add(bloom);

		maxId = myId;
		workerThread = new FSyncServer(this);
		workerThread.start();

	}

	public void reSync() throws NoSuchAlgorithmException {
		SessionInfo.getInstance().log("starting resynchronization");
		stopSync();

		startSync();

	}

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
	 * Caution: does save the aligned value as a side effect! (TODO: have a
	 * thought whether this is really necessary)
	 * 
	 * @param alignTo
	 *            the time stamp to align to
	 * @return the aligned time stamp
	 */
	long alignAvgTs(long alignTo) {
		synchronized (avgMonitor) {
			avgTs += alignTo - lastAvgUpdateTs; // align avgTs
			// lastAvgUpdateTs = Utils.getTimestamp();
			lastAvgUpdateTs = Clock.getTime();

		}
		return avgTs;
	}

	/**
	 * initialize the avergae time stamp to the playback time
	 * 
	 * @return
	 */
	long initAvgTs() {
		updateAvgTs(Utils.getPlaybackTime());
		return avgTs;
	}

	/**
	 * update the average timstamp and reset the last update time stampt
	 * 
	 * @param newValue
	 *            the value to be set
	 */
	void updateAvgTs(long newValue) {
		synchronized (avgMonitor) {
			avgTs = newValue;
			lastAvgUpdateTs = Clock.getTime();
		}
	}

	/**
	 * flood the information so far to the known peers
	 * 
	 * @param nts
	 */
	void broadcastToPeers(long nts) {
		/* broadcast to known peers */
		FSyncMsg m = new FSyncMsg(avgTs, nts, myId, bloom, maxId, SessionInfo
				.getInstance().getSeqN());

		String msg = m.getMessageString(SyncI.DELIM, SyncI.TYPE_FINE);

		for (Peer p : SessionInfo.getInstance().getPeers().values()) {
			try {
				MessageHandler.getInstance().sendMsg(msg, p.getAddress(),
						p.getPort());
			} catch (SocketException e) {
				/* ignore */
			} catch (IOException e) {
				/* ignore */
			}
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
