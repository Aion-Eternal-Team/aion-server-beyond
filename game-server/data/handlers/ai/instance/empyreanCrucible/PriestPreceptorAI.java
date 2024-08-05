package ai.instance.empyreanCrucible;

import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.ai.HpPhases;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.templates.npcskill.NpcSkillTargetAttribute;
import com.aionemu.gameserver.skillengine.SkillEngine;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.world.WorldPosition;

import ai.AggressiveNoLootNpcAI;

/**
 * @author Luzien, w4terbomb
 */
@AIName("priest_preceptor")
public class PriestPreceptorAI extends AggressiveNoLootNpcAI implements HpPhases.PhaseHandler {

	private final HpPhases hpPhases = new HpPhases(75, 25);
	private final int[] helpers;

	public PriestPreceptorAI(Npc owner) {
		super(owner);
		if (owner.getNpcId() == 217581) // Thrasymedes
			helpers = new int[] { 282366, 282367, 282368 }; // Boreas, Jumentis, Charna
		else // Freyr
			helpers = new int[] { 282369, 282370, 282371 }; // Traufnir, Sigyn, Sif
	}

	protected void handleSpawned() {
		super.handleSpawned();
		ThreadPoolManager.getInstance().schedule(() -> SkillEngine.getInstance().getSkill(getOwner(), 19612, 15, getOwner()).useNoAnimationSkill(), 1000);
	}

	@Override
	protected void handleDied() {
		despawnHelpers();
		super.handleDied();
	}

	@Override
	protected void handleBackHome() {
		despawnHelpers();
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
			case 75 -> getOwner().queueSkill(19611, 46, -1, NpcSkillTargetAttribute.RANDOM); // Word of Destruction II
			case 25 -> startTask();
		}
	}

	private void startTask() {
		getOwner().queueSkill(19610, 46, 2000);
		getOwner().queueSkill(19614, 46, -1, NpcSkillTargetAttribute.ME);
		ThreadPoolManager.getInstance().schedule(() -> {
			WorldPosition p = getPosition();
			for (int helperNpcId : helpers)
				applySoulSickness((Npc) spawn(helperNpcId, p.getX(), p.getY(), p.getZ(), p.getHeading()));
		}, 7000);
	}

	private void applySoulSickness(Npc npc) {
		ThreadPoolManager.getInstance().schedule(() -> SkillEngine.getInstance().getSkill(npc, 19594, 4, npc).useNoAnimationSkill(), 1000);
	}

	private void despawnHelpers() {
		getPosition().getWorldMapInstance().getNpcs(helpers).forEach(npc -> npc.getController().deleteIfAliveOrCancelRespawn());
	}
}