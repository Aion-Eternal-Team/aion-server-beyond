package com.aionemu.gameserver.custom.instance;

import static com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE.STR_MSG_INSTANCE_START_IDABRE;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.utils.Rnd;
import com.aionemu.gameserver.custom.instance.neuralnetwork.PlayerModel;
import com.aionemu.gameserver.custom.instance.neuralnetwork.PlayerModelController;
import com.aionemu.gameserver.instance.handlers.GeneralInstanceHandler;
import com.aionemu.gameserver.model.ChatType;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.drop.DropItem;
import com.aionemu.gameserver.model.flyring.FlyRing;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.PlayerCommonData;
import com.aionemu.gameserver.model.stats.calc.functions.StatSetFunction;
import com.aionemu.gameserver.model.stats.container.StatEnum;
import com.aionemu.gameserver.model.templates.flyring.FlyRingTemplate;
import com.aionemu.gameserver.model.utils3d.Point3D;
import com.aionemu.gameserver.network.aion.serverpackets.SM_DIE;
import com.aionemu.gameserver.network.aion.serverpackets.SM_MESSAGE;
import com.aionemu.gameserver.network.aion.serverpackets.SM_QUEST_ACTION;
import com.aionemu.gameserver.services.drop.DropRegistrationService;
import com.aionemu.gameserver.services.item.ItemService;
import com.aionemu.gameserver.services.player.PlayerService;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.world.WorldMapInstance;

/**
 * @author Jo, Estrayl
 */
public class RoahCustomInstanceHandler extends GeneralInstanceHandler {

	private static final Logger log = LoggerFactory.getLogger("CUSTOM_INSTANCE_LOG");

	private static final int AVG_DPS = 4000; // adjustable for debugging and testing

	private static final int CENTER_ARTIFACT_ID = 234029;
	private static final int TRASH_MOB_ID = 218483;
	private static final int BULKY_MOB_ID = 282757;
	private static final int DOMINATOR_MOB_ID = 284203;
	private static final int BOSS_MOB_A_M_ID = 231919; // asmodian male
	private static final int BOSS_MOB_A_F_ID = 231747; // asmodian female
	private static final int BOSS_MOB_E_M_ID = 231918; // elyos male
	private static final int BOSS_MOB_E_F_ID = 231717; // elyos female
	private static final int BOSS_MOB_AT_ID = 217221;

	private static final int TIME_LIMIT = 900; // 15 minutes

	private static final int REWARD_COIN_ID = 186000344;
	private static final int MIN_REWARD = 2;
	private static final float REWARD_SCALE = 2f;

	private AtomicLong startTime = new AtomicLong();
	private AtomicBoolean isInitialized = new AtomicBoolean();

	private int playerObjId;
	private PlayerModel model;
	private List<Integer> skillSet;

	private boolean isBossPhase;
	private int rank;
	private Future<?> despawnTask, trashMobSpawnTask, bulkyMobSpawnTask, dominatorMobSpawnTask;

	@Override
	public void onInstanceCreate(WorldMapInstance instance) {
		super.onInstanceCreate(instance);
		spawnRings();
		spawn(CENTER_ARTIFACT_ID, 504.1977f, 481.5051f, 87.2790f, (byte) 30);
		spawn(730588, 505.2431f, 377.0414f, 93.8944f, (byte) 30, 33); // Exit
	}

	private void spawnRings() {
		// transparent entry gate
		FlyRing f1 = new FlyRing(new FlyRingTemplate("ROAH_WING_1", mapId, new Point3D(501.77, 409.53, 94.12), new Point3D(503.93, 409.65, 98.9),
			new Point3D(506.26, 409.7, 94.15), 10), instanceId);
		f1.spawn();
	}

