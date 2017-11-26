package com.aionemu.gameserver.questEngine;

import java.io.File;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import org.apache.commons.lang3.StringUtils;
import org.quartz.JobDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.scripting.classlistener.AggregatedClassListener;
import com.aionemu.commons.scripting.classlistener.OnClassLoadUnloadListener;
import com.aionemu.commons.scripting.classlistener.ScheduledTaskClassListener;
import com.aionemu.commons.scripting.scriptmanager.ScriptManager;
import com.aionemu.commons.services.CronService;
import com.aionemu.gameserver.GameServerError;
import com.aionemu.gameserver.configs.main.GSConfig;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.GameEngine;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.QuestTemplate;
import com.aionemu.gameserver.model.templates.factions.NpcFactionTemplate;
import com.aionemu.gameserver.model.templates.npc.NpcTemplate;
import com.aionemu.gameserver.model.templates.quest.HandlerSideDrop;
import com.aionemu.gameserver.model.templates.quest.InventoryItem;
import com.aionemu.gameserver.model.templates.quest.QuestCategory;
import com.aionemu.gameserver.model.templates.quest.QuestDrop;
import com.aionemu.gameserver.model.templates.quest.QuestItems;
import com.aionemu.gameserver.model.templates.quest.QuestNpc;
import com.aionemu.gameserver.model.templates.rewards.BonusType;
import com.aionemu.gameserver.network.aion.serverpackets.SM_QUEST_COMPLETED_LIST;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.handlers.HandlerResult;
import com.aionemu.gameserver.questEngine.handlers.QuestHandlerLoader;
import com.aionemu.gameserver.questEngine.handlers.models.XMLQuest;
import com.aionemu.gameserver.questEngine.handlers.template.AbstractTemplateQuestHandler;
import com.aionemu.gameserver.questEngine.model.QuestActionType;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.services.QuestService;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.utils.collections.ListSplitter;
import com.aionemu.gameserver.utils.stats.AbyssRankEnum;
import com.aionemu.gameserver.world.World;
import com.aionemu.gameserver.world.zone.ZoneName;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;

/**
 * @author MrPoke, Hilgert
 * @modified vlog, Neon
 */
public class QuestEngine implements GameEngine {

	private static final Logger log = LoggerFactory.getLogger(QuestEngine.class);
	private ScriptManager scriptManager = new ScriptManager();
	private JobDetail messageTask;
	private TIntObjectHashMap<AbstractQuestHandler> questHandlers = new TIntObjectHashMap<>();
	private TIntObjectHashMap<QuestNpc> questNpcs = new TIntObjectHashMap<>();
	private TIntObjectHashMap<TIntArrayList> questItemRelated = new TIntObjectHashMap<>();
	private TIntArrayList questHouseItems = new TIntArrayList();
	private TIntObjectHashMap<TIntArrayList> questItems = new TIntObjectHashMap<>();
	private TIntArrayList questOnCompleted = new TIntArrayList();
	private Map<Race, TIntArrayList> questOnLevelUp = new EnumMap<>(Race.class);
	private TIntArrayList questOnDie = new TIntArrayList();
	private TIntArrayList questOnLogOut = new TIntArrayList();
	private TIntArrayList questOnEnterWorld = new TIntArrayList();
	private Map<ZoneName, TIntArrayList> questOnEnterZone = new HashMap<>();
	private Map<ZoneName, TIntArrayList> questOnLeaveZone = new HashMap<>();
	private Map<String, TIntArrayList> questOnPassFlyingRings = new HashMap<>();
	private TIntObjectHashMap<TIntArrayList> questOnMovieEnd = new TIntObjectHashMap<>();
	private TIntArrayList questOnTimerEnd = new TIntArrayList();
	private TIntArrayList onInvisibleTimerEnd = new TIntArrayList();
	private Map<AbyssRankEnum, TIntArrayList> questOnKillRanked = new EnumMap<>(AbyssRankEnum.class);
	private TIntObjectHashMap<TIntArrayList> questOnKillInWorld = new TIntObjectHashMap<>();
	private TIntObjectHashMap<TIntArrayList> questOnUseSkill = new TIntObjectHashMap<>();
	private TIntIntHashMap questOnFailCraft = new TIntIntHashMap();
	private TIntObjectHashMap<TIntArrayList> questOnEquipItem = new TIntObjectHashMap<>();
	private TIntObjectHashMap<TIntArrayList> questCanAct = new TIntObjectHashMap<>();
	private TIntArrayList questOnDredgionReward = new TIntArrayList();
	private Map<BonusType, TIntArrayList> questOnBonusApply = new EnumMap<>(BonusType.class);
	private TIntArrayList questUpdateItems = new TIntArrayList();
	private TIntArrayList reachTarget = new TIntArrayList();
	private TIntArrayList lostTarget = new TIntArrayList();
	private TIntArrayList questOnEnterWindStream = new TIntArrayList();
	private TIntArrayList questRideAction = new TIntArrayList();
	private Map<String, TIntArrayList> questOnKillInZone = new HashMap<>();

	private QuestEngine() {
	}

