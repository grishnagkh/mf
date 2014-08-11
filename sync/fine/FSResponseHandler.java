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
import mf.sync.utils.SessionInfo;
import mf.sync.utils.Utils;

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

			int nPeersRcv = msg.bloom.nElements(msg.maxId);
			int nPeersOwn = bloom.nElements(maxId);

			BloomFilter xor = msg.bloom.clone();
			xor.xor(bloom);
			BloomFilter and = msg.bloom.clone();
			and.and(bloom);

			boolean intersectContains;
			boolean contains = parent.getBloomList().contains(msg.bloom);
			long actTs, avgTs, wSum;

			boolean xorZero = xor.isZero();
			boolean andZero = and.isZero();

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
				bloom.or(msg.bloom);
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
					}

					updatePlayback();
				}
			}
			/* add the received bloom filter to the ones already seen */
			parent.getBloomList().add(msg.bloom);
			/* update maxId */
			parent.setMaxId(maxId < msg.maxId ? msg.maxId : maxId);
		}
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
		} else if (!FSync.getInstance().serverRunning()) {
			SessionInfo.getInstance().log("(re) start wo reset");
			FSync.getInstance().startWoReset();
		}
	}

	public void updatePlayback() {

		float newPlaybackRate;

		long pbt = Utils.getPlaybackTime();
		long t = Clock.getTime();
		long asyncMillis = parent.alignedAvgTs(t) - pbt;

		SessionInfo.getInstance().log(
				"update playback time: calculated average: "
						+ parent.alignedAvgTs(t) + "@timestamp:" + t
						+ "@async:" + asyncMillis + "@pbt:" + pbt);

		long timeMillis = 3 * Math.abs(asyncMillis);

		if (asyncMillis > 0) {
			newPlaybackRate = 1.33f;// (float) 4 / 3;
		} else {
			newPlaybackRate = 0.66f;// (float) 2 / 3;
		}

		if (DEBUG)
			SessionInfo.getInstance().log(
					"asynchronism: " + asyncMillis + "ms\nnew playback rate: "
							+ newPlaybackRate + "\ntime changed: " + timeMillis
							+ "ms");

		if (newPlaybackRate > 1) {
			// if we go faster, we want to ensure that we have buffered a lot
			Utils.ensureBuffered(4 * timeMillis);
		} else {
			// despite it is theoretically not necessary, ensure we have
			// buffered at least a bit
			Utils.ensureBuffered(timeMillis);
		}
		Utils.setPlaybackRate(newPlaybackRate);

		try {
			Thread.sleep((long) (timeMillis));
		} catch (InterruptedException e) {
			Utils.setPlaybackRate(1);
			SessionInfo.getInstance().log("got interrupted, skip to val");
			updatePlayback(true);
		}

		Utils.setPlaybackRate(1);

		if (DEBUG) {

			try {
				Thread.sleep(700);
			} catch (Exception e) {
			}
			asyncMillis = (parent.alignedAvgTs(Clock.getTime()) - Utils
					.getPlaybackTime());
			SessionInfo.getInstance().log(
					"asynchronism after playback adjustment: " + asyncMillis);

		}

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
						- Utils.getPlaybackTime()) < 80) {
			// dont do something, we are close enough^^
			if (DEBUG)
				SessionInfo.getInstance().log("we are close enough @@");
			return;
		}

		Utils.ensureBuffered(4000);

		if (DEBUG)
			SessionInfo.getInstance().log("setting time @@@");
		Utils.setPlaybackTime((int) parent.alignedAvgTs(Clock.getTime()));

	}
}