	@Override
	public boolean onPassFlyingRing(Player player, String flyingRing) {
		if (flyingRing.equals("ROAH_WING_1") && startTime.compareAndSet(0, System.currentTimeMillis())) {
			PacketSendUtility.broadcastToMap(instance, STR_MSG_INSTANCE_START_IDABRE());
			PacketSendUtility.broadcastToMap(instance, new SM_QUEST_ACTION(0, TIME_LIMIT));
			PacketSendUtility.broadcastToMap(instance, new SM_MESSAGE(0, null,
				"You have 15 minutes to demolish the central Aether Stone. Beware for its protectors!", ChatType.BRIGHT_YELLOW_CENTER));

			despawnTask = ThreadPoolManager.getInstance().schedule(() -> {
				setResult(false);
				despawnNpcs(CENTER_ARTIFACT_ID, TRASH_MOB_ID, BULKY_MOB_ID, DOMINATOR_MOB_ID, BOSS_MOB_A_M_ID, BOSS_MOB_A_F_ID, BOSS_MOB_E_M_ID,
					BOSS_MOB_E_F_ID, BOSS_MOB_AT_ID);
			}, TIME_LIMIT * 1000);

			// spawn bulky mob: 1/min after 1:20min
			bulkyMobSpawnTask = ThreadPoolManager.getInstance().scheduleAtFixedRate(new Runnable() {

				@Override
				public void run() {
					Npc bulky = null;
					for (Npc n : instance.getNpcs(BULKY_MOB_ID)) {
						if (!n.isDead() && !n.getLifeStats().isAboutToDie()) {
							bulky = n;
							break;
						}
					}
					if (bulky != null) {
						bulky.getLifeStats().setCurrentHp(bulky.getLifeStats().getMaxHp());
						PacketSendUtility.broadcastToMap(instance, new SM_MESSAGE(0, null, "Protector Vord recovered energy!", ChatType.BRIGHT_YELLOW_CENTER));
					} else {
						bulky = (Npc) spawn(BULKY_MOB_ID, 493.923f, 488.16794f, 87.18341f, (byte) 0);
						adaptNPC(bulky, rank);
						bulky.getAggroList().addHate(player, 1000);
						PacketSendUtility.broadcastToMap(instance, new SM_MESSAGE(0, null, "A tough protector has appeared!", ChatType.BRIGHT_YELLOW_CENTER));
					}
				}
			}, 80000, 60000);

			if (rank >= CustomInstanceRankEnum.BRONZE.getValue()) {
				// spawn trash mobs: 1 wave/min after 1min
				trashMobSpawnTask = ThreadPoolManager.getInstance().scheduleAtFixedRate(new Runnable() {

					@Override
					public void run() {
						int newTrash = 6;
						for (Npc n : instance.getNpcs(TRASH_MOB_ID)) // always spawn until 6 total
							if (!n.isDead())
								newTrash--;

						if (newTrash > 0) {
							for (int i = 0; i < newTrash; i++) {
								Npc mob = (Npc) spawn(TRASH_MOB_ID, 500.1f + Rnd.get() * 8, 460f + Rnd.get() * 4, 86.7112f, (byte) 30);
								adaptNPC(mob, rank);
								mob.getAggroList().addHate(player, 1000);
							}
						}
					}
				}, 60000, 60000 - rank * 1000); // 60s ... 30s
			}

			if (rank >= CustomInstanceRankEnum.SILVER.getValue()) {
				// spawn dominator mob: 1/min after 1:40min
				dominatorMobSpawnTask = ThreadPoolManager.getInstance().scheduleAtFixedRate(new Runnable() {

					@Override
					public void run() {
						Npc dominator = null;
						for (Npc n : instance.getNpcs(DOMINATOR_MOB_ID)) {
							if (!n.isDead() && !n.getLifeStats().isAboutToDie()) {
								dominator = n;
								break;
							}
						}
						if (dominator != null) {
							dominator.getLifeStats().setCurrentHp(dominator.getLifeStats().getMaxHp());
							PacketSendUtility.broadcastToMap(instance, new SM_MESSAGE(0, null, "Protector Vala recovered energy!", ChatType.BRIGHT_YELLOW_CENTER));

						} else {
							dominator = (Npc) spawn(DOMINATOR_MOB_ID, 515.23114f, 487.94998f, 87.176056f, (byte) 60);
							adaptNPC(dominator, rank);
							dominator.getAggroList().addHate(player, 1000);
							PacketSendUtility.broadcastToMap(instance, new SM_MESSAGE(0, null, "A vile protector has appeared!", ChatType.BRIGHT_YELLOW_CENTER));
						}
					}
				}, 100000, 60000);
			}

		}
		return false;
	}

