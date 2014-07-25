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

import java.util.ArrayList;
import java.util.List;

import mf.sync.utils.SyncI;

/**
 * 
 * @author stefan petscharnig
 *
 */

public class CSync implements SyncI {

	public static final int SEGSIZE = 2000;// for now^^

	/** request queue filled by message handler while we are waiting */
	private List<String> msgQueue;
	/** singleton instance */
	private static CSync instance;

	/** Singleton constructor */
	private CSync() {
		msgQueue = new ArrayList<String>();
	}

	/** Singleton method */
	public static CSync getInstance() {
		if (instance == null)
			instance = new CSync();
		return instance;
	}

	/** method for filling the queue response */
	public void coarseResponse(String msg) {
		msgQueue.add(msg);
	}

	/** start the sync server */
	public void startSync() {
		new Thread(new CSyncServer(msgQueue)).start();
	}

	/** process a sync request message */
	public void processRequest(String request) {
		new Thread(new CSyncRequestProcessor(request)).start();
	}
}
