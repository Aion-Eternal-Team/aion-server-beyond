package com.aionemu.gameserver.controllers;

import java.util.Collection;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.gameserver.ai.NpcAI;
import com.aionemu.gameserver.ai.event.AIEventType;
import com.aionemu.gameserver.ai.handler.ShoutEventHandler;
import com.aionemu.gameserver.ai.poll.AIQuestion;
import com.aionemu.gameserver.controllers.attack.AggroInfo;
import com.aionemu.gameserver.controllers.attack.AggroList;
import com.aionemu.gameserver.controllers.attack.AttackStatus;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.animations.ObjectDeleteAnimation;
import com.aionemu.gameserver.model.drop.DropItem;
import com.aionemu.gameserver.model.gameobjects.AionObject;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.DropNpc;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.Pet;
import com.aionemu.gameserver.model.gameobjects.PetSpecialFunction;
import com.aionemu.gameserver.model.gameobjects.Summon;
import com.aionemu.gameserver.model.gameobjects.VisibleObject;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.Rates;
import com.aionemu.gameserver.model.gameobjects.state.CreatureState;
import com.aionemu.gameserver.model.team.TemporaryPlayerTeam;
import com.aionemu.gameserver.model.team.common.service.PlayerTeamDistributionService;
import com.aionemu.gameserver.network.aion.serverpackets.SM_ATTACK_STATUS.LOG;
import com.aionemu.gameserver.network.aion.serverpackets.SM_ATTACK_STATUS.TYPE;
import com.aionemu.gameserver.network.aion.serverpackets.SM_PET;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.questEngine.QuestEngine;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.services.DialogService;
import com.aionemu.gameserver.services.RespawnService;
import com.aionemu.gameserver.services.SiegeService;
import com.aionemu.gameserver.services.abyss.AbyssPointsService;
import com.aionemu.gameserver.services.drop.DropRegistrationService;
import com.aionemu.gameserver.services.drop.DropService;
import com.aionemu.gameserver.services.event.EventService;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;
import com.aionemu.gameserver.taskmanager.tasks.MoveTaskManager;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.utils.stats.StatFunctions;
import com.aionemu.gameserver.world.World;
import com.aionemu.gameserver.world.zone.ZoneInstance;

/**
 * This class is for controlling Npc's
 * 
 * @author -Nemesiss-, ATracer (2009-09-29), Sarynth modified by Wakizashi
 */
public class NpcController extends CreatureController<Npc> {

	private static final Logger log = LoggerFactory.getLogger(NpcController.class);

	@Override
	public void see(VisibleObject object) {
		super.see(object);
		Npc owner = getOwner();
		if (object instanceof Creature) {
			Creature creature = (Creature) object;
			owner.getAi().onCreatureEvent(AIEventType.CREATURE_SEE, creature);
			if (creature instanceof Player) {
				if (owner.isDead())
					DropService.getInstance().see((Player) creature, owner);
			}
		}
	}

	@Override
	public void notSee(VisibleObject object, ObjectDeleteAnimation animation) {
		if (object instanceof Creature) {
			Creature creature = (Creature) object;
			getOwner().getAi().onCreatureEvent(AIEventType.CREATURE_NOT_SEE, creature);
		}
		super.notSee(object, animation);
	}

	@Override
	public void onBeforeSpawn() {
		super.onBeforeSpawn();
		Npc owner = getOwner();

		// set state from npc templates
		if (owner.getObjectTemplate().getState() > 0)
			owner.setState(owner.getObjectTemplate().getState());
		else
			owner.setState(CreatureState.WALK_MODE);

		owner.getLifeStats().setCurrentHpPercent(100);
		owner.getAi().onGeneralEvent(AIEventType.BEFORE_SPAWNED);

		if (owner.getSpawn().getState() > 0) {
			owner.setState(owner.getSpawn().getState());
		} else if (owner.getSpawn().canFly()) {
			owner.setState(CreatureState.FLYING);
		}
	}

	@Override
	public void onAfterSpawn() {
		super.onAfterSpawn();
		getOwner().getAi().onGeneralEvent(AIEventType.SPAWNED);
	}

	@Override
	public void onDespawn() {
		Npc owner = getOwner();
		cancelCurrentSkill(null);
		owner.getEffectController().removeAllEffects();
		DropService.getInstance().unregisterDrop(owner);
		owner.getAi().onGeneralEvent(AIEventType.DESPAWNED);
		getOwner().getObserveController().clear();
		super.onDespawn();
	}

