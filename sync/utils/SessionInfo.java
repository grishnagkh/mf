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

import android.annotation.SuppressLint;

public class SessionInfo {

	/** singleton instance */
	private static SessionInfo instance;

	/** used to store data about self */
	private Peer mySelf;

	/** Map of known peers */
	private Map<Integer, Peer> peers;

	private String sessionId;

	private long validThru;

	/**
	 * singleton method, do not use (default port is hard coded into vlc, use
	 * getInstance() instead)
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

}
