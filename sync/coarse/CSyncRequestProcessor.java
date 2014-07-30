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
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import mf.sync.net.MessageHandler;
import mf.sync.utils.Peer;
import mf.sync.utils.SessionInfo;
import mf.sync.utils.SyncI;
import mf.sync.utils.Utils;
import android.util.Log;

/**
 * class processing a coarse sync request
 * 
 * @author stefan petscharnig
 */
class CSyncRequestProcessor implements Runnable {

	private String req;
	public static final String TAG = "csprr";

	public CSyncRequestProcessor(String req) {
		this.req = req;
	}

	// sometimes we get 7 fileds back, most times 6, happy hacking
	public void run() {
		// parse request
		Log.d(TAG, "got the following request: " + req);
		String[] responseFields = req.split(SyncI.DELIM);

		int peerId;

		if (responseFields.length == 6) {
			peerId = Integer.parseInt(responseFields[5]);
		} else if (responseFields.length == 7) { // check our hack
			peerId = Integer.parseInt(responseFields[6]);
		} else {
			SessionInfo.getInstance().log(
					"dropping invalid request [length]: " + req);
			return; // invalid message
		}

		String senderIP = responseFields[1];
		InetAddress peerAddress = null;

		try {
			peerAddress = InetAddress.getByName(senderIP);
		} catch (UnknownHostException e) {
			Log.d(TAG, "invalid request [IP]");
			return; // invalid IP, don't care;
		}
		int senderPort = Integer.parseInt(responseFields[2]);

		int myPts = Utils.getPlaybackTime();
		long myNts = Utils.getTimestamp();

		String myIP = SessionInfo.getInstance().getMySelf().getAddress()
				.getHostAddress();
		int myPort = SessionInfo.getInstance().getMySelf().getPort();
		int myId = SessionInfo.getInstance().getMySelf().getId();
		String msg = Utils.buildMessage(SyncI.DELIM, SyncI.TYPE_COARSE_RESP,
				myIP, myPort, myPts, myNts, myId);

		// send response
		try {
			MessageHandler.getInstance().sendMsg(msg, peerAddress, senderPort);
		} catch (SocketException e) {
			Log.e(TAG, "could not send message");
		} catch (IOException e) {
			Log.e(TAG, "could not send message");
		}

		if (!SessionInfo.getInstance().getPeers().containsKey(peerId)) {
			Log.d("", "add a new peer");
			Peer p = new Peer(peerId, peerAddress, senderPort);
			SessionInfo.getInstance().getPeers().put(peerId, p);
		}

	}
}
