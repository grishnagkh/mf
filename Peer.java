package at.itec.mf;

import java.net.InetAddress;

class Peer{
		private int id;
		private int port;
		private long pts;
		private long nts;
		private InetAddress address;
		
		public Peer(int id, InetAddress address, int port){
			this.id = id;
			this.address = address;
			this.port = port;
		}
		
		public int getId() {
			return id;
		}

		public void setId(int id) {
			this.id = id;
		}
		public int getPort() {
			return port;
		}

		public void setPort(int port) {
			this.port = port;
		}

		public long getPts() {
			return pts;
		}

		public void setPts(long pts) {
			this.pts = pts;
		}

		public long getNts() {
			return nts;
		}

		public void setNts(long nts) {
			this.nts = nts;
		}

		public InetAddress getAddress() {
			return address;
		}

		public void setAddress(InetAddress address) {
			this.address = address;
		}

	}