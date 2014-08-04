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
import mf.sync.utils.Utils;
import android.util.Log;

public class FSyncServer extends Thread {

	private FSync parent;

	public static final String TAG = "FSync Server";

	public FSyncServer(FSync parent) {
		this.parent = parent;
	}

	public void run() {
		Log.d(TAG, "started fine sync thread");
		long avgTs = parent.initAvgTs();

		int ctr = 0;

		while (!isInterrupted() && ctr++ < 3) {
			try {
				Thread.sleep(SyncI.PERIOD_FS_MS);
			} catch (InterruptedException iex) {
				break;
			}

			/* udpate */
			long nts = Utils.getTimestamp();
			avgTs = parent.alignAvgTs(nts);
			parent.broadcastToPeers(nts);
		}

		long nts = Utils.getTimestamp();
		avgTs = parent.alignAvgTs(nts);
		Utils.setPlaybackTime((int) avgTs);
		SessionInfo.getInstance().log("setting time to: " + avgTs); 
		SessionInfo.getInstance().log("FSync interrupted or stopped");

		Log.d(TAG, "FSync interrupted");
	}
}