	@Override
	public void load(CountDownLatch progressLatch) {
		log.info("Quest engine load started");

		for (QuestTemplate data : DataManager.QUEST_DATA.getQuestsData()) {
			for (QuestDrop drop : data.getQuestDrop()) {
				drop.setQuestId(data.getId());
				QuestService.addQuestDrop(drop.getNpcId(), drop);
			}
			if (data.getInventoryItems() != null) {
				for (InventoryItem inventoryItem : data.getInventoryItems().getInventoryItem()) {
					if (!questUpdateItems.contains(inventoryItem.getItemId()))
						questUpdateItems.add(inventoryItem.getItemId());
				}
			}
		}

		AggregatedClassListener acl = new AggregatedClassListener();
		acl.addClassListener(new OnClassLoadUnloadListener());
		acl.addClassListener(new ScheduledTaskClassListener());
		acl.addClassListener(new QuestHandlerLoader());
		scriptManager.setGlobalClassListener(acl);

		try {
			scriptManager.load(new File("./data/scripts/system/quest_handlers.xml"));
			for (XMLQuest xmlQuest : DataManager.XML_QUESTS.getAllQuests())
				xmlQuest.register(this);
			log.info("Loaded " + questHandlers.size() + " quest handlers.");
			if (GSConfig.ANALYZE_QUESTHANDLERS)
				analyzeQuestHandlers();
		} catch (Exception e) {
			throw new GameServerError("Can't initialize quest handlers.", e);
		} finally {
			if (progressLatch != null)
				progressLatch.countDown();
		}

		addMessageSendingTask();
	}

	public void reload() {
		shutdown();
		load(null);
	}

	@Override
	public void shutdown() {
		log.info("Quest engine shutdown started");
		scriptManager.shutdown();
		clear();
		log.info("Quest engine shutdown complete");
	}

	public void clear() {
		CronService.getInstance().cancel(messageTask);
		QuestService.clearQuestDrops();
		questNpcs.clear();
		questItemRelated.clear();
		questItems.clear();
		questHouseItems.clear();
		questOnLevelUp.clear();
		questOnCompleted.clear();
		questOnEnterWorld.clear();
		questOnDie.clear();
		questOnLogOut.clear();
		questOnEnterZone.clear();
		questOnLeaveZone.clear();
		questOnMovieEnd.clear();
		questOnTimerEnd.clear();
		onInvisibleTimerEnd.clear();
		questOnPassFlyingRings.clear();
		questOnKillRanked.clear();
		questOnKillInWorld.clear();
		questOnKillInZone.clear();
		questOnUseSkill.clear();
		questOnFailCraft.clear();
		questOnEquipItem.clear();
		questCanAct.clear();
		questOnDredgionReward.clear();
		questOnBonusApply.clear();
		questUpdateItems.clear();
		reachTarget.clear();
		lostTarget.clear();
		questOnEnterWindStream.clear();
		questRideAction.clear();
		questHandlers.clear();
	}

	public boolean onDialog(QuestEnv env) {
		try {
			AbstractQuestHandler questHandler = null;
			if (env.getQuestId() != 0) {
				questHandler = getQuestHandlerByQuestId(env.getQuestId());
				if (questHandler != null)
					if (questHandler.onDialogEvent(env))
						return true;
					else {
						QuestTemplate qt = DataManager.QUEST_DATA.getQuestById(env.getQuestId());
						if (qt != null && qt.getCategory() == QuestCategory.CHALLENGE_TASK)
							PacketSendUtility.sendPacket(env.getPlayer(), SM_SYSTEM_MESSAGE.STR_MSG_QUEST_LIMIT_START_DAILY(9));
					}
			} else {
				Npc npc = (Npc) env.getVisibleObject();
				for (int questId : getQuestNpc(npc == null ? 0 : npc.getNpcId()).getOnTalkEvent()) {
					questHandler = getQuestHandlerByQuestId(questId);
					if (questHandler != null) {
						env.setQuestId(questId);
						if (questHandler.onDialogEvent(env))
							return true;
					}
				}
				env.setQuestId(0);
			}
		} catch (Exception ex) {
			log.error("QE: exception in onDialog", ex);
			return false;
		}
		return false;
	}

	public boolean onKill(QuestEnv env) {
		try {
			Npc npc = (Npc) env.getVisibleObject();
			for (int questId : getQuestNpc(npc.getNpcId()).getOnKillEvent()) {
				AbstractQuestHandler questHandler = getQuestHandlerByQuestId(questId);
				if (questHandler != null) {
					env.setQuestId(questId);
					questHandler.onKillEvent(env);
				}
			}
		} catch (Exception ex) {
			log.error("QE: exception in onKill", ex);
			return false;
		}
		return true;
	}

	public boolean onAttack(QuestEnv env) {
		try {
			Npc npc = (Npc) env.getVisibleObject();
			for (int questId : getQuestNpc(npc.getNpcId()).getOnAttackEvent()) {
				AbstractQuestHandler questHandler = getQuestHandlerByQuestId(questId);
				if (questHandler != null) {
					env.setQuestId(questId);
					questHandler.onAttackEvent(env);
				}
			}
		} catch (Exception ex) {
			log.error("QE: exception in onAttack", ex);
			return false;
		}
		return true;
	}

	public void sendCompletedQuests(Player player) {
		ListSplitter<QuestState> splittedQs = new ListSplitter<>(player.getQuestStateList().getCompletedQuests(), 1345, true);
		while (splittedQs.hasMore()) {
			int updateMode = splittedQs.isFirst() ? 0 : 1; // first packet sent resets players list
			PacketSendUtility.sendPacket(player, new SM_QUEST_COMPLETED_LIST(updateMode, splittedQs.getNext()));
		}
	}

