/*
 * FSyncMsg.java
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

package mf.sync.net;

import mf.bloomfilter.BloomFilter;
import mf.sync.SyncI;
import mf.sync.utils.Utils;

public class FSyncMsg {
	public int seqN, maxId, myId;
	public long avg, nts;
	public BloomFilter<Integer> bloom;

	private FSyncMsg() {
	}

	public FSyncMsg(long avgTs, long nts, int myId, BloomFilter<Integer> bloom,
			int maxId, int seqN) {
		this.avg = avgTs;
		this.nts = nts;
		this.maxId = maxId;
		this.seqN = seqN;
		this.bloom = bloom;
		this.myId = myId;
	}

	public String getMessageString(String delim, int type) {
		String msg = type + delim + avg + delim + nts + delim + myId + delim
				+ Utils.toString(bloom.getBitSet()) + delim + maxId + delim
				+ seqN;
		return msg;
	}

	/* transforms a string built by utils into a fine sync message */
	public static FSyncMsg fromString(String str) {
		FSyncMsg msg = new FSyncMsg();

		String[] msgA = str.split(SyncI.DELIM);

		msg.bloom = new BloomFilter<Integer>(SyncI.BITS_PER_ELEM,
				SyncI.N_EXP_ELEM, SyncI.N_HASHES);
		msg.bloom.setBitSet(Utils.fromString(msgA[SyncI.FS_BLOOM_POS]));

		msg.seqN = Integer.parseInt(msgA[SyncI.FS_SYNC_N_POS]);
		msg.avg = Long.parseLong(msgA[SyncI.FS_R_AVG_POS]);
		msg.nts = Long.parseLong(msgA[SyncI.FS_R_NTP_POS]);
		msg.maxId = Integer.parseInt(msgA[SyncI.FS_MAX_ID_POS]);

		return msg;
	}
}
