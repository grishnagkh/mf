package at.itec.mf;

import java.net.InetAddress;

/**
 * 
 * model for a peer
 * 
 * @author stefan petscharnig
 *
 */
public class Peer {
	/** the peer id */
	private int id;
	/** the peer port where we are listening to sync messages */
	private int port;
	/** the peer address */
	private InetAddress address;

	/**
	 * constructor
	 * 
	 * @param id
	 * @param address
	 * @param port
	 */
	public Peer(int id, InetAddress address, int port) {
		this.id = id;
		this.address = address;
		this.port = port;
	}

	/**
	 * getter
	 * 
	 * @return
	 */
	public int getId() {
		return id;
	}

	/**
	 * setter
	 * 
	 * @param id
	 */
	public void setId(int id) {
		this.id = id;
	}

	/**
	 * getter
	 * 
	 * @return
	 */
	public int getPort() {
		return port;
	}

	/**
	 * setter
	 * 
	 * @param id
	 */
	public void setPort(int port) {
		this.port = port;
	}

	/**
	 * getter
	 * 
	 * @return
	 */
	public InetAddress getAddress() {
		return address;
	}

	/**
	 * setter
	 * 
	 * @param id
	 */
	public void setAddress(InetAddress address) {
		this.address = address;
	}

	public String toString() {
		return id + "," + address + "," + port;
	}
}