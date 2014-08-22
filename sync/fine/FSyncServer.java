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

/**
 *
 * Server periodically broadcasting fine sync messages
 *
 * @author stefan petscharnig
 *
 */
@Deprecated
public class FSyncServer extends Thread {
	private static final boolean DEBUG = false;
	/** FSnyc instance for aligning timestamps */
	private FSync parent;

	/**
	 * Constructor
	 *
	 * @param parent
	 */
	public FSyncServer(FSync parent) {
		// this.parent = parent;
	}

	@Override
	public void interrupt() {
		// if (DEBUG)
		// SessionInfo.getInstance().log("FSync interrupted");
		super.interrupt();
	}

	/**
	 * atm we only send 3 message rounds, should work for a network with
	 * diameter 3 (assuming link speed equal on all links)
	 */
	@Override
	public void run() {
		// if (DEBUG)
		// SessionInfo.getInstance().log("FSync thread started");
		// while (!isInterrupted()) {
		// try {
		// Thread.sleep(SyncI.PERIOD_FS_MS);
		// } catch (InterruptedException iex) {
		// break;
		// }
		// parent.broadcastToPeers();
		// }
		// if (DEBUG)
		// SessionInfo.getInstance().log("FSync thread died");
	}
}