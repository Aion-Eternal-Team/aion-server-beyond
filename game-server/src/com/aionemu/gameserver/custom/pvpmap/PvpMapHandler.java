package com.aionemu.gameserver.custom.pvpmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.aionemu.commons.services.CronService;
import com.aionemu.commons.utils.Rnd;
import com.aionemu.gameserver.ai.AIEngine;
import com.aionemu.gameserver.configs.main.CustomConfig;
import com.aionemu.gameserver.controllers.NpcController;
import com.aionemu.gameserver.controllers.effect.EffectController;
import com.aionemu.gameserver.controllers.observer.ActionObserver;
import com.aionemu.gameserver.controllers.observer.ItemUseObserver;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.instance.handlers.GeneralInstanceHandler;
import com.aionemu.gameserver.model.ChatType;
import com.aionemu.gameserver.model.EmotionType;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.TaskId;
import com.aionemu.gameserver.model.actions.PlayerMode;
import com.aionemu.gameserver.model.animations.TeleportAnimation;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.StaticDoor;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.state.CreatureState;
import com.aionemu.gameserver.model.siege.FortressLocation;
import com.aionemu.gameserver.model.templates.npc.NpcTemplate;
import com.aionemu.gameserver.model.templates.spawns.SpawnTemplate;
import com.aionemu.gameserver.network.aion.serverpackets.SM_BIND_POINT_TELEPORT;
import com.aionemu.gameserver.network.aion.serverpackets.SM_DIE;
import com.aionemu.gameserver.network.aion.serverpackets.SM_EMOTION;
import com.aionemu.gameserver.network.aion.serverpackets.SM_MESSAGE;
import com.aionemu.gameserver.network.aion.serverpackets.SM_MOTION;
import com.aionemu.gameserver.network.aion.serverpackets.SM_PLAYER_INFO;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.services.PvpService;
import com.aionemu.gameserver.services.SiegeService;
import com.aionemu.gameserver.services.instance.InstanceService;
import com.aionemu.gameserver.services.player.PlayerReviveService;
import com.aionemu.gameserver.services.teleport.BindPointTeleportService;
import com.aionemu.gameserver.services.teleport.TeleportService;
import com.aionemu.gameserver.skillengine.effect.AbnormalState;
import com.aionemu.gameserver.spawnengine.SpawnEngine;
import com.aionemu.gameserver.spawnengine.StaticDoorSpawnManager;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.utils.idfactory.IDFactory;
import com.aionemu.gameserver.utils.stats.AbyssRankEnum;
import com.aionemu.gameserver.world.World;
import com.aionemu.gameserver.world.WorldMapInstance;
import com.aionemu.gameserver.world.WorldPosition;
import com.aionemu.gameserver.world.geo.GeoService;
import com.aionemu.gameserver.world.knownlist.NpcKnownList;
import com.aionemu.gameserver.world.zone.ZoneInstance;

/**
 * Created on 06.04.2016.
 * 
 * @author Yeats
 */
public class PvpMapHandler extends GeneralInstanceHandler {

	private static final int SHUGO_SPAWN_RATE = 30;
	private final Map<Integer, WorldPosition> origins = new HashMap<>();
	private final Map<Integer, Long> joinOrLeaveTime = new HashMap<>();
	private final List<WorldPosition> respawnLocations = new ArrayList<>(), treasurePositions = new ArrayList<>(), supplyPositions = new ArrayList<>(),
		keymasterPositions = new ArrayList<>();
	private final AtomicBoolean canJoin = new AtomicBoolean();
	private List<Future<?>> tasks = new ArrayList<>();
	private Future<?> supplyTask, despawnTask;
	private boolean randomBossAlive = false;
	private final int randomBossNpcIds[] = { 231196, 233740, 235759, 235765, 235763, 235767, 235771, 235619, 235620, 235621, 855822, 855843, 230857,
		230858, 277224, 855776, 219934, 219933, 235975, 855263, 231304 };

	public PvpMapHandler() {
		super();
		InstanceService.getNextAvailableInstance(301220000, 0, (byte) 0, this);
	}

	@Override
	public void onInstanceCreate(WorldMapInstance instance) {
		super.onInstanceCreate(instance);
		StaticDoorSpawnManager.spawnTemplate(mapId, instanceId);
		for (StaticDoor door : instance.getDoors().values()) {
			door.setOpen(true);
		}
		addRespawnLocations();
		startSupplyTask();
		spawnKeymasters();
		spawnTreasureChests();
		spawnNpcs();
		startRandomBossTask();
		canJoin.set(true);
	}

	private void spawnShugo(Player player) {
		if (canJoin.get() && Rnd.chance() < SHUGO_SPAWN_RATE) {
			Npc oldShugo = instance.getNpc(833543);
			if (oldShugo != null) {
				oldShugo.getController().delete();
			}
			double radian = Math.toRadians(PositionUtil.convertHeadingToAngle(player.getHeading()));
			float x = player.getX() + (float) (Math.cos(radian) * 2);
			float y = player.getY() + (float) (Math.sin(radian) * 2);
			float z = GeoService.getInstance().getZ(player.getWorldId(), x, y, player.getZ(), instanceId);
			if (Float.isNaN(z))
				z = player.getZ() + 0.5f;
			spawn(833543, x, y, z, PositionUtil.getHeadingTowards(x, y, player.getX(), player.getY()));
		}
	}

	// spawns a supply chest every ~6min if there are enough players on the map
	private void startSupplyTask() {
		supplyTask = ThreadPoolManager.getInstance().scheduleAtFixedRate(this::scheduleSupplySpawn, 120000, 400000);
	}

	private void scheduleSupplySpawn() {
		if (spawnAllowed()) {
			if (supplyPositions.isEmpty()) {
				addSupplyPositions();
			}
			final WorldPosition pos = Rnd.get(supplyPositions);
			supplyPositions.remove(pos);
			spawn(831980, pos.getX(), pos.getY(), pos.getZ(), pos.getHeading()); // flag
			sendMsg(SM_SYSTEM_MESSAGE.STR_MSG_GUARDLIGHTHERO_SPAWN_IDLDF5_UNDER_01_WAR(), 0);
			scheduleSupplyDespawn();
			ThreadPoolManager.getInstance().schedule(() -> {
				spawn(233192, pos.getX(), pos.getY(), pos.getZ(), pos.getHeading()); // chest
			}, 30000);
		}
	}

	private void spawnKeymasters() {
		addKeymasterPositions();
		int npcIds[] = { 219218, 219218, 219218, 219191, 219191, 219192, 219192, 219193 };
		for (int id : npcIds) {
			spawnKeymasterOrTreasureChest(id, true);
		}
	}

	private void spawnKeymasterOrTreasureChest(int npcId, boolean isKeymaster) {
		WorldPosition pos;
		if (isKeymaster) {
			pos = Rnd.get(keymasterPositions);
			keymasterPositions.remove(pos);
		} else {
			pos = Rnd.get(treasurePositions);
			treasurePositions.remove(pos);
		}
		spawn(npcId, pos.getX(), pos.getY(), pos.getZ(), pos.getHeading());
	}

	private void scheduleRespawn(int npcId, int time, final boolean isKeymaster) {
		tasks.add(ThreadPoolManager.getInstance().schedule(() -> spawnKeymasterOrTreasureChest(npcId, isKeymaster), time, TimeUnit.MINUTES));
	}

	private void spawnTreasureChests() {
		addTreasurePositions();
		int npcIds[] = { 701388, 701388, 701388, 701388, 701388, 701389, 701389, 701389, 701390, 701390 };
		for (int id : npcIds) {
			spawnKeymasterOrTreasureChest(id, false);
		}
	}

