package at.itec.mf;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.LibVlcException;

import android.util.Log;

/**
 * 
 * coarse synchronization: we want make an educated guess (estimate) where the
 * other session users are so we start at this position with the playback so
 * that we only need little adaptions for the fine sync
 * 
 * as we set performance over a perfect guess, it is implemented via udp
 * 
 * @author stefan petscharnig
 *
 */

public class CSync implements SyncI {
	/** request queue filled by message handler while we are waiting */
	private List<String> msgQueue;
	/** singleton instance */
	private static CSync instance;

	/** Singleton constructor */
	private CSync() {
		msgQueue = new ArrayList<String>();
	}

	/** Singleton method */
	public static CSync getInstance() {
		if (instance == null)
			instance = new CSync();
		return instance;
	}

	/** method for filling the queue response */
	public void coarseResponse(String msg) {
		msgQueue.add(msg);
	}

	/** start the sync server */
	public void startSync() {
		new Thread(new CSyncRunnable()).start();
	}

	/** process a sync request message */
	public void processRequest(String request) {
		new Thread(new CSyncProcessRequestRunnable(request)).start();
	}

	/**
	 * 
	 * class handling the initial step of the coarse sync 1 send a request to
	 * all known peers 2 wait some time 3 parse and process responses
	 * 
	 * @author stefan petscharnig
	 *
	 */
	private class CSyncRunnable implements Runnable {
		public void run() {

			/* 1 */
			for (Peer p : SessionManager.getInstance().getPeers().values()) {
				String myIP = SessionManager.getInstance().getMySelf()
						.getAddress().getHostAddress();
				int myPort = SessionManager.getInstance().getMySelf().getPort();
				int myId = SessionManager.getInstance().getMySelf().getId();

				String msg = Utils.buildMessage(DELIM, TYPE_COARSE_REQ, myIP,
						myPort, 0, Utils.getTimestamp(), myId);

				try {
					SyncMessageHandler.getInstance().sendMsg(msg,
							p.getAddress(), p.getPort());
				} catch (SocketException e) {
					// TODO exception handling
				} catch (IOException e) {
					// TODO exception handling
				}
			}
			/* 2 */
			try {
				Thread.sleep(WAIT_TIME_CS_MS);
			} catch (InterruptedException e) {
				// TODO exception handling
			}

			long avgPTS = 0;

			/* 3 */

			for (String response : msgQueue) {
				String[] responseFields = response.split(DELIM);
				long pts = Long.parseLong(responseFields[3]);
				long nts = Long.parseLong(responseFields[4]);
				avgPTS += pts + (Utils.getTimestamp() - nts);
			}
			avgPTS /= msgQueue.size();

			// empty request queue
			msgQueue.clear();

			Log.d(TAG_CS, "calculated average from coarse synchronization: "
					+ avgPTS);

			try {
				LibVLC.getInstance().setTime(avgPTS);
			} catch (LibVlcException e) {
				/*
				 * hmm.. did not work, but here we already should have the
				 * playback... suspicios... may someone has trained a kitten to
				 * sabotage us... ignore this: we cannot cope with a super
				 * intelligent trained kitten!
				 */

			}

		}
	}

	/**
	 * class processing a coarse sync request
	 * 
	 * @author stefan petscharnig
	 */
	private class CSyncProcessRequestRunnable implements Runnable {
		String req;

		public CSyncProcessRequestRunnable(String req) {
			this.req = req;
		}

		public void run() {
			// parse request
			String[] responseFields = req.split("\\" + DELIM);
			if (responseFields.length != 6) { // simplest check available...
				return; // invalid message
			}
			String senderIP = responseFields[1];
			InetAddress peerAddress = null;
			try {
				peerAddress = InetAddress.getByName(senderIP);
			} catch (UnknownHostException e) {
				return; // invalid IP, don't care;

			}
			int senderPort = Integer.parseInt(responseFields[2]);

			long myPts = 0;
			try {
				myPts = LibVLC.getInstance().getTime();
			} catch (LibVlcException e1) {
				Log.e(TAG_CS, "unable to get media information");
			}

			long myNts = Utils.getTimestamp();

			String myIP = SessionManager.getInstance().getMySelf().getAddress()
					.getHostAddress();
			int myPort = SessionManager.getInstance().getMySelf().getPort();
			int myId = SessionManager.getInstance().getMySelf().getId();
			String msg = Utils.buildMessage(DELIM, TYPE_COARSE_RESP, myIP,
					myPort, myPts, myNts, myId);

			// send response
			try {
				SyncMessageHandler.getInstance().sendMsg(msg, peerAddress,
						senderPort);
			} catch (SocketException e) {
				// TODO exception handling, most likely ignore and log

			} catch (IOException e) {
				// TODO exception handling, most likely ignore and log

			}
			int peerId = Integer.parseInt(responseFields[5]);
			if (!SessionManager.getInstance().getPeers().containsKey(peerId)) {
				Peer p = new Peer(peerId, peerAddress, senderPort);
				SessionManager.getInstance().getPeers().put(peerId, p);
			}

		}
	}

}