	@Override
	public void onDie(Creature lastAttacker) {
		Npc owner = getOwner();
		if (owner.getSpawn().hasPool())
			owner.getSpawn().setUse(owner.getInstanceId(), false);

		boolean shouldDecay = true;
		boolean shouldRespawn = true;
		boolean shouldLoot = true;
		try {
			shouldDecay = owner.getAi().ask(AIQuestion.SHOULD_DECAY);
			shouldRespawn = owner.getAi().ask(AIQuestion.SHOULD_RESPAWN);
			shouldLoot = owner.getAi().ask(AIQuestion.SHOULD_LOOT);
			if (owner.getAi().ask(AIQuestion.SHOULD_REWARD))
				doReward();
			owner.getPosition().getWorldMapInstance().getInstanceHandler().onDie(owner);
			owner.getAi().onGeneralEvent(AIEventType.DIED);
		} catch (Exception e) {
			log.error("onDie() exception for " + owner + ":", e);
		}

		super.onDie(lastAttacker);

		if (shouldRespawn && SiegeService.getInstance().isRespawnAllowed(owner))
			RespawnService.scheduleRespawn(getOwner());

		if (shouldDecay) {
			if (shouldLoot)
				petLoot(owner);
			RespawnService.scheduleDecayTask(owner);
		} else { // instant despawn (no decay time = no loot)
			delete();
		}
	}

	private void petLoot(Npc owner) {
		Pet lootingPet = findPetForLooting(owner);
		if (lootingPet != null && PositionUtil.isInRange(owner, lootingPet.getMaster(), 28, false)) {
			int npcObjId = owner.getObjectId();
			Set<DropItem> drops = DropRegistrationService.getInstance().getCurrentDropMap().get(npcObjId);
			if (drops != null && !drops.isEmpty()) {
				PacketSendUtility.sendPacket(lootingPet.getMaster(), new SM_PET(PetSpecialFunction.AUTOLOOT, true, npcObjId));
				for (DropItem dropItem : drops.toArray(new DropItem[drops.size()])) // array copy since the drops get removed on retrieval
					DropService.getInstance().requestDropItem(lootingPet.getMaster(), npcObjId, dropItem.getIndex(), true);
				PacketSendUtility.sendPacket(lootingPet.getMaster(), new SM_PET(PetSpecialFunction.AUTOLOOT, false, npcObjId));
			}
		}
	}

	private Pet findPetForLooting(Npc npc) {
		DropNpc dropNpc = DropRegistrationService.getInstance().getDropRegistrationMap().get(npc.getObjectId());
		if (dropNpc == null) // npc didn't drop anything
			return null;
		if (dropNpc.getAllowedLooters().size() != 1) // auto looting is not available in FFA loot mode
			return null;
		Player player = World.getInstance().findPlayer(dropNpc.getAllowedLooters().iterator().next());
		if (player == null) // looter got disconnected
			return null;
		Pet pet = player.getPet();
		return pet != null && pet.getCommonData().isLooting() ? pet : null;
	}

	@Override
	public void doReward() {
		super.doReward();
		AggroList list = getOwner().getAggroList();
		Collection<AggroInfo> finalList = list.getFinalDamageList(true);
		AionObject winner = list.getMostDamage();

		if (winner == null) {
			return;
		}

		float totalDmg = 0;
		for (AggroInfo info : finalList) {
			totalDmg += info.getDamage();
		}

		if (totalDmg <= 0) {
			log.warn("WARN total damage to " + getOwner().getName() + " is " + totalDmg + " reward process was skiped!");
			return;
		}

		float instanceApMultiplier = 1f;
		if (getOwner().isInInstance()) {
			instanceApMultiplier = getOwner().getPosition().getWorldMapInstance().getInstanceHandler().getInstanceApMultiplier();
		}
		for (AggroInfo info : finalList) {
			AionObject attacker = info.getAttacker();

			if (attacker instanceof Npc) // don't reward npcs or summons
				continue;

			float percentage = info.getDamage() / totalDmg;
			if (percentage > 1) {
				log.warn("WARN BIG REWARD PERCENTAGE: " + percentage + " damage: " + info.getDamage() + " total damage: " + totalDmg + " name: "
					+ info.getAttacker().getName() + " obj: " + info.getAttacker().getObjectId() + " owner: " + getOwner().getName() + " player was skiped");
				continue;
			}
			if (attacker instanceof TemporaryPlayerTeam<?>) {
				PlayerTeamDistributionService.doReward((TemporaryPlayerTeam<?>) attacker, percentage, getOwner(), winner);
			} else if (attacker instanceof Player && ((Player) attacker).isInGroup()) {
				PlayerTeamDistributionService.doReward(((Player) attacker).getPlayerGroup(), percentage, getOwner(), winner);
			} else if (attacker instanceof Player) {
				Player player = (Player) attacker;
				if (!player.isDead()) {
					// Reward init
					long rewardXp = StatFunctions.calculateExperienceReward(player.getLevel(), getOwner());
					int rewardDp = StatFunctions.calculateDPReward(player, getOwner());
					float rewardAp = 1;

					// Dmg percent correction
					rewardXp *= percentage;
					rewardDp *= percentage;
					rewardAp *= percentage;
					rewardAp *= instanceApMultiplier;

					QuestEngine.getInstance().onKill(new QuestEnv(getOwner(), player, 0));
					EventService.getInstance().onPveKill(player, getOwner());
					player.getCommonData().addExp(rewardXp, Rates.XP_HUNTING, getOwner().getObjectTemplate().getL10n());
					player.getCommonData().addDp(rewardDp);
					if (getOwner().getAi().ask(AIQuestion.SHOULD_REWARD_AP)) {
						int calculatedAp = StatFunctions.calculatePvEApGained(player, getOwner());
						rewardAp *= calculatedAp;
						if (rewardAp >= 1) {
							AbyssPointsService.addAp(player, getOwner(), (int) rewardAp);
						}
					}
				}
				if (attacker.equals(winner) && getOwner().getAi().ask(AIQuestion.SHOULD_LOOT))
					DropRegistrationService.getInstance().registerDrop(getOwner(), player, player.getLevel(), null);
			}
		}
	}