	private void startRandomBossTask() {
		CronService.getInstance().schedule(() -> {
			int bonus = World.getInstance().getAllPlayers().size() * 2;
			bonus = bonus > 30 ? 30 : bonus;
			if (Rnd.get(1, 100) <= (CustomConfig.PVP_MAP_RANDOM_BOSS_BASE_RATE + bonus)) {
				int npcId = randomBossNpcIds[Rnd.get(0, randomBossNpcIds.length - 1)];
				NpcTemplate template = DataManager.NPC_DATA.getNpcTemplate(npcId);
				SpawnTemplate spawn = SpawnEngine.newSingleTimeSpawn(mapId, npcId, 744.337f, 292.986f, 233.697f, (byte) 43);
				final Npc npc = new Npc(IDFactory.getInstance().nextId(), new NpcController(), spawn, template);
				AIEngine.getInstance().setupAI("modified_iron_wall_aggressive", npc);
				npc.setKnownlist(new NpcKnownList(npc));
				npc.setEffectController(new EffectController(npc));
				SpawnEngine.bringIntoWorld(npc, mapId, instanceId, spawn.getX(), spawn.getY(), spawn.getZ(), spawn.getHeading());
				scheduleRandomBossDespawn(npc);
				randomBossAlive = true;
				PacketSendUtility.broadcastToWorld(new SM_MESSAGE(0, null, "[PvP-Map] A powerful monster appeared.", ChatType.BRIGHT_YELLOW_CENTER),
					p -> p.getLevel() >= 60);
			}
		}, CustomConfig.PVP_MAP_RANDOM_BOSS_SCHEDULE);
	}

	private void scheduleRandomBossDespawn(final Npc npc) {
		tasks.add(ThreadPoolManager.getInstance().schedule(() -> {
			if (npc != null && !npc.getLifeStats().isAboutToDie() && !npc.getLifeStats().isAlreadyDead()) {
				npc.getController().delete();
				randomBossAlive = false;
			}
		}, 50, TimeUnit.MINUTES));
	}

	private void scheduleSupplyDespawn() {
		despawnTask = ThreadPoolManager.getInstance()
			.schedule(() -> instance.getNpcs().stream().filter(npc -> npc.getNpcId() == 831980 || npc.getNpcId() == 233192)
				.filter(npc -> !npc.isInState(CreatureState.DEAD)).forEach(npc -> npc.getController().delete()), 120000);
	}

	public void join(Player p) {
		if (canJoin(p)) {
			startTeleportation(p, false);
		}
	}

	private void startTeleportation(Player p, boolean isLeaving) {
		ActionObserver observer = getAllObserver(p);
		PacketSendUtility.broadcastPacket(p, new SM_BIND_POINT_TELEPORT(1, p.getObjectId(), 1, 0), true);
		p.getObserveController().attach(observer);

		p.getController().addTask(TaskId.SKILL_USE, ThreadPoolManager.getInstance().schedule(() -> {
			PacketSendUtility.broadcastPacket(p, new SM_BIND_POINT_TELEPORT(3, p.getObjectId(), 1, 0), true);
			ThreadPoolManager.getInstance().schedule(() -> {
				p.getObserveController().removeObserver(observer);
				p.getController().cancelTask(TaskId.SKILL_USE);
				if (!p.getController().isInCombat() && !p.getLifeStats().isAboutToDie() && !p.getLifeStats().isAlreadyDead()) {
					if (isLeaving) {
						removePlayer(p);
					} else {
						updateOrigin(p);
						updateJoinOrLeaveTime(p);
						InstanceService.registerPlayerWithInstance(instance, p);
						WorldPosition pos = Rnd.get(respawnLocations);
						TeleportService.teleportTo(p, pos.getMapId(), instanceId, pos.getX(), pos.getY(), pos.getZ(), pos.getHeading(),
							TeleportAnimation.BATTLEGROUND);
					}
				}
			}, 1000);
		}, 10000));
	}

	private ActionObserver getAllObserver(final Player p) {
		return new ItemUseObserver() {

			@Override
			public void abort() {
				BindPointTeleportService.cancelTeleport(p, 1);
			}
		};
	}

	private boolean canJoin(Player p) {
		if (p.isStaff()) {
			return true;
		} else if (!canJoin.get() || p.getController().hasScheduledTask(TaskId.SKILL_USE)) {
			PacketSendUtility.sendMessage(p, "You cannot enter the PvP-Map now.");
			return false;
		} else if (!checkState(p)) {
			PacketSendUtility.sendMessage(p, "You cannot enter the PvP-Map in your current state.");
			return false;
		} else if (joinOrLeaveTime.containsKey(p.getObjectId()) && ((System.currentTimeMillis() - joinOrLeaveTime.get(p.getObjectId())) < 120000)) {
			int timeInSeconds = (int) Math.ceil((120000 - (System.currentTimeMillis() - joinOrLeaveTime.get(p.getObjectId()))) / 1000f);
			PacketSendUtility.sendMessage(p, "You can reenter the PvP-Map in " + timeInSeconds + " second" + (timeInSeconds > 1 ? "s." : "."));
			return false;
		} else {
			return true;
		}
	}

	private boolean checkState(Player p) {
		return !p.getController().isInCombat() && !p.getLifeStats().isAboutToDie() && !p.getLifeStats().isAlreadyDead() && !p.isLooting()
			&& !p.isInGlidingState() && !p.isFlying() && !p.isUsingFlyTeleport() && !p.isInPlayerMode(PlayerMode.WINDSTREAM)
			&& !p.isInPlayerMode(PlayerMode.RIDE) && !p.hasStore() && p.getCastingSkill() == null
			&& !p.getEffectController().isInAnyAbnormalState(AbnormalState.CANT_ATTACK_STATE)
			&& !p.getEffectController().isInAnyAbnormalState(AbnormalState.ROOT);
	}

	private synchronized void updateOrigin(Player p) {
		origins.put(p.getObjectId(), p.getPosition());
	}

	private synchronized void updateJoinOrLeaveTime(Player p) {
		if (!p.isStaff())
			joinOrLeaveTime.put(p.getObjectId(), System.currentTimeMillis());
	}

	@Override
	public boolean onReviveEvent(Player player) {
		PlayerReviveService.revive(player, 100, 100, false, 0);
		PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_REBIRTH_MASSAGE_ME());
		player.getGameStats().updateStatsAndSpeedVisually();
		PacketSendUtility.sendPacket(player, new SM_PLAYER_INFO(player));
		PacketSendUtility.sendPacket(player, new SM_MOTION(player.getObjectId(), player.getMotions().getActiveMotions()));
		player.unsetResPosState();

