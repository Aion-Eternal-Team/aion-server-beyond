package ai.instance.empyreanCrucible;

import java.util.concurrent.Future;
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

	private final HpPhases hpPhases = new HpPhases(100, 75);
	private Future<?> task1, task2;

	public RM1337AI(Npc owner) {
		super(owner);
	}

	@Override
	protected void handleSpawned() {
		super.handleSpawned();
		PacketSendUtility.broadcastMessage(getOwner(), 1500229, 2000);
	}

	@Override
	protected void handleDespawned() {
		cancelTask();
		super.handleDespawned();
	}

	@Override
	protected void handleDied() {
		cancelTask();
		PacketSendUtility.broadcastMessage(getOwner(), 1500231);
		super.handleDied();
	}

	@Override
	protected void handleBackHome() {
		cancelTask();
		super.handleBackHome();
		hpPhases.reset();
	}

	@Override
	protected void handleAttack(Creature creature) {
		super.handleAttack(creature);
		hpPhases.tryEnterNextPhase(this);
	}

	@Override
	public void handleHpPhase(int phaseHpPercent) {
		switch (phaseHpPercent) {
			case 100 -> startSkillTask1();
			case 95 -> startSkillTask2();
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
				int skillId = getLifeStats().getHpPercentage() > 50 || Rnd.nextBoolean() ? 19550 : 19552;
				getOwner().queueSkill(skillId, 10, -1, NpcSkillTargetAttribute.RANDOM);
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
				getOwner().queueSkill(19551, 10);
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
