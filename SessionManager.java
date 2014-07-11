package at.itec.mf;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.util.Log;

/**
 * 
 * Socket Server listening to a specific port for session info from the vlc dash
 * plugin. A singleton.
 * 
 * Start this whenever a mpd file is requested After successfully parsing the
 * session info, this plugin starts the messagehandler and the coarse sync (fine
 * sync should be started by coarse sync)
 * 
 * @author stefan petscharnig
 *
 */

public class SessionManager {

	/** tag for android log */
	private static final String TAG = "session mf";
	/** singleton instance */
	private static SessionManager instance = null;

	/*
	 * Attention, do not change: following port is hard coded into vlc dash
	 * plugin!
	 */
	/** default port used for receiving session data */
	private static final int PORT = 12345;
	/** actual port used for receiving session data */
	private int port;

	/** used to store data about self */
	private Peer mySelf;
	/** Application Context, used for getting wifi address */
	private Context c;
	/** Map of known peers */
	private Map<Integer, Peer> peers;
	/** string representation of the session info */
	private String sInfo;

	/** Singleton constructor */
	private SessionManager() {
		this(PORT);
	}

	/** Singleton constructor */
	private SessionManager(int port) {
		this.port = port;
	}

	/**
	 * singleton method, do not use (default port is hard coded into vlc, use
	 * getInstance() instead)
	 */
	public static SessionManager getInstance(int port) {
		instance = instance == null ? new SessionManager(port) : instance;
		return instance;
	}

	/**
	 * Singleton method
	 * 
	 * @return
	 */
	public static SessionManager getInstance() {
		return getInstance(PORT);
	}

	/**
	 * init method which sets the application context used for getting wifi
	 * address
	 */
	public void init(Activity a) {
		c = a.getApplicationContext();
	}

	/**
	 * start listening for session info
	 */
	public void startListener() {
		new Thread(new SessionRunnable()).start();
	}

	/**
	 * 
	 * @return a list of peers in the session
	 * 
	 *         DANGER: in case of unknown host exception, malformed string or no
	 *         results from dash plugin, nothing will be done or we could even
	 *         crash, i dont know, maybe we would kill a kitten... how sad :/
	 */
	@SuppressLint("UseSparseArrays")
	public Map<Integer, Peer> getPeers() {
		if (peers == null) {
			peers = new HashMap<Integer, Peer>();
			try {
				convertPeers(sInfo);
			} catch (UnknownHostException e) {
				Log.e(TAG, "unknown host");
			}

		}
		return peers;
	}

	/**
	 * converts the info string to a list of peers
	 * 
	 * @param s
	 *            string representing session info
	 * @throws UnknownHostException
	 */
	private void convertPeers(String s) throws UnknownHostException {
		if (s.length() < 4)
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

			Peer p = new Peer(id, InetAddress.getByName(ipS), port);

			if (p.getAddress().equals(Utils.getWifiAddress(c))
					&& p.getPort() == port) {
				mySelf = p;
			} else {
				peers.put(id, p);
			}
		}
	}

	/**
	 * 
	 * @return information about this peer
	 */
	public Peer getMySelf() {
		return mySelf;
	}

	/**
	 * actual class doing the receiving of the session data on the non-vlc side
	 * 
	 * @author stefan petscharnig
	 *
	 */
	private class SessionRunnable implements Runnable {
		StringBuffer sessionInfo;

		/**
		 * worker method: just store the received string and start the coarse
		 * synchronization
		 */
		public void run() {
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
				sInfo = sessionInfo.toString();
				// we got a result, start message handler and coarse sync
				SyncMessageHandler.getInstance().startHandling();
				CSync.getInstance().startSync();

			} catch (Exception e) {
				// TODO exception handling
			}
		}
	}
}
