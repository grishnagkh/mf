/*
 * SessionManager.java
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
package mf.sync.utils;

import java.util.Map;

import mf.sync.utils.log.SyncLogger;
import android.annotation.SuppressLint;
import android.support.v4.util.ArrayMap;
import android.util.Log;

public class SessionInfo {

	/** singleton instance */
	private static SessionInfo instance;

	/** used to store data about self */
	private Peer mySelf;

	/** Map of known peers */
	private Map<Integer, Peer> peers;

	private String sessionId;
	private int seqN;
	private long validThru;
	private SyncLogger log;

	public SessionInfo() {
		peers = new ArrayMap<Integer, Peer>();
		// log = new ArrayList<String>();
		log = new SyncLogger(20);
		seqN = 0;
	}

	/**
	 * singleton method
	 */
	public static SessionInfo getInstance() {
		if (instance == null) {
			instance = new SessionInfo();
		}
		return instance;
	}

	@SuppressLint("UseSparseArrays")
	public Map<Integer, Peer> getPeers() {
		return peers;
	}

	public void setPeers(Map<Integer, Peer> peers) {
		Log.d("SessionInfo", "setting peers: " + peers);
		this.peers = peers;
	}

	public void setMySelf(Peer mySelf) {
		this.mySelf = mySelf;
	}

	public Peer getMySelf() {
		return mySelf;
	}

	public void setSessionId(String sessionId) {
		this.sessionId = sessionId;
	}

	public void setValidThru(String validThru) {
		this.validThru = Long.parseLong(validThru);
	}

	public String getSessionId() {
		return sessionId;
	}

	public long getValidThru() {
		return validThru;
	}

	public void log(String s) {
		log.append(s);
	}

	public SyncLogger getLog() {
		return log;
	}

	public int getSeqN() {
		return seqN;
	}

	public void setSeqN(int sn) {
		seqN = sn;
	}

}
