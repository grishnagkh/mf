package at.itec.mf;

import java.io.IOException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.LibVlcException;

import at.itec.mf.bloomfilter.BloomFilter;

public class FineSync implements SyncI {

	private BloomFilter<Integer> bloom;
	private List<BloomFilter<Integer>> bloomList;

	private long oldTs; // the time of the last avg ts update
	private long avgTs;
	private long pts;
	private long nts;

	private int maxId;
	private int myId;

	private static FineSync instance;

	private boolean fineSyncNecessary = true;

	private FineSync() {
		bloomList = new ArrayList<BloomFilter<Integer>>();
		maxId = SessionManager.getInstance().getMySelf().getId();
		myId = SessionManager.getInstance().getMySelf().getId();
	}

	public static FineSync getInstance() {
		instance = instance == null ? new FineSync() : instance;
		return instance;
	}

	public void startSync() {
		new Thread(new FSWorker()).start();
	}

	public void processRequest(String msg) {
		new Thread(new FSResponseHandler(msg)).start();
	}

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
						UDPSyncMessageHandler.getInstance().sendUDPMessage(msg,
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
