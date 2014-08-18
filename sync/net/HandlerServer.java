/*
 * HandlerServer.java
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
package mf.sync.net;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

import mf.sync.SyncI;
import mf.sync.coarse.CSync;
import mf.sync.fine.FSync;
import mf.sync.utils.SessionInfo;
import mf.sync.utils.log.SyncLogger;
import android.util.SparseIntArray;

/**
 * Class implementing the distribution behaviour for the MessageHandler
 * 
 * @author stefan petscahrnig
 *
 */

public class HandlerServer extends Thread {
	private static boolean discardMessages = false;
	private static final boolean DEBUG_DUPLICATE_MESSAGES = false;
	private static final boolean DEBUG = false;
	/** length of the receive buffer */
	public static final int RCF_BUF_LEN = 4096; // let us have a 4k buffer..
	/** handler thread object */
	private Thread t;
	/** UDP socket for receiving messages */
	private DatagramSocket serverSocket;
	/** receive Buffer */
	private byte[] rcvBuf = new byte[RCF_BUF_LEN];
	/** port to listen to */
	private int port;
	/**
	 * data structure for storing received messages, we store the highest
	 * message id per peer
	 */
	private SparseIntArray received;// Map<Integer, Integer> received;
	/**
	 * debug log for received messages
	 */
	static SyncLogger rcvLog;

	static void createLogger() {
		rcvLog = new SyncLogger(5);
	}

	/**
	 * Constructor
	 */
	public HandlerServer() {
		createLogger();
		if (DEBUG)
			SessionInfo.getInstance().log("new handler server");
	}

	ByteArrayInputStream byteStream;
	ObjectInputStream is;

	@Override
	public void interrupt() {
		if (DEBUG)
			SessionInfo.getInstance().log(
					"Interrupting message handler server...");

		received.clear();

		if (serverSocket != null && is != null) {
			try {
				is.close();
				SessionInfo.getInstance().log(
						"server socket closed? " + serverSocket.isClosed());
				if (!serverSocket.isClosed())
					serverSocket.close();
			} catch (IOException e) {
			}
		}

		if (DEBUG)
			SessionInfo.getInstance().log("message handler server interrupted");

		super.interrupt();
	}

	/**
	 * start listening to a specific port
	 * 
	 * @param port
	 *            the port to listen to
	 */
	public void start(int port) {
		t = new Thread(this);
		this.port = port;
		t.start();
	}

	/** worker method */
	@Override
	public void run() {

		received = new SparseIntArray();
		try {
			serverSocket = new DatagramSocket(port);
			serverSocket.setSoTimeout(0);
		} catch (SocketException e1) {
		}
		DatagramPacket rcv = new DatagramPacket(rcvBuf, RCF_BUF_LEN);

		while (!isInterrupted()) {
			Object readObj = null;

			try {
				rcv.setLength(RCF_BUF_LEN);
				serverSocket.receive(rcv);

				byteStream = new ByteArrayInputStream(rcvBuf);
				is = new ObjectInputStream(new BufferedInputStream(byteStream));
				readObj = is.readObject();

			} catch (IOException e) {
				continue;
			} catch (ClassNotFoundException e) {
				continue;
			} catch (Exception e) {
				e.printStackTrace();
				break;
			}
			if (discardMessages) {
				if (DEBUG)
					SessionInfo
							.getInstance()
							.log("message was discarded, because we take a break ;) ");
				continue;
			}
			if (readObj instanceof SyncMsg) {
				SyncMsg m = (SyncMsg) readObj;
				if (received.get(m.peerId) > m.msgId) {
					if (DEBUG_DUPLICATE_MESSAGES)
						SessionInfo.getInstance().log(
								"duplicate message, dropping...");
					continue; // we already have received this message

				} else {
					if (DEBUG_DUPLICATE_MESSAGES)
						SessionInfo.getInstance().log(
								"message id unseen, adding to seen list");
					received.put(m.peerId, m.msgId);
				}
			} else {
				continue;
			}

			if (readObj instanceof FSyncMsg) {
				FSyncMsg msg = (FSyncMsg) readObj;
				rcvLog.append(msg.peerId + "I" + msg.avg + "I" + msg.nts + "I"
						+ msg.bloom);
				FSync.getInstance().processRequest(msg);
			} else if (readObj instanceof CSyncMsg) {
				CSyncMsg msg = (CSyncMsg) readObj;
				rcvLog.append(msg.type + "I" + msg.peerId + "I" + msg.senderIp);
				if (msg.type == SyncI.TYPE_COARSE_REQ) {
					CSync.getInstance().processRequest(msg);
				} else if (msg.type == SyncI.TYPE_COARSE_RESP) {
					CSync.getInstance().coarseResponse(msg);
				} else {
					// do nothing
					SessionInfo.getInstance().log(
							"got a csync message with wrong message type");
				}
			} else {
				// do nothing
				SessionInfo
						.getInstance()
						.log("got a message which is neither a fsync msg nor a csync message");
			}
		}
		SessionInfo.getInstance().log("handler server stopped");
	}

	/**
	 * if we want to pause the message handler thread, we just ignore the
	 * incoming messages, do not shut it down
	 */
	public void ignoreIncoming(boolean b) {
		discardMessages = b;

	}

}
