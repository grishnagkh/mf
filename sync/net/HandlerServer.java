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

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

import mf.sync.SyncI;
import mf.sync.coarse.CSync;
import mf.sync.fine.FSync;
import mf.sync.utils.SessionInfo;
import mf.sync.utils.log.SyncLogger;

/**
 * Class implementing the distribution behaviour for the MessageHandler
 * 
 * @author stefan petscahrnig
 *
 */
public class HandlerServer extends Thread {
	public static final String TAG = "HandlerServer";
	/** length of the receive buffer */
	public static final int RCF_BUF_LEN = 2048; // let us have a 2k buffer..

	private static final boolean DEBUG_CRC = false;
	private static final boolean DEBUG_DUPLICATE_MESSAGES = false;
	private static final boolean DEBUG = false;
	private Thread t;
	/** UDP socket for receiving messages */
	private DatagramSocket serverSocket;
	/** receive Buffer */
	private byte[] rcvBuf = new byte[RCF_BUF_LEN];
	private int port;

	private List<String> received;

	static SyncLogger rcvLog;

	static {
		/* init logger */
		rcvLog = new SyncLogger(5);
	}

	public HandlerServer() {
		if (DEBUG)
			SessionInfo.getInstance().log("new handler server");
	}

	@Override
	public void interrupt() {
		if (DEBUG)
			SessionInfo.getInstance().log("Interrupt message handler server");

		received.clear();

		if (serverSocket != null) {
			serverSocket.close();
		}
		super.interrupt();
	}

	public void start(int port) {
		t = new Thread(this);
		this.port = port;
		t.start();
	}

	/** worker method */
	@Override
	public void run() {
		received = new ArrayList<String>();

		try {
			serverSocket = new DatagramSocket(port);
			serverSocket.setSoTimeout(0);
		} catch (SocketException e1) {
			SessionInfo.getInstance().log(e1.toString());
		}
		DatagramPacket rcv = new DatagramPacket(rcvBuf, RCF_BUF_LEN);

		while (!isInterrupted()) {
			try {
				rcv.setLength(RCF_BUF_LEN);
				serverSocket.receive(rcv);
				String msg = new String(rcv.getData());

				int idx = msg.indexOf('#') + 1;

				String crc = msg.substring(0, idx - 1);
				msg = msg.substring(idx);
				msg = msg.trim();

				/* CRC check */
				 long checkSum = Long.parseLong(crc);
				 CRC32 check = new CRC32();
				 check.update(msg.getBytes());
				 if (checkSum != check.getValue()) {
				 if (DEBUG_CRC)
				 SessionInfo.getInstance().log("crc check not passed");
				 continue;
				 }
				 if (DEBUG_CRC)
				 SessionInfo.getInstance().log("crc check passed");
				// long checkLen = Long.parseLong(crc);
				// if (checkLen != msg.length()) {
				// continue;
				// }^
				 
				idx = msg.indexOf('#') + 1;
				/* duplicate message check */
				String id = msg.substring(0, idx);
				if (received.contains(id)) {
					if (DEBUG_DUPLICATE_MESSAGES)
						SessionInfo.getInstance().log(
								"duplicate message, dropping...");
					continue; // we have received this message
				} else {
					if (DEBUG_DUPLICATE_MESSAGES)
						SessionInfo.getInstance().log(
								"message id unseen, adding to seen list");
					received.add(id);
				}

				msg = msg.substring(idx);

				if (msg.length() < 50) {
					rcvLog.append(msg);
				} else {
					rcvLog.append(msg.substring(0, 49) + "...");
				}

				/* distribute the message */
				if (msg.startsWith("" + SyncI.TYPE_COARSE_REQ)) {
					CSync.getInstance()
							.processRequest(CSyncMsg.fromString(msg));

				} else if (msg.startsWith("" + SyncI.TYPE_COARSE_RESP)) {
					CSync.getInstance().coarseResponse(msg);
				} else if (msg.startsWith("" + SyncI.TYPE_FINE)) {
					FSync.getInstance()
							.processRequest(FSyncMsg.fromString(msg));
				} else {
					// other requests, really? should not happen
				}
			} catch (IOException e) {

			}

		}
	}

}
