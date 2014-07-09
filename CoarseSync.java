package at.itec.mf;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

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

public class CoarseSync {

	public static final String DELIM = "|";
	public static final int WAIT_TIME_MS = 1000;
	public static final String TAG = "coarse sync mf";

	private List<String> requestQueue;

	private static CoarseSync instance;

	public static CoarseSync getInstance() {
		if (instance == null)
			instance = new CoarseSync();
		return instance;
	}

	private CoarseSync() {
		requestQueue = new ArrayList<String>();
	}

	public void coarseResponse(String msg) {
		requestQueue.add(msg);
	}

	public void startCoarseSync() {
		new Thread(new CSyncRunnable()).start();
	}

	public void processRequest(String request) {
		new Thread(new CSyncProcessRequestRunnable(request)).start();
	}

	private class CSyncRunnable implements Runnable {
		public void run() {

			// 1 send a request to all known peers
			for (Peer p : SessionManager.getInstance().getPeers().values()) {
				int type = UDPSyncMessageHandler.TYPE_COARSE_REQ;
				String myIP = SessionManager.getInstance().getMySelf()
						.getAddress().toString();
				int myPort = SessionManager.getInstance().getMySelf().getPort();
				int myId = SessionManager.getInstance().getMySelf().getId();

				String msg = Utils.buildMessage(DELIM, type, myIP, myPort, 0,
						Utils.getTimestamp(), myId);

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
				Thread.sleep(WAIT_TIME_MS);
			} catch (InterruptedException e) {
				// TODO exception handling
			}

			long avgPTS = 0;

			// 3 parse and process responses

			// TODO lock the request queue for other threads?

			for (String response : requestQueue) {
				String[] responseFields = response.split(DELIM);
				long pts = Long.parseLong(responseFields[3]);
				long nts = Long.parseLong(responseFields[4]);
				avgPTS += pts + (Utils.getTimestamp() - nts);
			}
			avgPTS /= requestQueue.size();

			// empty request queue
			requestQueue.clear();
			
			//TODO unlock the request Queue?

			// TODO: set playback to avgPTS
			Log.d(TAG, "calculated average from coarse synchronization: "
					+ avgPTS);

		}
	}

	private class CSyncProcessRequestRunnable implements Runnable {
		String req;

		public CSyncProcessRequestRunnable(String req) {
			this.req = req;
		}

		public void run() {
			// parse request
			String[] responseFields = req.split("|");
			if (responseFields.length != 5) { // simplest check available^^
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

			// FIXME playback timestamp
			long myPts = 1;

			long myNts = Utils.getTimestamp();

			int type = UDPSyncMessageHandler.TYPE_COARSE_REQ;
			String myIP = SessionManager.getInstance().getMySelf().getAddress()
					.toString();
			int myPort = SessionManager.getInstance().getMySelf().getPort();
			int myId = SessionManager.getInstance().getMySelf().getId();
			String msg = Utils.buildMessage(DELIM, type, myIP, myPort, myPts,
					myNts, myId);

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