	/**
	 * Notifies all quest handlers (which registered the event), that the player level changed
	 * 
	 * @param player
	 *          - The player who leveled up
	 */
	public void onLevelChanged(Player player) {
		try {
			TIntArrayList raceQuestsOnLevelUp = getOrCreateOnLevelUpForRace(player.getRace());
			for (int index = 0; index < raceQuestsOnLevelUp.size(); index++) {
				int questId = raceQuestsOnLevelUp.get(index);
				QuestState qs = player.getQuestStateList().getQuestState(questId);
				if (qs == null || qs.getStatus() != QuestStatus.COMPLETE) {
					AbstractQuestHandler questHandler = getQuestHandlerByQuestId(questId);
					if (questHandler != null)
						questHandler.onLevelChangedEvent(player);
				}
			}
		} catch (Exception ex) {
			log.error("QE: exception in onLevelChanged", ex);
		}
	}

	/**
	 * Notifies all quest handlers (which registered the event), that the quest with the specified ID completed
	 * 
	 * @param player
	 *          - Player who completed the quest
	 * @param questId
	 *          - The quest that the player completed
	 */
	public void onQuestCompleted(Player player, int questId) {
		try {
			QuestEnv env = new QuestEnv(null, player, questId);
			for (int index = 0; index < questOnCompleted.size(); index++) {
				AbstractQuestHandler questHandler = getQuestHandlerByQuestId(questOnCompleted.get(index));
				if (questHandler != null)
					questHandler.onQuestCompletedEvent(env);
			}
		} catch (Exception ex) {
			log.error("QE: exception in onQuestCompleted", ex);
		}
	}

	public void onDie(QuestEnv env) {
		try {
			for (int index = 0; index < questOnDie.size(); index++) {
				AbstractQuestHandler questHandler = getQuestHandlerByQuestId(questOnDie.get(index));
				if (questHandler != null) {
					env.setQuestId(questOnDie.get(index));
					questHandler.onDieEvent(env);
				}
			}
		} catch (Exception ex) {
			log.error("QE: exception in onDie", ex);
		}
	}

	public void onLogOut(QuestEnv env) {
		try {
			for (int index = 0; index < questOnLogOut.size(); index++) {
				AbstractQuestHandler questHandler = getQuestHandlerByQuestId(questOnLogOut.get(index));
				if (questHandler != null) {
					env.setQuestId(questOnLogOut.get(index));
					questHandler.onLogOutEvent(env);
				}
			}
		} catch (Exception ex) {
			log.error("QE: exception in onLogOut", ex);
		}
	}

	public void onNpcReachTarget(QuestEnv env) {
		try {
			for (int index = 0; index < reachTarget.size(); index++) {
				AbstractQuestHandler questHandler = getQuestHandlerByQuestId(reachTarget.get(index));
				if (questHandler != null) {
					env.setQuestId(reachTarget.get(index));
					questHandler.onNpcReachTargetEvent(env);
				}
			}
		} catch (Exception ex) {
			log.error("QE: exception in onNpcReachTarget", ex);
		}
	}

	public void onNpcLostTarget(QuestEnv env) {
		try {
			for (int index = 0; index < lostTarget.size(); index++) {
				AbstractQuestHandler questHandler = getQuestHandlerByQuestId(lostTarget.get(index));
				if (questHandler != null) {
					env.setQuestId(lostTarget.get(index));
					questHandler.onNpcLostTargetEvent(env);
				}
			}
		} catch (Exception ex) {
			log.error("QE: exception in onNpcLostTarget", ex);
		}
	}

	public void onPassFlyingRing(QuestEnv env, String flyRing) {
		try {
			TIntArrayList questIds = questOnPassFlyingRings.get(flyRing);
			if (questIds != null) {
				for (int index = 0; index < questIds.size(); index++) {
					AbstractQuestHandler questHandler = getQuestHandlerByQuestId(questIds.get(index));
					if (questHandler != null) {
						env.setQuestId(questIds.get(index));
						questHandler.onPassFlyingRingEvent(env, flyRing);
					}
				}
			}
		} catch (Exception ex) {
			log.error("QE: exception in onFlyRingPassEvent", ex);
		}
	}

	public void onEnterWorld(Player player) {
		try {
			for (int index = 0; index < questOnEnterWorld.size(); index++) {
				int questId = questOnEnterWorld.get(index);
				AbstractQuestHandler questHandler = getQuestHandlerByQuestId(questId);
				if (questHandler != null)
					questHandler.onEnterWorldEvent(new QuestEnv(null, player, questId));
			}
		} catch (Exception ex) {
			log.error("QE: exception in onEnterWorld", ex);
		}
	}

	public HandlerResult onItemUseEvent(QuestEnv env, Item item) {
		try {
			TIntArrayList questIds = questItemRelated.get(item.getItemId());
			if (questIds != null) {
				for (int index = 0; index < questIds.size(); index++) {
					AbstractQuestHandler questHandler = getQuestHandlerByQuestId(questIds.get(index));
					if (questHandler != null) {
						env.setQuestId(questIds.get(index));
						HandlerResult result = questHandler.onItemUseEvent(env, item);
						// allow other quests to process, the same item can be used in multiple quests
						if (result != HandlerResult.UNKNOWN)
							return result;
					}
				}
			}
			return HandlerResult.UNKNOWN;
		} catch (Exception ex) {
			log.error("QE: exception in onItemUseEvent", ex);
			return HandlerResult.FAILED;
		}
	}

