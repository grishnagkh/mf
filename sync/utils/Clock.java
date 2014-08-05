package mf.sync.utils;

import java.net.InetAddress;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

public class Clock {

	public static String[] NTP_HOSTS = new String[] {
			"time1srv.sci.uni-klu.ac.at", "0.at.pool.ntp.org", "0.pool.ntp.org" };

	private static long nts = 0, updateTime = 0;

	private static synchronized void updateClock() {

		for (int i = 0; i < NTP_HOSTS.length; i++) {
			try {
				NTPUDPClient client = new NTPUDPClient();
				client.setDefaultTimeout(100);
				InetAddress hostAddr = InetAddress.getByName(NTP_HOSTS[i]);

				TimeInfo info = client.getTime(hostAddr);

				updateTime = System.currentTimeMillis();
				// SessionInfo.getInstance().log(
				// "update ntp time from " + NTP_HOSTS[i]);
				nts = info.getReturnTime();

				client.close();

				break;
			} catch (Exception e) {
				// does not matter
			}
		}
	}

	public static long getTime() {

		if (System.currentTimeMillis() - updateTime > 1000) {
			new Thread(new Runnable() {

				@Override
				public void run() {
					updateClock();
				}
			}).start();

		}
		return System.currentTimeMillis() - updateTime + nts;
	}
}
