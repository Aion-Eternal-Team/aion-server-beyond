package ai.instance.empyreanCrucible;

import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.ai.HpPhases;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.templates.npcskill.NpcSkillTargetAttribute;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.world.WorldPosition;

import ai.AggressiveNpcAI;

/**
 * AI for Mage Preceptor in Empyrean Crucible
 * @author Luzien, w4terbomb
 */
@AIName("mage_preceptor")
public class MagePreceptorAI extends AggressiveNpcAI implements HpPhases.PhaseHandler {

	private final HpPhases hpPhases = new HpPhases(75, 50, 25);

	public MagePreceptorAI(Npc owner) {
		super(owner);
	}

	@Override
	public void handleDespawned() {
		despawnNpcs();
		super.handleDespawned();
	}

	@Override
	public void handleDied() {
		despawnNpcs();
		super.handleDied();
	}

	@Override
	public void handleBackHome() {
		despawnNpcs();
		super.handleBackHome();
		hpPhases.reset();
	}

	@Override
	public void handleAttack(Creature creature) {
		super.handleAttack(creature);
		hpPhases.tryEnterNextPhase(this);
	}

	@Override
	public void handleHpPhase(int phaseHpPercent) {
		switch (phaseHpPercent) {
			case 75 -> queueSkill(19605, NpcSkillTargetAttribute.RANDOM);
			case 50 -> handle50PercentPhase();
			case 25 -> handle25PercentPhase();
		}
	}

	private void handle50PercentPhase() {
		queueSkill(19609, NpcSkillTargetAttribute.MOST_HATED);
		scheduleTask(() -> {
			if (!isDead()) {
				queueSkill(19609, NpcSkillTargetAttribute.MOST_HATED);
				scheduleTask(this::spawnNpcs, 4500);
			}
		}, 3000);
	}

	private void handle25PercentPhase() {
		queueSkill(19605, NpcSkillTargetAttribute.RANDOM);
		scheduleRepeatedSkills(3000, 9000, 15000);
	}

	private void queueSkill(int skillId, NpcSkillTargetAttribute targetAttribute) {
		getOwner().queueSkill(skillId, 10, 0, targetAttribute);
	}

	private void scheduleTask(Runnable task, int delay) {
		ThreadPoolManager.getInstance().schedule(task, delay);
	}

	private void scheduleRepeatedSkills(int... delays) {
		for (int delay : delays) {
			scheduleTask(() -> queueSkill(19605, NpcSkillTargetAttribute.RANDOM), delay);
		}
	}

	private void spawnNpcs() {
		WorldPosition p = getPosition();
		spawn(282364, p.getX(), p.getY(), p.getZ(), p.getHeading());
		spawn(282363, p.getX(), p.getY(), p.getZ(), p.getHeading());
		scheduleTask(() -> queueSkill(19605, NpcSkillTargetAttribute.RANDOM), 2000);
	}

	private void despawnNpcs() {
		despawnNpc(282364);
		despawnNpc(282363);
	}

	private void despawnNpc(int npcId) {
		Npc npc = getPosition().getWorldMapInstance().getNpc(npcId);
		if (npc != null) {
			npc.getController().delete();
		}
	}
}