	public void onHouseItemUseEvent(QuestEnv env) {
		try {
			for (int index = 0; index < questHouseItems.size(); index++) {
				AbstractQuestHandler questHandler = getQuestHandlerByQuestId(questHouseItems.get(index));
				if (questHandler != null) {
					env.setQuestId(questHouseItems.get(index));
					questHandler.onHouseItemUseEvent(env);
				}
			}
		} catch (Exception ex) {
			log.error("QE: exception in onHouseItemUseEvent", ex);
		}
	}

	public void onItemGet(Player player, int itemId) {
		TIntArrayList questIds = questItems.get(itemId);
		if (questIds != null) {
			for (int i = 0; i < questIds.size(); i++) {
				int questId = questItems.get(itemId).get(i);
				AbstractQuestHandler questHandler = getQuestHandlerByQuestId(questId);
				if (questHandler != null)
					questHandler.onGetItemEvent(new QuestEnv(null, player, questId));
			}
		}
		if (questUpdateItems.contains(itemId))
			player.getController().updateNearbyQuests();
	}

	public void onItemRemoved(Player player, int itemId) {
		if (questUpdateItems.contains(itemId))
			player.getController().updateNearbyQuests();
	}

	public boolean onKillRanked(QuestEnv env, AbyssRankEnum playerRank) {
		try {
			if (playerRank != null) {
				TIntArrayList questIds = questOnKillRanked.get(playerRank);
				if (questIds != null) {
					for (int index = 0; index < questIds.size(); index++) {
						AbstractQuestHandler questHandler = getQuestHandlerByQuestId(questIds.get(index));
						if (questHandler != null) {
							env.setQuestId(questIds.get(index));
							questHandler.onKillRankedEvent(env);
						}
					}
				}
			}
		} catch (Exception ex) {
			log.error("QE: exception in onKillRanked", ex);
			return false;
		}
		return true;
	}

	public boolean onKillInWorld(QuestEnv env, int worldId) {
		try {
			TIntArrayList questIds = questOnKillInWorld.get(worldId);
			if (questIds != null) {
				for (int i = 0; i < questIds.size(); i++) {
					AbstractQuestHandler questHandler = getQuestHandlerByQuestId(questIds.get(i));
					if (questHandler != null) {
						env.setQuestId(questIds.get(i));
						questHandler.onKillInWorldEvent(env);
					}
				}
			}
		} catch (Exception ex) {
			log.error("QE: exception in onKillInWorld", ex);
			return false;
		}
		return true;
	}

	public boolean onKillInZone(QuestEnv env, String zoneName) {
		try {
			TIntArrayList questIds = questOnKillInZone.get(zoneName);
			if (questIds != null) {
				for (int i = 0; i < questIds.size(); i++) {
					AbstractQuestHandler questHandler = getQuestHandlerByQuestId(questIds.get(i));
					if (questHandler != null) {
						env.setQuestId(questIds.get(i));
						questHandler.onKillInZoneEvent(env);
					}
				}
			}
		} catch (Exception ex) {
			log.error("QE: exception in onKillInZone", ex);
			return false;
		}
		return true;
	}

	public boolean onEnterZone(QuestEnv env, ZoneName zoneName) {
		try {
			TIntArrayList questIds = questOnEnterZone.get(zoneName);
			if (questIds != null) {
				for (int index = 0; index < questIds.size(); index++) {
					AbstractQuestHandler questHandler = getQuestHandlerByQuestId(questIds.get(index));
					if (questHandler != null) {
						env.setQuestId(questIds.get(index));
						questHandler.onEnterZoneEvent(env, zoneName);
					}
				}
			}
		} catch (Exception ex) {
			log.error("QE: exception in onEnterZone", ex);
			return false;
		}
		return true;
	}

	public boolean onLeaveZone(QuestEnv env, ZoneName zoneName) {
		try {
			TIntArrayList questIds = questOnLeaveZone.get(zoneName);
			if (questIds != null) {
				for (int i = 0; i < questIds.size(); i++) {
					AbstractQuestHandler questHandler = getQuestHandlerByQuestId(questIds.get(i));
					if (questHandler != null) {
						env.setQuestId(questIds.get(i));
						questHandler.onLeaveZoneEvent(env, zoneName);
					}
				}
			}
		} catch (Exception ex) {
			log.error("QE: exception in onLeaveZone", ex);
			return false;
		}
		return true;
	}

	public boolean onMovieEnd(QuestEnv env, int movieId) {
		try {
			TIntArrayList questIds = questOnMovieEnd.get(movieId);
			if (questIds != null) {
				for (int index = 0; index < questIds.size(); index++) {
					AbstractQuestHandler questHandler = getQuestHandlerByQuestId(questIds.get(index));
					if (questHandler != null) {
						env.setQuestId(questIds.get(index));
						if (questHandler.onMovieEndEvent(env, movieId))
							return true;
					}
				}
			}
		} catch (Exception ex) {
			log.error("QE: exception in onMovieEnd", ex);
		}
		return false;
	}

