package ai.instance.empyreanCrucible;

import com.aionemu.gameserver.utils.ThreadPoolManager;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.ai.HpPhases;
import com.aionemu.gameserver.controllers.attack.AggroInfo;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;

import ai.AggressiveNpcAI;

/**
 * @author Luzien, w4terbomb
 */
@AIName("king_consierd")
public class KingConsierdAI extends AggressiveNpcAI implements HpPhases.PhaseHandler {

	private final HpPhases hpPhases = new HpPhases(75, 25);
	private final AtomicBoolean isHome = new AtomicBoolean(true);
	private Future<?> eventTask;
	private Future<?> skillTask;

	public KingConsierdAI(Npc owner) {
		super(owner);
	}

	@Override
	public void handleDespawned() {
		cancelTasks();
		super.handleDespawned();
	}

	@Override
	public void handleDied() {
		cancelTasks();
		despawnNpcs(getPosition().getWorldMapInstance().getNpcs(282378));
		super.handleDied();
	}

	@Override
	public void handleBackHome() {
		cancelTasks();
		despawnNpcs(getPosition().getWorldMapInstance().getNpcs(282378));
		super.handleBackHome();
		hpPhases.reset();
	}

	@Override
	public void handleAttack(Creature creature) {
		super.handleAttack(creature);
		hpPhases.tryEnterNextPhase(this);
		if (isHome.compareAndSet(true, false)) {
			startBloodThirstTask();
			scheduleInitialSkills();
		}
	}

	private void scheduleInitialSkills() {
		ThreadPoolManager.getInstance().schedule(() -> {
			getOwner().queueSkill(19691, 1, 0);
			ThreadPoolManager.getInstance().schedule(() -> getOwner().queueSkill(17954, 29, 0), 4000);
		}, 2000);
	}

	@Override
	public void handleHpPhase(int phaseHpPercent) {
		switch (phaseHpPercent) {
			case 75 -> startSkillTask();
			case 25 -> getOwner().queueSkill(19690, 1, 0);
		}
	}

	private void startBloodThirstTask() {
		eventTask = ThreadPoolManager.getInstance().schedule(() -> getOwner().queueSkill(19624, 10, 0), 180000); // 3min, need confirm
	}

	private void startSkillTask() {
		skillTask = ThreadPoolManager.getInstance().scheduleAtFixedRate(this::executeSkillTask, 0, 25000);
	}

	private void executeSkillTask() {
		if (isDead()) {
			cancelTasks();
		} else {
			getOwner().queueSkill(17951, 29, 0);
			ThreadPoolManager.getInstance().schedule(() -> {
				dropAggro();
				if (getLifeStats().getHpPercentage() <= 50)
					spawnBabyConsierd();
				ThreadPoolManager.getInstance().schedule(() -> getOwner().queueSkill(17952, 29, 0), 2000);
			}, 3500);
		}
	}

	private void spawnBabyConsierd() {
		var position = getPosition();
		spawn(282378, position.getX(), position.getY(), position.getZ(), position.getHeading());
		spawn(282378, position.getX(), position.getY(), position.getZ(), position.getHeading());
	}

	private void dropAggro() {
		if (getTarget() instanceof Creature hated && getAggroList().isHating(hated)) {
			AggroInfo ai = getAggroList().getAggroInfo(hated);
			ai.setHate(ai.getHate() / 2);
			think();
		}
	}

	private void cancelTasks() {
		if (eventTask != null && !eventTask.isDone())
			eventTask.cancel(true);
		if (skillTask != null && !skillTask.isCancelled())
			skillTask.cancel(true);
	}

	private void despawnNpcs(List<Npc> npcs) {
		npcs.forEach(npc -> npc.getController().delete());
	}
}
