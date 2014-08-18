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

import java.io.Serializable;
import java.security.NoSuchAlgorithmException;

/**
 * Bloom filter implementation for integer keys using byte arrays
 */
public class BloomFilter implements Serializable {

	private static final long serialVersionUID = -6253484899527882578L;
	/** the actual data structure */
	private byte[] bloom;
	/** number of hashes per element */
	private int nHashes;
	/** number of added peers */
	private int nPeers;

	public int getNPeers() {
		return nPeers;
	}

	public void merge(BloomFilter bf) {
		this.or(bf);
		nPeers += bf.getNPeers();
	}

	/**
	 * Constructor
	 * 
	 * @throws NoSuchAlgorithmException
	 */
	public BloomFilter() throws NoSuchAlgorithmException {
		this(1024, 2);
	}

	/**
	 * Constructor
	 * 
	 * @param sizeBytes
	 * @param nHashes
	 * @throws NoSuchAlgorithmException
	 */
	public BloomFilter(int sizeBytes, int nHashes) {
		bloom = new byte[sizeBytes];
		this.nHashes = nHashes;
		nPeers = 0;
	}

	/**
	 * add an element to the bloom filter
	 * 
	 * @param toAdd
	 */
	public void add(int toAdd) {
		for (int pos : getIndices(getBytes(toAdd), nHashes)) {
			bloom[pos / 8] |= (1 << (pos % 8));
		}
		nPeers++;
	}

	/**
	 * logical or of the bloom filter, results are save in the current bloom
	 * filter
	 * 
	 * @param bbf
	 *            the bloom filter to apply the operation with
	 */
	public void or(BloomFilter bbf) {
		for (int i = 0; i < bloom.length; i++) {
			bloom[i] |= bbf.bloom[i];
		}
	}

	/**
	 * logical and of the bloom filter, results are save in the current bloom
	 * filter
	 * 
	 * @param bbf
	 *            the bloom filter to apply the operation with
	 */
	public void and(BloomFilter bbf) {
		for (int i = 0; i < bloom.length; i++) {
			bloom[i] &= bbf.bloom[i];
		}
	}

	/**
	 * logical xor of the bloom filter, results are save in the current bloom
	 * filter
	 * 
	 * @param bbf
	 *            the bloom filter to apply the operation with
	 */
	public void xor(BloomFilter bbf) {
		for (int i = 0; i < bloom.length; i++) {
			bloom[i] ^= bbf.bloom[i];
		}
	}

	/**
	 * tests whether the bloom filter is empty (not elements inserted)
	 * 
	 * @return true, if no peer was added, false otherwise
	 */
	public boolean empty() {
		return isZero();
	}

	/**
	 * tests whether at least one bit is set in the filter
	 * 
	 * @return boolean indicating whether bloom filter bits are all zero or not
	 */
	public boolean isZero() {
		for (int i = 0; i < bloom.length; i++) {
			if (bloom[i] != 0) {
				return false;
			}
		}
		return true;
	}

	/**
	 * 
	 * @param elem
	 *            the element to search for
	 * @return true if the element was inserted in the bloom filter
	 */
	public boolean contains(int elem) {
		for (int pos : getIndices(getBytes(elem), nHashes)) {
			pos = Math.abs(pos % (bloom.length * 8));
			if ((bloom[pos / Byte.SIZE] & (1 << (pos % Byte.SIZE))) == 0) {
				// bit is not set
				return false;
			}
		}
		return true;
	}

	/**
	 * Converts and int to a byte array
	 * 
	 * @param toConvert
	 * @return byte[] representation of an integer
	 */
	private byte[] getBytes(int toConvert) {

		byte[] ret = new byte[Integer.SIZE / Byte.SIZE];
		int ctr = -1;
		while (++ctr < ret.length) {
			ret[ctr] = (byte) (toConvert % (1 << Byte.SIZE));
			toConvert = toConvert >> Byte.SIZE;
		}
		return ret;
	}

	/**
	 * Here, the actual hashing is done. The hash value is mapped to the size of
	 * the filter
	 * 
	 * @param data
	 *            data to hash
	 * @param hashes
	 *            number of hash values
	 * @return
	 */
	private int[] getIndices(byte[] data, int hashes) {

		int[] result = new int[hashes];

		byte salt = 0;
		for (int j = 0; j < hashes; j++) {
			byte[] digest = hash(data, salt);
			int tmpHash = digest[0];
			for (int i = 1; i < Integer.SIZE / Byte.SIZE && i < digest.length; i++) {
				tmpHash <<= Byte.SIZE;
				tmpHash |= digest[i];
			}
			/* clear first bit */
			tmpHash &= 0x7FFFFFFF;
			/* align to size of bloom filter */
			tmpHash %= bloom.length * Byte.SIZE;
			result[j] = tmpHash;
		}
		return result;

	}

	private byte[] hash(byte[] data, byte salt) {
		SHA1 sha1 = new SHA1();
		sha1.update(salt);
		sha1.update(data);
		byte[] res = new byte[20];
		sha1.digest(res);
		return res;
	}

	@Override
	public BloomFilter clone() {
		BloomFilter bbf = null;

		bbf = new BloomFilter(bloom.length, nHashes);
		bbf.or(this);

		return bbf;
	}

	@Override
	public boolean equals(Object other) {
		if (!(other instanceof BloomFilter))
			return false;

		BloomFilter tmp = clone();
		tmp.xor((BloomFilter) other);
		return tmp.isZero();
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < bloom.length; i++) {
			sb.append(bloom[i] + ";");
		}
		return sb.toString();
	}

}