	@Override
	public void onDie(Npc npc) {
		switch (npc.getNpcId()) {
			case CENTER_ARTIFACT_ID:
				cancelAllTasks();
				// use pcd for rare cases in which the artifact got destroyed by dots/servants and the player got DC'ed before
				PlayerCommonData pcd = PlayerService.getOrLoadPlayerCommonData(playerObjId);
				float usedTime = (System.currentTimeMillis() - startTime.get()) / 1000f;
				log.info("[CI_ROAH] Player [id=" + pcd.getPlayerObjId() + ", name=" + pcd.getName() + ", class=" + pcd.getPlayerClass() + ", rank="
					+ CustomInstanceRankEnum.getRankDescription(rank) + "(" + rank + ")] succeeded in destroying the artifact in " + usedTime + "s (DPS:"
					+ npc.getLifeStats().getMaxHp() / usedTime + ").");
				despawnNpcs(TRASH_MOB_ID, BULKY_MOB_ID, DOMINATOR_MOB_ID);

				PacketSendUtility.broadcastToMap(instance,
					new SM_MESSAGE(0, null, "You feel a shadowy presence from the throne room.", ChatType.BRIGHT_YELLOW_CENTER));

				PacketSendUtility.broadcastToMap(instance, new SM_QUEST_ACTION(0, 0));

				int bossID = BOSS_MOB_AT_ID;
				if (pcd.getPlayerClass() != PlayerClass.RIDER) {
					switch (pcd.getRace()) {
						case ASMODIANS:
							switch (pcd.getGender()) {
								case MALE:
									bossID = BOSS_MOB_A_M_ID;
									break;
								case FEMALE:
									bossID = BOSS_MOB_A_F_ID;
									break;
							}
							break;
						case ELYOS:
							switch (pcd.getGender()) {
								case MALE:
									bossID = BOSS_MOB_E_M_ID;
									break;
								case FEMALE:
									bossID = BOSS_MOB_E_F_ID;
									break;
							}
							break;
					}
				}
				spawn(bossID, 503.96774f, 631.935f, 104.548775f, (byte) 90); // spawn bossMob
				isBossPhase = true;
				npc.getController().delete();
				break;
			case BOSS_MOB_A_M_ID:
			case BOSS_MOB_A_F_ID:
			case BOSS_MOB_E_M_ID:
			case BOSS_MOB_E_F_ID:
			case BOSS_MOB_AT_ID:
				setResult(true);
				calcBossDrop(npc.getObjectId(), playerObjId);
				PacketSendUtility.broadcastToMap(instance, new SM_QUEST_ACTION(0, 0));
				break;
		}
	}

	@Override
	public void onEnterInstance(Player player) {
		if (isInitialized.compareAndSet(false, true)) {
			playerObjId = instance.getRegisteredObjects().iterator().next();
			rank = CustomInstanceService.getInstance().getPlayerRankObject(playerObjId).getRank();
			PacketSendUtility.broadcastToMap(instance, new SM_MESSAGE(0, null,
				"Welcome to the 'Eternal Challenge', " + CustomInstanceRankEnum.getRankDescription(rank) + " challenger!", ChatType.BRIGHT_YELLOW_CENTER));
			Npc artifact = getNpc(CENTER_ARTIFACT_ID);
			if (artifact != null) {
				adaptNPC(artifact, rank);
				spawnUnlockableNpcs(player);
			}
			// train player model from previous boss runs
			skillSet = PlayerModelController.getSkillSetForPlayer(playerObjId);
			model = PlayerModelController.trainModelForPlayer(playerObjId, skillSet);
		}
	}

	private void despawnNpcs(int... npcIds) {
		for (int npcId : npcIds) {
			for (Npc npc : instance.getNpcs(npcId)) {
				if (npc != null)
					npc.getController().delete();
			}
		}
	}

