/*
 * SyncMessageHandler.java
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
package at.itec.mf;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

import android.util.Log;

/**
 * 
 * a message handler which forwards the received udp packages to the
 * synchronization modules
 * 
 * @author stefan petscharnig
 *
 */

public class SyncMessageHandler {
	/** Tag for android Log */
	public static final String TAG = "message handler";
	/** length of the receive buffer */
	public static final int RCF_BUF_LEN = 4096; // we start with a 4k buffer
	/** default port where we listen for synchronization messages */
	public static final int PORT = 12346;

	/** actual port to listen */
	private int port;
	/** singleton instance */
	private static SyncMessageHandler instance;

	/** boolean for graceful shutdown */
	private boolean stopMe;

	/** singleton method using default port */
	public static SyncMessageHandler getInstance() {
		return getInstance(PORT);
	}

	/** singleton method using custom port */
	public static SyncMessageHandler getInstance(int port) {
		if (instance == null)
			instance = new SyncMessageHandler(port);
		return instance;
	}

	/** singleton constructor using default port */
	private SyncMessageHandler() {
		this(PORT);
	}

	/** singleton constructor using custom port */
	private SyncMessageHandler(int port) {
		this.port = port;
	}

	/**
	 * Method for sending messages via UDP
	 * 
	 * @param msg
	 *            The message string to send
	 * @param address
	 *            receive Inetaddress
	 * @param port
	 *            receive prot
	 * @throws SocketException
	 * @throws IOException
	 */
	public synchronized void sendMsg(String msg, InetAddress address, int port)
			throws SocketException, IOException {
		Log.d(TAG, "sending message: " + msg);
		DatagramSocket clientSocket = new DatagramSocket();
		DatagramPacket sendPacket = new DatagramPacket(msg.getBytes(),
				msg.getBytes().length, address, port);
		clientSocket.send(sendPacket);
		clientSocket.close();
		Log.d(TAG, "sending done");
	}

	HandlerServer srv;

	/**
	 * start the handling of messages, starts a thread waiting for incoming
	 * messages and distributing the work to the sync modules
	 */
	public void startHandling() {
		// new Thread(new ServerRunnable()).start();
		srv = new HandlerServer();
		srv.start();
	}

	/** stop the listener for requests */
	public void stopHandling() {
		Log.d(TAG, "stoppung handler...");
		stopMe = true;
		srv.interrupt();
	}

	/**
	 * Class implementing the distribution behaviour for the MessageHandler
	 * 
	 * @author stefan
	 *
	 */
	private class HandlerServer extends Thread {
		Thread t;
		/** UDP socket for receiving messages */
		DatagramSocket serverSocket;
		/** receive Buffer */
		byte[] rcvBuf = new byte[RCF_BUF_LEN];

		@Override
		public void interrupt() {
			super.interrupt();
			if (serverSocket != null) {
				serverSocket.close();
			}
		}

		public void start() {
			t = new Thread(this);
			t.start();
		}

		/** worker method */
		@Override
		public void run() {
			CSync.getInstance().startSync();
			
			Log.d(TAG, "start message handler");
			try {
				serverSocket = new DatagramSocket(port);
				serverSocket.setSoTimeout(0);
			} catch (SocketException e1) {
				Log.d(TAG, e1.toString());
			}
			if (serverSocket == null) {
				Log.d("why", "do you do this to me?");
			}
			stopMe = false;
			while (!stopMe) {

				// listen for messages
				DatagramPacket rcv = new DatagramPacket(rcvBuf, RCF_BUF_LEN);
				try {
					Log.d(TAG, "receiving...");
					serverSocket.receive(rcv);
					Log.d(TAG, "received a paket");
					String msg = new String(rcv.getData());
					msg = msg.trim();
					Log.d(TAG, "received message: " + msg);
					/* distribute the message */
					if (msg.startsWith("" + SyncI.TYPE_COARSE_REQ)) {
						Log.d("", "add  to message queue");
						CSync.getInstance().processRequest(msg);
					} else if (msg.startsWith("" + SyncI.TYPE_COARSE_RESP)) {
						CSync.getInstance().coarseResponse(msg);
					} else if (msg.startsWith("" + SyncI.TYPE_FINE)) {
						FSync.getInstance().processRequest(msg);
					} else {
						// other requests, really? should not happen

					}
				} catch (IOException e) {

				}

			}
			stopMe = false;

			Log.d(TAG, "close port for sync messages");
			serverSocket.close();
		}

	}

}
