/*
 * MessageHandler.java
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
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

import mf.sync.SyncI;
import mf.sync.coarse.CSync;
import mf.sync.fine.FSync;
import mf.sync.utils.SessionInfo;
import android.util.SparseIntArray;

/**
 *
 * a message handler which forwards the received udp packages to the
 * synchronization modules
 *
 * @author stefan petscharnig
 *
 */

public class MessageHandler extends Thread {

	/** singleton method using default port */
	public static MessageHandler getInstance() {
		return getInstance(PORT);
	}

	/** singleton method using custom port */
	public static MessageHandler getInstance(int port) {
		if (instance == null)
			instance = new MessageHandler(port);
		return instance;
	}

	/** default port where we listen for synchronization messages */
	public static final int PORT = 12346;
	public static final boolean DEBUG = true;
	private static final boolean DEBUG_SEND_LOG = true;
	private static final boolean DEBUG_DUPLICATE_MESSAGES = false;
	/** length of the receive buffer */
	public static final int RCF_BUF_LEN = 4096; // let us have a 4k buffer..
	private static final boolean FILTER_DUPLICATES = true;

	/** UDP socket for receiving messages */
	private DatagramSocket serverSocket;
	/** receive Buffer */
	private byte[] rcvBuf = new byte[RCF_BUF_LEN];
	/**
	 * data structure for storing received messages, we store the highest
	 * message id per peer
	 */
	private SparseIntArray received;

	private ByteArrayInputStream byteStream;
	private ObjectInputStream is;

	/** actual port to listen */
	private int port;
	/** singleton instance */
	private static MessageHandler instance;
	static int cnt;

	static {
		cnt = 0;
	}

	private DatagramPacket rcv;

	/** singleton constructor using default port */
	private MessageHandler() {
		this(PORT);
	}

	/** singleton constructor using custom port */
	private MessageHandler(int port) {
		this.port = port;

	}

	@Override
	public void interrupt() {

		if (DEBUG)
			SessionInfo.getInstance().log(
					"Interrupting message handler server...");
		if (received != null)
			received.clear();

		if (serverSocket != null && is != null)
			try {
				is.close();
				SessionInfo.getInstance().log(
						"server socket closed? " + serverSocket.isClosed());
				if (!serverSocket.isClosed())
					serverSocket.close();
			} catch (IOException e) {
			}

		if (DEBUG)
			SessionInfo.getInstance().log("message handler server interrupted");

		instance = null;

		super.interrupt();
	}

	@Override
	public void run() {

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
				continue;
			}
			if (readObj instanceof SyncMsg && FILTER_DUPLICATES) {
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
			}

			if (readObj instanceof FSyncMsg) {
				if (!SessionInfo.getInstance().isCSynced()) {
					SessionInfo
							.getInstance()
							.log("csync has not finished yet, discard this message...");
					continue;
				}
				FSyncMsg msg = (FSyncMsg) readObj;
				SessionInfo
						.getInstance()
						.getRcvLog()
						.append(msg.peerId + "I" + msg.avg + "I" + msg.nts
								+ "I" + msg.bloom);
				FSync.getInstance().processRequest(msg);
			} else if (readObj instanceof CSyncMsg) {
				CSyncMsg msg = (CSyncMsg) readObj;
				SessionInfo
						.getInstance()
						.getRcvLog()
						.append(msg.type + "I" + msg.peerId + "I"
								+ msg.senderIp);
				if (msg.type == SyncI.TYPE_COARSE_REQ)
					CSync.getInstance().processRequest(msg);
				else if (msg.type == SyncI.TYPE_COARSE_RESP)
					CSync.getInstance().coarseResponse(msg);
				else
					// do nothing
					SessionInfo.getInstance().log(
							"got a csync message with wrong message type: "
									+ msg.type);
			} else
				// do nothing
				SessionInfo
						.getInstance()
						.log("got a message which is neither a fsync msg nor a csync message");
		}
	}

	/**
	 * Method for sending messages via UDP
	 *
	 * @param msg
	 *            The message string to send
	 * @param destAddress
	 *            receive Inetaddress
	 * @param destPort
	 *            receive prot
	 */

	public synchronized void sendMsg(SyncMsg msg) {

		try {
			msg.msgId = cnt++;
			msg.peerId = SessionInfo.getInstance().getMySelf().getId();

			ByteArrayOutputStream byteStream = new ByteArrayOutputStream(4092);
			ObjectOutputStream os = new ObjectOutputStream(
					new BufferedOutputStream(byteStream));
			os.flush();
			os.writeObject(msg);
			os.flush();

			byte[] sendBuf = byteStream.toByteArray();
			DatagramPacket packet = new DatagramPacket(sendBuf, sendBuf.length,
					msg.destAddress, msg.destPort);
			DatagramSocket clientSocket = new DatagramSocket();
			clientSocket.send(packet);
			clientSocket.close();
			os.close();
		} catch (IOException e) {
			SessionInfo.getInstance().getSendLog().append("error");
			return;
		}
		if (DEBUG_SEND_LOG)
			SessionInfo
					.getInstance()
					.getSendLog()
					.append(msg.peerId + "." + msg.msgId + " " + " "
							+ msg.destAddress + ":" + msg.destPort);

	}

	@Override
	public void start() {
		received = new SparseIntArray();
		try {
			serverSocket = new DatagramSocket(port);
			serverSocket.setSoTimeout(0);
		} catch (SocketException e1) {
		}
		rcv = new DatagramPacket(rcvBuf, RCF_BUF_LEN);

		super.start();
	}

	/**
	 * start the handling of messages, starts a thread waiting for incoming
	 * messages and distributing the work to the sync modules
	 */

	public void startHandling(boolean active) {

		if (active) {
			if (DEBUG)
				SessionInfo.getInstance().log(
						"start message handler in active mode");

			this.start();
		} else {
			if (DEBUG)
				SessionInfo
						.getInstance()
						.log("start message handler in inactive mode not implemented yet...");
			// inactive start not yet implemented
			;
		}
	}

	/**
	 * stop the listener for requests
	 *
	 * @param clearSessionData
	 */
	public void stopHandling(boolean clearSessionData) {
		this.interrupt();
		CSync.getInstance().interrupt();

		FSync.getInstance().interrupt();
		SessionInfo.getInstance().resetFSyncData();

		if (clearSessionData)
			SessionInfo.getInstance().clearSessionData();

		if (DEBUG)
			SessionInfo.getInstance().log(
					"session data cleared: " + clearSessionData);

	}

}