	@Override
	public void onDialogRequest(Player player) {
		// notify npc dialog request observer
		if (!getOwner().getObjectTemplate().canInteract())
			return;
		if (!PositionUtil.isInTalkRange(player, getOwner())) {
			if (getOwner().getObjectTemplate().isDialogNpc())
				PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_DIALOG_TOO_FAR_TO_TALK());
			else
				PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_WAREHOUSE_TOO_FAR_FROM_NPC());
			return;
		}
		player.getObserveController().notifyRequestDialogObservers(getOwner());

		getOwner().getAi().onCreatureEvent(AIEventType.DIALOG_START, player);
	}

	@Override
	public void onDialogSelect(int dialogActionId, int prevDialogId, final Player player, int questId, int extendedRewardIndex) {
		QuestEnv env = new QuestEnv(getOwner(), player, questId, dialogActionId);
		if (!PositionUtil.isInTalkRange(player, getOwner()) && !QuestEngine.getInstance().onDialog(env))
			return;
		if (!getOwner().getAi().onDialogSelect(player, dialogActionId, questId, extendedRewardIndex)) {
			DialogService.onDialogSelect(dialogActionId, player, getOwner(), questId, extendedRewardIndex);
		}
	}

	@Override
	public void onAddHate(Creature attacker, boolean isNewInAggroList) {
		if (isNewInAggroList && attacker instanceof Player) {
			if (((Player) attacker).isInTeam()) {
				for (Player player : ((Player) attacker).getCurrentTeam().filterMembers(m -> PositionUtil.isInRange(getOwner(), m, 50)))
					QuestEngine.getInstance().onAddAggroList(new QuestEnv(getOwner(), player, 0));
			} else {
				QuestEngine.getInstance().onAddAggroList(new QuestEnv(getOwner(), (Player) attacker, 0));
			}
		}
		super.onAddHate(attacker, isNewInAggroList);
	}

	@Override
	public void onAttack(Creature attacker, int skillId, TYPE type, int damage, boolean notifyAttack, LOG logId, AttackStatus attackStatus,
		boolean allowGodstoneActivation) {
		if (getOwner().isDead())
			return;
		final Creature actingCreature;

		// summon should gain its own aggro (except if despawned, for example because of a damage over time effect)
		if (attacker instanceof Summon && attacker.isSpawned())
			actingCreature = attacker;
		else
			actingCreature = attacker.getActingCreature();

		super.onAttack(actingCreature, skillId, type, damage, notifyAttack, logId, attackStatus, allowGodstoneActivation);

		Npc npc = getOwner();
		ShoutEventHandler.onEnemyAttack((NpcAI) npc.getAi(), attacker);
		if (actingCreature instanceof Player)
			QuestEngine.getInstance().onAttack(new QuestEnv(npc, (Player) actingCreature, 0));
	}

	@Override
	public void onStartMove() {
		super.onStartMove();
		MoveTaskManager.getInstance().addCreature(getOwner());
	}

	@Override
	public void onStopMove() {
		super.onStopMove();
		MoveTaskManager.getInstance().removeCreature(getOwner());
	}

	@Override
	public void onEnterZone(ZoneInstance zoneInstance) {
		if (zoneInstance.getAreaTemplate().getZoneName() == null) {
			log.error("No name found for a Zone in the map " + zoneInstance.getAreaTemplate().getWorldId());
		}
	}

	@Override
	public boolean useSkill(int skillId, int skillLevel) {
		SkillTemplate skillTemplate = DataManager.SKILL_DATA.getSkillTemplate(skillId);
		if (!getOwner().isSkillDisabled(skillTemplate)) {
			getOwner().getGameStats().renewLastSkillTime();
			return super.useSkill(skillId, skillLevel);
		}
		return false;
	}

	public void loseAggro(boolean restoreHp) {
		getOwner().setTarget(null);
		getOwner().getAggroList().clear();
		if (restoreHp)
			getOwner().getLifeStats().triggerRestoreTask();
	}
}
