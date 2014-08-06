/*
 * SyncLogger.java
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
package mf.sync.utils.log;

/**
 * 
 * Ring buffer log implementation for debugging. Stores the last <logSize>
 * strings
 * 
 * @author stefan petscharnig
 *
 */

public class SyncLogger {
	/** the actual log data structure */
	private String[] log;
	/** actual position in the buffer */
	private int actual;
	/** number of saved messages */
	private int logSize;

	/**
	 * Constructor
	 * 
	 * @param logSize
	 *            number of saved messages
	 */
	public SyncLogger(int logSize) {
		this.logSize = logSize;
		log = new String[logSize];
	}

	/**
	 * append a string to the end of the log
	 * 
	 * @param elem
	 */
	public void append(String elem) {
		log[actual++ % logSize] = elem;
	}

	/**
	 * clear the log
	 */
	public void clear() {
		actual = 0;
		for (int i = actual % logSize; i < actual % logSize + logSize; i++) {
			log[i % logSize] = null;
		}
	}

	/**
	 * @return a string representation of the log, newest messages first
	 */
	@Override
	public String toString() {
		if (actual == 0)
			return "";

		StringBuffer sb = new StringBuffer();
		for (int i = actual + logSize - 1; i > actual - 1; i--) {
			if (log[i % logSize] != null)
				sb.append(log[i % logSize] + "\n");
		}
		return sb.toString();
	}
}
