/*
 * CSyncServer.java
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
import java.util.List;

import mf.player.gui.MainActivity;
import mf.sync.fine.FSync;
import mf.sync.net.MessageHandler;
import mf.sync.utils.Peer;
import mf.sync.utils.SessionInfo;
import mf.sync.utils.SyncI;
import mf.sync.utils.Utils;
import android.util.Log;

/**
 * 
 * class handling the initial step of the coarse sync 1 send a request to all
 * known peers 2 wait some time 3 parse and process responses
 * 
 * @author stefan petscharnig
 *
 */
public class CSyncServer extends Thread {

	public static final String TAG = "csr";
	public static final boolean DEBUG_ON_SCREEN = true;
	private int SEGSIZE = 2000;

	private List<String> msgQueue;

	public CSyncServer(List<String> messageQueue) {
		if (DEBUG_ON_SCREEN) {
			SessionInfo.getInstance().log("new csync server");
		}
		msgQueue = messageQueue;
	}

	public void run() {

		if (SessionInfo.getInstance().getMySelf() == null) {
			// for testing without session server // XXX
			if (DEBUG_ON_SCREEN)
				SessionInfo
						.getInstance()
						.log("mySelf was null, so no session info was received, enter playback mode (synced with oneself ;) )");
			Peer p = new Peer(1, Utils.getWifiAddress(MainActivity.c), 12346);
			SessionInfo.getInstance().setMySelf(p);
		}

		/* 1 */

		String myIP = SessionInfo.getInstance().getMySelf().getAddress()
				.getHostAddress();
		int myPort = SessionInfo.getInstance().getMySelf().getPort();
		int myId = SessionInfo.getInstance().getMySelf().getId();

		String msg = Utils.buildMessage(SyncI.DELIM, SyncI.TYPE_COARSE_REQ,
				myIP, myPort, 0, Utils.getTimestamp(), myId);
		if (DEBUG_ON_SCREEN)
			SessionInfo.getInstance().log("built csync message: " + msg);

		for (Peer p : SessionInfo.getInstance().getPeers().values()) {

			if (DEBUG_ON_SCREEN)
				SessionInfo.getInstance().log("Processing peer: " + p);

			try {
				MessageHandler.getInstance().sendMsg(msg, p.getAddress(),
						p.getPort());
			} catch (SocketException e) {
				if (DEBUG_ON_SCREEN)
					SessionInfo.getInstance().log(
							"FATAL: could not send message" + e.toString());
			} catch (IOException e) {
				if (DEBUG_ON_SCREEN)
					SessionInfo.getInstance().log(
							"FATAL: could not send message " + e.toString());
			}
		}
		/* 2 */
		try {
			Thread.sleep(SyncI.WAIT_TIME_CS_MS);
		} catch (InterruptedException e) {
			return;
		}

		long avgPTS = 0;

		/* 3 */

		if (msgQueue.size() == 0) {
			if (DEBUG_ON_SCREEN)
				SessionInfo.getInstance().log("no messages in response queue");
			FSync.getInstance().startSync();
			return;
		}

		for (String response : msgQueue) {
			String[] responseFields = response.split(SyncI.DELIM);
			long pts = Long.parseLong(responseFields[3]);
			long nts = Long.parseLong(responseFields[4]);
			avgPTS += pts + (Utils.getTimestamp() - nts);
			Log.d(TAG,
					"trip time (peer " + responseFields[4] + "): "
							+ (Utils.getTimestamp() - nts));
		}
		avgPTS /= msgQueue.size();

		// empty request queue
		msgQueue.clear();

		if (DEBUG_ON_SCREEN)
			SessionInfo.getInstance().log(
					"calculated average from c synchronization: " + avgPTS);

		/*
		 * scale time to the 2s segments, just to make some sense for fine
		 * sync^^
		 */

		avgPTS = SEGSIZE + avgPTS - avgPTS % SEGSIZE;

		Utils.setPlaybackTime((int) avgPTS);
		if (DEBUG_ON_SCREEN)
			SessionInfo.getInstance().log("setting playback time to " + avgPTS);

		FSync.getInstance().startSync();
	}

}
