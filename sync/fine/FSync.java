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
import java.util.ArrayList;
import java.util.List;

import mf.bloomfilter.BloomFilter;
import mf.sync.SyncI;
import mf.sync.net.FSyncMsg;
import mf.sync.net.MessageHandler;
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
	private BloomFilter<Integer> bloom;
	/** a list of seen bloom filters */
	private List<BloomFilter<Integer>> bloomList;

	/** time when the last avgTs update */
	private long lastAvgUpdateTs;
	/** average time stamp at time oldTs */
	private long avgTs;
	private int maxId;
	private int myId;
	/** singleton instance */
	private static FSync instance;

	private Thread workerThread;
	private Object avgMonitor;

	/**
	 * Constructor
	 */
	private FSync() {
		avgMonitor = new Object();
		bloomList = new ArrayList<BloomFilter<Integer>>();
		myId = SessionInfo.getInstance().getMySelf().getId();
		maxId = myId;
		avgMonitor = this;
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
		initBloom();
		// reset [for resync]
		bloomList.clear();
		maxId = myId;
		workerThread = new FSyncServer(this);
		workerThread.start();
	}

	public void reSync() {
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
	 * sent a message periodically to the neighbour
	 * 
	 * @author stefan petscharnig
	 *
	 */

	long alignAvgTs(long alignTo) {
		synchronized (avgMonitor) {
			avgTs += alignTo - lastAvgUpdateTs; // align avgTs
			lastAvgUpdateTs = Utils.getTimestamp();
		}
		return avgTs;
	}

	long initAvgTs() {
		synchronized (avgMonitor) {
			avgTs = Utils.getPlaybackTime();
			lastAvgUpdateTs = Utils.getTimestamp();
		}
		return avgTs;
	}

	void updateAvgTs(long newValue) {
		synchronized (avgMonitor) {
			avgTs = newValue;
			lastAvgUpdateTs = Utils.getTimestamp();
		}
	}

	private void initBloom() {
		bloom = new BloomFilter<Integer>(SyncI.BITS_PER_ELEM, SyncI.N_EXP_ELEM,
				SyncI.N_HASHES);
		bloom.add(SessionInfo.getInstance().getMySelf().getId());
		bloomList.add(bloom);
	}

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

	BloomFilter<Integer> getBloom() {
		return bloom;
	}

	List<BloomFilter<Integer>> getBloomList() {
		return bloomList;
	}

	int getMaxId() {
		return maxId;
	}

	void setMaxId(int maxId) {
		this.maxId = maxId;
	}

	public boolean serverRunning() {
		return workerThread != null && workerThread.isAlive();
	}
}