	public void onQuestTimerEnd(QuestEnv env) {
		try {
			for (int index = 0; index < questOnTimerEnd.size(); index++) {
				AbstractQuestHandler questHandler = getQuestHandlerByQuestId(questOnTimerEnd.get(index));
				if (questHandler != null) {
					env.setQuestId(questOnTimerEnd.get(index));
					questHandler.onQuestTimerEndEvent(env);
				}
			}
		} catch (Exception ex) {
			log.error("QE: exception in onQuestTimerEnd", ex);
		}
	}

	public void onInvisibleTimerEnd(QuestEnv env) {
		try {
			for (int index = 0; index < onInvisibleTimerEnd.size(); index++) {
				AbstractQuestHandler questHandler = getQuestHandlerByQuestId(onInvisibleTimerEnd.get(index));
				if (questHandler != null) {
					env.setQuestId(onInvisibleTimerEnd.get(index));
					questHandler.onInvisibleTimerEndEvent(env);
				}
			}
		} catch (Exception ex) {
			log.error("QE: exception in onInvisibleTimerEnd", ex);
		}
	}

	public boolean onUseSkill(QuestEnv env, int skillId) {
		try {
			TIntArrayList questIds = questOnUseSkill.get(skillId);
			if (questIds != null) {
				for (int i = 0; i < questIds.size(); i++) {
					AbstractQuestHandler questHandler = getQuestHandlerByQuestId(questIds.get(i));
					if (questHandler != null) {
						env.setQuestId(questIds.get(i));
						questHandler.onUseSkillEvent(env, skillId);
					}
				}
			}
		} catch (Exception ex) {
			log.error("QE: exception in onUseSkill", ex);
			return false;
		}
		return true;
	}

	public void onFailCraft(QuestEnv env, int itemId) {
		if (questOnFailCraft.containsKey(itemId)) {
			int questId = questOnFailCraft.get(itemId);
			AbstractQuestHandler questHandler = getQuestHandlerByQuestId(questId);
			if (questHandler != null) {
				if (env.getPlayer().getInventory().getItemCountByItemId(itemId) == 0) {
					env.setQuestId(questId);
					questHandler.onFailCraftEvent(env, itemId);
				}
			}
		}
	}

	public void onEquipItem(QuestEnv env, int itemId) {
		TIntArrayList questIds = questOnEquipItem.get(itemId);
		if (questIds != null) {
			for (int i = 0; i < questIds.size(); i++) {
				AbstractQuestHandler questHandler = getQuestHandlerByQuestId(questIds.get(i));
				if (questHandler != null) {
					env.setQuestId(questIds.get(i));
					questHandler.onEquipItemEvent(env, itemId);
				}
			}
		}
	}

	public boolean onCanAct(final QuestEnv env, int templateId, final QuestActionType questActionType, final Object... objects) {
		TIntArrayList questIds = questCanAct.get(templateId);
		if (questIds != null) {
			return !questIds.forEach(questId -> {
				AbstractQuestHandler questHandler = getQuestHandlerByQuestId(questId);
				if (questHandler != null) {
					env.setQuestId(questId);
					if (questHandler.onCanAct(env, questActionType, objects))
						return false; // Abort for
				}
				return true;
			});
		}
		return false;
	}

	public void onDredgionReward(QuestEnv env) {
		try {
			for (int index = 0; index < questOnDredgionReward.size(); index++) {
				AbstractQuestHandler questHandler = getQuestHandlerByQuestId(questOnDredgionReward.get(index));
				if (questHandler != null) {
					env.setQuestId(questOnDredgionReward.get(index));
					questHandler.onDredgionRewardEvent(env);
				}
			}
		} catch (Exception ex) {
			log.error("QE: exception in onDredgionReward", ex);
		}
	}

	public HandlerResult onBonusApplyEvent(QuestEnv env, BonusType bonusType, List<QuestItems> rewardItems) {
		try {
			TIntArrayList questIds = questOnBonusApply.get(bonusType);
			if (questIds != null) {
				for (int index = 0; index < questIds.size(); index++) {
					AbstractQuestHandler questHandler = getQuestHandlerByQuestId(questIds.get(index));
					if (questHandler != null) {
						env.setQuestId(questIds.get(index));
						return questHandler.onBonusApplyEvent(env, bonusType, rewardItems);
					}
				}
			}
			return HandlerResult.UNKNOWN;
		} catch (Exception ex) {
			log.error("QE: exception in onBonusApply", ex);
			return HandlerResult.FAILED;
		}
	}

	public boolean onAddAggroList(QuestEnv env) {
		try {
			Npc npc = (Npc) env.getVisibleObject();
			for (int questId : getQuestNpc(npc.getNpcId()).getOnAddAggroListEvent()) {
				AbstractQuestHandler questHandler = getQuestHandlerByQuestId(questId);
				if (questHandler != null) {
					env.setQuestId(questId);
					questHandler.onAddAggroListEvent(env);
				}
			}
		} catch (Exception ex) {
			log.error("QE: exception in onAddAggroList", ex);
			return false;
		}
		return true;
	}

	public boolean onAtDistance(QuestEnv env) {
		QuestNpc questNpc = null;
		Npc npc = (Npc) env.getVisibleObject();
		if (!questNpcs.containsKey(npc.getNpcId())) {
			return false;
		}
		questNpc = getQuestNpc(npc.getNpcId());
		if (getQuestNpc(npc.getNpcId()).getOnDistanceEvent().size() == 0)
			return false;
		Player player = env.getPlayer();
		if (!PositionUtil.isInRange(npc, player, questNpc.getQuestRange()))
			return false;
		try {
			for (int questId : questNpc.getOnDistanceEvent()) {
				AbstractQuestHandler questHandler = getQuestHandlerByQuestId(questId);
				if (questHandler != null) {
					env.setQuestId(questId);
					questHandler.onAtDistanceEvent(env);
				}
			}
		} catch (Exception ex) {
			log.error("QE: exception in onAtDistance", ex);
			return false;
		}
		return true;
	}

