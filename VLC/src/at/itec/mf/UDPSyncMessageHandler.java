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

	public static final int TYPE_COARSE_REQ = 1;
	public static final int TYPE_COARSE_RESP = 1;
	public static final int TYPE_FINE = 3;

	public static final int RCF_BUF_LEN = 4096; // we start with a 4k buffer

	public static final int PORT = 12346;

	/*
	 * test
	 */
	public static void main(String[] args) {
		getInstance().startHandling();
	}

	private static UDPSyncMessageHandler instance;

	public static UDPSyncMessageHandler getInstance() {
		if (instance == null)
			instance = new UDPSyncMessageHandler();
		return instance;
	}

	private UDPSyncMessageHandler() {

	}

	public static void sendUDPMessage(String msg, InetAddress address, int port) throws SocketException, IOException{
		DatagramSocket clientSocket = new DatagramSocket();
		DatagramPacket sendPacket = new DatagramPacket(msg.getBytes(),
				msg.getBytes().length, address, port);
		clientSocket.send(sendPacket);
		clientSocket.close();
	}
	
	public boolean startHandling() {
		if(!SessionInfoListener.getInstance().gotResult()){
			//we have not received data about peers... TODO
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
				serverSocket = new DatagramSocket(PORT);
			} catch (SocketException e) {
				e.printStackTrace();
			}
			while (true) {

				// listen for messages
				DatagramPacket rcv = new DatagramPacket(rcvBuf, RCF_BUF_LEN);
				try {
					serverSocket.receive(rcv);
					String tmp = new String(rcv.getData());
					
					if (tmp.startsWith("" + TYPE_COARSE_REQ)) {
						// TODO fill coarse sync request queue
//						System.out.println("coarse sync request message");
					} else if (tmp.startsWith("" + TYPE_COARSE_RESP)) {
						// TODO fill coarse sync init queue
//						System.out.println("coarse sync response message");
					} else if (tmp.startsWith("" + TYPE_FINE)) {
//						System.out.println("fine sync message");
						// TODO fill fine sync queue
					} else {
						// other requests, really?
						// TODO: just print them atm
					}
				} catch (IOException e) {
					e.printStackTrace();
				}

			}
		}

	}

}
