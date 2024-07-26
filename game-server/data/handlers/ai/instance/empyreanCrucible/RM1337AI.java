package ai.instance.empyreanCrucible;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import com.aionemu.commons.utils.Rnd;
import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.ai.HpPhases;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.templates.npcskill.NpcSkillTargetAttribute;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;

import ai.AggressiveNpcAI;

/**
 * @author Luzien, w4terbomb
 */
@AIName("rm_1337")
public class RM1337AI extends AggressiveNpcAI implements HpPhases.PhaseHandler {

	private final HpPhases hpPhases = new HpPhases(15, 35, 55, 75, 95);

	private final AtomicBoolean isHome = new AtomicBoolean(true);
	private final AtomicBoolean isEventStarted = new AtomicBoolean(false);
	private Future<?> task1, task2;

	public RM1337AI(Npc owner) {
		super(owner);
	}

	@Override
	public void handleSpawned() {
		super.handleSpawned();
		PacketSendUtility.broadcastMessage(getOwner(), 1500229, 2000);
	}

	@Override
	public void handleDespawned() {
		cancelTask();
		super.handleDespawned();
	}

	@Override
	public void handleDied() {
		cancelTask();
		PacketSendUtility.broadcastMessage(getOwner(), 1500231);
		super.handleDied();
	}

	@Override
	public void handleBackHome() {
		cancelTask();
		super.handleBackHome();
	}

	@Override
	public void handleAttack(Creature creature) {
		super.handleAttack(creature);
		if (isHome.compareAndSet(true, false))
			startSkillTask1();
		handleHpPhase(getLifeStats().getHpPercentage());
	}

	@Override
	public void handleHpPhase(int phaseHpPercent) {
		switch (phaseHpPercent) {
			case 15, 35, 55, 75, 95 -> {
				if (isEventStarted.compareAndSet(false, true))
					startSkillTask2();
			}
		}
	}

	private void cancelTask() {
		if (task1 != null && !task1.isCancelled())
			task1.cancel(true);
		if (task2 != null && !task2.isCancelled())
			task2.cancel(true);
	}

	private void startSkillTask1() {
		task1 = ThreadPoolManager.getInstance().scheduleAtFixedRate(() -> {
			if (isDead()) {
				cancelTask();
			} else {
				if (getOwner().getCastingSkill() != null)
					return;
				if (getLifeStats().getHpPercentage() <= 50)
					switch (Rnd.nextInt(2)) {
						case 0 -> getOwner().queueSkill(19550, 10, 0, NpcSkillTargetAttribute.RANDOM);
						default -> getOwner().queueSkill(19552, 10, 0, NpcSkillTargetAttribute.RANDOM);
					}
				else
					getOwner().queueSkill(19550, 10, 0, NpcSkillTargetAttribute.RANDOM);
			}
		}, 10000, 23000);
	}

	private void startSkillTask2() {
		task2 = ThreadPoolManager.getInstance().scheduleAtFixedRate(() -> {
			if (isDead()) {
				cancelTask();
			} else {
				getOwner().getController().cancelCurrentSkill(null);
				PacketSendUtility.broadcastMessage(getOwner(), 1500230);
				getOwner().queueSkill(19551, 10, 0);
				spawnSparks();
			}
		}, 0, 60000);
	}

	private void spawnSparks() {
		ThreadPoolManager.getInstance().schedule(() -> {
			if (!isDead()) {
				IntStream.range(0, Rnd.get(8, 12)).forEach(i -> rndSpawnInRange(282373, 3, 12));
			}
		}, 4000);
	}
}
