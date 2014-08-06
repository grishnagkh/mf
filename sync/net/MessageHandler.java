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

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.zip.CRC32;

import mf.sync.fine.FSync;
import mf.sync.utils.SessionInfo;
import mf.sync.utils.log.SyncLogger;
import android.util.Log;

/**
 * 
 * a message handler which forwards the received udp packages to the
 * synchronization modules
 * 
 * @author stefan petscharnig
 *
 */

public class MessageHandler {
	/** Tag for android Log */
	public static final String TAG = "message handler";

	/** default port where we listen for synchronization messages */
	public static final int PORT = 12346;

	SyncLogger sendLog;

	/** actual port to listen */
	private int port;
	/** singleton instance */
	private static MessageHandler instance;

	private HandlerServer srv;

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

	/** singleton constructor using default port */
	private MessageHandler() {
		this(PORT);
	}

	/** singleton constructor using custom port */
	private MessageHandler(int port) {
		this.port = port;
		sendLog = new SyncLogger(5);
		SessionInfo.getInstance().getLog().append("create new message handler");
	}

	public SyncLogger getSendLog() {
		return sendLog;
	}

	public SyncLogger getRcvLog() {
		if (srv != null)
			return HandlerServer.rcvLog;
		return null;
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

	static int cnt;

	static {
		cnt = 0;
	}

	public synchronized void sendMsg(String msg, InetAddress address, int port)
			throws SocketException, IOException {

		msg = SessionInfo.getInstance().getMySelf().getId() + "." + cnt++ + "#"
				+ msg;

		if (msg.length() < 50) {
			sendLog.append(msg);
		} else {
			sendLog.append(msg.substring(0, 49) + "...");
		}

		CRC32 check = new CRC32();
		check.update(msg.getBytes());

		msg = check.getValue() + "#" + msg;
	
		DatagramSocket clientSocket = new DatagramSocket();
		DatagramPacket sendPacket = new DatagramPacket(msg.getBytes(),
				msg.getBytes().length, address, port);
		clientSocket.send(sendPacket);
		clientSocket.close();
	}

	/**
	 * start the handling of messages, starts a thread waiting for incoming
	 * messages and distributing the work to the sync modules
	 */
	public void startHandling() {
		Log.d(TAG, "start handling");
		srv = new HandlerServer();
		srv.start(port);
	}

	/** stop the listener for requests */
	public void stopHandling() {
		Log.d(TAG, "stop handling");
		getRcvLog().clear();
		getSendLog().clear();
		SessionInfo.getInstance().getLog().clear();
		SessionInfo.getInstance().getPeers().clear();
		FSync.getInstance().stopSync();
		srv.interrupt();
	}

}
