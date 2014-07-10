package at.itec.mf;

import java.util.BitSet;

import at.itec.mf.bloomfilter.BloomFilter;

public class Utils {
	// TODO: implement NTP query ?
	public static long getTimestamp() {
		return System.currentTimeMillis();
	}

	public static String buildMessage(String delim, int type, String myIP,
			int myPort, long pts, long nts, int myId) {
		return type + delim + myIP + delim + myPort + delim + pts + delim + nts
				+ delim + myId;
	}

	public static String buildMessage(String delim, int type, long avgTS,
			long nts, int myId, String bloomFilterRep, int maxId) {
		return type + delim + avgTS + delim + nts + delim + myId + delim
				+ bloomFilterRep + delim + maxId;
	}

	public static String toString(BitSet b) {
		StringBuffer ret = new StringBuffer();
		for (int i = 0; i < b.size(); i += 8) {
			ret.append(b.get(i) ? 1 : 0);
		}
		return ret.toString();
	}

	public static BitSet fromString(String str) {
		BitSet ret = new BitSet(str.length());
		for (int i = 0; i < str.length(); i++) {
			ret.set(i, str.charAt(i) == 1 ? true : false);
		}
		return ret;
	}

	/**
	 * return true if and result contains at least 1 true value assuming bloom
	 * filters have equal length
	 * 
	 * @param rcvBF
	 * @param bloom
	 * @return
	 */
	public static boolean and(BloomFilter<?> bf1, BloomFilter<?> bf2) {
		BitSet b1 = bf1.getBitSet();
		BitSet b2 = bf2.getBitSet();
		for (int i = 0; i < b1.length(); i++)
			if (b1.get(i) && b2.get(i))
				return true;
		return false;
	}

	/**
	 * return true if xor result contains at least 1 true value
	 * 
	 * @param rcvBF
	 * @param bloom
	 * @return
	 */
	public static boolean xor(BloomFilter<?> bf1, BloomFilter<?> bf2) {
		BitSet b1 = bf1.getBitSet();
		BitSet b2 = bf2.getBitSet();
		for (int i = 0; i < b1.length(); i++)
			if (b1.get(i) ^ b2.get(i))
				return true;
		return false;
	}

	public static int getN(BloomFilter<Integer> bloom, int maxId) {
		int ctr = 0;
		for (int i = 1; i < maxId; i++) {
			if (bloom.contains(i))
				ctr++;
		}
		return ctr;
	}
}
