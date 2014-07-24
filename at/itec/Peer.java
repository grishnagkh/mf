/*
 * Peer.java
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
package mf.at.itec;

import java.net.InetAddress;

/**
 * 
 * model for a peer
 * 
 * @author stefan petscharnig
 *
 */
public class Peer {
	/** the peer id */
	private int id;
	/** the peer port where we are listening to sync messages */
	private int port;
	/** the peer address */
	private InetAddress address;

	/**
	 * constructor
	 * 
	 * @param id
	 * @param address
	 * @param port
	 */
	public Peer(int id, InetAddress address, int port) {
		this.id = id;
		this.address = address;
		this.port = port;
	}

	/**
	 * getter
	 * 
	 * @return
	 */
	public int getId() {
		return id;
	}

	/**
	 * setter
	 * 
	 * @param id
	 */
	public void setId(int id) {
		this.id = id;
	}

	/**
	 * getter
	 * 
	 * @return
	 */
	public int getPort() {
		return port;
	}

	/**
	 * setter
	 * 
	 * @param id
	 */
	public void setPort(int port) {
		this.port = port;
	}

	/**
	 * getter
	 * 
	 * @return
	 */
	public InetAddress getAddress() {
		return address;
	}

	/**
	 * setter
	 * 
	 * @param id
	 */
	public void setAddress(InetAddress address) {
		this.address = address;
	}

	public String toString() {
		return id + "," + address + "," + port;
	}
}