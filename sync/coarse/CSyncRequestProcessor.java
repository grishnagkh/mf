/*
 * CSyncRequestProcessor.java
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
package mf.sync.coarse;

import java.net.UnknownHostException;

import mf.sync.SyncI;
import mf.sync.net.CSyncMsg;
import mf.sync.net.MessageHandler;
import mf.sync.utils.Clock;
import mf.sync.utils.Peer;
import mf.sync.utils.SessionInfo;
import mf.sync.utils.Utils;

/**
 * class processing a coarse sync request
 * 
 * @author stefan petscharnig
 */
public class CSyncRequestProcessor implements Runnable {
	/** the received request */
	private CSyncMsg req;

	/**
	 * Constructor
	 * 
	 * @param cSyncMsg
	 * @throws UnknownHostException
	 */
	public CSyncRequestProcessor(CSyncMsg cSyncMsg) {
		this.req = cSyncMsg;
	}

	@Override
	public void run() {

		CSyncMsg msg = new CSyncMsg(SessionInfo.getInstance().getMySelf()
				.getAddress(), SessionInfo.getInstance().getMySelf().getPort(),
				Utils.getPlaybackTime(), Clock.getTime(), SessionInfo
						.getInstance().getMySelf().getId());

		// send response
		msg.address = req.senderIp;
		msg.port = req.senderPort;
		msg.senderIp = SessionInfo.getInstance().getMySelf().getAddress();
		msg.senderPort = SessionInfo.getInstance().getMySelf().getPort();
		msg.type = SyncI.TYPE_COARSE_RESP;
		MessageHandler.getInstance().sendMsg(msg);

		if (!SessionInfo.getInstance().getPeers().containsKey(req.peerId)) {
			Peer p = new Peer(req.peerId, req.senderIp, req.senderPort);
			SessionInfo.getInstance().getPeers().put(req.peerId, p);
		}

	}
}
