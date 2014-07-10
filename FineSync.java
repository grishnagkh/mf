package at.itec.mf;

import java.io.IOException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.LibVlcException;

import at.itec.mf.bloomfilter.BloomFilter;

public class FineSync {

	public static final int N_HASHES = 4;
	public static final int N_EXP_ELEM = 64;
	public static final int BITS_PER_ELEM = 2;

	public static final String DELIM = "|";
	public static final int PERIOD_MS = 1000;
	public static final String TAG = "fine sync mf";

	private BloomFilter<Integer> bloom;
	private List<BloomFilter<Integer>> bloomList;
	private long oldTs; // the time of the last avgts update
	private long avgTs;
	private int maxId;
	private int myId;
	private long pts = 0;
	private long nts = 0;

	// difference in ms where we stop synchronizing
	public static final long EPSILON = 20;

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

	public void startFineSync() {
		new Thread(new FSWorker()).start();
	}

	public void processRequest(String msg) {
		new Thread(new FSResponseHandler(msg)).start();
	}

	// TODO: align times to old ts and nts
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
				
				long delta = (pts - (avgTs + nts-oldTs));
				
				if (delta * delta < EPSILON * EPSILON) {
					return;
				}

				// broadcast to neighbors
				for (Peer p : SessionManager.getInstance().getPeers().values()) {
					String msg = Utils.buildMessage(DELIM,
							UDPSyncMessageHandler.TYPE_FINE, (avgTs + nts-oldTs), nts,
							myId,
							Utils.toString(bloom.getBitSet()), maxId);
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
					Thread.sleep(PERIOD_MS);
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
			// TODO: unfortunately the delimiter is part of the reg expression
			// language ... maybe we will change that sometime..
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
					// overlap and received bloom filter has more information
					bloom = rcvBF;
					if (bloom.contains(myId)) {
						// take the received average and correct it
						long newTs = Utils.getTimestamp();
						avgTs = (newTs - oldTs) + rAvg;
						oldTs = newTs;
					} else {
						// take the received timestamp and add our own
						long newTs = Utils.getTimestamp();
						long wSum = (nBloom2 * (rAvg + (newTs - rNtp)) + (avgTs + (newTs - oldTs)));
						avgTs = wSum / (nBloom2 + 1);
						oldTs = newTs;
						bloom.add(myId);
					}
				}
			} else {
				// the same bloom filters; ignore: benj says that the timestamps
				// must be equal in this case, and when benj says that, it
			}

			maxId = maxId < paketMax ? paketMax : maxId;

			// update player here?
		}
	}

}
