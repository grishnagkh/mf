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

	/** maximum peer id seen so far */
	private int maxId;
	/** own peer id */
	private int myId;
	/** singleton instance */
	private static FSync instance;

	public static final boolean DEBUG = true;

	private final boolean SKIP_TO_AVERAGE = false;

	/**
	 * Constructor
	 */
	private FSync() {
		try {

			myId = SessionInfo.getInstance().getMySelf().getId();
			maxId = myId;

		} catch (Exception e) {
			// i think this is the case, when sessioninfo was cleared and fsync
			// interrupted in this order
			instance = null;
		}
	}

	/**
	 * flood the information so far to the known peers
	 *
	 * @param nts
	 */
	void broadcastToPeers() {

		/* broadcast to known peers */
		long nts = Clock.getTime();
		FSyncMsg m = new FSyncMsg(SessionInfo.getInstance().alignedAvgTs(nts),
				nts, myId, SessionInfo.getInstance().getBloom(), maxId,
				SessionInfo.getInstance().getSeqN());

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
	int getMaxId() {
		return maxId;
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

	public boolean restart() {
		// everything except for the bloom filters and bloom filter lists will
		// be cleared
		if (DEBUG) {
			SessionInfo.getInstance().log("start fine sync (without reset)");
		}


		try {
			start();
		} catch (Exception e) {
			interrupt();
			return false;
		}
		return true;
	}

	@Override
	public void run() {
		if (DEBUG) {
			SessionInfo.getInstance().log("FSync thread started");
		}
		while (!isInterrupted()) {
			try {
				Thread.sleep(SyncI.PERIOD_FS_MS);
			} catch (InterruptedException iex) {
				break;
			}
			broadcastToPeers();
			if (Clock.getTime()
					- SessionInfo.getInstance().getLastAvgUpdateTs() > (SessionInfo
					.getInstance().getPeers().size() + 2)
					* SyncI.PERIOD_FS_MS) {

				long pbt = PlayerControl.getPlaybackTime();
				long now = Clock.getTime();
				long asyncMillis = SessionInfo.getInstance().alignedAvgTs(now)
						- pbt;
				if (DEBUG) {
					SessionInfo.getInstance().log(
							"updating playback time: calculated average: "
									+ SessionInfo.getInstance().alignedAvgTs(
											now) + "@timestamp:" + now
									+ "@async:" + asyncMillis + "@pbt:" + pbt);
				}
				// wait a sec
				PlayerControl.ensureTime(PlayerControl.getPlaybackTime(), 1000);
				updatePlayback(asyncMillis);
				instance = null;
				break;
			}
		}
		if (DEBUG) {
			SessionInfo.getInstance().log("FSync thread died");
		}
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

	public void updatePlayback(long asyncMillis) {
		if (SKIP_TO_AVERAGE) {
			PlayerControl.setPlaybackTime((int) SessionInfo.getInstance()
					.alignedAvgTs(Clock.getTime()));
			return;
		}

		/* the *3* come from the pre-calculation see paper */
		long timeMillis = 3 * Math.abs(asyncMillis);

		if (DEBUG) {
			SessionInfo.getInstance().log("ensure buffered start");
		}

		float newPlaybackRate;

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

		if (DEBUG) {
			SessionInfo.getInstance().log("ensure buffered end");
		}

		if (DEBUG) {
			SessionInfo.getInstance().log(
					"asynchronism: " + asyncMillis + "ms\tnew playback rate: "
							+ newPlaybackRate + "\ttime changed: " + timeMillis
							+ "ms");
		}

		PlayerControl.setPlaybackRate(newPlaybackRate); // adjust playback
		// rate

		try {
			Thread.sleep(timeMillis); // wait
		} catch (InterruptedException e) {
			if (DEBUG) {
				SessionInfo.getInstance().log(
						"got interrupted, synchronization failed");
			}
		} finally {
			// reset the playback rate
			PlayerControl.setPlaybackRate(1);
		}
	}
}