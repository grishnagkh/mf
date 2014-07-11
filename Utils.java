package at.itec.mf;

import java.util.BitSet;
import at.itec.mf.bloomfilter.BloomFilter;

/**
 * 
 * Utility class providing methods for timing, message building and bloom filter
 * comparison
 * 
 * @author stefan petscahrnig
 *
 */
public class Utils {

	// TODO: implement NTP query ?
	public static long getTimestamp() {
		return System.currentTimeMillis();
	}

	/**
	 * 
	 * builds a coarse sync message
	 * 
	 * @param delim
	 * @param type
	 * @param myIP
	 * @param myPort
	 * @param pts
	 * @param nts
	 * @param myId
	 * @return
	 */
	public static String buildMessage(String delim, int type, String myIP,
			int myPort, long pts, long nts, int myId) {
		return type + delim + myIP + delim + myPort + delim + pts + delim + nts
				+ delim + myId;
	}

	/**
	 * builds a fine sync message
	 * 
	 * @param delim
	 * @param type
	 * @param avgTS
	 * @param nts
	 * @param myId
	 * @param bloomFilterRep
	 * @param maxId
	 * @return
	 */
	public static String buildMessage(String delim, int type, long avgTS,
			long nts, int myId, String bloomFilterRep, int maxId) {
		return type + delim + avgTS + delim + nts + delim + myId + delim
				+ bloomFilterRep + delim + maxId;
	}

	/**
	 * 
	 * @param b
	 *            the bitset to convert
	 * @return a string representation of a bitset
	 */
	public static String toString(BitSet b) {
		StringBuffer ret = new StringBuffer();
		for (int i = 0; i < b.size(); i += 8) {
			ret.append(b.get(i) ? 1 : 0);
		}
		return ret.toString();
	}

	/**
	 * 
	 * @param str
	 *            a bit string containing 1's and 0's
	 * @return a bitset corresponding to the input string
	 */

	public static BitSet fromString(String str) {
		BitSet ret = new BitSet(str.length());
		for (int i = 0; i < str.length(); i++) {
			ret.set(i, str.charAt(i) == '1' ? true : false);
		}
		return ret;
	}

	/**
	 * @param bf1
	 * @param bf2
	 * @return true if and result contains at least 1 true value (assuming bloom
	 *         filters have equal length)
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
	 * 
	 * @param rcvBF
	 * @param bloom
	 * @return true if xor result contains at least 1 true value
	 */
	public static boolean xor(BloomFilter<?> bf1, BloomFilter<?> bf2) {
		BitSet b1 = bf1.getBitSet();
		BitSet b2 = bf2.getBitSet();
		for (int i = 0; i < b1.length(); i++)
			if (b1.get(i) ^ b2.get(i))
				return true;
		return false;
	}

	/**
	 * 
	 * @param bloom
	 * @param maxId
	 * @return the (maximum) number of peers which can be in a given bloom
	 *         filter, up to a given maximum id
	 */
	public static int getN(BloomFilter<Integer> bloom, int maxId) {
		int ctr = 0;
		for (int i = 1; i < maxId; i++) {
			if (bloom.contains(i))
				ctr++;
		}
		return ctr;
	}
}
