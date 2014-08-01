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
import mf.sync.utils.SessionInfo;
import mf.sync.utils.SyncI;
import mf.sync.utils.Utils;

public class FSResponseHandler extends Thread {
	private String msg;
	public static final String TAG = "FSResponse Handler";
	private static final boolean DEBUG_OVERLAY = true;
	private FSync parent;

	private int maxId;
	private int myId;

	public FSResponseHandler(String msg, FSync parent, int maxId) {
		myId = SessionInfo.getInstance().getMySelf().getId();
		this.maxId = maxId;
		this.msg = msg;
		this.parent = parent;
	}

	public void run() {

		String[] msgA = msg.split(SyncI.DELIM);
		BloomFilter<Integer> rcvBF = new BloomFilter<Integer>(
				SyncI.BITS_PER_ELEM, SyncI.N_EXP_ELEM, SyncI.N_HASHES);

		long rAvg = Long.parseLong(msgA[1]);
		long rNtp = Long.parseLong(msgA[2]);

		rcvBF.setBitSet(Utils.fromString(msgA[4]));
		int paketMax = Integer.parseInt(msgA[5]);

		synchronized (parent) {

			BloomFilter<Integer> bloom = parent.getBloom();
			if (bloom == null) {
				SessionInfo.getInstance().log(
						"bloom is null.. what the heck?!?");
				return;
			}
			int nPeersRcv = Utils.getN(rcvBF, paketMax);
			int nPeersOwn = Utils.getN(bloom, maxId);

			if (DEBUG_OVERLAY) {
				SessionInfo.getInstance().log(
						"#peers in received bf: " + nPeersRcv + ", maxId: "
								+ paketMax);
				SessionInfo.getInstance().log(
						"#peers in stored bf: " + nPeersOwn + ", maxId: "
								+ maxId);
			}

			long actTs = Utils.getTimestamp();
			long avgTs = parent.alignAvgTs(actTs);

			if (Utils.xor(rcvBF, bloom)) {// the bloom filters are different
				if (DEBUG_OVERLAY)
					SessionInfo.getInstance()
							.log("bloom filters are different");
				if (!Utils.and(rcvBF, bloom)) {// the bloom filters do not
												// overlap
												// SessionInfo.getInstance().log(
					// "bloom filters do not overlap");
					/* merge the filters */
					bloom.getBitSet().or(rcvBF.getBitSet());
					long wSum = (avgTs * nPeersOwn + (rAvg + (actTs - rNtp))
							* nPeersRcv);
					parent.updateAvgTs(wSum / (nPeersRcv + nPeersOwn));

					/* add the received bloom filter to the ones already seen */
					parent.getBloomList().add(rcvBF);

				} else if (!parent.getBloomList().contains(rcvBF)) {
					if (DEBUG_OVERLAY)
						SessionInfo
								.getInstance()
								.log("bloom filters do overlap && we have seen this blomm filter before");
					if (nPeersRcv > nPeersOwn) {
						if (DEBUG_OVERLAY)
							SessionInfo
									.getInstance()
									.log("bloom filters do overlap and the received one contains more information");
						/*
						 * overlap and received bloom filter has more
						 * information
						 */
						bloom = rcvBF;
						if (bloom.contains(myId)) {
							if (DEBUG_OVERLAY)
								SessionInfo
										.getInstance()
										.log("this peer already is in the filter, so set the received average");
							/* correct received average */
							parent.updateAvgTs(rAvg + (actTs - rNtp));
						} else {
							if (DEBUG_OVERLAY)
								SessionInfo
										.getInstance()
										.log("this peer is not in the filter, so correct the received average and add ourself");
							/* add own timestamp to received one */
							long wSum = (nPeersRcv * (rAvg + (actTs - rNtp)) + avgTs);
							parent.updateAvgTs(wSum / (nPeersOwn + 1));
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

		parent.setMaxId(maxId < paketMax ? paketMax : maxId);

	}
}
