package at.itec.mf;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

import android.util.Log;

/**
 * 
 * a message handler which forwards the received udp packages either to the fine
 * or coarse sync
 * 
 * @author stefan petscharnig
 *
 */

public class UDPSyncMessageHandler {

	public static final String TAG = "message handler";

	public static final int TYPE_COARSE_REQ = 1;
	public static final int TYPE_COARSE_RESP = 2;
	public static final int TYPE_FINE = 3;

	public static final int RCF_BUF_LEN = 4096; // we start with a 4k buffer

	public static final int PORT = 12346;
	private int port;

	private static UDPSyncMessageHandler instance;

	public static UDPSyncMessageHandler getInstance() {
		return getInstance(PORT);
	}

	public static UDPSyncMessageHandler getInstance(int port) {
		if (instance == null)
			instance = new UDPSyncMessageHandler(port);
		return instance;
	}

	private UDPSyncMessageHandler() {
		this(PORT);
	}

	private UDPSyncMessageHandler(int port) {
		this.port = port;
	}

	public synchronized void sendUDPMessage(String msg, InetAddress address,
			int port) throws SocketException, IOException {
		DatagramSocket clientSocket = new DatagramSocket();
		DatagramPacket sendPacket = new DatagramPacket(msg.getBytes(),
				msg.getBytes().length, address, port);
		clientSocket.send(sendPacket);
		clientSocket.close();
	}

	public boolean startHandling() {
		if (!SessionManager.getInstance().gotResult()) {
			// we have not received data about peers... TODO
			return false;
		}
		new Thread(new ServerRunnable()).start();
		return true;
	}

	private class ServerRunnable implements Runnable {

		DatagramSocket serverSocket;
		byte[] rcvBuf = new byte[RCF_BUF_LEN];

		public void run() {
			try {
				serverSocket = new DatagramSocket(port);
			} catch (SocketException e) {
				e.printStackTrace();
			}
			while (true) {

				// listen for messages
				DatagramPacket rcv = new DatagramPacket(rcvBuf, RCF_BUF_LEN);
				try {
					serverSocket.receive(rcv);
					String msg = new String(rcv.getData());

					if (msg.startsWith("" + TYPE_COARSE_REQ)) {
						CoarseSync.getInstance().processRequest(msg);
					} else if (msg.startsWith("" + TYPE_COARSE_RESP)) {
						CoarseSync.getInstance().coarseResponse(msg);

					} else if (msg.startsWith("" + TYPE_FINE)) {
						FineSync.getInstance().processRequest(msg);
					} else {
						// other requests, really?
						Log.d(TAG, "We received some other request: " + msg);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}

			}
		}

	}

}
