package com.aionemu.gameserver.ai.manager;

import com.aionemu.gameserver.ai.AILogger;
import com.aionemu.gameserver.ai.AIState;
import com.aionemu.gameserver.ai.AbstractAI;
import com.aionemu.gameserver.ai.NpcAI;
import com.aionemu.gameserver.ai.event.AIEventType;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.utils.MathUtil;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.world.geo.GeoService;

/**
 * @author ATracer
 */
public class SimpleAttackManager {

	/**
	 * @param npcAI
	 * @param delay
	 */
	public static void performAttack(NpcAI npcAI, int delay) {
		if (npcAI.isLogging()) {
			AILogger.info(npcAI, "performAttack");
		}
		if (npcAI.getOwner().getGameStats().isNextAttackScheduled()) {
			if (npcAI.isLogging()) {
				AILogger.info(npcAI, "Attack already scheduled");
			}
			scheduleCheckedAttackAction(npcAI, delay);
			return;
		}

		npcAI.getOwner().getGameStats().setNextAttackTime(System.currentTimeMillis() + delay);
		if (delay > 0) {
			ThreadPoolManager.getInstance().schedule(new SimpleAttackAction(npcAI), delay);
		} else {
			attackAction(npcAI);
		}
	}

	/**
	 * @param npcAI
	 * @param delay
	 */
	private static void scheduleCheckedAttackAction(NpcAI npcAI, int delay) {
		if (delay < 2000) {
			delay = 2000;
		}
		if (npcAI.isLogging()) {
			AILogger.info(npcAI, "Scheduling checked attack " + delay);
		}
		ThreadPoolManager.getInstance().schedule(new SimpleCheckedAttackAction(npcAI), delay);
	}

	public static boolean isTargetInAttackRange(Npc npc) {
		if (npc.getAi().isLogging()) {
			float distance = npc.getDistanceToTarget();
			AILogger.info((AbstractAI) npc.getAi(), "isTargetInAttackRange: " + distance);
		}
		if (npc.getTarget() == null || !(npc.getTarget() instanceof Creature))
			return false;
		return MathUtil.isInAttackRange(npc, (Creature) npc.getTarget(), npc.getGameStats().getAttackRange().getCurrent() / 1000f);
		// return distance <= npc.getController().getAttackDistanceToTarget() + NpcMoveController.MOVE_CHECK_OFFSET;
	}

	/**
	 * @param npcAI
	 */
	protected static void attackAction(final NpcAI npcAI) {
		if (!npcAI.isInState(AIState.FIGHT)) {
			return;
		}
		if (npcAI.isLogging()) {
			AILogger.info(npcAI, "attackAction");
		}
		Npc npc = npcAI.getOwner();
		Creature target;
		if (npc.getTarget() instanceof Creature && !(target = (Creature) npc.getTarget()).getLifeStats().isAlreadyDead()) {
			Creature mostHated = npc.getAggroList().getMostHated();
			if (mostHated != null && !mostHated.getLifeStats().isAlreadyDead() && !target.equals(mostHated)) {
				npcAI.onCreatureEvent(AIEventType.TARGET_CHANGED, mostHated);
				return;
			}
			if (!npc.canSee(target)) {
				npc.getController().cancelCurrentSkill(null);
				npcAI.onGeneralEvent(AIEventType.TARGET_GIVEUP);
				return;
			}
			if (!isTargetInAttackRange(npc)) {
				npcAI.onGeneralEvent(AIEventType.TARGET_TOOFAR);
			} else if (!GeoService.getInstance().canSee(npc, target)) { //delete geo check when we've implemented a pathfinding system
				npc.getController().cancelCurrentSkill(null);
				if (((System.currentTimeMillis() - npc.getMoveController().getLastMoveUpdate()) > 15000)
						&& npc.getGameStats().getLastAttackedTimeDelta() > 15) {
					npcAI.onGeneralEvent(AIEventType.TARGET_GIVEUP);
				} else {
					npcAI.onGeneralEvent(AIEventType.ATTACK_COMPLETE);
				}
			} else {
				npc.getPosition().setH(MathUtil.getHeadingTowards(npc, target));
				npc.getController().attackTarget(target, 0, false);
				npcAI.onGeneralEvent(AIEventType.ATTACK_COMPLETE);
			}
		} else {
			npcAI.onGeneralEvent(AIEventType.TARGET_GIVEUP);
		}
	}

	private final static class SimpleAttackAction implements Runnable {

		private NpcAI npcAI;

		SimpleAttackAction(NpcAI npcAI) {
			this.npcAI = npcAI;
		}

		@Override
		public void run() {
			attackAction(npcAI);
			npcAI = null;
		}

	}

	private final static class SimpleCheckedAttackAction implements Runnable {

		private NpcAI npcAI;

		SimpleCheckedAttackAction(NpcAI npcAI) {
			this.npcAI = npcAI;
		}

		@Override
		public void run() {
			if (!npcAI.getOwner().getGameStats().isNextAttackScheduled()) {
				attackAction(npcAI);
			} else {
				if (npcAI.isLogging()) {
					AILogger.info(npcAI, "Scheduled checked attacked confirmed");
				}
			}
			npcAI = null;
		}

	}

}