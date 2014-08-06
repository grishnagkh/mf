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
	private BloomFilter bloom;

	public FSResponseHandler(FSyncMsg fSyncMsg, FSync parent, int maxId) {

		this.msg = fSyncMsg;

		myId = SessionInfo.getInstance().getMySelf().getId();
		this.maxId = maxId;
		this.parent = parent;
	}

	public void run() {

		/* check sequence number, if received seq n > stored seq n -> resync */
		synchronized (parent) {
			checkSeqN();

			bloom = parent.getBloom();
			if (bloom == null) {
				SessionInfo.getInstance().log(
						"bloom is null.. what the heck?!?");
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

			boolean contains = parent.getBloomList().contains(msg.bloom);

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
		if (Math.abs((int) parent.alignAvgTs(Clock.getTime())
				- Utils.getPlaybackTime()) < 80) {
			// dont do something, we are close enough^^
			SessionInfo.getInstance().log("we are close enough @@");
			return;
		}

		// enforce only skipping to positions which are buffered...
		while ((int) parent.alignAvgTs(Clock.getTime()) > Utils.getBufferPos() - 2000) {
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
			}
		}
		SessionInfo.getInstance().log("setting time @@@");
		Utils.setPlaybackTime((int) parent.alignAvgTs(Clock.getTime()));

	}
}
