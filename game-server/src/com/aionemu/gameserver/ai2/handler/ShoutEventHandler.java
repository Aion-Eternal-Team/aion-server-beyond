package com.aionemu.gameserver.ai2.handler;

import java.util.List;

import com.aionemu.commons.utils.Rnd;
import com.aionemu.gameserver.ai2.NpcAI2;
import com.aionemu.gameserver.ai2.poll.AIQuestion;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.npcshout.NpcShout;
import com.aionemu.gameserver.model.templates.npcshout.ShoutEventType;
import com.aionemu.gameserver.model.templates.walker.WalkerTemplate;
import com.aionemu.gameserver.services.NpcShoutsService;

import javolution.util.FastTable;

/**
 * @author Rolandas
 * @reworked Neon
 */
public final class ShoutEventHandler {

	public static void onSee(NpcAI2 npcAI, Creature target) {
		if (target instanceof Player && npcAI.ask(AIQuestion.CAN_SHOUT)) {
			Npc npc = npcAI.getOwner();
			List<NpcShout> shouts = DataManager.NPC_SHOUT_DATA.getNpcShouts(npc.getPosition().getMapId(), npc.getNpcId(), ShoutEventType.SEE);
			NpcShoutsService.getInstance().shoutRandom(npc, (Player) target, shouts, 0);
		}
	}

	public static void onSpawn(NpcAI2 npcAI) {
		if (npcAI.ask(AIQuestion.CAN_SHOUT)) {
			Npc npc = npcAI.getOwner();
			NpcShoutsService.getInstance().registerShoutTask(npc);
		}
	}

	public static void onBeforeDespawn(NpcAI2 npcAI) {
		if (npcAI.ask(AIQuestion.CAN_SHOUT)) {
			Npc npc = npcAI.getOwner();
			List<NpcShout> shouts = DataManager.NPC_SHOUT_DATA.getNpcShouts(npc.getPosition().getMapId(), npc.getNpcId(), ShoutEventType.BEFORE_DESPAWN);
			NpcShoutsService.getInstance().shoutRandom(npc, null, shouts, 0);
			NpcShoutsService.getInstance().removeShoutCooldown(npc);
		}
	}

	public static void onReachedWalkPoint(NpcAI2 npcAI) {
		if (npcAI.ask(AIQuestion.CAN_SHOUT)) {
			Npc npc = npcAI.getOwner();
			String walkerId = npc.getSpawn().getWalkerId();
			if (walkerId == null)
				return;
			ShoutEventType shoutType = npc.getMoveController().isChangingDirection() ? ShoutEventType.WALK_DIRECTION : ShoutEventType.WALK_WAYPOINT;
			List<NpcShout> shouts = DataManager.NPC_SHOUT_DATA.getNpcShouts(npc.getPosition().getMapId(), npc.getNpcId(), shoutType);
			if (shouts == null || shouts.isEmpty()) {
				WalkerTemplate tp = DataManager.WALKER_DATA.getWalkerTemplate(walkerId);
				int stepCount = tp.getRouteSteps().size();
				if (Rnd.get(stepCount) < 2) {
					if (npc.getTarget() instanceof Player)
						NpcShoutsService.getInstance().shoutRandom(npc, (Player) npc.getTarget(), shouts, 0);
					else
						NpcShoutsService.getInstance().shoutRandom(npc, null, shouts, 0);
				}
			}
		}
	}

	public static void onSwitchedTarget(NpcAI2 npcAI, Creature target) {
		if (target instanceof Player && npcAI.ask(AIQuestion.CAN_SHOUT)) {
			Npc npc = npcAI.getOwner();
			List<NpcShout> shouts = DataManager.NPC_SHOUT_DATA.getNpcShouts(npc.getPosition().getMapId(), npc.getNpcId(), ShoutEventType.SWITCH_TARGET);
			NpcShoutsService.getInstance().shoutRandom(npc, (Player) target, shouts, 0);
		}
	}

	public static void onDied(NpcAI2 npcAI) {
		if (npcAI.ask(AIQuestion.CAN_SHOUT)) {
			Npc npc = npcAI.getOwner();
			List<NpcShout> shouts = DataManager.NPC_SHOUT_DATA.getNpcShouts(npc.getPosition().getMapId(), npc.getNpcId(), ShoutEventType.DIED);
			NpcShoutsService.getInstance().shoutRandom(npc, null, shouts, 0);
		}
	}

