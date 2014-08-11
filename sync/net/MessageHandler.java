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

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

import mf.sync.utils.SessionInfo;
import mf.sync.utils.log.SyncLogger;

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
		HandlerServer.createLogger();
	}

	public SyncLogger getSendLog() {
		return sendLog;
	}

	public SyncLogger getRcvLog() {
		return HandlerServer.rcvLog;
	}

	static int cnt;

	static {
		cnt = 0;
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
					msg.address, msg.port);
			DatagramSocket clientSocket = new DatagramSocket();
			clientSocket.send(packet);
			clientSocket.close();
			os.close();
		} catch (IOException e) {

			sendLog.append("error");

			sendLog.append(e.toString());
			return;
		}
		sendLog.append(msg.peerId + "." + msg.msgId + " " + " " + msg.address
				+ ":" + msg.port);

	}

	/**
	 * start the handling of messages, starts a thread waiting for incoming
	 * messages and distributing the work to the sync modules
	 */

	public void startHandling() {
		if (srv == null) {
			srv = new HandlerServer();
			srv.start(port);
		}
	}

	/**
	 * stop the listener for requests
	 * 
	 * @param clearSessionData
	 */
	public void stopHandling(boolean clearSessionData) {
		if (clearSessionData) {
			SessionInfo.getInstance().log("clear sessiong data...");
			SessionInfo.getInstance().log("clear peers...");
			SessionInfo.getInstance().getPeers().clear();
		}

		if (srv != null) {
			SessionInfo.getInstance()
					.log("try to interrupt message handler...");
			srv.interrupt();
		}
		SessionInfo.getInstance().log("setting message handler to null");
		srv = null;
	}

	public void resumeHandling() {
		if(srv != null){
			srv.ignoreIncoming(false);
		}
	}

	public void pauseHandling() {
		if(srv != null){
			srv.ignoreIncoming(true);
		}
	}

}