	private void calcBossDrop(int npcObjId, int playerId) {
		Set<DropItem> dropItems = DropRegistrationService.getInstance().getCurrentDropMap().get(npcObjId);
		if (DropRegistrationService.getInstance().getCurrentDropMap().get(npcObjId) != null)
			DropRegistrationService.getInstance().getCurrentDropMap().get(npcObjId).clear();
		int index = 0;
		dropItems.add(DropRegistrationService.getInstance().regDropItem(index++, playerId, npcObjId, REWARD_COIN_ID, getRewardCoinAmount(rank)));
	}

	private int getRewardCoinAmount(int rank) {
		return Math.round(MIN_REWARD + rank * REWARD_SCALE);
	}

	private void adaptNPC(Npc npc, int rank, Player player) {
		List<StatSetFunction> functions = new ArrayList<>();

		switch (npc.getNpcId()) { // IRON ... ANCIENT+
			case CENTER_ARTIFACT_ID: // ~5 ... 10min
				int maxHP = 300 * AVG_DPS + rank * 10 * AVG_DPS;
				if (rank > 21)
					maxHP += (rank - 21) * 150000;
				functions.add(new StatSetFunction(StatEnum.MAXHP, maxHP));
				break;
			case TRASH_MOB_ID: // ~1s fix (AoE-able)
				functions.add(new StatSetFunction(StatEnum.MAXHP, 1));
				functions.add(new StatSetFunction(StatEnum.SPEED, 6000 + rank * 100));
				break;
			case BULKY_MOB_ID: // ~10 ... 25s
				functions.add(new StatSetFunction(StatEnum.MAXHP, 10 * AVG_DPS + rank * AVG_DPS / 2));
				break;
			case DOMINATOR_MOB_ID: // ~10s fix
				functions.add(new StatSetFunction(StatEnum.MAXHP, 10 * AVG_DPS));
				functions.add(new StatSetFunction(StatEnum.PHYSICAL_ATTACK, 0)); // only debuffs, no dmg
				functions.add(new StatSetFunction(StatEnum.ATTACK_SPEED, 2000 - rank * 30));
				break;
		}

		npc.getGameStats().addEffect(null, functions);
		npc.getLifeStats().setCurrentHp(npc.getLifeStats().getMaxHp());
	}

	private void adaptNPC(Npc npc, int rank) {
		adaptNPC(npc, rank, null);
	}

	private void setResult(boolean success) {
		cancelAllTasks();
		if (success) {
			// upgrade rank
			rank++;
			PacketSendUtility.broadcastToMap(instance,
				new SM_MESSAGE(0, null, "Your rank has been increased to " + CustomInstanceRankEnum.getRankDescription(rank)
					+ ". Stay steadfast for tougher challenges and higher rewards!", ChatType.BRIGHT_YELLOW_CENTER));
		} else {
			// degrade rank
			if (rank >= 24)
				rank = 21; // to Ancient
			else
				rank = Math.max(0, (rank - 3) - (rank % 3)); // to 1st rank of last category
			PacketSendUtility.broadcastToMap(instance, new SM_MESSAGE(0, null,
				"Your rank has been decreased to " + CustomInstanceRankEnum.getRankDescription(rank) + ".", ChatType.BRIGHT_YELLOW_CENTER));

			if (rank > 0) // prevent suicide abuse || loss rewards
				ItemService.addItem(instance.getPlayer(playerObjId), REWARD_COIN_ID, getRewardCoinAmount(rank), true);
		}
		CustomInstanceService.getInstance().changePlayerRank(playerObjId, rank);
		CustomInstanceService.getInstance().saveNewPlayerModelEntries(playerObjId);
	}

	private void cancelAllTasks() {
		if (despawnTask != null)
			despawnTask.cancel(true);
		if (trashMobSpawnTask != null)
			trashMobSpawnTask.cancel(true);
		if (bulkyMobSpawnTask != null)
			bulkyMobSpawnTask.cancel(true);
		if (dominatorMobSpawnTask != null)
			dominatorMobSpawnTask.cancel(true);
	}

