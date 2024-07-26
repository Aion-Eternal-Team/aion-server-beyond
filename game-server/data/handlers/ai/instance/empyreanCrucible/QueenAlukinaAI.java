package ai.instance.empyreanCrucible;

import java.util.concurrent.Future;

import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.ai.HpPhases;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;

import ai.AggressiveNpcAI;

/**
 * @author Luzien, w4terbomb
 */
@AIName("alukina_emp")
public class QueenAlukinaAI extends AggressiveNpcAI implements HpPhases.PhaseHandler {

	private final HpPhases hpPhases = new HpPhases(75, 50, 25);
	private Future<?> task;

	public QueenAlukinaAI(Npc owner) {
		super(owner);
	}

	@Override
	public void handleDespawned() {
		cancelTask();
		super.handleDespawned();
	}

	@Override
	public void handleDied() {
		cancelTask();
		super.handleDied();
	}

	@Override
	public void handleBackHome() {
		cancelTask();
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
		getOwner().queueSkill(17899, 41, 0);
		switch (phaseHpPercent) {
			case 75 -> {
				scheduleSkill(17900, 4500);
				PacketSendUtility.broadcastMessage(getOwner(), 340487, 10000);
				scheduleSkill(17899, 14000);
				scheduleSkill(17900, 18000);
			}
			case 50 -> {
				scheduleSkill(17280, 4500);
				scheduleSkill(17902, 8000);
			}
			case 25 -> task = ThreadPoolManager.getInstance().scheduleAtFixedRate(() -> {
				if (isDead()) {
					cancelTask();
				} else {
					getOwner().queueSkill(17901, 41, 0);
					scheduleSkill(17902, 5500);
					scheduleSkill(17902, 7500);
				}
			}, 4500, 20000);
		}
	}

	private void cancelTask() {
		if (task != null && !task.isCancelled())
			task.cancel(true);
	}

	private void scheduleSkill(final int skill, int delay) {
		ThreadPoolManager.getInstance().schedule(() -> {
			if (!isDead())
				getOwner().queueSkill(skill, 41, 0);
		}, delay);
	}
}
