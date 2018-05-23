package de.fraunhofer.iosb.ilt.frostBenchmark;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 *
 * @author scf
 */
public class TimeoutWatcher {

	private final Timer timer;
	private final TimerTask task;
	private final List<TimeoutListener> listeners = new CopyOnWriteArrayList<>();
	private long nextTimeout = 0;

	public TimeoutWatcher() {
		timer = new Timer(true);
		task = new TimerTask() {
			@Override
			public void run() {
				checkTimeout();
			}
		};
		timer.scheduleAtFixedRate(task, 1000, 1000);
	}

	public void terminate() {
		task.cancel();
		timer.cancel();
	}

	/**
	 * Set the unixtime in ms for the next timeout. Set to 0 to disable the
	 * timout watcher.
	 *
	 * @param nextTimeout the unixtime in ms for the next timeout.
	 */
	public void setNextTimeout(long nextTimeout) {
		this.nextTimeout = nextTimeout;
	}

	private void checkTimeout() {
		if (nextTimeout == 0) {
			return;
		}
		long currentTime = System.currentTimeMillis();
		if (currentTime > nextTimeout) {
			nextTimeout = 0;
			notifyWatchers();
		}
	}

	public void addTimeoutListener(TimeoutListener listener) {
		listeners.add(listener);
	}

	public void removeTimeoutListener(TimeoutListener listener) {
		listeners.remove(listener);
	}

	private void notifyWatchers() {
		for (TimeoutListener listener : listeners) {
			listener.timeoutReached();
		}
	}
}
