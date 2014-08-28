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

public class CSyncMsg extends SyncMsg {
	private static final long serialVersionUID = -3482596285327476854L;
	/** message fields */
	public InetAddress senderIp;
	public long pts, nts;
	public int myId, senderPort, type;

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

}
