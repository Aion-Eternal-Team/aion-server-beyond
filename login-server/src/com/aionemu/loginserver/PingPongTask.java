package com.aionemu.loginserver;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.LoggerFactory;

import com.aionemu.loginserver.network.gameserver.GsConnection;
import com.aionemu.loginserver.network.gameserver.serverpackets.SM_PING;
import com.aionemu.loginserver.utils.ThreadPoolManager;

/**
 * @author KID, Neon
 */
public class PingPongTask implements Runnable {

	private final GsConnection connection;
	private final AtomicInteger unrespondedPingCount = new AtomicInteger();
	private Future<?> task;

	public PingPongTask(GsConnection connection) {
		this.connection = connection;
	}

	@Override
	public void run() {
		if (unrespondedPingCount.getAndIncrement() <= 2) {
			connection.sendPacket(new SM_PING());
		} else {
			stop();
			LoggerFactory.getLogger(PingPongTask.class).warn("Gameserver #" + connection.getGameServerInfo().getId() + " connection died, closing it.");
			connection.close();
		}
	}

	public void onReceivePong() {
		unrespondedPingCount.set(0);
	}

	public void start() {
		if (task != null)
			throw new UnsupportedOperationException("PingPongTask was already started");
		task = ThreadPoolManager.getInstance().scheduleAtFixedRate(this, 5000, 5000);
	}

	public void stop() {
		if (task != null)
			task.cancel(false);
	}
}
