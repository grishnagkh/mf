/*
 * CSync.java
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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import mf.sync.SyncI;
import mf.sync.fine.FSync;
import mf.sync.net.CSyncMsg;
import mf.sync.net.MessageHandler;
import mf.sync.utils.Clock;
import mf.sync.utils.Peer;
import mf.sync.utils.PlayerControl;
import mf.sync.utils.SessionInfo;

/**
 *
 * @author stefan petscharnig
 */

public class CSync extends Thread {

	/** Singleton method */
	public static CSync getInstance() {
		if (instance == null)
			instance = new CSync();
		return instance;
	}

	/** singleton instance */
	private static CSync instance;
	/** debug messages in the session log */
	public static final boolean DEBUG = true;
	public static final boolean FSYNC_ENABLED = true;

	private static final long SEGSIZE = 4000;

	/** request queue filled by message handler while we are waiting */
	private List<CSyncMsg> msgQueue;

	/** Singleton constructor */
	private CSync() {
		msgQueue = new ArrayList<CSyncMsg>();
	}

	// /** method for filling the queue response */
	public void coarseResponse(CSyncMsg msg) {
		if (DEBUG)
			SessionInfo.getInstance().log("process coarse response");
		msgQueue.add(msg);
	}

	@Override
	public void interrupt() {
		super.interrupt();
		/* delete this instance, and, as necessary, create a new one */
		instance = null;
	}

	// // test
	// public void destroy() {
	// instance = null;
	// cSyncServer = null;
	// }

	/**
	 * process a sync request message
	 *
	 * @throws UnknownHostException
	 */

	public void processRequest(CSyncMsg cSyncMsg) {
		if (DEBUG)
			SessionInfo.getInstance().log("process coarse request");
		new Thread(new CSyncRequestProcessor(cSyncMsg)).start();
	}

	@Override
	public void run() {

		if (DEBUG)
			SessionInfo.getInstance().log("coarse sync started");

		InetAddress myAdress = SessionInfo.getInstance().getMySelf()
				.getAddress();
		int myPort = SessionInfo.getInstance().getMySelf().getPort();
		int myId = SessionInfo.getInstance().getMySelf().getId();

		CSyncMsg msg = new CSyncMsg(SessionInfo.getInstance().getMySelf()
				.getAddress(), SessionInfo.getInstance().getMySelf().getPort(),
				0, Clock.getTime(), SessionInfo.getInstance().getMySelf()
						.getId());
		msg.type = SyncI.TYPE_COARSE_REQ;
		msg.senderIp = myAdress;
		msg.senderPort = myPort;
		msg.myId = myId;
		if (DEBUG)
			SessionInfo.getInstance().log("built csync message");

		for (Peer p : SessionInfo.getInstance().getPeers().values()) {
			if (DEBUG)
				SessionInfo.getInstance().log("Processing peer: " + p);
			msg.destAddress = p.getAddress();
			msg.destPort = p.getPort();

			MessageHandler.getInstance().sendMsg(msg);

		}

		/* 2 */
		try {
			Thread.sleep(SyncI.WAIT_TIME_CS_MS);
		} catch (InterruptedException e) {
			this.interrupt();
			return;
		}

		long avgPTS = 0;

		/* 3 */

		if (msgQueue.size() == 0) {
			if (DEBUG)
				SessionInfo
						.getInstance()
						.log("no messages in response queue, no one seems to be here yet");

			/*
			 * i think this is not necessary anymore... remember that nobody
			 * seems to be here?
			 */
			SessionInfo.getInstance().setCSynced();

			return;
		}

		int ctr = 0;

		for (CSyncMsg resp : msgQueue) {
			avgPTS += resp.pts + (Clock.getTime() - resp.nts);
			ctr++;
		}
		if (ctr != 0)
			avgPTS /= ctr;

		// empty request queue
		msgQueue.clear();

		if (DEBUG)
			SessionInfo.getInstance().log(
					"calculated average from c synchronization: " + avgPTS);

		/*
		 * scale time to the 2s segments, just to make some sense for fine
		 * sync^^
		 */

		avgPTS = SEGSIZE + avgPTS - avgPTS % SEGSIZE;

		PlayerControl.setPlaybackTime((int) avgPTS);

		//
		// /**
		// * we ensure that the playback resumed a little
		// */
		PlayerControl.ensureTime(avgPTS, SEGSIZE / 2);

		if (FSYNC_ENABLED) {
			if (FSync.getInstance().getState() != Thread.State.NEW)
				FSync.getInstance().interrupt();
			FSync.getInstance().start();
		}

		if (DEBUG)
			SessionInfo.getInstance().log("coarse sync finished");
		/*
		 * to delete this thread, when we want to be started again
		 */
		instance = null;
		SessionInfo.getInstance().setCSynced();
	}

	/** start the coarse sync *server* */
	@Override
	public void start() {
		if (DEBUG)
			SessionInfo.getInstance().log("start coarse sync");

		super.start();
		// finished = false;

		// stopSync();
		// cSyncServer = new CSyncServer(msgQueue);
		// cSyncServer.start();
	}

	// /**
	// * stop a potentially running coarse sync
	// */
	// public void stopSync() {
	// finished = true;
	// if (cSyncServer != null && cSyncServer.isAlive()) {
	// if (DEBUG)
	// SessionInfo.getInstance().log("stopping already running csync");
	// cSyncServer.interrupt();
	// }
	// }
}
