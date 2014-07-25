/*
 * SessionManager.java
 *
 * Copyright (c) 2014, Stefan Petscharnig. All rights reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301 USA
 */
package mf.player.at.itec;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import mf.player.at.itec.gui.MainActivity;
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
	private static final String TAG = "SessionManager";
	/** singleton instance */
	private static SessionManager instance;

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

	private boolean waiting;
	
	private String sessionId;
	private long validThru;

	/** Singleton constructor */
	private SessionManager() {
		this(PORT);
	}

	/** Singleton constructor */
	private SessionManager(int port) {
		this.port = port;
		waiting = false;
	}

	/**
	 * singleton method, do not use (default port is hard coded into vlc, use
	 * getInstance() instead)
	 */
	public static SessionManager getInstance(int port) {
		if (instance == null) {
			instance = new SessionManager(port);
		}
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
		if (a == null) {
			Log.d(TAG, "wtf is wrong with you?");
		}
		c = a;
	}

	/**
	 * start listening for session info
	 */
	public void startListener() {
		if (waiting) {
			Log.d(TAG,
					"we already have a listener started, so wait my young padawan...");
			return;
		}
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
		}
		convertPeers(sInfo);
		return peers;
	}

	public void setPeers(Map<Integer, Peer> peers) {
		this.peers = peers;
	}

	/**
	 * converts the info string to a list of peers
	 * 
	 * @param s
	 *            string representing session info
	 * @throws UnknownHostException
	 */
	private int convertPeers(String s) {
		if (s.length() < 4)
			return -1;

		s = s.substring(1, s.length() - 1);

		Log.d(TAG, "String received" + s);
		InetAddress ownAddress = Utils.getWifiAddress(MainActivity.c);
		if (ownAddress == null) {
			return -1;
		}

		for (String str : s.split("\\}")) {
			if ("".equals(str.trim()))
				break;

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

			Peer p;
			try {
				p = new Peer(id, InetAddress.getByName(ipS), port);
			} catch (UnknownHostException e) {
				return -1;
			}

			// /* hack for testing how to play video files.. */
			// mySelf = p; // TODO: remove hack //just uncomment for now.. maybe
			// we need it again^^ hacker paranoia

			if (p.getAddress().equals(ownAddress)) {// && p.getPort() == port) {
				mySelf = p;
			} else {
				peers.put(id, p);
			}
		}
		return 0;
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
		public synchronized void run() {
			waiting = true;
			Log.d(TAG, "start handling...");
			/* when we start this a second time, we want the peers to reset... */
			peers = null;

			sessionInfo = new StringBuffer();
			try {
				Log.d(TAG, "open server socket");
				ServerSocket sock = new ServerSocket(port);
				Socket client = sock.accept();

				BufferedReader in = new BufferedReader(new InputStreamReader(
						client.getInputStream()));

				String tmp;
				while (client.isConnected() && (tmp = in.readLine()) != null) {
					sessionInfo.append(tmp);
				}
				Log.d(TAG, "close server socket");
				sock.close();
				waiting = false;
				sInfo = sessionInfo.toString();
				Log.d(TAG, "got session info: " + sInfo);
				// we got a result, start message handler and coarse sync
				/*
				 * should be run when playing a video, see VideoPlayerActivity
				 * onResume, onPause
				 */
				// SyncMessageHandler.getInstance().startHandling();

			} catch (Exception e) {
				Log.d(TAG, "session info read failure, sInfo: <" + sInfo + ">");
			}

		}
	}

	public void setMySelf(Peer mySelf) {
		this.mySelf = mySelf;
	}

	public void setSessionId(String sessionId) {
		this.sessionId = sessionId;
	}

	public void setValidThru(String validThru) {
		this.validThru = Long.parseLong(validThru);
	}
}
