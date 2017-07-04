package ai.instance.esoterrace;

import java.util.concurrent.atomic.AtomicBoolean;

import com.aionemu.gameserver.ai.AIActions;
import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.ai.manager.WalkManager;
import com.aionemu.gameserver.ai.poll.AIQuestion;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.skillengine.SkillEngine;
import com.aionemu.gameserver.utils.ThreadPoolManager;

import ai.AggressiveNpcAI;

/**
 * @author xTz
 */
@AIName("greenfingers")
public class GreenfingersAI extends AggressiveNpcAI {

	private AtomicBoolean isDestroyed = new AtomicBoolean(false);
	private int walkPosition;
	private int helperSkill;

	@Override
	public boolean canThink() {
		return false;
	}

	@Override
	protected void handleSpawned() {
		super.handleSpawned();
		switch (getNpcId()) {
			case 282176:
				walkPosition = 24;
				helperSkill = 19271;
				break;
			case 282177:
				walkPosition = 26;
				helperSkill = 18751;
				break;
			case 282178:
				walkPosition = 40;
				helperSkill = 16634;
				break;
		}
	}

	@Override
	protected void handleMoveArrived() {
		super.handleMoveArrived();
		int point = getOwner().getMoveController().getCurrentStep().getStepIndex();
		if (walkPosition == point) {
			if (isDestroyed.compareAndSet(false, true)) {
				getSpawnTemplate().setWalkerId(null);
				WalkManager.stopWalking(this);
				Npc boss = getPosition().getWorldMapInstance().getNpc(217185);
				if (boss != null) {
					SkillEngine.getInstance().getSkill(getOwner(), helperSkill, 55, boss).useNoAnimationSkill();
				}
				startDespawnTask();
			}
		}
	}

	private void startDespawnTask() {
		ThreadPoolManager.getInstance().schedule(new Runnable() {

			@Override
			public void run() {
				if (!isDead()) {
					AIActions.deleteOwner(GreenfingersAI.this);
				}
			}

		}, 3000);
	}

	@Override
	public boolean ask(AIQuestion question) {
		switch (question) {
			case CAN_RESIST_ABNORMAL:
				return true;
			case SHOULD_DECAY:
			case SHOULD_RESPAWN:
			case SHOULD_REWARD:
				return false;
			default:
				return super.ask(question);
		}
	}
}
