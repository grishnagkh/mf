/*
 * FSync.java
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

package at.itec.mf;

import java.io.IOException;

import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.LibVlcException;

import at.itec.mf.bloomfilter.BloomFilter;

/**
 * 
 * Class handling the fine synchronization using mf algorithm
 * 
 * @author stefan petscharnig
 *
 */
public class FSync implements SyncI {

	/** actual bloom filter */
	private BloomFilter<Integer> bloom;
	/** a list of seen bloom filters */
	private List<BloomFilter<Integer>> bloomList;

	/** time when the last avgTs was received */
	private long oldTs; // the time of the last avg ts update
	/** average time stamp at time oldTs */
	private long avgTs;
	/** actual play back time stamp */
	private long pts;
	/** actual ntp time stamp */
	private long nts;
	/** maximum peer id seen so far */
	private int maxId;
	/** own peer id */
	private int myId;
	/** singleton instance */
	private static FSync instance;

	/** stop the message sending */
	private boolean fineSyncNecessary = true;

	/**
	 * Constructor
	 */
	private FSync() {
		bloomList = new ArrayList<BloomFilter<Integer>>();
		maxId = SessionManager.getInstance().getMySelf().getId();
		myId = SessionManager.getInstance().getMySelf().getId();
	}

	/**
	 * singleton method
	 * 
	 * @return
	 */
	public static FSync getInstance() {
		instance = instance == null ? new FSync() : instance;
		return instance;
	}

	/**
	 * start fine sync message sending in a new thread
	 */
	public void startSync() {
		new Thread(new FSWorker()).start();
	}

	/**
	 * do process a fine sync request in a new thread
	 */
	public void processRequest(String msg) {
		new Thread(new FSResponseHandler(msg)).start();
	}

	/**
	 * sent a message periodically to the neighbour
	 * 
	 * @author stefan petscharnig
	 *
	 */
	private class FSWorker implements Runnable {

		public void run() {
			// create the bloom filter

			bloom = new BloomFilter<Integer>(BITS_PER_ELEM, N_EXP_ELEM,
					N_HASHES);

			bloom.add(SessionManager.getInstance().getMySelf().getId());

			while (fineSyncNecessary) {

				// is fine sync necessary, or should we stop it?

				try {
					pts = LibVLC.getInstance().getTime();
				} catch (LibVlcException e) {
					// something went terribly wrong
					return;
				}
				nts = Utils.getTimestamp();
				long uts = avgTs + nts - oldTs; // updated (average) timestamp
				long delta = pts - uts;

				if (delta * delta < EPSILON * EPSILON) {
					return;
				}

				// broadcast to neighbors
				for (Peer p : SessionManager.getInstance().getPeers().values()) {
					String msg = Utils.buildMessage(DELIM, TYPE_FINE, uts, nts,
							myId, Utils.toString(bloom.getBitSet()), maxId);
					try {
						SyncMessageHandler.getInstance().sendMsg(msg,
								p.getAddress(), p.getPort());
					} catch (SocketException e) {
						// ignore
					} catch (IOException e) {
						// ignore
					}
				}
				try {
					Thread.sleep(PERIOD_FS_MS);
				} catch (InterruptedException iex) {
					// ignore
				}

			}
		}
	}

	/**
	 * process fine sync responses
	 * 
	 * @author stefan petscharnig
	 *
	 */
	private class FSResponseHandler implements Runnable {
		private String msg;

		public FSResponseHandler(String msg) {
			this.msg = msg;
		}

		public void run() {
			try {
				pts = LibVLC.getInstance().getTime();
			} catch (LibVlcException e) {
				// something went terribly wrong
				return;
			}
			nts = Utils.getTimestamp();

			String[] msgA = msg.split("\\" + DELIM);

			BloomFilter<Integer> rcvBF = new BloomFilter<Integer>(
					BITS_PER_ELEM, N_EXP_ELEM, N_HASHES);
			rcvBF.setBitSet(Utils.fromString(msgA[4]));
			int paketMax = Integer.parseInt(msgA[5]);
			int nBloom1 = Utils.getN(rcvBF, paketMax);
			int nBloom2 = Utils.getN(bloom, maxId);

			long rAvg = Long.parseLong(msgA[1]);
			long rNtp = Long.parseLong(msgA[2]);

			if (Utils.xor(rcvBF, bloom)) {// the bloom filters are different
				if (!Utils.and(rcvBF, bloom)) {// the bloom filters do not
												// overlap
					// merge the filters
					bloom.getBitSet().or(rcvBF.getBitSet());

					// calc weighted average
					long newTs = Utils.getTimestamp();
					long wSum = ((avgTs + (newTs - oldTs)) * nBloom1 + (rAvg + (newTs - rNtp))
							* nBloom2);
					avgTs = wSum / (nBloom1 + nBloom2);

					oldTs = newTs;

					// add the received bloom filter to the ones already seen
					bloomList.add(rcvBF);
					// add ourself to the bloom filter
					bloom.add(myId);

				} else if (!bloomList.contains(rcvBF) && nBloom1 < nBloom2) {
					/*
					 * TODO: does the list "contain" the bf when the same are
					 * sent over the network? to test..,. if not, a comparison
					 * method must be written
					 */
					// overlap and received bloom filter has more information
					bloom = rcvBF;
					if (bloom.contains(myId)) {
						// take the received average and correct it
						long newTs = Utils.getTimestamp();
						avgTs = (newTs - oldTs) + rAvg;
						oldTs = newTs;
					} else {
						// take the received time stamp and add our own
						long newTs = Utils.getTimestamp();
						long wSum = (nBloom2 * (rAvg + (newTs - rNtp)) + (avgTs + (newTs - oldTs)));
						avgTs = wSum / (nBloom2 + 1);
						oldTs = newTs;
						bloom.add(myId);
					}
				}
			} else {
				// the same bloom filters: ignore; time stamps must be equal
			}

			maxId = maxId < paketMax ? paketMax : maxId;

			// update player here?
		}
	}

}
