/*
 * SyncI.java
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
package mf.sync;

/**
 * 
 * Interface containing synchronization constants and methods
 * 
 * @author stefan petscharnig
 *
 */
public interface SyncI {

	/**
	 * amount of time we wait in the coarse sync after sending a request to all
	 * known peers
	 */
	public static final int WAIT_TIME_CS_MS = 1500;
	/** fine sync period length */
	public static final int PERIOD_FS_MS = 1000;

	/** bloom filter length [byte] */
	public static final int BLOOM_FILTER_LEN_BYTE = 8;
	/** number of used hash functions in the bloom filter */
	public static final int N_HASHES = 3;

	/** constants for message types */
	public static final int TYPE_COARSE_REQ = 1;
	public static final int TYPE_COARSE_RESP = 2;
	public static final int TYPE_FINE = 3;

}
