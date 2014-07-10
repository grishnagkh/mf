package at.itec.mf;

import java.util.BitSet;

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

	public String toString(BitSet b) {
		StringBuffer ret = new StringBuffer();
		for (int i = 0; i < b.size(); i += 8) {
			ret.append(b.get(i) ? 1 : 0);
		}
		return ret.toString();
	}

	public BitSet fromString(String str) {
		BitSet ret = new BitSet(str.length());
		for (int i = 0; i < str.length(); i++) {
			ret.set(i, str.charAt(i) == 1 ? true : false);
		}
		return ret;
	}
}
