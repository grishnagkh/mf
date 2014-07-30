package mf.sync.utils.log;

public class SyncLogger {
	String[] log;
	int actual;
	int logSize;

	public SyncLogger(int logSize) {
		this.logSize = logSize;
		log = new String[logSize];
	}

	public void append(String elem) {
		log[actual++ % logSize] = elem;
	}

	public void clear() {
		actual = 0;
		for (int i = actual % logSize; i < actual % logSize + logSize; i++) {
			log[i % logSize] = null;
		}
	}

	public String toString() {
		if (actual == 0)
			return "";

		StringBuffer sb = new StringBuffer();
		for (int i = actual + logSize - 1; i > actual - 1; i--) {
			if (log[i % logSize] != null)
				sb.append(log[i % logSize] + "\n");
		}
		return sb.toString();
	}
}
