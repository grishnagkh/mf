package mf.bloomfilter;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/*
 * Bloom filter implementation using byte arrays, 
 * name is temporary, to have a difference to the northern one^^ 
 */
public class BloomFilter {

	private byte[] bloom;
	private int nHashes;

	static final MessageDigest md;
	static { // The digest method is reused between instances
		MessageDigest tmp;
		try {
			tmp = java.security.MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {
			tmp = null;
		}
		md = tmp;
	}

	public BloomFilter() throws NoSuchAlgorithmException {
		this(1024, 2);
	}

	public BloomFilter(int sizeBytes, int nHashes)
			throws NoSuchAlgorithmException {
		bloom = new byte[sizeBytes];
		this.nHashes = nHashes;
	}

	public byte[] getBytes(int i) {
		byte[] ret = new byte[Integer.SIZE / Byte.SIZE];
		int ctr = -1;
		while (++ctr < ret.length) {
			ret[ctr] = (byte) (i % (1 << Byte.SIZE));
			i = i >> Byte.SIZE;
		}
		return ret;
	}

	public void add(int toAdd) {
		for (int pos : getIndices(getBytes(toAdd), nHashes)) {
			pos = Math.abs(pos % (bloom.length * 8));
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
	 * converts toString representation into a bytefilter with given number of
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

	private int[] getIndices(byte[] data, int hashes) {

		int[] result = new int[hashes];

		int k = 0;
		byte salt = 23;
		while (k < hashes) {
			byte[] digest;
			md.update(salt);
			salt += 1;
			digest = md.digest(data);

			for (int i = 0; i < digest.length / 4 && k < hashes; i++) {
				int h = 0;
				for (int j = (i * 4); j < (i * 4) + 4; j++) {
					h <<= 8;
					h |= ((int) digest[j]) & 0xFF;
				}
				result[k] = h;
				k++;
			}
		}
		return result;
	}
}
