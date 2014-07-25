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

import mf.sync.coarse.CSync;
import mf.sync.fine.FSync;
import mf.sync.utils.SyncI;
import android.util.Log;

/**
 * Class implementing the distribution behaviour for the MessageHandler
 * 
 * @author stefan petscahrnig
 *
 */
public class HandlerServer extends Thread {
	public static final String TAG = "HandlerServer";
	/** length of the receive buffer */
	public static final int RCF_BUF_LEN = 4096; // we start with a 4k buffer
	Thread t;
	/** UDP socket for receiving messages */
	DatagramSocket serverSocket;
	/** receive Buffer */
	byte[] rcvBuf = new byte[RCF_BUF_LEN];

	int port;

	@Override
	public void interrupt() {
		super.interrupt();
		if (serverSocket != null) {
			serverSocket.close();
		}
	}

	public void start(int port) {
		t = new Thread(this);
		this.port = port;
		t.start();
	}

	/** worker method */
	@Override
	public void run() {
		CSync.getInstance().startSync();

		try {
			serverSocket = new DatagramSocket(port);
			serverSocket.setSoTimeout(0);
		} catch (SocketException e1) {
			Log.d(TAG, e1.toString());
		}
		if (serverSocket == null) {
			Log.d("why", "do you do this to me?");
		}
		while (!isInterrupted()) {
			DatagramPacket rcv = new DatagramPacket(rcvBuf, RCF_BUF_LEN);
			try {
				serverSocket.receive(rcv);
				String msg = new String(rcv.getData());
				msg = msg.trim();
				/* distribute the message */
				if (msg.startsWith("" + SyncI.TYPE_COARSE_REQ)) {
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
	}

}
