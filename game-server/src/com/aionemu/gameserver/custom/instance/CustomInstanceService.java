package com.aionemu.gameserver.custom.instance;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.database.dao.DAOManager;
import com.aionemu.gameserver.custom.instance.neuralnetwork.PlayerModelEntry;
import com.aionemu.gameserver.dao.CustomInstanceDAO;
import com.aionemu.gameserver.dao.CustomInstancePlayerModelEntryDAO;
import com.aionemu.gameserver.model.animations.TeleportAnimation;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Persistable;
import com.aionemu.gameserver.model.gameobjects.VisibleObject;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.instance.InstanceService;
import com.aionemu.gameserver.services.teleport.TeleportService;
import com.aionemu.gameserver.skillengine.model.Skill;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.time.ServerTime;
import com.aionemu.gameserver.world.WorldMapInstance;

/**
 * @author Jo, Estrayl
 */
public class CustomInstanceService {

	private final static List<Integer> restrictedSkills = Arrays.asList(0, 243, 244, 277, 282, 302, 912, 1178, 1327, 1346, 1347, 1757, 2106, 2167, 2400,
		2425, 2565, 2778, 3331, 3643, 3663, 3683, 3705, 3729, 3788, 3789, 3833, 3835, 3837, 3839, 3904, 3991, 4407, 8291, 10164, 11011, 13010, 13234,
		13231);

	private static final Logger log = LoggerFactory.getLogger("CUSTOM_INSTANCE_LOG");
	public static final int REWARD_COIN_ID = 186000409;
	private static final int CUSTOM_INSTANCE_WORLD_ID = 300070000; // roah chamber

	// Neural network related
	private Map<Integer, List<PlayerModelEntry>> playerModelEntriesCache = new ConcurrentHashMap<>();

	private CustomInstanceService() {
	}

	public boolean canEnter(int playerId) {
		CustomInstanceRank playerRankObject = DAOManager.getDAO(CustomInstanceDAO.class).loadPlayerRankObject(playerId);
		if (playerRankObject == null)
			return true;
		ZonedDateTime now = ServerTime.now();
		ZonedDateTime reUseTime = now.with(LocalTime.of(9, 0));
		if (now.isBefore(reUseTime))
			reUseTime = reUseTime.minusDays(1);
		return playerRankObject.getLastEntry() < reUseTime.toEpochSecond() * 1000;
	}

	public void onEnter(Player player) {
		if (!updateLastEntry(player.getObjectId(), System.currentTimeMillis())) {
			PacketSendUtility.sendMessage(player, "Sorry. Some shugo broke our database, please report this in our bugtracker :(");
			return;
		}
		playerModelEntriesCache.put(player.getObjectId(), loadPlayerModelEntries(player.getObjectId()));
		WorldMapInstance wmi = InstanceService.getNextAvailableInstance(CUSTOM_INSTANCE_WORLD_ID, 0, (byte) 1, new RoahCustomInstanceHandler());
		wmi.register(player.getObjectId());
		TeleportService.teleportTo(player, wmi.getMapId(), wmi.getInstanceId(), 504.0f, 396.0f, 94.0f, (byte) 30, TeleportAnimation.FADE_OUT_BEAM);
	}

	public CustomInstanceRank loadOrCreateRank(int playerId) {
		CustomInstanceRank customInstanceRank = DAOManager.getDAO(CustomInstanceDAO.class).loadPlayerRankObject(playerId);
		if (customInstanceRank == null)
			customInstanceRank = new CustomInstanceRank(playerId, 0, System.currentTimeMillis());
		return customInstanceRank;
	}

	public boolean updateLastEntry(int playerId, long newEntryTime) {
		CustomInstanceRank rankObj = loadOrCreateRank(playerId);
		rankObj.setLastEntry(newEntryTime);
		return DAOManager.getDAO(CustomInstanceDAO.class).storePlayer(rankObj);
	}

	public void changePlayerRank(int playerId, int newRank) {
		CustomInstanceRank rankObj = loadOrCreateRank(playerId);
		int oldRank = rankObj.getRank();
		rankObj.setRank(newRank);
		if (DAOManager.getDAO(CustomInstanceDAO.class).storePlayer(rankObj))
			log.info("[CI_ROAH] Changing instance rank for [playerId=" + playerId + "] from " + CustomInstanceRankEnum.getRankDescription(oldRank) + "("
				+ oldRank + ") to " + CustomInstanceRankEnum.getRankDescription(newRank) + "(" + newRank + ").");
	}

	public void recordPlayerModelEntry(Player player, Skill skill, VisibleObject target) {
		// FILTER: Only record roah custom instance skills for the moment
		if (player.getWorldId() != CUSTOM_INSTANCE_WORLD_ID || restrictedSkills.contains(skill.getSkillId()))
			return;

		WorldMapInstance wmi = player.getPosition().getWorldMapInstance();
		if (!(wmi.getInstanceHandler() instanceof RoahCustomInstanceHandler) || !((RoahCustomInstanceHandler) wmi.getInstanceHandler()).isBossPhase()
			|| !player.getPosition().getWorldMapInstance().isRegistered(player.getObjectId()))
			return;

		List<PlayerModelEntry> entries = getPlayerModelEntries(player.getObjectId());
		entries.add(new PlayerModelEntry(player, skill.getSkillId(), target instanceof Creature ? (Creature) target : null));
	}

	private List<PlayerModelEntry> loadPlayerModelEntries(int playerId) {
		return DAOManager.getDAO(CustomInstancePlayerModelEntryDAO.class).loadPlayerModelEntries(playerId);
	}

	public void saveNewPlayerModelEntries(int playerId) {
		List<PlayerModelEntry> pmes = playerModelEntriesCache.remove(playerId);
		if (pmes == null)
			return;
		Collection<PlayerModelEntry> filteredEntries = pmes.stream().filter(Persistable.NEW).collect(Collectors.toList());
		DAOManager.getDAO(CustomInstancePlayerModelEntryDAO.class).insertNewRecords(filteredEntries);
	}

	public List<PlayerModelEntry> getPlayerModelEntries(int playerId) {
		return playerModelEntriesCache.computeIfAbsent(playerId, k -> new ArrayList<>());
	}

	private static class SingletonHolder {

		protected static final CustomInstanceService instance = new CustomInstanceService();
	}

	public static CustomInstanceService getInstance() {
		return SingletonHolder.instance;
	}
}