	private void spawnUnlockableNpcs(Player player) {
		if (rank >= CustomInstanceRankEnum.BRONZE.getValue()) // + General Goods Merchant
			if (player.getRace() == Race.ASMODIANS)
				spawn(205848, 510.84058f, 407.1751f, 93.91156f, (byte) 87);
			else
				spawn(205826, 510.84058f, 407.1751f, 93.91156f, (byte) 87);

		if (rank >= CustomInstanceRankEnum.SILVER.getValue()) // + Warehouse Manager
			spawn(205711, 496.6516f, 407.25f, 93.85523f, (byte) 99);

		if (rank >= CustomInstanceRankEnum.GOLD.getValue()) // + Legion Warehouse
			if (player.getRace() == Race.ASMODIANS)
				spawn(805253, 494.753f, 405.53464f, 93.87636f, (byte) 104);
			else
				spawn(805245, 494.753f, 405.53464f, 93.87636f, (byte) 104);

		if (rank >= CustomInstanceRankEnum.PLATINUM.getValue()) // + Mail
			if (player.getRace() == Race.ASMODIANS)
				spawn(700079, 499.6871f, 406.73828f, 93.84634f, (byte) 91);
			else
				spawn(700000, 499.6871f, 406.73828f, 93.84634f, (byte) 91);

		if (rank >= CustomInstanceRankEnum.MITHRIL.getValue()) // + Soul Healer
			spawn(205727, 513.6179f, 405.60223f, 93.91156f, (byte) 82);

		if (rank >= CustomInstanceRankEnum.CERANIUM.getValue()) // + Trade Broker
			if (player.getRace() == Race.ASMODIANS)
				spawn(205853, 516.24396f, 402.30844f, 93.91156f, (byte) 68);
			else
				spawn(798912, 516.24396f, 402.30844f, 93.91156f, (byte) 68);

		if (rank >= CustomInstanceRankEnum.ANCIENT.getValue()) // + Stigma Master
			if (player.getRace() == Race.ASMODIANS)
				spawn(800765, 493.39554f, 403.7841f, 93.88781f, (byte) 106);
			else
				spawn(800760, 493.39554f, 403.7841f, 93.88781f, (byte) 106);

		if (rank >= CustomInstanceRankEnum.ANCIENT_PLUS.getValue()) // + AP Shugo
			spawn(804833, 508.1798f, 407.87378f, 93.8523f, (byte) 89);
	}

	private int getElapsedTimeMillis() {
		return (int) (System.currentTimeMillis() - startTime.get());
	}

	@Override
	public boolean onReviveEvent(Player player) {
		PacketSendUtility.sendPacket(player, new SM_QUEST_ACTION(0, TIME_LIMIT * 1000 - getElapsedTimeMillis()));
		return super.onReviveEvent(player);
	}

	@Override
	public boolean onDie(Player player, Creature lastAttacker) {
		setResult(false);
		if (isBossPhase) {
			PacketSendUtility.sendMessage(player, "At last! I have become .. your greatest nightmare!", ChatType.BRIGHT_YELLOW_CENTER);
			despawnNpcs(BOSS_MOB_A_M_ID, BOSS_MOB_A_F_ID, BOSS_MOB_E_M_ID, BOSS_MOB_E_F_ID, BOSS_MOB_AT_ID);
		} else {
			PacketSendUtility.sendMessage(player, "You shall not pass!", ChatType.BRIGHT_YELLOW_CENTER);
			despawnNpcs(CENTER_ARTIFACT_ID, TRASH_MOB_ID, BULKY_MOB_ID, DOMINATOR_MOB_ID);
		}
		PacketSendUtility.sendPacket(player, new SM_DIE(false, false, 0, 0));
		return true;
	}

	public boolean isBossPhase() {
		return isBossPhase;
	}

	public PlayerModel getPlayerModel() {
		return model;
	}

	public List<Integer> getSkillSet() {
		return skillSet;
	}
}
