/*
 * FSResponseHandler.java
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

import mf.bloomfilter.BloomFilter;
import mf.sync.net.FSyncMsg;
import mf.sync.utils.Clock;
import mf.sync.utils.PlayerControl;
import mf.sync.utils.SessionInfo;

/**
 * Handle fine sync responses
 *
 * @author stefan petscharnig
 *
 */
public class FSResponseHandler extends Thread {

	/** debug messages to the session log? */
	public static final boolean DEBUG = true;
	/** the message to process */
	private FSyncMsg msg;
	/** instance of FSync for avgts */
	private FSync parent;
	/** maximum id seen so far */
	private int maxId;
	/** own id */
	private int myId;
	/** bloom filter containing most information available */
	private BloomFilter bloom;

	/** count steps without update */
	static int noUpdateCtr = 0;

	/**
	 * Constructor
	 *
	 * @param fSyncMsg
	 * @param parent
	 * @param maxId
	 */
	public FSResponseHandler(FSyncMsg fSyncMsg, FSync parent, int maxId) {
		this.msg = fSyncMsg;
		myId = SessionInfo.getInstance().getMySelf().getId();
		this.maxId = maxId;
		this.parent = parent;
	}

	/**
	 * check the sequence number, if the sequence number received is bigger than
	 * the stored one, take the received sequence number and do a
	 * resynchronization
	 */
	private void checkSeqN() {

		if (msg.seqN > SessionInfo.getInstance().getSeqN()) {
			SessionInfo.getInstance().setSeqN(msg.seqN);
			FSync.getInstance().reSync(); // hard resync, reset
		} else {
			// SessionInfo.getInstance().log("(re) start wo reset");
			// FSync.getInstance().restartWoReset();
			// not here
		}
	}

	@Override
	public void run() {

		synchronized (FSync.getInstance()) {
			/* check sequence number */
			checkSeqN();

			bloom = parent.getBloom();
			if (bloom == null) {
				SessionInfo.getInstance().log(
						"bloom is null.. what the heck?!?");
				return;
			}
			/* some calculations in advance */

			int nPeersRcv = msg.bloom.getNElements();
			int nPeersOwn = bloom.getNElements();

			BloomFilter xor = msg.bloom.clone();
			xor.xor(bloom);
			BloomFilter and = msg.bloom.clone();
			and.and(bloom);

			boolean intersectContains;
			boolean contains = parent.getBloomList().contains(msg.bloom);
			long actTs, avgTs, wSum;

			boolean xorZero = xor.isZero();
			boolean andZero = and.isZero();

			if (!contains) {
				/* we have not seen the bloom filter */
				SessionInfo.getInstance().log(
						"seen a bf we have not see before, restart fine sync");
				FSync.getInstance().restartWoReset();
			}

			boolean bfUpdated = false;

			if (!xorZero && andZero) {
				if (DEBUG) {
					SessionInfo.getInstance().log("npeersown: " + nPeersOwn);
					SessionInfo.getInstance().log("npeersrcv: " + nPeersRcv);
				}

				actTs = Clock.getTime();
				avgTs = parent.alignedAvgTs(actTs);

				wSum = (avgTs * nPeersOwn) + (msg.avg + (actTs - msg.nts))
						* nPeersRcv;

				parent.updateAvgTs(wSum / (nPeersRcv + nPeersOwn), actTs);
				if (DEBUG) {
					SessionInfo.getInstance().log("my avg: " + avgTs);
					long d = (msg.avg + (actTs - msg.nts));
					SessionInfo.getInstance().log(
							"corrected received avg: " + d);
					SessionInfo.getInstance().log(
							"new avg: " + parent.alignedAvgTs(actTs));
				}
				updatePlayback();
				bloom.merge(msg.bloom);
				bfUpdated = true;
			}
			if (!xorZero && !andZero && !contains) {
				if (DEBUG) {
					SessionInfo.getInstance().log("npeersown: " + nPeersOwn);
					SessionInfo.getInstance().log("npeersrcv: " + nPeersRcv);
				}
				if (nPeersRcv >= nPeersOwn) {
					intersectContains = and.contains(myId);
					actTs = Clock.getTime();

					if (intersectContains) {
						parent.updateAvgTs(msg.avg + (actTs - msg.nts), actTs);
						if (DEBUG) {
							long d = (msg.avg + (actTs - msg.nts));
							SessionInfo.getInstance().log(
									"corrected received avg: " + d);
						}
						bloom = msg.bloom;
						bfUpdated = true;
					} else {
						avgTs = parent.alignedAvgTs(actTs);
						wSum = (nPeersRcv * (msg.avg + (actTs - msg.nts)))
								+ avgTs;
						parent.updateAvgTs(wSum / (nPeersRcv + 1), actTs);
						if (DEBUG) {
							SessionInfo.getInstance().log("my avg: " + avgTs);
							long d = (msg.avg + (actTs - msg.nts));
							SessionInfo.getInstance().log(
									"corrected received avg: " + d);
							SessionInfo.getInstance().log(
									"new avg: " + parent.alignedAvgTs(actTs));
						}
						bloom = msg.bloom;
						bloom.add(myId);
						bfUpdated = true;
					}
					updatePlayback();
				}
			}
			if (!bfUpdated)
				noUpdateCtr++;
			else
				noUpdateCtr = 0;

			if (noUpdateCtr > 50)
				FSync.getInstance().stopSync();

			/* add the received bloom filter to the ones already seen */
			parent.getBloomList().add(msg.bloom);
			/* update maxId */
			parent.setMaxId(maxId < msg.maxId ? msg.maxId : maxId);
		}
	}

