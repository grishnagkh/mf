package mf.sync.utils;

import java.net.InetAddress;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

/**
 * Class which acts as a time reference, periodically syncs its time with and
 * ntp server
 *
 * @author stefan
 *
 */
public class Clock {

	/**
	 *
	 * @return a (if possible) ntp-synced time stamp
	 */
	public static long getTime() {
		/* check whether a update should be done */
		if (System.currentTimeMillis() - updateTime > CLOCK_SYNC_INTERVAL_MS) {
			/* perform the update in a new thread */
			new Thread(new Runnable() {
				@Override
				public void run() {
					updateClock();
				}
			}).start();

		}
		return System.currentTimeMillis() - updateTime + nts;
	}
	/**
	 * method updating the clock, does this asynchronically to ensure speed
	 */
	private static synchronized void updateClock() {

		for (int i = 0; i < NTP_HOSTS.length; i++) {
			try {
				NTPUDPClient client = new NTPUDPClient();
				client.setDefaultTimeout(200);
				InetAddress hostAddr = InetAddress.getByName(NTP_HOSTS[i]);

				TimeInfo info = client.getTime(hostAddr);

				updateTime = info.getReturnTime();
				info.computeDetails();
				nts = updateTime + info.getOffset();

				if (DEBUG) {
					SessionInfo.getInstance().log(
							"offset time from " + NTP_HOSTS[i] + " : "
									+ info.getOffset());
				}
				client.close();
				break;
			} catch (Exception e) {
				/*
				 * ntp servers not reachable... does not matter, take the old
				 * timestamp or the system clock per defaults
				 */
				if (DEBUG) {
					SessionInfo.getInstance().log(
							"could not connect to ntp server: " + NTP_HOSTS[i]);
				}
			}
		}
	}
	/** minimum interval in which there is NO synchronization */
	public static final int CLOCK_SYNC_INTERVAL_MS = 600;
	/** produce debug output to the session info logger ? */
	public static final boolean DEBUG = false;
	/** list of ntp hosts */
	public static String[] NTP_HOSTS = new String[] {
			"time1srv.sci.uni-klu.ac.at", "0.at.pool.ntp.org", "0.pool.ntp.org" };

	/** last ntp synced stimestamp */
	private static long nts = 0;

	/** (local) timstamp of last ntp update */
	private static long updateTime = 0;
}
