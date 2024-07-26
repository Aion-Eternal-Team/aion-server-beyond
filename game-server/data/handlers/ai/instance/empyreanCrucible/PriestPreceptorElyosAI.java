package ai.instance.empyreanCrucible;

import java.util.concurrent.atomic.AtomicBoolean;

import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.templates.npcskill.NpcSkillTargetAttribute;
import com.aionemu.gameserver.skillengine.SkillEngine;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.world.WorldPosition;

import ai.AggressiveNoLootNpcAI;

/**
 * AI for Priest Preceptor (Elyos) in Empyrean Crucible
 *
 * @author w4terbomb
 */
@AIName("priest_preceptor_elyos")
public class PriestPreceptorElyosAI extends AggressiveNoLootNpcAI {

	private final AtomicBoolean startTask1 = new AtomicBoolean();
	private final AtomicBoolean startTask2 = new AtomicBoolean();

	public PriestPreceptorElyosAI(Npc owner) {
		super(owner);
	}

	@Override
	public void handleSpawned() {
		super.handleSpawned();
		ThreadPoolManager.getInstance().schedule(() -> getOwner().queueSkill(19612, 46, 0), 1000);
	}

	@Override
	public void handleAttack(Creature creature) {
		super.handleAttack(creature);
		checkPercentage(getLifeStats().getHpPercentage());
	}

	private void checkPercentage(int percentage) {
		if (percentage <= 75 && startTask1.compareAndSet(false, true))
			getOwner().queueSkill(19611, 46, 0, NpcSkillTargetAttribute.RANDOM);
		if (percentage <= 25 && startTask2.compareAndSet(false, true))
			startTask();
	}

	private void startTask() {
		getOwner().queueSkill(19610, 46, 0);
		ThreadPoolManager.getInstance().schedule(new Runnable() {
			@Override
			public void run() {
				getOwner().queueSkill(19614, 46, 0, NpcSkillTargetAttribute.ME);
				ThreadPoolManager.getInstance().schedule(() -> {
					WorldPosition p = getPosition();
					applySoulSickness((Npc) spawn(282366, p.getX(), p.getY(), p.getZ(), p.getHeading())); //Boreas.
					applySoulSickness((Npc) spawn(282367, p.getX(), p.getY(), p.getZ(), p.getHeading())); //Jumentis.
					applySoulSickness((Npc) spawn(282368, p.getX(), p.getY(), p.getZ(), p.getHeading())); //Charna.
				}, 5000);
			}
		}, 2000);
	}

	private void applySoulSickness(final Npc npc) {
		ThreadPoolManager.getInstance().schedule(() -> SkillEngine.getInstance().getSkill(npc, 19594, 4, npc).useNoAnimationSkill(), 1000);
	}

	@Override
	protected void handleDied() {
		despawnNpcsById(282366, 282367, 282368); // Boreas, Jumentis, Charna.
		super.handleDied();
	}

	@Override
	protected void handleBackHome() {
		despawnNpcsById(282366, 282367, 282368); // Boreas, Jumentis, Charna.
		stopTask();
		super.handleDied();
	}

	private void stopTask() {
		startTask1.set(false);
		startTask2.set(false);
	}

	private void despawnNpcsById(int... npcIds) {
		getPosition().getWorldMapInstance().getNpcs(npcIds).forEach(npc -> npc.getController().deleteIfAliveOrCancelRespawn());
	}
}