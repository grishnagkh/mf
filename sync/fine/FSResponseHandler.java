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

import java.security.NoSuchAlgorithmException;

import mf.bloomfilter.BloomFilter;
import mf.sync.net.FSyncMsg;
import mf.sync.utils.Clock;
import mf.sync.utils.SessionInfo;
import mf.sync.utils.Utils;

public class FSResponseHandler extends Thread {

	private FSyncMsg msg;

	public static final String TAG = "FSResponse Handler";
	private FSync parent;

	private int maxId;
	private int myId;

	public FSResponseHandler(FSyncMsg fSyncMsg, FSync parent, int maxId) {

		this.msg = fSyncMsg;

		myId = SessionInfo.getInstance().getMySelf().getId();
		this.maxId = maxId;

		this.parent = parent;
	}

	BloomFilter bloom;

	public void run() {

		checkSeqN();

		bloom = parent.getBloom();
		if (bloom == null) {
			SessionInfo.getInstance().log("bloom is null.. what the heck?!?");
			return;
		}

		int nPeersRcv = msg.bloom.nElements(msg.maxId);
		int nPeersOwn = bloom.nElements(maxId);

		BloomFilter xor = msg.bloom.clone();
		BloomFilter and = msg.bloom.clone();
		xor.xor(bloom);
		and.and(bloom);

		boolean xorZero = xor.isZero();
		boolean andZero = and.isZero();
		boolean intersectContains = and.contains(myId);

		synchronized (parent) {

			boolean contains = parent.getBloomList().contains(msg.bloom);

			// long actTs = Utils.getTimestamp();
			long actTs = Clock.getTime();

			long avgTs = parent.alignAvgTs(actTs);

			if (!xorZero && andZero) {
				bloom.or(msg.bloom);
				long wSum = (avgTs * nPeersOwn) + (msg.avg + (actTs - msg.nts))
						* nPeersRcv;
				parent.updateAvgTs(wSum / (nPeersRcv + nPeersOwn));
				updatePlayback();
			}

			if (!xorZero && !andZero && !contains) {
				if (nPeersRcv >= nPeersOwn) {
					if (intersectContains) {
						parent.updateAvgTs(msg.avg + (actTs - msg.nts));
						bloom = msg.bloom;
					} else {
						long wSum = (nPeersRcv * (msg.avg + (actTs - msg.nts)))
								+ avgTs;
						parent.updateAvgTs(wSum / (nPeersRcv + 1));
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

	private void checkSeqN() {
		if (msg.seqN > SessionInfo.getInstance().getSeqN()) {
			SessionInfo.getInstance().setSeqN(msg.seqN);
			try {
				FSync.getInstance().reSync();
			} catch (NoSuchAlgorithmException e) {
				SessionInfo.getInstance().log(
						"no such algorithm.. what the heck?!?");
			}
		}

	}

	public void updatePlayback() {
		// long t = Utils.getTimestamp();
		long t = Clock.getTime();
		int t1 = (int) parent.alignAvgTs(t);
		SessionInfo.getInstance().log("setting time: " + t1 + " @ " + t);
		Utils.setPlaybackTime(t1);
	}
}
