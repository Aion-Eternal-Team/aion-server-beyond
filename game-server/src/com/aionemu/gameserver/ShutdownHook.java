package com.aionemu.gameserver;

import java.security.Permission;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.services.CronService;
import com.aionemu.commons.services.cron.CurrentThreadRunnableRunner;
import com.aionemu.commons.utils.ExitCode;
import com.aionemu.commons.utils.concurrent.RunnableStatsManager;
import com.aionemu.commons.utils.concurrent.RunnableStatsManager.SortBy;
import com.aionemu.gameserver.configs.main.ShutdownConfig;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.services.GameTimeService;
import com.aionemu.gameserver.services.PeriodicSaveService;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.world.World;

import ch.qos.logback.classic.LoggerContext;

/**
 * @author lord_rex, Neon
 */
public class ShutdownHook extends Thread {

	private static final Logger log = LoggerFactory.getLogger(ShutdownHook.class);
	private final AtomicInteger remainingSeconds = new AtomicInteger(Integer.MIN_VALUE);
	private volatile int exitCode = ExitCode.ERROR;

	public static ShutdownHook getInstance() {
		return SingletonHolder.INSTANCE;
	}

	private ShutdownHook() {
		System.setSecurityManager(new ExitMonitorSecurityManager()); // detects the exitCode when exit is triggered externally
		if (ShutdownConfig.RESTART_SCHEDULE != null) {
			CronService.getInstance().schedule(() -> System.exit(ExitCode.RESTART), CurrentThreadRunnableRunner.class, ShutdownConfig.RESTART_SCHEDULE,
				false); // CurrentThreadRunnableRunner, otherwise ThreadPoolManager.getInstance().shutdown() will try to wait for this cron task
			log.info("Scheduled automatic server restart based on cron expression: {}", ShutdownConfig.RESTART_SCHEDULE);
		}
	}

	@Override
	public void run() {
		// this method is run when System.exit is triggered, or via other external events like console CTRL+C
		shutdown(exitCode, ShutdownConfig.DELAY);
	}

	protected void shutdown(int exitCode, int delaySeconds) {
		if (delaySeconds < 0)
			return;
		// update effective exit code
		this.exitCode = exitCode;
		// update shutdown delay if possible (unset or more than one second left). return if another thread already runs the shutdown procedure
		if (remainingSeconds.getAndUpdate(seconds -> seconds == Integer.MIN_VALUE || seconds > 1 ? delaySeconds : seconds) != Integer.MIN_VALUE)
			return;

		for (int announceInterval = 1, expectedSeconds = remainingSeconds.get(); remainingSeconds.get() > 0;) {
			try {
				if (World.getInstance().getAllPlayers().isEmpty())
					break; // fast exit

				if (remainingSeconds.get() % announceInterval == 0) {
					String shutdownMsg = this.exitCode == ExitCode.RESTART ? "restarting" : "shutting down";
					log.info("Runtime is " + shutdownMsg + " in " + remainingSeconds + " seconds.");
					PacketSendUtility.broadcastToWorld(SM_SYSTEM_MESSAGE.STR_SERVER_SHUTDOWN(remainingSeconds.get()));
					announceInterval = nextInterval(remainingSeconds.get(), 5, 60);
				}

				sleep(1000);

				// if remainingSeconds got updated from another thread
				if (!remainingSeconds.compareAndSet(expectedSeconds, --expectedSeconds)) {
					expectedSeconds = remainingSeconds.get();
					announceInterval = 1;
				}
			} catch (InterruptedException e) {
				// ignore
			} catch (Exception e) {
				log.error("", e);
			}
		}
		if (!isRunning()) {
			log.info("Shutdown was aborted");
			return;
		}

		GameServer.shutdownNioServer(); // shuts down network, disconnects cs/ls/all players and saves them

		RunnableStatsManager.dumpClassStats(SortBy.AVG);
		PeriodicSaveService.getInstance().onShutdown();

		// Save game time.
		GameTimeService.getInstance().saveGameTime();
		// Shutdown of cron service
		CronService.getInstance().shutdown();
		// ThreadPoolManager shutdown
		ThreadPoolManager.getInstance().shutdown();

		// shut down logger factory to flush all pending log messages
		((LoggerContext) LoggerFactory.getILoggerFactory()).stop();

		Runtime.getRuntime().halt(this.exitCode);
	}

	/**
	 * @param remainingSeconds
	 *          - remaining time in seconds, until the shutdown will be performed
	 * @param minInterval
	 *          - minimum interval to be returned (minInterval will equal remainingSeconds if remainingSeconds is shorter)
	 * @param maxInterval
	 *          - maximum interval to be returned
	 * @return The interval (in seconds) to wait until the next announce should be sent to all players.
	 */
	private static int nextInterval(int remainingSeconds, int minInterval, int maxInterval) {
		if (remainingSeconds < minInterval)
			minInterval = Math.max(1, remainingSeconds);
		int interval = remainingSeconds / 2;
		interval = interval / 5 * 5; // ensure a "clean" interval (dividable by 5, like 5, 10, 15s and so on)
		return Math.min(maxInterval, Math.max(minInterval, interval));
	}

	protected boolean abortShutdown() {
		return isRunning() && remainingSeconds.updateAndGet(seconds -> seconds > 0 ? Integer.MIN_VALUE : seconds) == Integer.MIN_VALUE;
	}

	protected boolean isRunning() {
		return remainingSeconds.get() != Integer.MIN_VALUE;
	}

	protected int getRemainingSeconds() {
		return remainingSeconds.get();
	}

	private class ExitMonitorSecurityManager extends SecurityManager {

		@Override
		public void checkPermission(Permission perm) {
		}

		@Override
		public void checkPermission(Permission perm, Object context) {
		}

		@Override
		public void checkExit(int status) {
			// retrieve the external exit code
			exitCode = status;
		}
	}

	private static final class SingletonHolder {

		private static final ShutdownHook INSTANCE = new ShutdownHook();
	}
}
