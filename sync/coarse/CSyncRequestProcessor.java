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

import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;

import mf.sync.SyncI;
import mf.sync.net.CSyncMsg;
import mf.sync.net.MessageHandler;
import mf.sync.utils.Clock;
import mf.sync.utils.Peer;
import mf.sync.utils.SessionInfo;
import mf.sync.utils.Utils;
import android.util.Log;

/**
 * class processing a coarse sync request
 * 
 * @author stefan petscharnig
 */
public class CSyncRequestProcessor implements Runnable {
	/** the received request */
	private CSyncMsg req;
	public static final String TAG = "csprr";

	/**
	 * Constructor
	 * 
	 * @param cSyncMsg
	 * @throws UnknownHostException
	 */
	public CSyncRequestProcessor(CSyncMsg cSyncMsg) throws UnknownHostException {
		this.req = cSyncMsg;
	}

	@Override
	public void run() {

		CSyncMsg msg = new CSyncMsg(SessionInfo.getInstance().getMySelf()
				.getAddress(), SessionInfo.getInstance().getMySelf().getPort(),
				Utils.getPlaybackTime(), Clock.getTime(), SessionInfo
						.getInstance().getMySelf().getId());

		// send response
		try {
			MessageHandler.getInstance().sendMsg(
					msg.getSendMessage(SyncI.DELIM, SyncI.TYPE_COARSE_RESP),
					req.senderIp, req.senderPort);
		} catch (SocketException e) {
			Log.e(TAG, "could not send message");
		} catch (IOException e) {
			Log.e(TAG, "could not send message");
		}

		if (!SessionInfo.getInstance().getPeers().containsKey(req.peerId)) {
			Peer p = new Peer(req.peerId, req.senderIp, req.senderPort);
			SessionInfo.getInstance().getPeers().put(req.peerId, p);
		}

	}
}