	public void onEnterWindStream(QuestEnv env, int loc) {
		try {
			for (int index = 0; index < questOnEnterWindStream.size(); index++) {
				AbstractQuestHandler questHandler = getQuestHandlerByQuestId(questOnEnterWindStream.get(index));
				if (questHandler != null) {
					env.setQuestId(questOnEnterWindStream.get(index));
					questHandler.onEnterWindStreamEvent(env, loc);
				}
			}
		} catch (Exception ex) {
			log.error("QE: exception in onWindStream", ex);
		}
	}

	public void rideAction(QuestEnv env, int itemId) {
		try {
			for (int index = 0; index < questRideAction.size(); index++) {
				AbstractQuestHandler questHandler = getQuestHandlerByQuestId(questRideAction.get(index));
				if (questHandler != null) {
					env.setQuestId(questRideAction.get(index));
					questHandler.rideAction(env, itemId);
				}
			}
		} catch (Exception ex) {
			log.error("QE: exception in rideAction", ex);
		}
	}

	public QuestNpc registerQuestNpc(int npcId) {
		if (!questNpcs.containsKey(npcId)) {
			questNpcs.put(npcId, new QuestNpc(npcId));
		}
		return questNpcs.get(npcId);
	}

	public QuestNpc registerQuestNpc(int npcId, int range) {
		if (!questNpcs.containsKey(npcId)) {
			questNpcs.put(npcId, new QuestNpc(npcId, range));
		}
		return questNpcs.get(npcId);
	}

	public void registerQuestItem(int itemId, int questId) {
		TIntArrayList itemRelatedQuests = questItemRelated.get(itemId);
		if (itemRelatedQuests == null) {
			itemRelatedQuests = new TIntArrayList();
			questItemRelated.put(itemId, itemRelatedQuests);
		}
		itemRelatedQuests.add(questId);
	}

	public void registerQuestHouseItem(int questId) {
		if (!questHouseItems.contains(questId))
			questHouseItems.add(questId);
	}

	public void registerOnGetItem(int itemId, int questId) {
		TIntArrayList questIds = questItems.get(itemId);
		if (questIds == null) {
			questIds = new TIntArrayList();
			questItems.put(itemId, questIds);
		}
		questIds.add(questId);
	}

	public void registerOnLevelChanged(int questId) {
		QuestTemplate template = DataManager.QUEST_DATA.getQuestById(questId);
		Race racePermitted = template.getRacePermitted();
		TIntArrayList quests = null;
		if (racePermitted == null) {
			quests = getOrCreateOnLevelUpForRace(Race.ASMODIANS);
			if (!quests.contains(questId))
				quests.add(questId);
			quests = getOrCreateOnLevelUpForRace(Race.ELYOS);
			if (!quests.contains(questId))
				quests.add(questId);
		} else {
			quests = getOrCreateOnLevelUpForRace(racePermitted);
			if (!quests.contains(questId))
				quests.add(questId);
		}
	}

	private TIntArrayList getOrCreateOnLevelUpForRace(Race race) {
		TIntArrayList quests = questOnLevelUp.get(race);
		if (quests == null) {
			quests = new TIntArrayList();
			questOnLevelUp.put(race, quests);
		}
		return quests;
	}

	public void registerOnQuestCompleted(int questId) {
		if (!questOnCompleted.contains(questId))
			questOnCompleted.add(questId);
	}

	public void registerOnEnterWorld(int questId) {
		if (!questOnEnterWorld.contains(questId))
			questOnEnterWorld.add(questId);
	}

	public void registerOnDie(int questId) {
		if (!questOnDie.contains(questId))
			questOnDie.add(questId);
	}

	public void registerOnLogOut(int questId) {
		if (!questOnLogOut.contains(questId))
			questOnLogOut.add(questId);
	}

	public void registerOnEnterZone(ZoneName zoneName, int questId) {
		TIntArrayList questIds = questOnEnterZone.get(zoneName);
		if (questIds == null) {
			questIds = new TIntArrayList();
			questOnEnterZone.put(zoneName, questIds);
		}
		questIds.add(questId);
	}

	public void registerOnKillInZone(String zone, int questId) {
		TIntArrayList questIds = questOnKillInZone.get(zone);
		if (questIds == null) {
			questIds = new TIntArrayList();
			questOnKillInZone.put(zone, questIds);
		}
		questIds.add(questId);
	}

	public void registerOnLeaveZone(ZoneName zoneName, int questId) {
		TIntArrayList questIds = questOnLeaveZone.get(zoneName);
		if (questIds == null) {
			questIds = new TIntArrayList();
			questOnLeaveZone.put(zoneName, questIds);
		}
		questIds.add(questId);
	}

