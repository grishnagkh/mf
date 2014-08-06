/*
 * CSyncMsg.java
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

import java.net.InetAddress;
import java.net.UnknownHostException;

import mf.sync.SyncI;

public class CSyncMsg {
	/** message fields */
	public InetAddress senderIp;
	public long pts, nts;
	public int peerId, myId, senderPort;

	/**
	 * Constructor
	 */
	public CSyncMsg() {
	}

	/**
	 * Constructor
	 * 
	 * @param addr
	 * @param port
	 * @param pts
	 * @param nts
	 * @param id
	 */
	public CSyncMsg(InetAddress addr, int port, int pts, long nts, int id) {
		myId = id;
		senderPort = port;
		senderIp = addr;
		this.pts = pts;
		this.nts = nts;
	}

	/**
	 * transforms a string representation of a csync message into a CSycMsg
	 * object
	 * 
	 * @param str
	 * @return
	 * @throws UnknownHostException
	 */
	public static CSyncMsg fromString(String str) throws UnknownHostException {
		CSyncMsg msg = new CSyncMsg();

		String[] msgA = str.split(SyncI.DELIM);

		msg.senderIp = InetAddress.getByName(msgA[SyncI.CS_SENDER_IP_POS]);
		msg.senderPort = Integer.parseInt(msgA[SyncI.CS_SENDER_PORT_POS]);
		msg.pts = Long.parseLong(msgA[SyncI.CS_PTS_POS]);
		msg.nts = Long.parseLong(msgA[SyncI.CS_NTS_POS]);
		msg.peerId = Integer.parseInt(msgA[SyncI.CS_PEER_ID_POS]);

		return msg;
	}

	/**
	 * get string representation for sending over udp
	 * 
	 * @param delim
	 * @param type
	 * @return as tring representation of the message
	 */
	public String getSendMessage(String delim, int type) {
		return type + delim + senderIp.getHostAddress() + delim + senderPort
				+ delim + pts + delim + nts + delim + myId;
	}
}
