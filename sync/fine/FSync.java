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
public class FSync extends Thread {

	/**
	 * singleton method
	 *
	 * @return
	 */
	public static FSync getInstance() {
		instance = instance == null ? new FSync() : instance;
		return instance;
	}

	/** actual bloom filter */
	private static BloomFilter bloom;

	/** a list of seen bloom filters */
	private static List<BloomFilter> bloomList;

	static {
		bloomList = new ArrayList<BloomFilter>();
		bloom = new BloomFilter(SyncI.BLOOM_FILTER_LEN_BYTE, SyncI.N_HASHES);
	}

	/*
	 * TODO: rewrite sstart and stop o f the threads, set playback time in the
	 * player control (in a new thread) damit des nit warten muss falls neue
	 * infos da sind... macht den synchronized block zeitlich kürzer...
	 */

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
	// /** periodical broadcast server */
	// private Thread workerThread;
	/** monitor for the avg value */
	private Object avgMonitor;
	public static final boolean DEBUG = true;

	/**
	 * Constructor
	 */
	private FSync() {
		try {
			avgMonitor = new Object();

			myId = SessionInfo.getInstance().getMySelf().getId();

			maxId = myId;
			avgMonitor = this;
			bloom.add(SessionInfo.getInstance().getMySelf().getId());
			bloomList.add(bloom);
			initAvgTs();
		} catch (Exception e) {
			// i think this is the case, when sessioninfo was cleared and fsync
			// interrupted in this order
			instance = null;
		}
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
	 * flood the information so far to the known peers
	 *
	 * @param nts
	 */
	void broadcastToPeers() {
		try {
			if (PlayerControl.getSpeed() != 1)
				// do not broadcast you timestamp, when your playback time
				// advances
				// differently
				// (playback speed != 1)
				return;
		} catch (Exception e) {
			return;
		}
		/* broadcast to known peers */
		long nts = Clock.getTime();
		FSyncMsg m = new FSyncMsg(alignedAvgTs(nts), nts, myId, bloom, maxId,
				SessionInfo.getInstance().getSeqN());

		if (DEBUG) {
			SessionInfo.getInstance().log("avg" + m.avg + "@" + m.nts);
			SessionInfo.getInstance().log(
					"pts" + PlayerControl.getPlaybackTime() + "@"
							+ Clock.getTime());
		}

		for (Peer p : SessionInfo.getInstance().getPeers().values()) {
			m.destAddress = p.getAddress();
			m.destPort = p.getPort();
			MessageHandler.getInstance().sendMsg(m);
		}
	}

	// test
	// public void destroy() {
	// instance = null;
	// }
	//
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
	 * initialize the avergae time stamp to the playback time
	 *
	 * @return
	 */
	long initAvgTs() {
		avgTs = PlayerControl.getPlaybackTime();
		lastAvgUpdateTs = Clock.getTime();

		return avgTs;
	}

	@Override
	public void interrupt() {
		instance = null;
		super.interrupt();
	}

	/**
	 * do process a fine sync request in a new thread
	 */
	public void processRequest(FSyncMsg fSyncMsg) {
		new FSResponseHandler(fSyncMsg, this, maxId).start();
	}

	public void reset() {
		bloom = new BloomFilter();
		bloomList = new ArrayList<BloomFilter>();
	}

	public boolean restart() {
		// everything except for the bloom filters and bloom filter lists will
		// be cleared
		if (DEBUG)
			SessionInfo.getInstance().log("start fine sync (without reset)");

		initAvgTs();

		try {
			start();
		} catch (Exception e) {
			interrupt();
			return false;
		}
		return true;
	}

	// /**
	// * start the fine synchronization without doing a reset, performed when
	// new
	// * peers come into play
	// */
	// public void restartWoReset() {
	// stopSync();
	// if (DEBUG)
	// SessionInfo.getInstance().log("start fine sync (without reset)");
	//
	// initAvgTs();
	// workerThread = new FSyncServer(this);
	// workerThread.start();
	// }
	//
	// /**
	// * hard resync, does reset everything (new synchronization round)
	// */
	// public void reSync() {
	// if (DEBUG)
	// SessionInfo.getInstance().log("starting resynchronization");
	// stopSync();
	// startSync();
	// }
	//
	// /**
	// *
	// * @return
	// */
	// public boolean serverRunning() {
	// return workerThread != null && workerThread.isAlive();
	// }

	@Override
	public void run() {
		if (DEBUG)
			SessionInfo.getInstance().log("FSync thread started");
		while (!isInterrupted()) {
			try {
				Thread.sleep(SyncI.PERIOD_FS_MS);
			} catch (InterruptedException iex) {
				break;
			}
			broadcastToPeers();
			if (Clock.getTime() - lastAvgUpdateTs > (SessionInfo.getInstance()
					.getPeers().size() + 3)
					* SyncI.PERIOD_FS_MS) {

				long pbt = PlayerControl.getPlaybackTime();
				long t = Clock.getTime();
				long asyncMillis = alignedAvgTs(t) - pbt;
				if (DEBUG)
					SessionInfo.getInstance().log(
							"updating playback time: calculated average: "
									+ alignedAvgTs(t) + "@timestamp:" + t
									+ "@async:" + asyncMillis + "@pbt:" + pbt);

				updatePlayback(asyncMillis);
				instance = null;
				break;
			}
		}
		if (DEBUG)
			SessionInfo.getInstance().log("FSync thread died");
	}

	/**
	 *
	 * @param maxId
	 */
	void setMaxId(int maxId) {
		this.maxId = maxId;
	}

	@Override
	public void start() {
		super.start();
	}

	/**
	 * update the average timstamp and reset the last update time stampt
	 *
	 * @param newValue
	 *            the value to be set
	 */
	void updateAvgTs(long newValue, long updateTime) {
		if (DEBUG)
			SessionInfo.getInstance().log("waiting for avgMonitor");
		synchronized (avgMonitor) {
			if (DEBUG)
				SessionInfo.getInstance().log("got avgMonitor");
			avgTs = newValue;
			lastAvgUpdateTs = updateTime;
		}
		if (DEBUG)
			SessionInfo.getInstance().log("release avgMonitor");
	}

	public void updatePlayback(long asyncMillis) {
		float newPlaybackRate;

		/* the *3* come from the pre-calculation see paper */
		long timeMillis = 3 * Math.abs(asyncMillis);

		if (DEBUG)
			SessionInfo.getInstance().log("ensure buffered start");

		if (asyncMillis > 0) { // we are behind, go faster
			newPlaybackRate = 1.33f;// (float) 4 / 3; //precalculated, see
			// paper
			/*
			 * if we go faster, we want to ensure that we have buffered some
			 * data...
			 */
			PlayerControl.ensureBuffered(3 * timeMillis);
		} else { // we are on top, so do slower
			newPlaybackRate = 0.66f;// (float) 2 / 3; //precalculated, see
			// paper
			/*
			 * despite it is theoretically not necessary, ensure we have
			 * buffered at least a bit
			 */
			PlayerControl.ensureBuffered(timeMillis);
		}

		if (DEBUG)
			SessionInfo.getInstance().log("ensure buffered end");

		if (DEBUG)
			SessionInfo.getInstance().log(
					"asynchronism: " + asyncMillis + "ms\tnew playback rate: "
							+ newPlaybackRate + "\ttime changed: " + timeMillis
							+ "ms");

		PlayerControl.setPlaybackRate(newPlaybackRate); // adjust playback
		// rate

		try {
			Thread.sleep(timeMillis); // wait
		} catch (InterruptedException e) {
			if (DEBUG)
				SessionInfo.getInstance().log(
						"got interrupted, synchronization failed");
		} finally {
			// reset the playback rate
			PlayerControl.setPlaybackRate(1);
		}
	}
	// /**
	// * start fine sync message sending in a new thread
	// */
	// public void startSync() {
	// if (DEBUG)
	// SessionInfo.getInstance().log("start fine sync (with reset)");
	//
	// bloomList.clear();
	// initAvgTs();
	// bloom = new BloomFilter(SyncI.BLOOM_FILTER_LEN_BYTE, SyncI.N_HASHES);
	//
	// bloom.add(SessionInfo.getInstance().getMySelf().getId());
	// bloomList.add(bloom);
	//
	// maxId = myId;
	// workerThread = new FSyncServer(this);
	// workerThread.start();
	//
	// }
	//
	// /**
	// * stop the fine synchronization
	// */
	// public void stopSync() {
	// if (serverRunning())
	// workerThread.interrupt();
	// }

}