	public void registerOnKillRanked(AbyssRankEnum playerRank, int questId) {
		for (AbyssRankEnum rank : AbyssRankEnum.values()) {
			if (rank.getId() >= playerRank.getId()) {
				TIntArrayList onKillRankedQuests = questOnKillRanked.get(rank);
				if (onKillRankedQuests == null) {
					onKillRankedQuests = new TIntArrayList();
					questOnKillRanked.put(rank, onKillRankedQuests);
				}
				onKillRankedQuests.add(questId);
			}
		}
	}

	public void registerOnKillInWorld(int worldId, int questId) {
		TIntArrayList questIds = questOnKillInWorld.get(worldId);
		if (questIds == null) {
			questIds = new TIntArrayList();
			questOnKillInWorld.put(worldId, questIds);
		}
		questIds.add(questId);
	}

	public void registerOnPassFlyingRings(String flyingRing, int questId) {
		TIntArrayList questIds = questOnPassFlyingRings.get(flyingRing);
		if (questIds == null) {
			questIds = new TIntArrayList();
			questOnPassFlyingRings.put(flyingRing, questIds);
		}
		questIds.add(questId);
	}

	public void registerOnMovieEndQuest(int movieId, int questId) {
		TIntArrayList questIds = questOnMovieEnd.get(movieId);
		if (questIds == null) {
			questIds = new TIntArrayList();
			questOnMovieEnd.put(movieId, questIds);
		}
		questIds.add(questId);
	}

	public void registerOnQuestTimerEnd(int questId) {
		if (!questOnTimerEnd.contains(questId))
			questOnTimerEnd.add(questId);
	}

	public void registerOnInvisibleTimerEnd(int questId) {
		if (!onInvisibleTimerEnd.contains(questId))
			onInvisibleTimerEnd.add(questId);
	}

	public void registerQuestSkill(int skillId, int questId) {
		TIntArrayList questIds = questOnUseSkill.get(skillId);
		if (questIds == null) {
			questIds = new TIntArrayList();
			questOnUseSkill.put(skillId, questIds);
		}
		questIds.add(questId);
	}

	public void registerOnFailCraft(int itemId, int questId) {
		questOnFailCraft.putIfAbsent(itemId, questId);
	}

	public void registerOnEquipItem(int itemId, int questId) {
		TIntArrayList questIds = questOnEquipItem.get(itemId);
		if (questIds == null) {
			questIds = new TIntArrayList();
			questOnEquipItem.put(itemId, questIds);
		}
		questIds.add(questId);
	}

	public boolean registerCanAct(int questId, int npcId) {
		NpcTemplate template = DataManager.NPC_DATA.getNpcTemplate(npcId);
		if (template == null) {
			log.warn("[QuestEngine] No such NPC template for " + npcId + " in Q" + questId);
			return false;
		}
		if ("quest_use_item".equals(template.getAi())) {
			TIntArrayList questNpcs = questCanAct.get(npcId);
			if (questNpcs == null) {
				questNpcs = new TIntArrayList();
				questCanAct.put(npcId, questNpcs);
			}
			questNpcs.add(questId);
			return true;
		}
		return false;
	}

	public void registerOnDredgionReward(int questId) {
		if (!questOnDredgionReward.contains(questId))
			questOnDredgionReward.add(questId);
	}

	public void registerOnBonusApply(int questId, BonusType bonusType) {
		TIntArrayList onBonusApplyQuests = questOnBonusApply.get(bonusType);
		if (onBonusApplyQuests == null) {
			onBonusApplyQuests = new TIntArrayList();
			questOnBonusApply.put(bonusType, onBonusApplyQuests);
		}
		onBonusApplyQuests.add(questId);
	}

	public void registerAddOnReachTargetEvent(int questId) {
		if (!reachTarget.contains(questId))
			reachTarget.add(questId);
	}

	public void registerAddOnLostTargetEvent(int questId) {
		if (!lostTarget.contains(questId))
			lostTarget.add(questId);
	}

	public void registerOnEnterWindStream(int questId) {
		if (!questOnEnterWindStream.contains(questId))
			questOnEnterWindStream.add(questId);
	}

	public void registerOnRide(int questId) {
		if (!questRideAction.contains(questId))
			questRideAction.add(questId);
	}

	public QuestNpc getQuestNpc(int npcId) {
		QuestNpc questNpc = questNpcs.get(npcId);
		if (questNpc != null) {
			return questNpc;
		}
		return new QuestNpc(npcId);
	}

	private AbstractQuestHandler getQuestHandlerByQuestId(int questId) {
		return questHandlers.get(questId);
	}

	public int getQuestHandlerCount() {
		return questHandlers.size();
	}

	public boolean isHaveHandler(int questId) {
		return questHandlers.containsKey(questId);
	}

	public void addQuestHandler(AbstractQuestHandler questHandler) {
		int questId = questHandler.getQuestId();
		if (questHandlers.putIfAbsent(questId, questHandler) != null)
			log.warn("Duplicate handler for quest: " + questId);
		else
			questHandler.register();
	}

	/** Add handler side drop (if not already in xml) */
	public void addHandlerSideQuestDrop(int questId, int npcId, int itemId, int amount, int chance) {
		HandlerSideDrop hsd = new HandlerSideDrop(questId, npcId, itemId, amount, chance);
		QuestService.addQuestDrop(hsd.getNpcId(), hsd);
	}

	public void addHandlerSideQuestDrop(int questId, int npcId, int itemId, int amount, int chance, int step) {
		HandlerSideDrop hsd = new HandlerSideDrop(questId, npcId, itemId, amount, chance, step);
		QuestService.addQuestDrop(hsd.getNpcId(), hsd);
	}

