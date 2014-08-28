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

public class FSyncMsg extends SyncMsg {

	private static final long serialVersionUID = 6736024082413117802L;
	/** message fields */
	public int seqN, maxId, myId;
	public long avg, nts;
	public BloomFilter bloom;

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
}
