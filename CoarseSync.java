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
				// TODO: compose Message String
				String msg = "";
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

			// lock the request queue for other threads
			synchronized (requestQueue) {
				// 3a parse and process responses

				for (String response : requestQueue) {
					String[] responseFields = response.split(DELIM);
					long pts = Long.parseLong(responseFields[3]);
					long nts = Long.parseLong(responseFields[4]);
					avgPTS += pts + (Utils.getTimestamp() - nts);
				}
				avgPTS /= requestQueue.size();

				// empty request queue
				requestQueue.clear();
			}// unlock the request Queue

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
			
			//FIXME playback timestamp
			long myPts = 1;
			//FIXME network timestamp
			long myNts = 123541412;
			
			String msg = UDPSyncMessageHandler.TYPE_COARSE_RESP + DELIM
					+ SessionManager.getInstance().getMySelf().getAddress()
					+ DELIM
					+ SessionManager.getInstance().getMySelf().getPort()
					+ DELIM + myPts + DELIM + myNts + DELIM
					+ SessionManager.getInstance().getMySelf().getId();

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
