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

import java.security.NoSuchAlgorithmException;

import mf.bloomfilter.BloomFilter;
import mf.sync.SyncI;

public class FSyncMsg extends SyncMsg {

	private static final long serialVersionUID = 6736024082413117802L;
	/** message fields */
	public int seqN, maxId, myId;
	public long avg, nts;
	public BloomFilter bloom;

	/**
	 * Constructor
	 */
	private FSyncMsg() {
	}

	/**
	 * Constructor
	 * 
	 * @param avgTs
	 * @param nts
	 * @param myId
	 * @param bloom
	 * @param maxId
	 * @param seqN
	 */
	public FSyncMsg(long avgTs, long nts, int myId, BloomFilter bloom,
			int maxId, int seqN) {
		this.avg = avgTs;
		this.nts = nts;
		this.maxId = maxId;
		this.seqN = seqN;
		this.bloom = bloom;
		this.myId = myId;
	}

	/** make message ready for transmission */
	public String getMessageString(String delim, int type) {
		String msg = type + delim + avg + delim + nts + delim + myId + delim
				+ bloom.toString() + delim + maxId + delim + seqN;
		return msg;
	}

	/**
	 * transforms a string representation of a fsync message into a FSycMsg
	 * object
	 * 
	 * @param str
	 * @return
	 */
	public static FSyncMsg fromString(String str) {
		FSyncMsg msg = new FSyncMsg();

		String[] msgA = str.split(SyncI.DELIM);

		try {
			msg.bloom = BloomFilter.fromString(msgA[SyncI.FS_BLOOM_POS],
					SyncI.N_HASHES);
		} catch (NoSuchAlgorithmException e) {

		}

		msg.seqN = Integer.parseInt(msgA[SyncI.FS_SYNC_N_POS]);
		msg.avg = Long.parseLong(msgA[SyncI.FS_R_AVG_POS]);
		msg.nts = Long.parseLong(msgA[SyncI.FS_R_NTP_POS]);
		msg.maxId = Integer.parseInt(msgA[SyncI.FS_MAX_ID_POS]);

		return msg;
	}
}
