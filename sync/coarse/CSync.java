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

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import mf.sync.net.CSyncMsg;
import mf.sync.utils.SessionInfo;

/**
 *
 * @author stefan petscharnig
 */

public class CSync {
	/** Singleton method */
	public static CSync getInstance() {
		if (instance == null)
			instance = new CSync();
		return instance;
	}

	/** debug messages in the session log */
	public static final boolean DEBUG = true;
	/** server instance */
	private CSyncServer cSyncServer;
	/** request queue filled by message handler while we are waiting */
	private List<CSyncMsg> msgQueue;

	/** singleton instance */
	private static CSync instance;

	protected boolean finished = false;

	/** Singleton constructor */
	private CSync() {
		if (DEBUG)
			SessionInfo.getInstance().log("new csync, init message queue");
		msgQueue = new ArrayList<CSyncMsg>();
	}

	/** method for filling the queue response */
	public void coarseResponse(CSyncMsg msg) {
		if (DEBUG)
			SessionInfo.getInstance().log("process coarse response");
		msgQueue.add(msg);
	}

	// test
	public void destroy() {
		instance = null;
		cSyncServer = null;
	}

	public boolean hasFinished() {
		return finished;
	}

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

	/** start the sync server */
	public void startSync() {
		finished = false;
		if (DEBUG)
			SessionInfo.getInstance().log("start sync");
		stopSync();
		cSyncServer = new CSyncServer(msgQueue);
		cSyncServer.start();
	}

	/**
	 * stop a potentially running coarse sync
	 */
	public void stopSync() {
		finished = true;
		if (cSyncServer != null && cSyncServer.isAlive()) {
			if (DEBUG)
				SessionInfo.getInstance().log("stopping already running csync");
			cSyncServer.interrupt();
		}
	}
}
