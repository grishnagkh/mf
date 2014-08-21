package mf.sync.fine;

import mf.sync.utils.Clock;
import mf.sync.utils.PlayerControl;
import mf.sync.utils.SessionInfo;

public class PAThread extends Thread {
	private boolean running = false;
	private FSync parent;
	public static final boolean DEBUG = true;

	public PAThread(FSync parent) {
		this.parent = parent;
	}

	@Override
	public void interrupt() {
		PlayerControl.setPlaybackRate(1);
		super.interrupt();
	}

	@Override
	public void run() {

		long pbt = PlayerControl.getPlaybackTime();
		long t = Clock.getTime();
		long asyncMillis = parent.alignedAvgTs(t) - pbt;
		if (DEBUG)
			SessionInfo.getInstance().log(
					"updating playback time: calculated average: "
							+ parent.alignedAvgTs(t) + "@timestamp:" + t
							+ "@async:" + asyncMillis + "@pbt:" + pbt);

		updatePlayback(asyncMillis);

	}

	@Override
	public void start() {
		running = true;
		super.start();
	}

	/**
	 *
	 * kickoff the update service
	 *
	 * @return true, if the thread was started, false if the thread already runs
	 *         or was run
	 */
	public boolean updatePlayback() {
		if (!running)
			start();
		return running;
	}

	/**
	 * update the playback according to the information we got from fine
	 * synchronization this approach uses faster/slower for a given time in
	 * order to omit skips
	 */
	public void updatePlayback(long asyncMillis) {
		float newPlaybackRate;

		/* the *3* come from the pre-calculation see paper */
		long timeMillis = 3 * Math.abs(asyncMillis);

		if (DEBUG)
			SessionInfo.getInstance().log("ensure buffered start");

		if (asyncMillis > 0) { // we are behind, go faster
			newPlaybackRate = 1.33f;// (float) 4 / 3; //precalculated, see
									// paper
			/*
			 * if we go faster, we want to ensure that we have buffered some
			 * data...
			 */
			PlayerControl.ensureBuffered(3 * timeMillis);
		} else { // we are on top, so do slower
			newPlaybackRate = 0.66f;// (float) 2 / 3; //precalculated, see
									// paper
			/*
			 * despite it is theoretically not necessary, ensure we have
			 * buffered at least a bit
			 */
			PlayerControl.ensureBuffered(timeMillis);
		}

		if (DEBUG)
			SessionInfo.getInstance().log("ensure buffered end");

		if (DEBUG)
			SessionInfo.getInstance().log(
					"asynchronism: " + asyncMillis + "ms\tnew playback rate: "
							+ newPlaybackRate + "\ttime changed: " + timeMillis
							+ "ms");

		PlayerControl.setPlaybackRate(newPlaybackRate); // adjust playback
														// rate

		try {
			Thread.sleep(timeMillis); // wait
		} catch (InterruptedException e) {
			PlayerControl.setPlaybackRate(1);
			if (DEBUG)
				SessionInfo.getInstance().log(
						"got interrupted, synchronization failed");
		}

		PlayerControl.setPlaybackRate(1); // reset the playback rate to
											// normal
	}
}