	/**
	 * Called on Aggro when NPC is ready to attack
	 */
	public static void onAttackBegin(NpcAI2 npcAI) {
		if (npcAI.ask(AIQuestion.CAN_SHOUT)) {
			Npc npc = npcAI.getOwner();
			List<NpcShout> shouts = DataManager.NPC_SHOUT_DATA.getNpcShouts(npc.getPosition().getMapId(), npc.getNpcId(), ShoutEventType.ATTACK_BEGIN);
			NpcShoutsService.getInstance().shoutRandom(npc, null, shouts, 0);
		}
	}

	/**
	 * Handle NPC attacked event (when damage was received or not)
	 */
	public static void onEnemyAttack(NpcAI2 npcAI, Creature attacker) {
		// TODO: [RR] change AI or randomize behavior for "cowards" and "fanatics" ???
		// TODO: Figure out what the difference between ATTACK_BEGIN and HELP; HELPCALL should make NPC run
		if (npcAI.ask(AIQuestion.CAN_SHOUT)) {
			Npc npc = npcAI.getOwner();
			if (attacker.getActingCreature() instanceof Player) {
				if (npc.getAttackedCount() == 0) {
					List<NpcShout> shouts = DataManager.NPC_SHOUT_DATA.getNpcShouts(npc.getPosition().getMapId(), npc.getNpcId(), ShoutEventType.ATTACKED);
					if (shouts != null && !shouts.isEmpty()) {
						NpcShoutsService.getInstance().shoutRandom(npc, (Player) attacker.getActingCreature(), shouts, 0);
						return;
					}
					shouts = DataManager.NPC_SHOUT_DATA.getNpcShouts(npc.getPosition().getMapId(), npc.getNpcId(), ShoutEventType.HELPCALL);
					NpcShoutsService.getInstance().shoutRandom(npc, (Player) attacker.getActingCreature(), shouts, 0);
				}
			} else {
				List<NpcShout> shouts = DataManager.NPC_SHOUT_DATA.getNpcShouts(npc.getPosition().getMapId(), npc.getNpcId(), ShoutEventType.ATTACKED);
				if (shouts != null && !shouts.isEmpty()) {
					NpcShout shout = shouts.get(shouts.size() == 1 ? 0 : Rnd.get(shouts.size()));
					NpcShoutsService.getInstance().shout(npc, null, shout, shout.getPollDelay() / 1000);
				}
			}
		}
	}

	public static void onCast(NpcAI2 npcAI, Creature firstTarget) {
		if (firstTarget instanceof Player && npcAI.ask(AIQuestion.CAN_SHOUT))
			handleNumericEvent(npcAI, (Player) firstTarget, ShoutEventType.CAST_K);
	}

	/**
	 * Handle target attacked events
	 */
	public static void onAttack(NpcAI2 npcAI, Creature attacked) {
		if (attacked instanceof Player && npcAI.ask(AIQuestion.CAN_SHOUT))
			handleNumericEvent(npcAI, (Player) attacked, ShoutEventType.ATTACK_K);
	}

	private static void handleNumericEvent(NpcAI2 npcAI, Player creature, ShoutEventType eventType) {
		Npc npc = npcAI.getOwner();
		List<NpcShout> shouts = DataManager.NPC_SHOUT_DATA.getNpcShouts(npc.getPosition().getMapId(), npc.getNpcId(), eventType);
		if (shouts == null || shouts.isEmpty())
			return;

		List<NpcShout> validShouts = new FastTable<>();
		List<NpcShout> nonNumberedShouts = new FastTable<>();
		for (NpcShout shout : shouts) {
			if (shout.getSkillNo() == 0)
				nonNumberedShouts.add(shout);
			else if (shout.getSkillNo() == npc.getSkillNumber())
				validShouts.add(shout);
		}

		NpcShoutsService.getInstance().shoutRandom(npc, creature, !validShouts.isEmpty() ? validShouts : nonNumberedShouts, 0);
	}

	public static void onAttackEnd(NpcAI2 npcAI) {
		if (npcAI.ask(AIQuestion.CAN_SHOUT)) {
			Npc npc = npcAI.getOwner();
			List<NpcShout> shouts = DataManager.NPC_SHOUT_DATA.getNpcShouts(npc.getPosition().getMapId(), npc.getNpcId(), ShoutEventType.ATTACK_END);
			NpcShoutsService.getInstance().shoutRandom(npc, null, shouts, 0);
		}
	}
}
