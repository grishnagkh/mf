/*
 * FSWorker.java
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

package mf.sync.fine;

import mf.sync.SyncI;
import mf.sync.utils.SessionInfo;

/**
 * 
 * Server periodically broadcasting fine sync messages
 * 
 * @author stefan petscharnig
 *
 */
public class FSyncServer extends Thread {
	private static final boolean DEBUG = true;
	/** FSnyc instance for aligning timestamps */
	private FSync parent;

	/**
	 * Constructor
	 * 
	 * @param parent
	 */
	public FSyncServer(FSync parent) {
		this.parent = parent;
	}

	/**
	 * atm we only send 3 message rounds, should work for a network with
	 * diameter 3
	 */
	public void run() {
		int ctr = 0;
		if (DEBUG)
			SessionInfo.getInstance().log("FSync thread started");
		// TODO: when to stop the sync ? maybe when we have many devices it is
		// everytime restarted?
		while (!isInterrupted() && ctr++ < 3) {
			parent.broadcastToPeers();
			try {
				Thread.sleep(SyncI.PERIOD_FS_MS);
			} catch (InterruptedException iex) {
				break;
			}
		}
		if (DEBUG)
			SessionInfo.getInstance().log("FSync thread died");
	}

	@Override
	public void interrupt() {
		if (DEBUG)
			SessionInfo.getInstance().log("FSync interrupted");
		super.interrupt();
	}

}