	/**
	 * update the playback according to the information we got from fine
	 * synchronization this approach uses faster/slower for a given time in
	 * order to omit skips
	 */
	public void updatePlayback() {
		float newPlaybackRate;

		long pbt = PlayerControl.getPlaybackTime();
		long t = Clock.getTime();
		long asyncMillis = parent.alignedAvgTs(t) - pbt;

		if (DEBUG)
			SessionInfo.getInstance().log(
					"update playback time: calculated average: "
							+ parent.alignedAvgTs(t) + "@timestamp:" + t
							+ "@async:" + asyncMillis + "@pbt:" + pbt);

		/* the *3* come from the pre-calculation see paper */
		long timeMillis = 3 * Math.abs(asyncMillis);

		if (DEBUG)
			SessionInfo.getInstance().log("ensure buffered start");

		if (asyncMillis > 0) { // we are behind, go faster
			newPlaybackRate = 1.33f;// (float) 4 / 3; //precalculated, see paper
			/*
			 * if we go faster, we want to ensure that we have buffered some
			 * data...
			 */
			PlayerControl.ensureBuffered(3 * timeMillis);
		} else { // we are on top, so do slower
			newPlaybackRate = 0.66f;// (float) 2 / 3; //precalculated, see paper
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

		PlayerControl.setPlaybackRate(newPlaybackRate); // adjust playback rate

		try {
			Thread.sleep(timeMillis); // wait
		} catch (InterruptedException e) {
			PlayerControl.setPlaybackRate(1);
			if (DEBUG)
				SessionInfo.getInstance().log(
						"got interrupted, synchronization failed");
		}

		PlayerControl.setPlaybackRate(1); // reset the playback rate to normal
	}

	/**
	 * method for updating the playback, when we are in a sufficient range to
	 * the timestamp to update for (e.g. 2,3 frames) an update would bring us
	 * out of sync. Moreover we assume that we skip a range of 2000ms at a
	 * maximum and wait, until our buffer has so much in advance
	 */
	@Deprecated
	public void updatePlayback(boolean noSkipIfNear) {
		if (noSkipIfNear
				&& Math.abs((int) parent.alignedAvgTs(Clock.getTime())
						- PlayerControl.getPlaybackTime()) < 80) {
			// dont do something, we are close enough^^
			if (DEBUG)
				SessionInfo.getInstance().log("we are close enough @@");
			return;
		}

		PlayerControl.ensureBuffered(4000);

		if (DEBUG)
			SessionInfo.getInstance().log("setting time @@@");
		PlayerControl
				.setPlaybackTime((int) parent.alignedAvgTs(Clock.getTime()));

	}
}
