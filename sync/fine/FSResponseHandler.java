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
import android.util.Log;

public class FSResponseHandler extends Thread {
	private String msg;
	public static final String TAG = "FSResponse Handler";
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
			if (bloom == null)
				return;
			int nBloom1 = Utils.getN(rcvBF, paketMax);
			int nBloom2 = Utils.getN(bloom, maxId);

			Log.d(TAG, "#peers in received bf: " + nBloom1 + ", maxId: "
					+ paketMax);
			Log.d(TAG, "#peers in stored bf: " + nBloom2 + ", maxId: " + maxId);

			long actTs = Utils.getTimestamp();
			long avgTs = parent.alignAvgTs(actTs);

			/*
			 * TODO: does the list "contain" the bf when the same are sent over
			 * the network? to test..,. if not, a comparison method must be
			 * written
			 */
			long delta = 1239120491;

			if (Utils.xor(rcvBF, bloom)) {// the bloom filters are different
				Log.d(TAG, "bloom filters are different");
				if (!Utils.and(rcvBF, bloom)) {// the bloom filters do not
												// overlap
					Log.d(TAG, "bloom filters do not overlap");
					/* merge the filters */
					bloom.getBitSet().or(rcvBF.getBitSet());
					long wSum = (avgTs * nBloom2 + (rAvg + (actTs - rNtp))
							* nBloom1);
					parent.updateAvgTs(wSum / (nBloom1 + nBloom2));

					/* add the received bloom filter to the ones already seen */
					parent.getBloomList().add(rcvBF);

				} else if (!parent.getBloomList().contains(rcvBF)
						&& nBloom1 < nBloom2) {

					/* overlap and received bloom filter has more information */
					bloom = rcvBF;
					if (bloom.contains(myId)) {
						/* correct received average */
						parent.updateAvgTs(rAvg + (actTs - rNtp));
					} else {
						/* add own timestamp to received one */
						long wSum = (nBloom1 * (rAvg + (actTs - rNtp)) + avgTs);
						parent.updateAvgTs(wSum / (nBloom2 + 1));
						bloom.add(myId);
					}

				}
				long pts = Utils.getPlaybackTime();// XXX
				delta = actTs - pts;// XXX
				SessionInfo.getInstance().log("delta from fine sync: " + avgTs);
			} else {
				// the same bloom filters: ignore; time stamps must be equal
			}

		}

		parent.setMaxId(maxId < paketMax ? paketMax : maxId);

	}
}
