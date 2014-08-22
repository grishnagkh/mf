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

/**
 * Handle fine sync responses
 *
 * @author stefan petscharnig
 *
 */
public class FSResponseHandler extends Thread {
	/** debug messages to the session log? */
	public static final boolean DEBUG = true;
	private static final boolean CHECK_SEQ_N = false;
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
		// updateThread = new PAThread(parent);
	}

	/**
	 * check the sequence number, if the sequence number received is bigger than
	 * the stored one, take the received sequence number and do a
	 * resynchronization
	 */
	private void checkSeqN() {
		if (msg.seqN > SessionInfo.getInstance().getSeqN())
			SessionInfo.getInstance().setSeqN(msg.seqN);
		// TODO: resync hard
		else {
		}
	}

	@Override
	public void run() {

		synchronized (FSync.getInstance()) {
			/* check sequence number */
			if (CHECK_SEQ_N)
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
				if (!FSync.getInstance().restart())
					FSync.getInstance().start();
			}

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
				bloom.merge(msg.bloom);
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
				}
			}

			/* add the received bloom filter to the ones already seen */
			parent.getBloomList().add(msg.bloom);
			/* update maxId */
			parent.setMaxId(maxId < msg.maxId ? msg.maxId : maxId);
		}
	}
}
