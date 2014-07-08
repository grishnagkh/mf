package at.itec.mf;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import android.util.Log;

/**
 * 
 * Socket Server listening to a specific port for session info from the vlc dash
 * plugin. A singleton.
 * 
 * @author stefan petscharnig
 *
 */

public class SessionInfoListener {

	private static SessionInfoListener instance = null;

	private static final String TAG = "session mf";
	// coded in vlc dash plugin that port 12345 is used
	private static final int PORT = 12345;

	private int port;
	private SessionRunnable r;
	private Thread t;

	private boolean gotResult = false;

	public static SessionInfoListener getInstance(int port) {
		instance = instance == null ? new SessionInfoListener(port) : instance;
		return instance;
	}

	public static SessionInfoListener getInstance() {
		instance = instance == null ? new SessionInfoListener() : instance;
		return instance;
	}

	private SessionInfoListener() {
		this(PORT);
	}

	private SessionInfoListener(int port) {
		this.port = port;
	}

	public void startListener() {
		r = new SessionRunnable();
		t = new Thread(r);
		t.start();
	}

	public String getInfoString() {
		return r.getSessionInfo();
	}

	public boolean gotResult() {
		return gotResult;
	}

	/**
	 * 
	 * @return a list of peers in the session
	 * 
	 *         DANGER: in case of unknown host exception, malformed string or no
	 *         results from dash plugin, nothing will we done
	 * 
	 */
	public Map<Integer, Peer> getPeers() {
		if (peers == null) {
			peers = new HashMap<Integer, Peer>();
			try {
				convertPeers(r.getSessionInfo());
			} catch (UnknownHostException e) {
				Log.e(TAG, "unknown host");
			}

		}
		return peers;
	}

	private void convertPeers(String s) throws UnknownHostException {
		// split the peers

		if (s.length() < 4 || !gotResult)
			return;

		s = s.substring(1, s.length() - 1);

		System.out.println(s);
		for (String str : s.split("}")) {
			str = str.substring(1);

			String[] attrs = str.split(",");

			String idS = attrs[0];
			String ipS = attrs[1];
			String portS = attrs[2];

			int id = Integer.parseInt(idS.substring(idS.indexOf(':') + 1,
					idS.length()));
			int port = Integer.parseInt(portS.substring(portS.indexOf(':') + 1,
					portS.length()));
			ipS = ipS.substring(ipS.indexOf(':') + 1, ipS.length());
			System.out.println("<" + id + "," + ipS + "," + port + ">");
			Peer p = new Peer(id, InetAddress.getByName(ipS), port);
			peers.put(id, p);
		}
	}

	Map<Integer, Peer> peers;

	private class SessionRunnable implements Runnable {
		StringBuffer sessionInfo;

		public String getSessionInfo() {
			return sessionInfo.toString();
		}

		public void run() {
			// Looper.prepare();
			sessionInfo = new StringBuffer();
			try {

				ServerSocket sock = new ServerSocket(port);
				Socket client = sock.accept();

				BufferedReader in = new BufferedReader(new InputStreamReader(
						client.getInputStream()));

				String tmp;
				while (client.isConnected() && (tmp = in.readLine()) != null) {
					sessionInfo.append(tmp);
				}
				sock.close();
				gotResult = true;
			} catch (Exception e) {
				// TODO Auto-generated catch block
			}
		}
	}
}
/*
 * sample call (at startup start the session listener, or better, when starting
 * to open the dash plugin):
 * at.itec.mf.SessionInfoListener.getInstance().startListener();
 * 
 * sample test: (new Thread(new Runnable() {
 * 
 * @Override public void run() {
 * while(!at.itec.mf.SessionInfoListener.getInstance().gotResult()){ try {
 * Thread.sleep(1000); } catch (InterruptedException e) { // TODO Auto-generated
 * catch block e.printStackTrace(); } } Log.d("vlc mf",
 * at.itec.mf.SessionInfoListener.getInstance().getInfo());
 * 
 * } })).start();
 */