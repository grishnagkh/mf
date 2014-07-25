/*
 * CSync.java
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
package mf.player.at.itec;
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

public class CSync implements SyncI {

	public static final int SEGSIZE = 2000;// for now^^

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
			Log.d(TAG_CS, "start coarse sync request");
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
					Log.e(TAG_CS, "could not send message");
				} catch (IOException e) {
					Log.e(TAG_CS, "could not send message");
				}
			}
			Log.d(TAG_CS, "phase 1 completed, waiting...");
			/* 2 */
			try {
				Thread.sleep(WAIT_TIME_CS_MS);
			} catch (InterruptedException e) {
				Log.e(TAG_CS, "interrupted while sleep... ");
			}
			Log.d(TAG_CS, "phase 2 completed, calculating");
			long avgPTS = 0;

			/* 3 */

			if (msgQueue.size() == 0) {
				Log.d(TAG_CS, "no messages in queue");
				FSync.getInstance().startSync();
				return;
			}

			for (String response : msgQueue) {
				String[] responseFields = response.split("\\" + DELIM);
				long pts = Long.parseLong(responseFields[3]);
				long nts = Long.parseLong(responseFields[4]);
				avgPTS += pts + (Utils.getTimestamp() - nts);
				Log.d(TAG_CS, "trip time (peer " + responseFields[4] + "): "
						+ (Utils.getTimestamp() - nts));
			}
			avgPTS /= msgQueue.size();

			// empty request queue
			msgQueue.clear();

			Log.d(TAG_CS, "calculated average from coarse synchronization: "
					+ avgPTS);
			// scale time to the 2s segments, just to make some sense for fine
			// sync^^,

			avgPTS = SEGSIZE + avgPTS - avgPTS % SEGSIZE;
			// Log.d(TAG_CS, "try to set time to " + avgPTS);

			Utils.setPlaybackTime(avgPTS);

			// Log.d(TAG_CS, "have set time to " + avgPTS);

			FSync.getInstance().startSync();
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
			Log.d(TAG_CS, "got the following request: " + req);
			String[] responseFields = req.split("\\" + DELIM);
			if (responseFields.length != 6) { // simplest check available...
				Log.d(TAG_CS, "invalid request [length]");
				return; // invalid message
			}
			String senderIP = responseFields[1];
			InetAddress peerAddress = null;
			try {
				peerAddress = InetAddress.getByName(senderIP);
			} catch (UnknownHostException e) {
				Log.d(TAG_CS, "invalid request [IP]");
				return; // invalid IP, don't care;
			}
			int senderPort = Integer.parseInt(responseFields[2]);

			long myPts = 0;
			myPts = Utils.getPlaybackTime();

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
				Log.e(TAG_CS, "could not send message");
			} catch (IOException e) {
				Log.e(TAG_CS, "could not send message");
			}
			int peerId = Integer.parseInt(responseFields[5]);
			if (!SessionManager.getInstance().getPeers().containsKey(peerId)) {
				Peer p = new Peer(peerId, peerAddress, senderPort);
				SessionManager.getInstance().getPeers().put(peerId, p);
			}

		}
	}

}
