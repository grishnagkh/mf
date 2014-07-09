package at.itec.mf;

public class Utils {
	// TODO: implement NTP query ? 
	public static long getTimestamp() {
		return System.currentTimeMillis();
	}

	public static String buildMessage(String delim, int type, String myIP,
			int myPort, long pts, long nts, int myId) {
		return type + delim + myIP + delim + myPort + delim + pts + delim
				+ nts + delim + myId;

	}
}
