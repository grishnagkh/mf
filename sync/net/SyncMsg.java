package mf.sync.net;

import java.io.Serializable;
import java.net.InetAddress;

public abstract class SyncMsg implements Serializable {

	private static final long serialVersionUID = 7179713119327407162L;
	
	public int peerId;
	public int msgId;
	public int port;
	public InetAddress address;

}
