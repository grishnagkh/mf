package at.itec.mf;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

/**
 * 
 * coarse synchronization: we want make an educated guess (estimate) where the
 * other session users are so we start at this position with the playback so
 * that we only need little adaptions for the fine sync
 * 
 * as we set performance over a perfect guess, it is implemented via udp
 * 
 * @author stefan petscharnig
 *
 */

public class CoarseSync {

	public static final int WAIT_TIME_MS = 1000;

	private static CoarseSync instance;

	public static CoarseSync getInstance() {
		if (instance == null)
			instance = new CoarseSync();
		return instance;
	}

	private CoarseSync() {

	}

	public void start() {
		new Thread(new CSyncRunnable()).start();
	}

	private class CSyncRunnable implements Runnable {
		public void run() {
			// TODO
			// 1 send a request to all known peers
			// 2 wait some time tc
			// 3 process the response list
			// 4 while forever process requests; maybe an event whenever the
			// request list is filled? or when not possible we have to check
			// periodically :/ there, i would suggest half the wait time
		}
	}
	
}
