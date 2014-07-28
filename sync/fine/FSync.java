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
import mf.sync.net.MessageHandler;
import mf.sync.utils.Peer;
import mf.sync.utils.SessionInfo;
import mf.sync.utils.SyncI;
import mf.sync.utils.Utils;
import android.util.Log;

/**
 * 
 * Class handling the fine synchronization using mf algorithm
 * 
 * @author stefan petscharnig
 *
 */
public class FSync implements SyncI {

	/** actual bloom filter */
	private BloomFilter<Integer> bloom;
	/** a list of seen bloom filters */
	private List<BloomFilter<Integer>> bloomList;

	/** time when the last avgTs was received */
	private long lastAvgUpdateTs; // the time of the last avg ts update
	/** average time stamp at time oldTs */
	private long avgTs;
	private int maxId;
	private int myId;
	/** singleton instance */
	private static FSync instance;

	private Thread workerThread;
	private Object avgMonitor;

	// /** stop the message sending */
	// private boolean fineSyncNecessary = true;

	/**
	 * Constructor
	 */
	private FSync() {
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
		Log.d(TAG_FS, "want to start fine sync thread");
		workerThread = new FSWorker(this);
		workerThread.start();
	}

	public void reSync() {
		stopSync();
		startSync();
	}

	public void stopSync() {
		if (workerThread != null && workerThread.isAlive())
			workerThread.interrupt();
	}

	/**
	 * do process a fine sync request in a new thread
	 */
	public void processRequest(String msg) {
		new FSResponseHandler(msg, this, maxId).start();
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
		bloom = new BloomFilter<Integer>(BITS_PER_ELEM, N_EXP_ELEM, N_HASHES);
		bloom.add(SessionInfo.getInstance().getMySelf().getId());
		bloomList.add(bloom);
	}

	void broadcastToPeers(long nts) {
		// broadcast to known peers
		for (Peer p : SessionInfo.getInstance().getPeers().values()) {
			String msg = Utils.buildMessage(SyncI.DELIM, SyncI.TYPE_FINE,
					avgTs, nts, myId, Utils.toString(bloom.getBitSet()), maxId);
			try {
				MessageHandler.getInstance().sendMsg(msg, p.getAddress(),
						p.getPort());
			} catch (SocketException e) {
				// ignore
			} catch (IOException e) {
				// ignore
			}
		}
	}

	BloomFilter<Integer> getBloom() {
		return bloom;
	}

	public List<BloomFilter<Integer>> getBloomList() {
		return bloomList;
	}

	public void setMaxId(int maxId) {
		this.maxId = maxId;
	}

	/**
	 * process fine sync responses
	 * 
	 * @author stefan petscharnig
	 *
	 */
}
