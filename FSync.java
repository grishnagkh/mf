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

import android.util.Log;
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
	private long lastAvgUpdateTs; // the time of the last avg ts update
	/** average time stamp at time oldTs */
	private long avgTs;

	/** maximum peer id seen so far */
	private int maxId;
	/** own peer id */
	private int myId;
	/** singleton instance */
	private static FSync instance;

	private Object avgMonitor;

	// /** stop the message sending */
	// private boolean fineSyncNecessary = true;

	/**
	 * Constructor
	 */
	private FSync() {
		bloomList = new ArrayList<BloomFilter<Integer>>();
		myId = SessionManager.getInstance().getMySelf().getId();
		maxId = myId;
		avgMonitor = this;
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
		Log.d(TAG_FS, "want to start fine sync thread");
		new Thread(new FSWorker()).start();
	}

	/**
	 * do process a fine sync request in a new thread
	 */
	public void processRequest(String msg) {
		new Thread(new FSResponseHandler(msg)).start();
	}

	// FIXME: ^^

	/**
	 * sent a message periodically to the neighbour
	 * 
	 * @author stefan petscharnig
	 *
	 */

	private long alignAvgTs(long alignTo) {
		long ret = Utils.getTimestamp();
		synchronized (avgMonitor) {
			avgTs += alignTo - lastAvgUpdateTs; // align avgTs
			lastAvgUpdateTs = Utils.getTimestamp();
		}
		return Utils.getTimestamp() - ret;
	}

	private void initAvgTs() {
		try {
			synchronized (avgMonitor) {
				avgTs = LibVLC.getInstance().getTime();
				lastAvgUpdateTs = Utils.getTimestamp();
			}
		} catch (LibVlcException e) {
		}
	}

	private void initBloom() {
		bloom = new BloomFilter<Integer>(BITS_PER_ELEM, N_EXP_ELEM, N_HASHES);
		bloom.add(SessionManager.getInstance().getMySelf().getId());
		bloomList.add(bloom);
	}

	private void broadcastToPeers(long nts) {
		// broadcast to known peers
		for (Peer p : SessionManager.getInstance().getPeers().values()) {
			String msg = Utils.buildMessage(DELIM, TYPE_FINE, avgTs, nts, myId,
					Utils.toString(bloom.getBitSet()), maxId);
			try {
				SyncMessageHandler.getInstance().sendMsg(msg, p.getAddress(),
						p.getPort());
			} catch (SocketException e) {
				// ignore
			} catch (IOException e) {
				// ignore
			}
		}
	}

	private class FSWorker implements Runnable {

		public void run() {

			Log.d(TAG_FS, "started fine sync thread");
			// create the bloom filter

			initBloom();

			int remSteps = 40; // /XXX just for testing

			initAvgTs();

			while (remSteps-- > 0) {
				// udpate
				long nts = Utils.getTimestamp();
				// alignAvgTs(nts);
				Log.d(TAG_FS, "time to align: " + alignAvgTs(nts));

				try {
					long delta = avgTs - LibVLC.getInstance().getTime();
					Log.d(TAG_FS, "delta: " + delta);
				} catch (Exception e) {
				}

				broadcastToPeers(nts);

				try {
					Thread.sleep(PERIOD_FS_MS);
				} catch (InterruptedException iex) {
					// ignore
				}
			}
			/*
			 * end while, fine sync ended TODO: for resync, simply start this
			 * again
			 */

			Log.d(TAG_FS, "setting time to: " + avgTs);
			try {
				LibVLC.getInstance().setTime(avgTs);
			} catch (LibVlcException e) {
				Log.d(TAG_FS, "some weird exception while setting time..");
			}

			// reset [for resync]
			bloomList.clear();
			maxId = myId;

			Log.d(TAG_FS,
					"test whether time on device is slower/faster than playback time...");
			try {
				long t1a = LibVLC.getInstance().getTime();
				long t2a = System.currentTimeMillis();
				System.out.println();
				Thread.sleep(200);
				long t1b = LibVLC.getInstance().getTime();
				long t2b = System.currentTimeMillis();
				Log.d(TAG_FS, "time diff in playback: " + (t1b - t1a));
				Log.d(TAG_FS, "time diff in system: " + (t2b - t2a));
				Log.d(TAG_FS, "time delta within 200msec: "
						+ ((t2b - t2a) - (t1b - t1a)));

				Log.d(TAG_FS, "lets have another try...");
				t1a = LibVLC.getInstance().getTime();
				t2a = System.currentTimeMillis();
				System.out.println();
				Thread.sleep(1000);
				t2b = System.currentTimeMillis();
				t1b = LibVLC.getInstance().getTime();
				Log.d(TAG_FS, "time diff in playback: " + (t1b - t1a));
				Log.d(TAG_FS, "time diff in system: " + (t2b - t2a));
				Log.d(TAG_FS, "time delta within 1000 msec: "
						+ ((t2b - t2a) - (t1b - t1a)));

				Log.d(TAG_FS, "lets have another try using the position...");
				t1a = (long) (LibVLC.getInstance().getPosition() * LibVLC
						.getInstance().getLength());
				t2a = System.currentTimeMillis();
				System.out.println();
				Thread.sleep(1000);
				t2b = System.currentTimeMillis();

				t1b = (long) (LibVLC.getInstance().getPosition() * LibVLC
						.getInstance().getLength());
				Log.d(TAG_FS, "time diff in playback: " + (t1b - t1a));
				Log.d(TAG_FS, "time diff in system: " + (t2b - t2a));
				Log.d(TAG_FS, "time delta within 1000 msec: "
						+ ((t2b - t2a) - (t1b - t1a)));

				Log.d(TAG_FS,
						"testing getPosition and getTime in ~100ms interval...");
				for (int i = 0; i < 20; i++) {
					Log.d(TAG_FS, "system time: " + System.currentTimeMillis());
					Log.d(TAG_FS, "pos: " + LibVLC.getInstance().getPosition());
					Log.d(TAG_FS, "time: " + LibVLC.getInstance().getTime());
					Thread.sleep(100);
				}

			} catch (Exception e) {

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
			if (bloom == null)
				return;
			// try {
			// pts = LibVLC.getInstance().getTime();
			// } catch (LibVlcException e) {
			// // something went terribly wrong
			// return;
			// }
			long nts = Utils.getTimestamp();

			String[] msgA = msg.split("\\" + DELIM);

			// Log.d(TAG_FS, "message: " + msg);

			BloomFilter<Integer> rcvBF = new BloomFilter<Integer>(
					BITS_PER_ELEM, N_EXP_ELEM, N_HASHES);

			rcvBF.setBitSet(Utils.fromString(msgA[4]));

			int paketMax = Integer.parseInt(msgA[5]);

			int nBloom1 = Utils.getN(rcvBF, paketMax); // returns 0.. blöd
			int nBloom2 = Utils.getN(bloom, maxId);

			Log.d(TAG_FS, "#peers in received bf: " + nBloom1 + ", maxId: "
					+ paketMax);
			Log.d(TAG_FS, "#peers in stored bf: " + nBloom2 + ", maxId: "
					+ maxId);

			long rAvg = Long.parseLong(msgA[1]);
			long rNtp = Long.parseLong(msgA[2]);

			long newTs = Utils.getTimestamp();

			if (Utils.xor(rcvBF, bloom)) {// the bloom filters are different
				Log.d(TAG_FS, "bloom filters are different");
				if (!Utils.and(rcvBF, bloom)) {// the bloom filters do not
												// overlap
					Log.d(TAG_FS, "bloom filters do not overlap");
					// merge the filters
					bloom.getBitSet().or(rcvBF.getBitSet());

					// calc weighted average

					long wSum = ((avgTs + (newTs - lastAvgUpdateTs)) * nBloom1 + (rAvg + (newTs - rNtp))
							* nBloom2);
					avgTs = wSum / (nBloom1 + nBloom2);

					// add the received bloom filter to the ones already seen
					bloomList.add(rcvBF);
					// add ourself to the bloom filter
					bloom.add(myId);

				} else if (!bloomList.contains(rcvBF) && nBloom1 < nBloom2) {
					Log.d(TAG_FS,
							"bloom filters do overlap, we have not seen the "
									+ "bloom filter before and the received "
									+ "bloom filter has more information");
					/*
					 * TODO: does the list "contain" the bf when the same are
					 * sent over the network? to test..,. if not, a comparison
					 * method must be written
					 */
					// overlap and received bloom filter has more information
					bloom = rcvBF;
					if (bloom.contains(myId)) {
						Log.d(TAG_FS,
								"we already are in this bloom filter, just take this average");
						// take the received average and correct it
						avgTs = rAvg + (newTs - lastAvgUpdateTs);
					} else {
						Log.d(TAG_FS,
								"we are not in this bloom filter, update average and add ourself to the bloom filter");
						// take the received time stamp and add our own
						long wSum = (nBloom2 * (rAvg + (newTs - rNtp)) + (avgTs + (newTs - lastAvgUpdateTs)));
						avgTs = wSum / (nBloom2 + 1);
						bloom.add(myId);
					}
				}
			} else {
				// the same bloom filters: ignore; time stamps must be equal
			}

			Log.d(TAG_FS, "actual avg: " + avgTs + " (@time:" + lastAvgUpdateTs
					+ ")");

			maxId = maxId < paketMax ? paketMax : maxId;

			lastAvgUpdateTs = newTs;

		}
	}

}