		if (!canJoin.get() || respawnLocations.isEmpty()) {
			if (instance.getPlayer(player.getObjectId()) != null) {
				removePlayer(player);
			}
		} else {
			WorldPosition pos = Rnd.get(respawnLocations);
			TeleportService.teleportTo(player, pos.getMapId(), instanceId, pos.getX(), pos.getY(), pos.getZ(), pos.getHeading(),
				TeleportAnimation.BATTLEGROUND);
		}
		return true;
	}

	@Override
	public boolean onDie(final Player player, Creature lastAttacker) {
		if (canJoin.get()) {
			if (lastAttacker instanceof Player && !lastAttacker.equals(player)) {
				spawnShugo((Player) lastAttacker);
			}
			PvpService.getInstance().doReward(player, CustomConfig.PVP_MAP_AP_MULTIPLIER);
			announceDeath(player);
			PacketSendUtility.sendPacket(player, new SM_DIE(false, false, 0, 6));
		}
		return true;
	}

	@Override
	public void onDie(Npc npc) {
		switch (npc.getNpcId()) {
			case 233740:
			case 235759:
			case 235763:
			case 235767:
			case 235771:
			case 235619:
			case 235620:
			case 235621:
			case 855822:
			case 855843:
			case 230857:
			case 230858:
			case 855776:
			case 277224:
			case 219933:
			case 219934:
			case 235975:
			case 855263:
			case 231196:
			case 231304:
				randomBossAlive = false;
				break;
			case 219218: // keymaster chookuri
				keymasterPositions
					.add(new WorldPosition(mapId, npc.getSpawn().getX(), npc.getSpawn().getY(), npc.getSpawn().getZ(), npc.getSpawn().getHeading()));
				scheduleRespawn(npc.getNpcId(), Rnd.get(10, 15), true);
				break;
			case 219191: // keymaster zumita
			case 219192: // keymaster niksi
				keymasterPositions
					.add(new WorldPosition(mapId, npc.getSpawn().getX(), npc.getSpawn().getY(), npc.getSpawn().getZ(), npc.getSpawn().getHeading()));
				scheduleRespawn(npc.getNpcId(), Rnd.get(30, 50), true);
				break;
			case 219193: // keymaster dabra
				keymasterPositions
					.add(new WorldPosition(mapId, npc.getSpawn().getX(), npc.getSpawn().getY(), npc.getSpawn().getZ(), npc.getSpawn().getHeading()));
				scheduleRespawn(npc.getNpcId(), Rnd.get(110, 180), true);
				break;
		}
	}

	@Override
	public void handleUseItemFinish(Player player, Npc npc) {
		switch (npc.getNpcId()) {
			case 701388:
				treasurePositions
					.add(new WorldPosition(mapId, npc.getSpawn().getX(), npc.getSpawn().getY(), npc.getSpawn().getZ(), npc.getSpawn().getHeading()));
				scheduleRespawn(npc.getNpcId(), Rnd.get(10, 20), false);
				break;
			case 701389:
				treasurePositions
					.add(new WorldPosition(mapId, npc.getSpawn().getX(), npc.getSpawn().getY(), npc.getSpawn().getZ(), npc.getSpawn().getHeading()));
				scheduleRespawn(npc.getNpcId(), Rnd.get(30, 60), false);
				break;
			case 701390:
				treasurePositions
					.add(new WorldPosition(mapId, npc.getSpawn().getX(), npc.getSpawn().getY(), npc.getSpawn().getZ(), npc.getSpawn().getHeading()));
				scheduleRespawn(npc.getNpcId(), Rnd.get(120, 200), false);
				break;
		}
	}

	private void announceDeath(final Player player) {
		if (!player.isStaff() && player.getAbyssRank() != null) {
			int zoneNameId = getZoneNameId(player);
			if (zoneNameId > 0)
				PacketSendUtility.broadcastToMap(instance,
					SM_SYSTEM_MESSAGE.STR_ABYSS_ORDER_RANKER_DIE(player, AbyssRankEnum.getRankDescriptionId(player), zoneNameId));
			else
				PacketSendUtility.broadcastToMap(instance, SM_SYSTEM_MESSAGE.STR_ABYSS_ORDER_RANKER_DIE(player, AbyssRankEnum.getRankDescriptionId(player)));
		}
	}

	@Override
	public void onEnterInstance(Player player) {
		if (!player.isStaff()) {
			updateJoinOrLeaveTime(player);
			instance.forEachPlayer(p -> {
				if (!p.equals(player))
					PacketSendUtility.sendMessage(p, "A new player has joined!", ChatType.BRIGHT_YELLOW_CENTER);
			});
			PacketSendUtility.broadcastToWorld(new SM_MESSAGE(0, null, "An enemy has entered the PvP-Map!", ChatType.BRIGHT_YELLOW_CENTER),
				p -> p.getLevel() >= 60 && !p.isInInstance() && p.getRace() != player.getRace());
		}
	}

	@Override
	public void onLeaveInstance(Player player) {
		updateJoinOrLeaveTime(player);
	}

	@Override
	public void onPlayerLogin(Player player) {
		updateJoinOrLeaveTime(player);
	}

	@Override
	public void onPlayerLogOut(Player player) {
		updateJoinOrLeaveTime(player);
		removePlayer(player);
	}

	@Override
	public void onInstanceDestroy() {
		PvpMapService.getInstance().closeMap(instanceId);
		canJoin.set(false);
		cancelTasks();
		clearLists();
	}

	private void cancelTasks() {
		if (supplyTask != null && !supplyTask.isCancelled()) {
			supplyTask.cancel(true);
		}
		if (despawnTask != null && !despawnTask.isCancelled()) {
			despawnTask.cancel(true);
		}
		tasks.stream().filter(task -> task != null && !task.isCancelled()).forEach(task -> task.cancel(true));
	}

	private boolean spawnAllowed() {
		if (!canJoin.get())
			return false;
		byte asmodians = 0;
		byte elyos = 0;
		for (Player player : instance.getPlayersInside()) {
			if (player.isStaff()) {
				continue;
			} else if (player.getRace() == Race.ASMODIANS) {
				asmodians++;
			} else {
				elyos++;
			}
			if (asmodians > 1 && elyos > 1) {
				return true;
			}
		}
		return false;
	}

	public int getParticipantsSize() {
		int playerCount = 0;
		for (Player p : instance.getPlayersInside()) {
			if (!p.isStaff()) {
				playerCount++;
			}
		}
		return playerCount;
	}

	public int getInstanceId() {
		return instanceId;
	}

	public void removeAllPlayersAndStop() {
		canJoin.set(false);
		cancelTasks();
		instance.forEachPlayer(this::removePlayer);
		clearLists();
	}

	private void clearLists() {
		joinOrLeaveTime.clear();
		respawnLocations.clear();
		supplyPositions.clear();
		keymasterPositions.clear();
		treasurePositions.clear();
		origins.clear();
	}

	private synchronized void removePlayer(Player p) {
		updateJoinOrLeaveTime(p);
		if (p.getLifeStats().isAlreadyDead()) {
			PacketSendUtility.broadcastPacket(p, new SM_EMOTION(p, EmotionType.RESURRECT), true);
			PlayerReviveService.revive(p, 25, 25, true, 0);
		}
		WorldPosition position = origins.remove(p.getObjectId());
		if (position != null && !isAtVulnerableFortress(position.getMapId(), position.getX(), position.getY(), position.getZ())) {
			TeleportService.teleportTo(p, position);
		} else {
			TeleportService.moveToBindLocation(p);
		}
	}

	public boolean isAtVulnerableFortress(int worldId, float x, float y, float z) {
		FortressLocation fortress = SiegeService.getInstance().findFortress(worldId, x, y, z);
		return fortress != null && fortress.isVulnerable();
	}

	public boolean leave(Player p) {
		if (checkState(p) && !p.getController().hasScheduledTask(TaskId.SKILL_USE)) {
			startTeleportation(p, true);
			return true;
		}
		return false;
	}

	public boolean isOnMap(Creature creature) {
		return instance.getObject(creature.getObjectId()) != null;
	}

	public boolean isRandomBossAlive() {
		if (canJoin.get()) {
			return randomBossAlive;
		}
		return false;
	}

	private void spawnNpcs() {
		spawnAndSetRespawn(218544, 739.08954f, 743.7691f, 194.63808f, (byte) 69, 295);
		spawnAndSetRespawn(218544, 790.8744f, 522.2378f, 228.78705f, (byte) 90, 295);
		spawnAndSetRespawn(218544, 823.72784f, 548.57434f, 235.62047f, (byte) 77, 295);
		spawnAndSetRespawn(218544, 793.6772f, 596.01904f, 240.03558f, (byte) 8, 295);
		spawnAndSetRespawn(218544, 795.2162f, 584.24866f, 239.5659f, (byte) 0, 295);
		spawnAndSetRespawn(218544, 357.8682f, 479.70685f, 237.4225f, (byte) 99, 295);
		spawnAndSetRespawn(218544, 367.3182f, 485.55405f, 237.4225f, (byte) 108, 295);
		spawnAndSetRespawn(218544, 353.6212f, 427.49295f, 233.09781f, (byte) 96, 295);
		spawnAndSetRespawn(218544, 757.8844f, 748.44446f, 195.72215f, (byte) 71, 295);
		spawnAndSetRespawn(218544, 752.23975f, 757.58105f, 195.71138f, (byte) 69, 295);
		spawnAndSetRespawn(218544, 430.81192f, 758.72595f, 203.42834f, (byte) 14, 295);
		spawnAndSetRespawn(218544, 433.25076f, 779.33185f, 203.42834f, (byte) 108, 295);
		spawnAndSetRespawn(218544, 471.17935f, 762.66144f, 201.68672f, (byte) 3, 295);
		spawnAndSetRespawn(218544, 769.0259f, 781.72473f, 198.76245f, (byte) 79, 295);
		spawnAndSetRespawn(218544, 772.4939f, 762.43164f, 198.0455f, (byte) 70, 295);
		spawnAndSetRespawn(218544, 568.91833f, 508.94455f, 217.75f, (byte) 118, 295);
		spawnAndSetRespawn(218544, 595.42993f, 504.22293f, 217.66063f, (byte) 69, 295);
		spawnAndSetRespawn(218544, 656.7253f, 219.83476f, 238.48415f, (byte) 46, 295);
		spawnAndSetRespawn(218544, 653.7101f, 370.04272f, 239.61528f, (byte) 103, 295);
		spawnAndSetRespawn(218544, 762.97675f, 386.17636f, 242.0815f, (byte) 23, 295);
		spawnAndSetRespawn(218544, 826.2748f, 351.18307f, 243.75453f, (byte) 46, 295);
		spawnAndSetRespawn(218544, 781.19916f, 330.00568f, 253.43387f, (byte) 76, 295);
		spawnAndSetRespawn(218544, 698.00366f, 262.089f, 253.43388f, (byte) 15, 295);
		spawnAndSetRespawn(218544, 644.2379f, 408.7981f, 242.47498f, (byte) 103, 295);
		spawnAndSetRespawn(218544, 644.08527f, 290.91754f, 225.69778f, (byte) 9, 295);
		spawnAndSetRespawn(218544, 549.149f, 424.63623f, 222.66476f, (byte) 18, 295);
		spawnAndSetRespawn(218544, 655.04407f, 242.13554f, 225.69778f, (byte) 22, 295);
		spawnAndSetRespawn(218544, 457.68298f, 276.2121f, 246.71693f, (byte) 75, 295);
		spawnAndSetRespawn(218544, 684.6678f, 308.9507f, 225.69778f, (byte) 61, 295);
		spawnAndSetRespawn(218544, 449.93518f, 284.11102f, 245.73611f, (byte) 26, 295);
		spawnAndSetRespawn(218544, 401.42535f, 259.03088f, 253.28592f, (byte) 20, 295);
		spawnAndSetRespawn(218544, 423.5366f, 269.79514f, 247.5003f, (byte) 97, 295);
		spawnAndSetRespawn(218544, 798.90045f, 366.7033f, 230.98207f, (byte) 80, 295);
		spawnAndSetRespawn(218544, 438.80832f, 618.10834f, 214.52452f, (byte) 42, 295);
		spawnAndSetRespawn(218544, 429.44916f, 614.982f, 214.52452f, (byte) 40, 295);
		spawnAndSetRespawn(218544, 422.44595f, 647.92f, 214.52452f, (byte) 95, 295);
		spawnAndSetRespawn(218544, 576.42346f, 716.5756f, 205.78198f, (byte) 54, 295);
		spawnAndSetRespawn(218544, 317.8458f, 684.38007f, 212.99036f, (byte) 67, 295);
		spawnAndSetRespawn(218544, 309.91922f, 682.13055f, 212.75505f, (byte) 6, 295);
		spawnAndSetRespawn(218544, 239.23927f, 757.8234f, 201.60623f, (byte) 113, 295);
		spawnAndSetRespawn(218544, 252.59685f, 769.42523f, 201.95093f, (byte) 93, 295);
		spawnAndSetRespawn(218544, 546.30115f, 686.9413f, 205.5f, (byte) 46, 295);
		spawnAndSetRespawn(218544, 603.9977f, 876.9201f, 192.91196f, (byte) 89, 295);
		spawnAndSetRespawn(218544, 603.13055f, 864.87823f, 192.65533f, (byte) 89, 295);
		spawnAndSetRespawn(218544, 690.58f, 759.3537f, 182.375f, (byte) 55, 295);
		spawnAndSetRespawn(218544, 685.9591f, 746.5975f, 182.375f, (byte) 55, 295);
		spawnAndSetRespawn(218544, 721.9566f, 723.7783f, 189.375f, (byte) 67, 295);
		spawnAndSetRespawn(218544, 694.7766f, 730.0601f, 188.96326f, (byte) 0, 295);
		spawnAndSetRespawn(218544, 714.0073f, 739.8774f, 189.24994f, (byte) 75, 295);
		spawnAndSetRespawn(218544, 717.8495f, 731.7268f, 189.3656f, (byte) 70, 295);
		spawnAndSetRespawn(218547, 757.2226f, 709.5766f, 194.62617f, (byte) 43, 295);
		spawnAndSetRespawn(218547, 815.31647f, 537.01685f, 229.99895f, (byte) 70, 295);
		spawnAndSetRespawn(218547, 808.0359f, 568.60266f, 239.5f, (byte) 25, 295);
		spawnAndSetRespawn(218547, 813.6234f, 583.6792f, 239.39505f, (byte) 115, 295);
		spawnAndSetRespawn(218547, 340.38742f, 444.74323f, 234.125f, (byte) 10, 295);
		spawnAndSetRespawn(218547, 455.97104f, 778.36456f, 202.01328f, (byte) 96, 295);
		spawnAndSetRespawn(218547, 517.13416f, 764.3607f, 195.46646f, (byte) 118, 295);
		spawnAndSetRespawn(218547, 727.2865f, 779.1507f, 194.5f, (byte) 108, 295);
		spawnAndSetRespawn(218547, 607.4919f, 490.93564f, 217.90976f, (byte) 46, 295);
		spawnAndSetRespawn(218547, 607.90454f, 508.69162f, 218.125f, (byte) 68, 295);
		spawnAndSetRespawn(218547, 607.9011f, 520.136f, 217.875f, (byte) 14, 295);
		spawnAndSetRespawn(218547, 597.78455f, 480.55164f, 218.0f, (byte) 98, 295);
		spawnAndSetRespawn(218547, 633.9897f, 228.40494f, 238.07529f, (byte) 22, 295);
		spawnAndSetRespawn(218547, 620.4969f, 311.71567f, 236.74612f, (byte) 114, 295);
		spawnAndSetRespawn(218547, 640.37787f, 304.753f, 236.92535f, (byte) 54, 295);
		spawnAndSetRespawn(218547, 740.5545f, 388.09833f, 242.1149f, (byte) 33, 295);
		spawnAndSetRespawn(218547, 796.8819f, 396.5597f, 242.01622f, (byte) 83, 295);
		spawnAndSetRespawn(218547, 748.0393f, 322.6207f, 249.28568f, (byte) 43, 295);
		spawnAndSetRespawn(218547, 714.6477f, 285.88177f, 249.28185f, (byte) 42, 295);
		spawnAndSetRespawn(218547, 547.2966f, 433.86728f, 222.75f, (byte) 112, 295);
		spawnAndSetRespawn(218547, 638.9781f, 426.24887f, 242.47498f, (byte) 90, 295);
		spawnAndSetRespawn(218547, 673.12494f, 221.4442f, 225.69778f, (byte) 33, 295);
		spawnAndSetRespawn(218547, 461.76224f, 266.64352f, 246.5f, (byte) 60, 295);
		spawnAndSetRespawn(218547, 737.5307f, 352.08997f, 230.94298f, (byte) 43, 295);
		spawnAndSetRespawn(218547, 396.4398f, 282.85092f, 253.6672f, (byte) 85, 295);
		spawnAndSetRespawn(218547, 445.27808f, 268.4044f, 246.47473f, (byte) 79, 295);
		spawnAndSetRespawn(218547, 241.67007f, 743.7223f, 201.54707f, (byte) 22, 295);
		spawnAndSetRespawn(218547, 274.27524f, 735.2498f, 205.57104f, (byte) 22, 295);
		spawnAndSetRespawn(218547, 535.81146f, 693.7665f, 205.38959f, (byte) 3, 295);
		spawnAndSetRespawn(218547, 594.31903f, 854.5406f, 192.18222f, (byte) 101, 295);
		spawnAndSetRespawn(218547, 696.12354f, 705.2198f, 194.81421f, (byte) 8, 295);
		spawnAndSetRespawn(219189, 671.8091f, 737.7017f, 178.73135f, (byte) 43, 295);
		spawnAndSetRespawn(219189, 593.70734f, 547.63635f, 219.09225f, (byte) 7, 295);
		spawnAndSetRespawn(219189, 565.2322f, 812.2663f, 188.3649f, (byte) 105, 295);
		spawnAndSetRespawn(219189, 622.64264f, 825.0713f, 188.46423f, (byte) 69, 295);
		spawnAndSetRespawn(219166, 588.5865f, 546.58716f, 219.3217f, (byte) 68, 295);
		spawnAndSetRespawn(219190, 808.65955f, 464.9168f, 228.91623f, (byte) 48, 295);
		spawnAndSetRespawn(219190, 648.5453f, 381.37613f, 228.625f, (byte) 60, 295);
		spawnAndSetRespawn(219190, 669.57f, 402.74194f, 228.61024f, (byte) 32, 295);
		spawnAndSetRespawn(219190, 476.58044f, 681.6464f, 217.96188f, (byte) 38, 295);
		spawnAndSetRespawn(219195, 810.7329f, 453.15427f, 228.75f, (byte) 81, 295);
		spawnAndSetRespawn(219195, 725.1208f, 547.77325f, 233.37512f, (byte) 70, 295);
		spawnAndSetRespawn(219195, 458.33813f, 530.78235f, 222.37468f, (byte) 49, 295);
		spawnAndSetRespawn(219195, 706.0179f, 490.12515f, 234.89288f, (byte) 100, 295);
		spawnAndSetRespawn(219195, 346.1327f, 655.7598f, 219.9216f, (byte) 52, 295);
		spawnAndSetRespawn(219195, 669.57355f, 774.0344f, 181.60016f, (byte) 93, 295);
		spawnAndSetRespawn(218545, 808.67224f, 525.32306f, 230.087f, (byte) 67, 295);
		spawnAndSetRespawn(218545, 825.1957f, 561.02435f, 239.06339f, (byte) 82, 295);
		spawnAndSetRespawn(218545, 799.7665f, 572.0052f, 239.54282f, (byte) 20, 295);
		spawnAndSetRespawn(218545, 809.45435f, 597.70325f, 239.5659f, (byte) 88, 295);
		spawnAndSetRespawn(218545, 820.66394f, 592.6436f, 239.41522f, (byte) 79, 295);
		spawnAndSetRespawn(218545, 796.40765f, 606.99194f, 239.88962f, (byte) 103, 295);
		spawnAndSetRespawn(218545, 662.76245f, 547.9632f, 238.60799f, (byte) 93, 295);
		spawnAndSetRespawn(218545, 372.4097f, 442.73737f, 234.19669f, (byte) 65, 295);
		spawnAndSetRespawn(218545, 348.93497f, 491.24512f, 239.26987f, (byte) 107, 295);
		spawnAndSetRespawn(218545, 732.9599f, 754.0701f, 194.79166f, (byte) 100, 295);
		spawnAndSetRespawn(218545, 403.51105f, 491.78015f, 233.7788f, (byte) 12, 295);
		spawnAndSetRespawn(218545, 745.4872f, 733.6486f, 194.79395f, (byte) 40, 295);
		spawnAndSetRespawn(218545, 447.26068f, 752.36f, 202.51537f, (byte) 21, 295);
		spawnAndSetRespawn(218545, 481.4032f, 769.2115f, 201.59654f, (byte) 113, 295);
		spawnAndSetRespawn(218545, 771.4963f, 720.42f, 194.5f, (byte) 53, 295);
		spawnAndSetRespawn(218545, 776.93744f, 742.47125f, 195.54028f, (byte) 80, 295);
		spawnAndSetRespawn(218545, 755.26324f, 776.8493f, 195.54028f, (byte) 86, 295);
		spawnAndSetRespawn(218545, 691.7244f, 638.8437f, 203.85924f, (byte) 33, 295);
		spawnAndSetRespawn(218545, 710.84985f, 672.1933f, 206.10843f, (byte) 49, 295);
		spawnAndSetRespawn(218545, 609.9847f, 498.69614f, 218.12404f, (byte) 61, 295);
		spawnAndSetRespawn(218545, 596.21906f, 522.6787f, 217.75f, (byte) 81, 295);
		spawnAndSetRespawn(218545, 621.87305f, 248.52107f, 236.5246f, (byte) 3, 295);
		spawnAndSetRespawn(218545, 643.08826f, 252.42151f, 236.9016f, (byte) 64, 295);
		spawnAndSetRespawn(218545, 737.86536f, 408.9785f, 242.11967f, (byte) 93, 295);
		spawnAndSetRespawn(218545, 789.3389f, 376.88342f, 242.2093f, (byte) 23, 295);
		spawnAndSetRespawn(218545, 788.5563f, 309.24756f, 253.43407f, (byte) 53, 295);
		spawnAndSetRespawn(218545, 713.9743f, 317.5257f, 252.13892f, (byte) 16, 295);
		spawnAndSetRespawn(218545, 721.78467f, 324.17868f, 252.13892f, (byte) 73, 295);
		spawnAndSetRespawn(218545, 724.86456f, 248.67912f, 253.43427f, (byte) 34, 295);
		spawnAndSetRespawn(218545, 566.43604f, 414.4239f, 222.67874f, (byte) 35, 295);
		spawnAndSetRespawn(218545, 788.57983f, 572.7258f, 239.375f, (byte) 104, 295);
		spawnAndSetRespawn(218545, 682.3254f, 272.88013f, 225.69778f, (byte) 49, 295);
		spawnAndSetRespawn(218545, 558.0802f, 435.9432f, 222.75f, (byte) 98, 295);
		spawnAndSetRespawn(218545, 692.28894f, 351.46298f, 228.7785f, (byte) 103, 295);
		spawnAndSetRespawn(218545, 455.10605f, 258.98337f, 246.5f, (byte) 43, 295);
		spawnAndSetRespawn(218545, 385.7541f, 270.29462f, 253.5f, (byte) 7, 295);
		spawnAndSetRespawn(218545, 815.5708f, 350.05713f, 230.98207f, (byte) 73, 295);
		spawnAndSetRespawn(218545, 446.28192f, 270.5887f, 246.46321f, (byte) 32, 295);
		spawnAndSetRespawn(218545, 313.05374f, 623.97473f, 230.95587f, (byte) 47, 295);
		spawnAndSetRespawn(218545, 283.17975f, 754.6061f, 203.93437f, (byte) 79, 295);
		spawnAndSetRespawn(218545, 533.2114f, 704.00757f, 205.625f, (byte) 7, 295);
		spawnAndSetRespawn(218545, 582.3275f, 696.94165f, 210.41594f, (byte) 72, 295);
		spawnAndSetRespawn(218545, 619.1518f, 878.12915f, 193.24794f, (byte) 69, 295);
		spawnAndSetRespawn(218545, 697.3136f, 750.4479f, 185.59483f, (byte) 53, 295);
		spawnAndSetRespawn(218545, 726.8993f, 742.47095f, 193.55424f, (byte) 69, 295);
		spawnAndSetRespawn(218545, 731.63965f, 733.70996f, 193.5039f, (byte) 69, 295);
		spawnAndSetRespawn(218549, 795.813f, 541.2819f, 229.90819f, (byte) 100, 295);
		spawnAndSetRespawn(218549, 795.4988f, 557.5386f, 240.98943f, (byte) 11, 295);
		spawnAndSetRespawn(218549, 808.0439f, 611.9442f, 239.5659f, (byte) 81, 295);
		spawnAndSetRespawn(218549, 833.02f, 599.4704f, 239.46935f, (byte) 86, 295);
		spawnAndSetRespawn(218549, 381.9712f, 458.69794f, 235.71736f, (byte) 49, 295);
		spawnAndSetRespawn(218549, 445.28845f, 785.9884f, 202.92975f, (byte) 84, 295);
		spawnAndSetRespawn(218549, 737.7692f, 793.7346f, 195.6785f, (byte) 85, 295);
		spawnAndSetRespawn(218549, 450.3595f, 767.3717f, 202.3945f, (byte) 107, 295);
		spawnAndSetRespawn(218549, 497.7351f, 762.2599f, 200.00084f, (byte) 11, 295);
		spawnAndSetRespawn(218549, 805.90955f, 737.58636f, 196.34206f, (byte) 66, 295);
		spawnAndSetRespawn(218549, 572.9683f, 490.3728f, 217.75f, (byte) 113, 295);
		spawnAndSetRespawn(218549, 584.0002f, 499.08987f, 217.75002f, (byte) 107, 295);
		spawnAndSetRespawn(218549, 630.12134f, 338.17273f, 236.74385f, (byte) 111, 295);
		spawnAndSetRespawn(218549, 710.1343f, 405.6931f, 241.98926f, (byte) 92, 295);
		spawnAndSetRespawn(218549, 809.4866f, 361.95856f, 241.92001f, (byte) 19, 295);
		spawnAndSetRespawn(218549, 777.9167f, 287.46475f, 253.43427f, (byte) 65, 295);
		spawnAndSetRespawn(218549, 576.3781f, 400.8725f, 222.4382f, (byte) 116, 295);
		spawnAndSetRespawn(218549, 741.08673f, 257.78488f, 253.4343f, (byte) 27, 295);
		spawnAndSetRespawn(218549, 685.96643f, 344.79675f, 245.26868f, (byte) 14, 295);
		spawnAndSetRespawn(218549, 701.34973f, 357.53714f, 245.2944f, (byte) 73, 295);
		spawnAndSetRespawn(218549, 692.49194f, 243.17621f, 227.16022f, (byte) 61, 295);
		spawnAndSetRespawn(218549, 643.6444f, 268.54294f, 225.69778f, (byte) 5, 295);
		spawnAndSetRespawn(218549, 560.1584f, 423.3512f, 222.625f, (byte) 17, 295);
		spawnAndSetRespawn(218549, 435.71008f, 255.26958f, 246.375f, (byte) 24, 295);
		spawnAndSetRespawn(218549, 702.438f, 373.9413f, 228.674f, (byte) 115, 295);
		spawnAndSetRespawn(218549, 805.08105f, 323.8884f, 230.98207f, (byte) 32, 295);
		spawnAndSetRespawn(218549, 556.52484f, 707.8591f, 206.58656f, (byte) 41, 295);
		spawnAndSetRespawn(218549, 566.68835f, 675.889f, 211.4778f, (byte) 33, 295);
		spawnAndSetRespawn(218549, 595.42f, 832.61285f, 188.6633f, (byte) 93, 295);
		spawnAndSetRespawn(218549, 610.9518f, 854.17535f, 192.2f, (byte) 75, 295);
		spawnAndSetRespawn(218549, 605.73267f, 832.3487f, 188.59225f, (byte) 94, 295);
		spawnAndSetRespawn(218549, 706.22314f, 756.8924f, 188.98705f, (byte) 76, 295);
		spawnAndSetRespawn(218549, 733.72406f, 719.50574f, 194.5f, (byte) 25, 295);
		spawnAndSetRespawn(218549, 717.5115f, 753.83606f, 194.45918f, (byte) 108, 295);
		spawnAndSetRespawn(218546, 807.58453f, 549.3488f, 238.99246f, (byte) 24, 295);
		spawnAndSetRespawn(218546, 831.0175f, 575.88043f, 239.375f, (byte) 55, 295);
		spawnAndSetRespawn(218546, 834.5515f, 587.1165f, 239.47925f, (byte) 66, 295);
		spawnAndSetRespawn(218546, 346.86578f, 460.7019f, 235.10414f, (byte) 106, 295);
		spawnAndSetRespawn(218546, 440.78027f, 531.0118f, 223.15675f, (byte) 0, 295);
		spawnAndSetRespawn(218546, 430.2271f, 769.12067f, 204.05684f, (byte) 119, 295);
		spawnAndSetRespawn(218546, 796.9759f, 722.1847f, 196.7664f, (byte) 31, 295);
		spawnAndSetRespawn(218546, 736.8544f, 768.3472f, 194.5534f, (byte) 87, 295);
		spawnAndSetRespawn(218546, 581.5761f, 481.5813f, 217.75f, (byte) 33, 295);
		spawnAndSetRespawn(218546, 581.79236f, 515.9131f, 217.75f, (byte) 98, 295);
		spawnAndSetRespawn(218546, 617.6701f, 275.5021f, 236.78897f, (byte) 4, 295);
		spawnAndSetRespawn(218546, 639.31323f, 280.07794f, 236.37653f, (byte) 63, 295);
		spawnAndSetRespawn(218546, 681.21075f, 391.91763f, 240.07115f, (byte) 104, 295);
		spawnAndSetRespawn(218546, 770.5945f, 406.5085f, 241.92291f, (byte) 82, 295);
		spawnAndSetRespawn(218546, 734.4841f, 343.54346f, 249.30322f, (byte) 87, 295);
		spawnAndSetRespawn(218546, 694.2494f, 308.95227f, 249.30322f, (byte) 1, 295);
		spawnAndSetRespawn(218546, 569.8869f, 435.66293f, 222.04213f, (byte) 17, 295);
		spawnAndSetRespawn(218546, 684.5185f, 260.11212f, 225.69778f, (byte) 76, 295);
		spawnAndSetRespawn(218546, 706.58984f, 334.94543f, 229.35587f, (byte) 44, 295);
		spawnAndSetRespawn(218546, 437.9635f, 277.951f, 246.375f, (byte) 105, 295);
		spawnAndSetRespawn(218546, 299.42566f, 722.23926f, 205.89642f, (byte) 74, 295);
		spawnAndSetRespawn(218546, 557.1041f, 691.66016f, 205.91748f, (byte) 27, 295);
		spawnAndSetRespawn(218546, 588.75586f, 871.6779f, 192.84047f, (byte) 110, 295);
		spawnAndSetRespawn(218546, 694.68243f, 715.9783f, 193.79282f, (byte) 1, 295);
		spawnAndSetRespawn(218546, 742.9393f, 709.2019f, 194.5f, (byte) 22, 295);
		spawnAndSetRespawn(218920, 670.0766f, 560.6434f, 229.34996f, (byte) 97, 295);
		spawnAndSetRespawn(218920, 685.3019f, 427.8387f, 229.82187f, (byte) 2, 295);
		spawnAndSetRespawn(218920, 757.7039f, 356.35028f, 232.42679f, (byte) 14, 295);
		spawnAndSetRespawn(218920, 618.3982f, 361.9023f, 224.94342f, (byte) 65, 295);
		spawnAndSetRespawn(218920, 517.9982f, 230.86314f, 231.92047f, (byte) 117, 295);
		spawnAndSetRespawn(218548, 467.81332f, 551.91583f, 216.59146f, (byte) 47, 295);
		spawnAndSetRespawn(218548, 489.4462f, 736.5246f, 209.70947f, (byte) 4, 295);
		spawnAndSetRespawn(218548, 266.49475f, 605.3711f, 223.27095f, (byte) 7, 295);
		spawnAndSetRespawn(218548, 576.5212f, 229.43967f, 232.6177f, (byte) 19, 295);
		spawnAndSetRespawn(218548, 703.4563f, 466.252f, 227.25f, (byte) 69, 295);
		spawnAndSetRespawn(218548, 520.92334f, 475.87265f, 216.5186f, (byte) 101, 295);
		spawnAndSetRespawn(218548, 309.666f, 650.7151f, 214.44623f, (byte) 31, 295);
		spawnAndSetRespawn(218548, 483.80945f, 689.0492f, 216.82463f, (byte) 39, 295);
		spawnAndSetRespawn(218548, 530.01953f, 721.6219f, 203.44101f, (byte) 38, 295);
		spawnAndSetRespawn(218548, 584.07904f, 794.46204f, 187.90817f, (byte) 61, 295);
		spawnAndSetRespawn(218548, 634.5419f, 739.6843f, 183.52533f, (byte) 19, 295);
		spawnAndSetRespawn(219167, 306.2718f, 449.41235f, 234.73024f, (byte) 58, 295);
		spawnAndSetRespawn(219167, 406.56827f, 561.6233f, 214.85794f, (byte) 24, 295);
		spawnAndSetRespawn(219167, 470.27045f, 382.566f, 241.05424f, (byte) 32, 295);
		spawnAndSetRespawn(219167, 570.3662f, 364.65964f, 226.03668f, (byte) 3, 295);
		spawnAndSetRespawn(219167, 491.91467f, 675.4431f, 220.99588f, (byte) 43, 295);
		spawnAndSetRespawn(219167, 477.62268f, 668.1604f, 220.99417f, (byte) 30, 295);
		spawnAndSetRespawn(219167, 604.9417f, 803.43274f, 186.88289f, (byte) 12, 295);
		spawnAndSetRespawn(219196, 417.46832f, 312.88907f, 233.34177f, (byte) 96, 295);
		spawnAndSetRespawn(219198, 378.59048f, 365.98883f, 226.03691f, (byte) 54, 295);
		spawnAndSetRespawn(219198, 487.1122f, 663.8798f, 221.72038f, (byte) 38, 295);
		spawnAndSetRespawn(219169, 642.6832f, 562.6061f, 229.95897f, (byte) 95, 295);
		spawnAndSetRespawn(219169, 616.85236f, 237.28638f, 229.79572f, (byte) 56, 295);
		spawnAndSetRespawn(219169, 629.52075f, 426.4336f, 226.93431f, (byte) 42, 295);
		spawnAndSetRespawn(219169, 484.21207f, 411.73413f, 233.46696f, (byte) 10, 295);
		spawnAndSetRespawn(219188, 804.1674f, 406.99316f, 232.52094f, (byte) 55, 295);
		spawnAndSetRespawn(219188, 676.3855f, 489.59888f, 226.12563f, (byte) 94, 295);
		spawnAndSetRespawn(219188, 299.4595f, 699.49084f, 207.92665f, (byte) 20, 295);
		spawnAndSetRespawn(219188, 259.73505f, 750.9975f, 201.32014f, (byte) 23, 295);
		spawnAndSetRespawn(219188, 266.61508f, 748.002f, 201.34828f, (byte) 24, 295);
		spawnAndSetRespawn(219188, 595.1314f, 781.1905f, 186.65916f, (byte) 89, 295);
		spawnAndSetRespawn(218551, 737.00867f, 431.07535f, 230.37608f, (byte) 69, 295);
		spawnAndSetRespawn(218551, 359.74063f, 661.96454f, 217.18471f, (byte) 23, 295);
		spawnAndSetRespawn(219194, 495.03677f, 439.18695f, 223.67601f, (byte) 108, 295);
		spawnAndSetRespawn(219194, 615.3467f, 265.0791f, 226.51422f, (byte) 65, 295);
		spawnAndSetRespawn(219194, 435.5179f, 681.1302f, 214.8404f, (byte) 109, 295);
		spawnAndSetRespawn(219194, 519.0116f, 780.06616f, 194.38846f, (byte) 104, 295);
		spawnAndSetRespawn(218550, 499.67398f, 422.29724f, 226.32646f, (byte) 53, 295);
		spawnAndSetRespawn(218550, 701.68555f, 410.91293f, 231.0f, (byte) 43, 295);
		spawnAndSetRespawn(218550, 647.8726f, 481.46146f, 226.34134f, (byte) 103, 295);
		spawnAndSetRespawn(218550, 451.62155f, 714.1448f, 213.3955f, (byte) 5, 295);
		spawnAndSetRespawn(219187, 581.25275f, 333.01978f, 227.84341f, (byte) 7, 295);
		spawnAndSetRespawn(219187, 438.07648f, 695.8468f, 215.41328f, (byte) 109, 295);
		spawnAndSetRespawn(219197, 591.01575f, 812.62335f, 186.81348f, (byte) 37, 295);
	}

	private void addRespawnLocations() {
		respawnLocations.clear();
		respawnLocations.add(new WorldPosition(mapId, 635.93634f, 321.30035f, 225.83354f, (byte) 55));
		respawnLocations.add(new WorldPosition(mapId, 676.42755f, 372.9569f, 241.59515f, (byte) 44));
		respawnLocations.add(new WorldPosition(mapId, 608.67224f, 160.21959f, 216.97983f, (byte) 75));
		respawnLocations.add(new WorldPosition(mapId, 714.85077f, 449.22028f, 229.7269f, (byte) 66));
		respawnLocations.add(new WorldPosition(mapId, 782.01263f, 324.34634f, 230.98207f, (byte) 72));
		respawnLocations.add(new WorldPosition(mapId, 780.9346f, 394.94186f, 231.91853f, (byte) 23));
		respawnLocations.add(new WorldPosition(mapId, 711.1087f, 631.0938f, 211.92676f, (byte) 36));
		respawnLocations.add(new WorldPosition(mapId, 621.7019f, 586.81824f, 207.56651f, (byte) 22));
		respawnLocations.add(new WorldPosition(mapId, 507.85767f, 748.89264f, 209.65799f, (byte) 95));
		respawnLocations.add(new WorldPosition(mapId, 619.7108f, 848.1294f, 194.60683f, (byte) 85));
		respawnLocations.add(new WorldPosition(mapId, 338.4661f, 624.9308f, 246.96532f, (byte) 46));
		respawnLocations.add(new WorldPosition(mapId, 345.87512f, 674.56555f, 215.51974f, (byte) 62));
		respawnLocations.add(new WorldPosition(mapId, 284.751f, 393.5447f, 238.20274f, (byte) 15));
		respawnLocations.add(new WorldPosition(mapId, 289.76865f, 549.3902f, 231.23553f, (byte) 90));
		respawnLocations.add(new WorldPosition(mapId, 537.4296f, 261.55344f, 232.69629f, (byte) 102));
		respawnLocations.add(new WorldPosition(mapId, 449.66992f, 449.04193f, 270.74738f, (byte) 75));
		respawnLocations.add(new WorldPosition(mapId, 620.88776f, 460.86237f, 224.73405f, (byte) 61));
		respawnLocations.add(new WorldPosition(mapId, 587.5866f, 679.81f, 211.82333f, (byte) 49));
		respawnLocations.add(new WorldPosition(mapId, 685.5478f, 766.70215f, 182.31891f, (byte) 73));
	}

	private void addSupplyPositions() {
		supplyPositions.clear();
		supplyPositions.add(new WorldPosition(mapId, 709.6463f, 313.6129f, 254.21637f, (byte) 14));
		supplyPositions.add(new WorldPosition(mapId, 749.5364f, 330.05954f, 233.81584f, (byte) 89));
		supplyPositions.add(new WorldPosition(mapId, 703.55786f, 292.23004f, 233.81587f, (byte) 119));
		supplyPositions.add(new WorldPosition(mapId, 612.4221f, 274.82172f, 235.73499f, (byte) 5));
		supplyPositions.add(new WorldPosition(mapId, 648.80536f, 253.2089f, 235.73445f, (byte) 62));
		supplyPositions.add(new WorldPosition(mapId, 772.5526f, 411.02084f, 241.0154f, (byte) 90));
		supplyPositions.add(new WorldPosition(mapId, 709.7187f, 411.01987f, 241.01144f, (byte) 92));
		supplyPositions.add(new WorldPosition(mapId, 655.7371f, 530.3507f, 226.47437f, (byte) 107));
		supplyPositions.add(new WorldPosition(mapId, 795.3396f, 532.70593f, 229.58707f, (byte) 115));
		supplyPositions.add(new WorldPosition(mapId, 646.19354f, 212.37506f, 223.40485f, (byte) 113));
		supplyPositions.add(new WorldPosition(mapId, 330.28857f, 390.94733f, 226.0986f, (byte) 31));
		supplyPositions.add(new WorldPosition(mapId, 389.6383f, 625.3936f, 214.52452f, (byte) 1));
		supplyPositions.add(new WorldPosition(mapId, 280.92044f, 645.77905f, 217.54143f, (byte) 98));
		supplyPositions.add(new WorldPosition(mapId, 452.21927f, 515.92175f, 223.16016f, (byte) 49));
		supplyPositions.add(new WorldPosition(mapId, 705.30096f, 654.2082f, 206.6876f, (byte) 48));
		supplyPositions.add(new WorldPosition(mapId, 513.3479f, 462.09848f, 216.95465f, (byte) 13));
		supplyPositions.add(new WorldPosition(mapId, 629.89185f, 860.046f, 190.88751f, (byte) 100));
		supplyPositions.add(new WorldPosition(mapId, 677.7658f, 714.2354f, 178.125f, (byte) 43));
		supplyPositions.add(new WorldPosition(mapId, 493.3107f, 764.61127f, 200.02097f, (byte) 2));
		supplyPositions.add(new WorldPosition(mapId, 726.9365f, 328.25638f, 254.21623f, (byte) 73));
		supplyPositions.add(new WorldPosition(mapId, 640.6047f, 413.0314f, 243.93956f, (byte) 103));
	}

	private void addKeymasterPositions() {
		keymasterPositions.clear();
		keymasterPositions.add(new WorldPosition(mapId, 354.27307f, 497.8957f, 239.26987f, (byte) 95));
		keymasterPositions.add(new WorldPosition(mapId, 419.7437f, 769.88983f, 205.1365f, (byte) 119));
		keymasterPositions.add(new WorldPosition(mapId, 259.40518f, 736.55725f, 201.33997f, (byte) 25));
		keymasterPositions.add(new WorldPosition(mapId, 793.8396f, 774.8803f, 200.86058f, (byte) 70));
		keymasterPositions.add(new WorldPosition(mapId, 604.94684f, 900.01465f, 195.53622f, (byte) 94));
		keymasterPositions.add(new WorldPosition(mapId, 820.80194f, 606.7696f, 239.70268f, (byte) 82));
		keymasterPositions.add(new WorldPosition(mapId, 552.9182f, 414.02127f, 222.76308f, (byte) 19));
		keymasterPositions.add(new WorldPosition(mapId, 395.32816f, 272.79468f, 253.375f, (byte) 108));
		keymasterPositions.add(new WorldPosition(mapId, 717.7881f, 320.78925f, 233.5026f, (byte) 102));
		keymasterPositions.add(new WorldPosition(mapId, 590.50385f, 506.48993f, 217.75f, (byte) 57));
		keymasterPositions.add(new WorldPosition(mapId, 667.1276f, 278.3438f, 225.69778f, (byte) 32));
		keymasterPositions.add(new WorldPosition(mapId, 817.77606f, 371.4041f, 243.45387f, (byte) 48));
		keymasterPositions.add(new WorldPosition(mapId, 781.83466f, 357.42792f, 230.98207f, (byte) 52));
		keymasterPositions.add(new WorldPosition(mapId, 630.30853f, 263.7316f, 238.48415f, (byte) 33));
		keymasterPositions.add(new WorldPosition(mapId, 490.17584f, 667.2109f, 221.4411f, (byte) 61));
		keymasterPositions.add(new WorldPosition(mapId, 423.2521f, 629.2255f, 214.52452f, (byte) 64));
	}

	private void addTreasurePositions() {
		treasurePositions.clear();
		treasurePositions.add(new WorldPosition(mapId, 644.363f, 222.8753f, 238.07552f, (byte) 20));
		treasurePositions.add(new WorldPosition(mapId, 822.94727f, 370.6611f, 243.34569f, (byte) 73));
		treasurePositions.add(new WorldPosition(mapId, 565.6456f, 396.60397f, 228.94838f, (byte) 26));
		treasurePositions.add(new WorldPosition(mapId, 778.3336f, 787.82886f, 198.75298f, (byte) 83));
		treasurePositions.add(new WorldPosition(mapId, 599.0367f, 567.8311f, 214.96388f, (byte) 25));
		treasurePositions.add(new WorldPosition(mapId, 614.5359f, 886.3159f, 193.82806f, (byte) 78));
		treasurePositions.add(new WorldPosition(mapId, 436.56647f, 754.596f, 202.92058f, (byte) 17));
		treasurePositions.add(new WorldPosition(mapId, 412.67242f, 645.81995f, 214.52452f, (byte) 92));
		treasurePositions.add(new WorldPosition(mapId, 353.569f, 504.86948f, 239.26987f, (byte) 92));
		treasurePositions.add(new WorldPosition(mapId, 567.02625f, 595.80725f, 209.19331f, (byte) 109));
		treasurePositions.add(new WorldPosition(mapId, 647.5166f, 543.63727f, 222.8279f, (byte) 67));
		treasurePositions.add(new WorldPosition(mapId, 803.04175f, 604.1839f, 239.5659f, (byte) 92));
		treasurePositions.add(new WorldPosition(mapId, 676.91064f, 785.8807f, 181.20055f, (byte) 83));
		treasurePositions.add(new WorldPosition(mapId, 585.99786f, 662.6794f, 211.93208f, (byte) 40));
		treasurePositions.add(new WorldPosition(mapId, 575.78815f, 850.86847f, 188.95987f, (byte) 105));
		treasurePositions.add(new WorldPosition(mapId, 464.98325f, 733.43646f, 212.67583f, (byte) 95));
		treasurePositions.add(new WorldPosition(mapId, 248.65541f, 756.76154f, 201.36113f, (byte) 8));
		treasurePositions.add(new WorldPosition(mapId, 373.7291f, 327.44672f, 228.25072f, (byte) 7));
		treasurePositions.add(new WorldPosition(mapId, 447.88635f, 257.91846f, 246.49289f, (byte) 26));
		treasurePositions.add(new WorldPosition(mapId, 636.1489f, 422.67554f, 242.47498f, (byte) 104));
		treasurePositions.add(new WorldPosition(mapId, 587.17395f, 507.7597f, 217.75f, (byte) 106));
		treasurePositions.add(new WorldPosition(mapId, 521.03375f, 542.86005f, 214.33388f, (byte) 70));
		treasurePositions.add(new WorldPosition(mapId, 726.05597f, 437.2465f, 229.625f, (byte) 76));
		treasurePositions.add(new WorldPosition(mapId, 731.07806f, 272.2283f, 233.4975f, (byte) 30));
		treasurePositions.add(new WorldPosition(mapId, 775.14264f, 300.74243f, 233.49748f, (byte) 63));
		treasurePositions.add(new WorldPosition(mapId, 735.2953f, 248.73598f, 253.43423f, (byte) 44));
		treasurePositions.add(new WorldPosition(mapId, 786.8716f, 291.9434f, 253.43422f, (byte) 46));
	}

	private int getZoneNameId(Player player) {
		for (ZoneInstance zone : player.findZones()) {
			if (!zone.getAreaTemplate().getZoneName().name().equalsIgnoreCase("301220000")) {
				return getZoneNameIdByZoneName(zone.getAreaTemplate().getZoneName().name());
			}
		}
		return 0;
	}

	private int getZoneNameIdByZoneName(String name) {
		switch (name) {
			case "ANCILLARY_SENTRY_POST_301220000":
				return 404085;
			case "ARTILLERY_COMMAND_CENTER_301220000":
				return 404088;
			case "ASSAULT_COMMAND_CENTER_301220000":
				return 404090;
			case "AXIAL_SENTRY_POST_301220000":
				return 404084;
			case "CENTRAL_SUPPLY_BASE_301220000":
				return 404086;
			case "HEADQUARTERS_301220000":
				return 404092;
			case "HEADQUARTERS_ANNEX_301220000":
				return 404093;
			case "HOLY_GROUND_OF_RESURRECTION_301220000":
				return 404094;
			case "MILITARY_SUPPLY_BASE_2_301220000":
				return 404089;
			case "PASHID_ARMY_ENCAMPMENT_301220000":
				return 404083;
			case "PERIPHERAL_SUPPLY_BASE_301220000":
				return 404087;
			case "SIEGE_BASE_301220000":
				return 404091;
			case "THE_ETERNAL_BASTION_301220000":
				return 404082;
			case "UNDERGROUND_WATERWAY_1_301220000":
				return 404095;
			default:
				return 0;
		}
	}

	@Override
	public float getInstanceApMultiplier() {
		return CustomConfig.PVP_MAP_PVE_AP_MULTIPLIER;
	}
}
