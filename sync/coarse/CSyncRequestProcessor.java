package mf.sync.coarse;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import mf.sync.net.MessageHandler;
import mf.sync.utils.Peer;
import mf.sync.utils.SessionInfo;
import mf.sync.utils.SyncI;
import mf.sync.utils.Utils;
import android.util.Log;

/**
 * class processing a coarse sync request
 * 
 * @author stefan petscharnig
 */
class CSyncRequestProcessor implements Runnable {
	String req;
	String TAG = "csprr";

	public CSyncRequestProcessor(String req) {
		this.req = req;
	}

	public void run() {
		// parse request
		Log.d(TAG, "got the following request: " + req);
		String[] responseFields = req.split("\\" + SyncI.DELIM);
		if (responseFields.length != 6) { // simplest check available...
			Log.d(TAG, "invalid request [length]");
			return; // invalid message
		}
		String senderIP = responseFields[1];
		InetAddress peerAddress = null;
		try {
			peerAddress = InetAddress.getByName(senderIP);
		} catch (UnknownHostException e) {
			Log.d(TAG, "invalid request [IP]");
			return; // invalid IP, don't care;
		}
		int senderPort = Integer.parseInt(responseFields[2]);

		long myPts = 0;
		myPts = Utils.getPlaybackTime();

		long myNts = Utils.getTimestamp();

		String myIP = SessionInfo.getInstance().getMySelf().getAddress()
				.getHostAddress();
		int myPort = SessionInfo.getInstance().getMySelf().getPort();
		int myId = SessionInfo.getInstance().getMySelf().getId();
		String msg = Utils.buildMessage(SyncI.DELIM, SyncI.TYPE_COARSE_RESP,
				myIP, myPort, myPts, myNts, myId);

		// send response
		try {
			MessageHandler.getInstance().sendMsg(msg, peerAddress, senderPort);
		} catch (SocketException e) {
			Log.e(TAG, "could not send message");
		} catch (IOException e) {
			Log.e(TAG, "could not send message");
		}
		int peerId = Integer.parseInt(responseFields[5]);
		if (!SessionInfo.getInstance().getPeers().containsKey(peerId)) {
			Peer p = new Peer(peerId, peerAddress, senderPort);
			SessionInfo.getInstance().getPeers().put(peerId, p);
		}

	}
}
