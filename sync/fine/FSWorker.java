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

import mf.sync.utils.SyncI;
import mf.sync.utils.Utils;
import android.util.Log;

public class FSWorker extends Thread {

	private FSync parent;

	public static final String TAG = "FS Worker";

	public FSWorker(FSync parent) {
		this.parent = parent;
	}

	public void run() {

		Log.d(TAG, "started fine sync thread");
		// create the bloom filter
		long avgTs;
		int remSteps = 40; // /XXX just for testing

		avgTs = parent.initAvgTs();

		while (!isInterrupted() && remSteps-- > 0) {
			// udpate
			long nts = Utils.getTimestamp();
			avgTs = parent.alignAvgTs(nts);
			parent.broadcastToPeers(nts);
			try {
				Thread.sleep(SyncI.PERIOD_FS_MS);
			} catch (InterruptedException iex) {
				// ignore
			}
		}
		/*
		 * end while, fine sync ended TODO: for resync, simply start this again
		 */
		if (!isInterrupted()) {
			Log.d(TAG, "setting time to: " + avgTs);
			Utils.setPlaybackTime((int) avgTs);
		}

	}
}