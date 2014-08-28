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
		this.maxId = maxId;
		this.parent = parent;
	}

	/**
	 * check the sequence number, if the sequence number received is bigger than
	 * the stored one, take the received sequence number and do a
	 * resynchronization
	 */
	private void checkSeqN() {
		if (msg.seqN > SessionInfo.getInstance().getSeqN()) {
			SessionInfo.getInstance().setSeqN(msg.seqN);
			// TODO: implement resync hard
		} else {
		}
	}

	@Override
	public void run() {
		synchronized (SessionInfo.getInstance()) {
			// test
			if (CHECK_SEQ_N) {
				checkSeqN();
			}

			if (!SessionInfo.getInstance().getBloomList().contains(msg.bloom)) {
				/* we have not seen the bloom filter */
				if (!FSync.getInstance().restart()) {
					FSync.getInstance().start();
				}
			}

			bloom = SessionInfo.getInstance().getBloom();

			// update(pi, ntpi), update(pj, ntpj)
			long now = Clock.getTime();
			long avgTs = SessionInfo.getInstance().alignedAvgTs(now);
			long msgAvgTs = msg.avg + now - msg.nts;

			SessionInfo.getInstance().log(
					"avg:" + avgTs + "@msgAvg:" + msgAvgTs + "@" + now);

			BloomFilter xor = msg.bloom.clone();
			xor.xor(bloom);
			if (xor.isZero()) {
				// same bloom filters, ignore
			} else {
				BloomFilter and = msg.bloom.clone();
				and.and(bloom);
				if (and.isZero()) {
					bloom.merge(msg.bloom);
					SessionInfo
							.getInstance()
							.updateAvgTs(
									(bloom.getNElements() * avgTs + msg.bloom
											.getNElements() * msgAvgTs)
											/ (bloom.getNElements() + msg.bloom
													.getNElements()),
									now);
				} else if (!SessionInfo.getInstance().getBloomList()
						.contains(msg.bloom)) {
					if (msg.bloom.getNElements() >= bloom.getNElements()) {
						if (msg.bloom.contains(SessionInfo.getInstance()
								.getMySelf().getId())) {
							SessionInfo.getInstance()
									.updateAvgTs(msgAvgTs, now);
							bloom = msg.bloom;
						} else {
							SessionInfo
									.getInstance()
									.updateAvgTs(
											(avgTs + (msg.bloom.getNElements() * msgAvgTs))
													/ (1 + msg.bloom
															.getNElements()),
											now);
							bloom = msg.bloom;
							bloom.add(SessionInfo.getInstance().getMySelf()
									.getId());
						}
					}
				}
			}
			SessionInfo.getInstance().getBloomList().add(msg.bloom);
			parent.setMaxId(maxId < msg.maxId ? msg.maxId : maxId);

		}
	}
}
