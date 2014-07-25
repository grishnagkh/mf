package mf.sync.coarse;

import java.io.IOException;
import java.net.SocketException;
import java.util.List;

import mf.sync.fine.FSync;
import mf.sync.net.MessageHandler;
import mf.sync.utils.Peer;
import mf.sync.utils.SessionInfo;
import mf.sync.utils.SyncI;
import mf.sync.utils.Utils;
import android.util.Log;

/**
 * 
 * class handling the initial step of the coarse sync 1 send a request to all
 * known peers 2 wait some time 3 parse and process responses
 * 
 * @author stefan petscharnig
 *
 */
public class CSyncServer implements Runnable {
	String TAG = "csr";
	int SEGSIZE = 2000;
	private List<String> msgQueue;

	public CSyncServer(List<String> messageQueue) {
		msgQueue = messageQueue;
	}

	public void run() {
		Log.d(TAG, "start coarse sync request");
		/* 1 */
		for (Peer p : SessionInfo.getInstance().getPeers().values()) {

			String myIP = SessionInfo.getInstance().getMySelf().getAddress()
					.getHostAddress();
			int myPort = SessionInfo.getInstance().getMySelf().getPort();
			int myId = SessionInfo.getInstance().getMySelf().getId();

			String msg = Utils.buildMessage(SyncI.DELIM, SyncI.TYPE_COARSE_REQ,
					myIP, myPort, 0, Utils.getTimestamp(), myId);

			try {
				MessageHandler.getInstance().sendMsg(msg, p.getAddress(),
						p.getPort());
			} catch (SocketException e) {
				Log.e(TAG, "could not send message");
			} catch (IOException e) {
				Log.e(TAG, "could not send message");
			}
		}
		Log.d(TAG, "phase 1 completed, waiting...");
		/* 2 */
		try {
			Thread.sleep(SyncI.WAIT_TIME_CS_MS);
		} catch (InterruptedException e) {
			Log.e(TAG, "interrupted while sleep... ");
		}
		Log.d(TAG, "phase 2 completed, calculating");
		long avgPTS = 0;

		/* 3 */

		if (msgQueue.size() == 0) {
			Log.d(TAG, "no messages in queue");
			FSync.getInstance().startSync();
			return;
		}

		for (String response : msgQueue) {
			String[] responseFields = response.split("\\" + SyncI.DELIM);
			long pts = Long.parseLong(responseFields[3]);
			long nts = Long.parseLong(responseFields[4]);
			avgPTS += pts + (Utils.getTimestamp() - nts);
			Log.d(TAG,
					"trip time (peer " + responseFields[4] + "): "
							+ (Utils.getTimestamp() - nts));
		}
		avgPTS /= msgQueue.size();

		// empty request queue
		msgQueue.clear();

		Log.d(TAG, "calculated average from coarse synchronization: " + avgPTS);
		// scale time to the 2s segments, just to make some sense for fine
		// sync^^,

		avgPTS = SEGSIZE + avgPTS - avgPTS % SEGSIZE;
		// Log.d(TAG_CS, "try to set time to " + avgPTS);

		Utils.setPlaybackTime((int) avgPTS);

		// Log.d(TAG_CS, "have set time to " + avgPTS);

		FSync.getInstance().startSync();
	}

}
