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
import mf.sync.utils.SessionInfo;
import mf.sync.utils.Utils;

public class FSResponseHandler extends Thread {

	private FSyncMsg msg;

	public static final String TAG = "FSResponse Handler";
	private static final boolean DEBUG_OVERLAY = true;
	private FSync parent;

	private int maxId;
	private int myId;

	public FSResponseHandler(FSyncMsg fSyncMsg, FSync parent, int maxId) {

		this.msg = fSyncMsg;

		myId = SessionInfo.getInstance().getMySelf().getId();
		this.maxId = maxId;

		this.parent = parent;
	}

	public void run() {

		if (msg.seqN > SessionInfo.getInstance().getSeqN()) {
			SessionInfo.getInstance().setSeqN(msg.seqN);
			FSync.getInstance().reSync();
		}

		synchronized (parent) {

			BloomFilter<Integer> bloom = parent.getBloom();
			if (bloom == null) {
				SessionInfo.getInstance().log(
						"bloom is null.. what the heck?!?");
				return;
			}
			int nPeersRcv = Utils.getN(msg.bloom, msg.maxId);
			int nPeersOwn = Utils.getN(bloom, maxId);

			if (DEBUG_OVERLAY) {
				SessionInfo.getInstance().log(
						"#peers in received bf: " + nPeersRcv + ", maxId: "
								+ msg.maxId);
				SessionInfo.getInstance().log(
						"#peers in stored bf: " + nPeersOwn + ", maxId: "
								+ maxId);
			}

			long actTs = Utils.getTimestamp();
			long avgTs = parent.alignAvgTs(actTs);

			if (Utils.xor(msg.bloom, bloom)) {
				/* the bloom filters are different in at least one position */

				if (DEBUG_OVERLAY)
					SessionInfo.getInstance()
							.log("bloom filters are different");
				if (!Utils.and(msg.bloom, bloom)) {

					/*
					 * the bloom filters do not overlap
					 */

					/* merge the filters */
					bloom.getBitSet().or(msg.bloom.getBitSet());
					/* update average */
					long wSum = (avgTs * nPeersOwn + (msg.avg + (actTs - msg.ntp))
							* nPeersRcv);
					parent.updateAvgTs(wSum / (nPeersRcv + nPeersOwn));

				} else if (!parent.getBloomList().contains(msg.bloom)) {
					if (DEBUG_OVERLAY)
						SessionInfo
								.getInstance()
								.log("bloom filters do overlap && we have seen this bloom filter before");
					if (nPeersRcv >= nPeersOwn) {
						if (DEBUG_OVERLAY)
							SessionInfo
									.getInstance()
									.log("bloom filters do overlap and the received one contains more information");
						/*
						 * overlap and received bloom filter has more
						 * information
						 */

						bloom.setBitSet(msg.bloom.getBitSet());

						if (bloom.contains(myId)) {
							if (DEBUG_OVERLAY)
								SessionInfo
										.getInstance()
										.log("this peer already is in the filter, so set the received average");
							/* correct received average */
							parent.updateAvgTs(msg.avg + (actTs - msg.ntp));
						} else {
							if (DEBUG_OVERLAY)
								SessionInfo
										.getInstance()
										.log("this peer is not in the filter, so correct the received average and add ourself: "
												+ myId);
							/* add own timestamp to received one */
							long wSum = (nPeersRcv
									* (msg.avg + (actTs - msg.ntp)) + avgTs);
							parent.updateAvgTs(wSum / (nPeersRcv + 1));
							bloom.add(myId);
						}
					}
				}

			} else {
				if (DEBUG_OVERLAY)
					SessionInfo.getInstance().log("same bloom filters");
				/* the same bloom filters: ignore; time stamps must be equal */
			}
		}
		/* add the received bloom filter to the ones already seen */
		parent.getBloomList().add(msg.bloom);
		/* update maxId */
		parent.setMaxId(maxId < msg.maxId ? msg.maxId : maxId);
	}
}
