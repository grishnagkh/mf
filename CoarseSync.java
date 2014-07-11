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

public class CoarseSync implements SyncI {

	private List<String> requestQueue;

	private static CoarseSync instance;

	private CoarseSync() {
		requestQueue = new ArrayList<String>();
	}

	public static CoarseSync getInstance() {
		if (instance == null)
			instance = new CoarseSync();
		return instance;
	}

	public void coarseResponse(String msg) {

		requestQueue.add(msg);
	}

	public void startSync() {
		new Thread(new CSyncRunnable()).start();
	}

	public void processRequest(String request) {
		new Thread(new CSyncProcessRequestRunnable(request)).start();
	}

	private class CSyncRunnable implements Runnable {
		public void run() {

			// 1 send a request to all known peers
			for (Peer p : SessionManager.getInstance().getPeers().values()) {
				String myIP = SessionManager.getInstance().getMySelf()
						.getAddress().getHostAddress();
				int myPort = SessionManager.getInstance().getMySelf().getPort();
				int myId = SessionManager.getInstance().getMySelf().getId();

				String msg = Utils.buildMessage(DELIM, TYPE_COARSE_REQ, myIP,
						myPort, 0, Utils.getTimestamp(), myId);

				try {
					UDPSyncMessageHandler.getInstance().sendUDPMessage(msg,
							p.getAddress(), p.getPort());
				} catch (SocketException e) {
					// TODO exception handling
				} catch (IOException e) {
					// TODO exception handling
				}
			}
			// 2 wait some time tc
			try {
				Thread.sleep(WAIT_TIME_CS_MS);
			} catch (InterruptedException e) {
				// TODO exception handling
			}

			long avgPTS = 0;

			// 3 parse and process responses

			for (String response : requestQueue) {
				String[] responseFields = response.split(DELIM);
				long pts = Long.parseLong(responseFields[3]);
				long nts = Long.parseLong(responseFields[4]);
				avgPTS += pts + (Utils.getTimestamp() - nts);
			}
			avgPTS /= requestQueue.size();

			// empty request queue
			requestQueue.clear();

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

	boolean doSync = true;

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

			// send message
			try {
				UDPSyncMessageHandler.getInstance().sendUDPMessage(msg,
						peerAddress, senderPort);
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
