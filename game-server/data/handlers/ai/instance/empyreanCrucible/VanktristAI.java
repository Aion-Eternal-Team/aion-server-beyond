package ai.instance.empyreanCrucible;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import com.aionemu.commons.utils.Rnd;
import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.ai.HpPhases;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.world.WorldPosition;

import ai.AggressiveNoLootNpcAI;

/**
 * @author w4terbomb
 */
@AIName("vanktrist")
public class VanktristAI extends AggressiveNoLootNpcAI implements HpPhases.PhaseHandler {

	private final HpPhases hpPhases = new HpPhases(75, 50);

	private Future<?> phaseTask;
	private final AtomicBoolean isAggred = new AtomicBoolean(false);
	private final AtomicBoolean isStartedEvent = new AtomicBoolean(false);

	private VanktristAI(Npc owner) {
		super(owner);
	}

	@Override
	protected void handleAttack(Creature creature) {
		super.handleAttack(creature);
		if (isAggred.compareAndSet(false, true)) {
			PacketSendUtility.broadcastMessage(getOwner(), 0);
		}
		checkPercentage(getLifeStats().getHpPercentage());
	}

	public void checkPercentage(int hpPercentage) {
		switch (hpPercentage) {
			case 50, 75 -> {
				if (isStartedEvent.compareAndSet(false, true))
					startPhaseTask();
			}
		}
	}

	@Override
	public void handleHpPhase(int phaseHpPercent) {
		switch (phaseHpPercent) {
			case 75 -> PacketSendUtility.broadcastMessage(getOwner(), 1500210, 0, 0);
			case 50 -> PacketSendUtility.broadcastMessage(getOwner(), 1500211, 0, 0);
		}
	}

	private void startPhaseTask() {
		phaseTask = ThreadPoolManager.getInstance().scheduleAtFixedRate(() -> {
			if (isDead()) {
				cancelPhaseTask();
			} else {
				getOwner().queueSkill(19567, 46, 0); //Gravitational Shift.
				List<Player> players = getLifedPlayers();
				if (!players.isEmpty()) {
					int size = players.size();
					int count = Rnd.get(1, size);
					for (int i = 0; i < count; i++) {
						spawnWeakenedDimensionalVortex(players.get(Rnd.get(players.getFirst().getObjectId(), players.size())));
					}
				}
			}
		}, 3000, 15000);
	}

	private void spawnWeakenedDimensionalVortex(Player player) {
		final float x = player.getX();
		final float y = player.getY();
		final float z = player.getZ();
		if (x > 0 && y > 0 && z > 0) {
			ThreadPoolManager.getInstance().schedule(new Runnable() {
				@Override
				public void run() {
					if (!isDead()) {
						spawn(217804, x, y, z, (byte) 0); //Weakened Dimensional Vortex.
					}
				}
			}, 3000);
		}
	}

	private List<Player> getLifedPlayers() {
		List<Player> players = new ArrayList<Player>();
		for (Player player : getKnownList().getKnownPlayers().values()) {
			if (!isDead()) {
				players.add(player);
			}
		}
		return players;
	}

	private void cancelPhaseTask() {
		if (phaseTask != null && !phaseTask.isDone()) {
			phaseTask.cancel(true);
		}
	}

	@Override
	protected void handleDespawned() {
		cancelPhaseTask();
		super.handleDespawned();
	}

	@Override
	protected void handleBackHome() {
		cancelPhaseTask();
		isStartedEvent.set(false);
		isAggred.set(false);
		super.handleBackHome();
	}

	@Override
	protected void handleDied() {
		final WorldPosition p = getPosition();
		if (p != null) {
			deleteNpcs(p.getWorldMapInstance().getNpcs(217804)); //Vortex.
		}
		cancelPhaseTask();
		super.handleDied();
	}

	private void deleteNpcs(List<Npc> npcs) {
		for (Npc npc : npcs) {
			if (npc != null) {
				npc.getController().onDelete();
			}
		}
	}

}