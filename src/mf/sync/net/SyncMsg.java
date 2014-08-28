/*
 * SyncMsg.java
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

import java.io.Serializable;
import java.net.InetAddress;

/**
 * class holding commn information about sent messages
 * 
 * @author stefan
 *
 */
public abstract class SyncMsg implements Serializable {

	private static final long serialVersionUID = 7179713119327407162L;
	/*
	 * in order to avoid problems with the android udp stack, which received
	 * messages multiple times, we have message ids and peer ids with all sync
	 * messages. every peer stores the maximum message id per sending peer
	 * received so far, in order to filter duplicate messages
	 */
	/** id of the sending peer */
	public int peerId;
	/** id of the message to be sent */
	public int msgId;
	/** destination port */
	public int destPort;
	/** destination address */
	public InetAddress destAddress;

}
