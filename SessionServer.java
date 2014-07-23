/*
 * SessionServer.java
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
package at.itec.mf;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

import android.util.Log;

/**
 * 
 * @author stefan petscharnig
 * 
 *         when there is no dedicated content servers and the clients play local
 *         files and want to be synced use this class as a server
 *
 */

public class SessionServer {
	public static final int PORT = 54321;
	private int port;

	public SessionServer() {
		this(PORT);
	}

	public SessionServer(int port) {
		this.port = port;
	}

	ServerThread st;

	public void start() {
		st = new ServerThread();
		st.start();
	}

	public void end() {
		st.interrupt();
	}

	List<String> peers; // id,ip,port
	static int maxId = 1;

	private class ServerThread extends Thread {
		Thread t;
		/** UDP socket for receiving messages */
		ServerSocket serverSocket;

		@Override
		public void interrupt() {
			super.interrupt();
			if (serverSocket != null) {
				try {
					serverSocket.close();
				} catch (IOException e) {
					// ignore
				}
			}
		}

		public void start() {
			peers = new ArrayList<String>();
			t = new Thread(this);
			t.start();
		}

		private class GrumlRunnable implements Runnable {
			Socket s;

			public GrumlRunnable(Socket s) {
				this.s = s;
			}

			@Override
			public void run() {
				String tmp;
				StringBuffer sb = new StringBuffer();

				try {
					BufferedReader br = new BufferedReader(
							new InputStreamReader(s.getInputStream()));
					while ((tmp = br.readLine()) != null) {
						sb.append(tmp);
						Log.d("session server","read: " + tmp);
					}

					peers.add(maxId++ + ","
							+ s.getInetAddress().getHostAddress() + ","
							+ sb.toString());

					sb = new StringBuffer();
					sb.append("[");
					for (String str : peers) {
						sb.append("{");
						sb.append("id:");
						sb.append(str.split(",")[0]);
						sb.append(", ip:");
						sb.append(str.split(",")[1]);
						sb.append(", port:");
						sb.append(str.split(",")[2]);

						sb.append("}");
					}
					sb.append("]");
					// for compability we send the same string as the dash
					// plugin would, to the same port it does..
					Socket retS = new Socket(s.getInetAddress(), 12345);
					BufferedWriter bw = new BufferedWriter(
							new OutputStreamWriter(retS.getOutputStream()));
					bw.write(sb.toString());
					bw.flush();
					Log.d("session server", "want to send: " + sb.toString());
					s.close();
					retS.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		/** worker method */
		@Override
		public void run() {

			try {

				serverSocket = new ServerSocket(port);
				serverSocket.setSoTimeout(0);
				Log.d("session server", "started.. waiting for messages...");
				while (!isInterrupted()) {
					Socket s = serverSocket.accept();
					Log.d("session server", "got message");
					new Thread(new GrumlRunnable(s)).start();
				}
				serverSocket.close();
			} catch (SocketException e1) {

			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

}
