/*
 * BloomFilter.java
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

package mf.bloomfilter;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Bloom filter implementation for int keys using byte arrays
 */
public class BloomFilter {

	private byte[] bloom;
	private int nHashes;

	private MessageDigest md;

	public BloomFilter() throws NoSuchAlgorithmException {
		this(1024, 2);
	}

	public BloomFilter(int sizeBytes, int nHashes)
			throws NoSuchAlgorithmException {
		bloom = new byte[sizeBytes];
		this.nHashes = nHashes;
		md = MessageDigest.getInstance("SHA-1");
	}

	public void add(int toAdd) {
		for (int pos : getIndices(getBytes(toAdd), nHashes)) {
			bloom[pos / 8] |= (1 << (pos % 8));
		}
	}

	public void or(BloomFilter bbf) {
		for (int i = 0; i < bloom.length; i++) {
			bloom[i] |= bbf.bloom[i];
		}
	}

	public void and(BloomFilter bbf) {
		for (int i = 0; i < bloom.length; i++) {
			bloom[i] &= bbf.bloom[i];
		}
	}

	public void xor(BloomFilter bbf) {
		for (int i = 0; i < bloom.length; i++) {
			bloom[i] ^= bbf.bloom[i];
		}
	}

	public boolean isZero() {
		for (int i = 0; i < bloom.length; i++) {
			if (bloom[i] != 0) {
				return false;
			}
		}
		return true;
	}

	public BloomFilter clone() {
		BloomFilter bbf = null;
		try {
			bbf = new BloomFilter(bloom.length, nHashes);
			bbf.or(this);
		} catch (NoSuchAlgorithmException e) {
		}
		return bbf;
	}

	public boolean contains(int n) {
		for (int pos : getIndices(getBytes(n), nHashes)) {
			pos = Math.abs(pos % (bloom.length * 8));
			if ((bloom[pos / Byte.SIZE] & (1 << (pos % Byte.SIZE))) == 0) {
				// bit is not set
				return false;
			}
		}
		return true;
	}

	public boolean equals(Object other) {
		if (!(other instanceof BloomFilter))
			return false;

		BloomFilter tmp = clone();
		tmp.xor((BloomFilter) other);
		return tmp.isZero();
	}

	/*
	 * when the bloom is restored over the network, we estimate the number of
	 * peers, therefore we search from zero up to a maximum max (included)
	 */
	public int nElements(int max) {
		int ctr = 0;
		for (int i = 0; i <= max; i++) {
			if (this.contains(i)) {
				ctr++;
			}
		}
		return ctr;
	}

	public String toString() {
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < bloom.length; i++) {
			sb.append(bloom[i] + ";");
		}
		return sb.toString();
	}

	/*
	 * converts toString representation into a bloomfilter with given number of
	 * hashes per element
	 */
	public static BloomFilter fromString(String str, int nHashes)
			throws NoSuchAlgorithmException {

		String[] blub = str.split(";");
		BloomFilter bbf = new BloomFilter(blub.length, nHashes);

		int ctr = 0;
		for (String pos : blub) {
			if (pos == "")
				break;
			bbf.bloom[ctr++] = Byte.parseByte(pos);
		}
		return bbf;
	}

	private byte[] getBytes(int i) {
		byte[] ret = new byte[Integer.SIZE / Byte.SIZE];
		int ctr = -1;
		while (++ctr < ret.length) {
			ret[ctr] = (byte) (i % (1 << Byte.SIZE));
			i = i >> Byte.SIZE;
		}
		return ret;
	}

	private int[] getIndices(byte[] data, int hashes) {

		int[] result = new int[hashes];
		byte salt = 1;

		for (int j = 0; j < hashes; j++) {

			md.update(salt++);
			byte[] digest = md.digest(data);
			int tmpHash = digest[0];
			for (int i = 1; i < Integer.SIZE / Byte.SIZE && i < digest.length; i++) {
				tmpHash <<= Byte.SIZE;
				tmpHash |= digest[i];
			}
			tmpHash &= 0x7FFFFFFF; // clear first bit
			tmpHash %= bloom.length * Byte.SIZE; // align to size of bloom
													// filter
			result[j] = tmpHash;
		}
		return result;

	}
}
