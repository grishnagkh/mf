package at.itec.mf;

public class FineSync {

	public static final String DELIM = "|";
	public static final int PERIOD_MS = 1000;
	public static final String TAG = "fine sync mf";

	private static FineSync instance;

	private boolean fineSyncNecessary = true;
	
	private FineSync() {

	}

	public static FineSync getInstance() {
		instance = instance == null ? new FineSync() : instance;
		return instance;
	}

	public void startFineSync() {
		new Thread(new FSWorker()).start();
	}

	public void processRequest() {
		new Thread(new FSResponseHandler()).start();
	}

	

	private class FSWorker implements Runnable {
		public void run() {
			while (fineSyncNecessary) {
				// TODO: implement
				
			}
		}
	}

	private class FSResponseHandler implements Runnable {
		public void run() {
			// TODO: implement
		}
	}

}