	private void analyzeQuestHandlers() {
		log.info("Analyzing quest handlers...");
		Set<Integer> unobtainableQuests = new HashSet<>();
		Set<Integer> factionIds = new HashSet<>();
		for (NpcFactionTemplate nft : DataManager.NPC_FACTIONS_DATA.getNpcFactionsData()) {
			if (nft.getNpcIds() == null || nft.getNpcIds().stream().anyMatch(npcId -> existsSpawnData(npcId)))
				factionIds.add(nft.getId());
		}
		StringBuilder obsoleteHandlers = new StringBuilder();
		for (AbstractQuestHandler qh : questHandlers.valueCollection()) {
			QuestTemplate qt = DataManager.QUEST_DATA.getQuestById(qh.getQuestId());
			if (qt.getMinlevelPermitted() == 99) {
				if (!(qh instanceof AbstractTemplateQuestHandler)) // since xml handlers aren't expensive, we ignore them. only log manually scripted handlers
					obsoleteHandlers.append("\n\tQuest ").append(qh.getQuestId()).append(" (minLvl=99, handler=").append(qh.getClass().getName()).append(")");
				unobtainableQuests.add(qh.getQuestId());
			} else if (qt.getNpcFactionId() > 0 && !factionIds.contains(qt.getNpcFactionId())) { // outdated or unimplemented npc faction
				if (!(qh instanceof AbstractTemplateQuestHandler)) // since xml handlers aren't expensive, we ignore them. only log manually scripted handlers
					obsoleteHandlers.append("\n\tQuest ").append(qh.getQuestId()).append(" (npcFactionId=").append(qt.getNpcFactionId()).append(", handler=")
						.append(qh.getClass().getName()).append(")");
				unobtainableQuests.add(qh.getQuestId());
			}
		}
		StringBuilder missingSpawns = new StringBuilder();
		for (int npcId : questNpcs.keys()) {
			if (!existsSpawnData(npcId)) { // if the npc doesn't appear in any spawn template (world, instance, base, siege, temporary, event, ...)
				Set<Integer> questIds = getQuestNpc(npcId).findAllRegisteredQuestIds();
				if (questIds.stream().allMatch(id -> unobtainableQuests.contains(id) || existsSpawnDataForAnyAlternativeNpc(id, npcId)))
					continue; // don't log unobtainable quests or if alternative npcs appear in spawn data (many quests support outdated + current npcs)
				missingSpawns.append("\n\tNpc ").append(npcId).append(" (quests: ").append(StringUtils.join(questIds, ", ")).append(")");
			}
		}
		if (obsoleteHandlers.length() > 0)
			log.warn("Possibly obsolete quest handlers (quests are not obtainable):{}", obsoleteHandlers.toString());
		if (missingSpawns.length() > 0)
			log.warn("Missing quest npc spawns:{}", missingSpawns.toString());
		if (obsoleteHandlers.length() == 0 && missingSpawns.length() == 0)
			log.info("Quest handler analysis finished without errors!");
		else
			log.info("Quest handler analysis finished (see above log messages for found errors)");
	}

	private boolean existsSpawnData(int npcId) {
		if (DataManager.SPAWNS_DATA.containsAnySpawnForNpc(npcId))
			return true;
		if (DataManager.TOWN_SPAWNS_DATA.containsAnySpawnForNpc(npcId))
			return true;
		if (DataManager.EVENT_DATA.containsAnySpawnForNpc(npcId))
			return true;
		return false;
	}

	/**
	 * @param questId
	 * @param npcId
	 * @return True, if alternative npc ids, which are valid for this quest, appear in spawn templates (e.g. mobs for quest kills or talk npcs)
	 */
	private boolean existsSpawnDataForAnyAlternativeNpc(int questId, int npcId) {
		XMLQuest quest = DataManager.XML_QUESTS.getQuest(questId);
		if (quest == null)
			return true; // no way to get alternative npcs from non-xml based handlers, so assume the quest spawns work (lol)
		Set<Integer> alternativeNpcs = quest.getAlternativeNpcs(npcId);
		if (alternativeNpcs == null)
			return false;
		return alternativeNpcs.stream().anyMatch(npc -> existsSpawnData(npc));
	}

	private void addMessageSendingTask() {
		messageTask = CronService.getInstance().schedule(() -> {
			World.getInstance().forEachPlayer(player -> {
				boolean daily = false, weekly = false;
				for (QuestState qs : player.getQuestStateList().getCompletedQuests()) {
					if (qs.canRepeat()) {
						QuestTemplate template = DataManager.QUEST_DATA.getQuestById(qs.getQuestId());
						if (!daily && template.isDaily())
							daily = true;
						else if (!weekly && template.isWeekly())
							weekly = true;
						if (daily && weekly)
							break;
					}
				}
				if (daily)
					PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_MSG_QUEST_LIMIT_RESET_DAILY());
				if (weekly)
					PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_MSG_QUEST_LIMIT_RESET_WEEK());
				if (daily || weekly)
					player.getController().updateNearbyQuests();
				player.getNpcFactions().sendDailyQuest();
			});
		}, "0 0 9 ? * *");
	}

	public static QuestEngine getInstance() {
		return SingletonHolder.instance;
	}

	private static class SingletonHolder {

		protected static final QuestEngine instance = new QuestEngine();
	}
}
