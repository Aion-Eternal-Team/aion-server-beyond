package instance;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.aionemu.commons.utils.Rnd;
import com.aionemu.gameserver.ai.NpcAI;
import com.aionemu.gameserver.ai.manager.WalkManager;
import com.aionemu.gameserver.instance.handlers.GeneralInstanceHandler;
import com.aionemu.gameserver.instance.handlers.InstanceID;
import com.aionemu.gameserver.model.DescriptionId;
import com.aionemu.gameserver.model.EmotionType;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.StaticDoor;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.state.CreatureState;
import com.aionemu.gameserver.model.instance.InstanceScoreType;
import com.aionemu.gameserver.model.instance.instancereward.NormalReward;
import com.aionemu.gameserver.model.templates.spawns.SpawnTemplate;
import com.aionemu.gameserver.network.aion.instanceinfo.NormalScoreInfo;
import com.aionemu.gameserver.network.aion.serverpackets.SM_DIE;
import com.aionemu.gameserver.network.aion.serverpackets.SM_EMOTION;
import com.aionemu.gameserver.network.aion.serverpackets.SM_INSTANCE_SCORE;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.services.abyss.AbyssPointsService;
import com.aionemu.gameserver.services.item.ItemService;
import com.aionemu.gameserver.services.player.PlayerReviveService;
import com.aionemu.gameserver.services.teleport.TeleportService;
import com.aionemu.gameserver.skillengine.SkillEngine;
import com.aionemu.gameserver.spawnengine.SpawnEngine;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.world.WorldMapInstance;

/**
 * @author Cheatkiller
 */
@InstanceID(300540000)
public class EternalBastionInstance extends GeneralInstanceHandler {

	private static final int START_DELAY = 180 * 1000;
	private Map<Integer, StaticDoor> doors;
	private Future<?> instanceTimer;
	private Future<?> assaultsPodsTask;
	private int assaultsPodsPosition;
	private Future<?> assaultsMobsTask;
	private int assaultsMobsCount;
	private Future<?> bombersTask;
	private long startTime;
	private NormalReward instanceReward;
	private boolean isInstanceDestroyed;
	private boolean isSpawned = false;
	private AtomicInteger killedOfficer = new AtomicInteger();
	private Future<?> failTimerTask;

	@Override
	public void onDie(Npc npc) {
		Creature master = npc.getMaster();
		if (master instanceof Player) {
			return;
		}

		int npcId = npc.getNpcId();
		switch (npcId) {
			case 231168:
			case 231169:
			case 231170:
				addPoints(npc, 1880);
				checkSiegePhase(Arrays.asList(231168, 231169, 231170), 1);
				break;
			case 231171:
			case 231172:
			case 231173:
				addPoints(npc, 1880);
				checkSiegePhase(Arrays.asList(231171, 231172, 231173), 2);
				break;
			case 231174:
			case 231175:
			case 231176:
				addPoints(npc, 1880);
				checkSiegePhase(Arrays.asList(231174, 231175, 231176), 3);
				break;
			case 231177:
			case 231178:
			case 231179:
				addPoints(npc, 1880);
				break;
			case 231143:
			case 231152:
			case 231153:
			case 231154:
			case 231155:
				addPoints(npc, 334);
				checkSiegePhase(Arrays.asList(231143, 231152, 231153, 231154, 231155), 4);
				break;
			case 231144:
			case 231162:
			case 231163:
			case 231164:
			case 231165:
			case 231141:
			case 231158:
			case 231156:
			case 231140:
			case 231159:
			case 231138:
			case 230784:
			case 230785:
				addPoints(npc, 334);
				break;
			case 231181:
			case 230782:
				addPoints(npc, 266);
				break;
			case 230746:
			case 230756:
			case 230753:
			case 230757:
			case 230754:
				addPoints(npc, 1002);
				sendMsg(1401940);
				killedOfficer.incrementAndGet();
				if (killedOfficer.compareAndSet(5, 0)) {
					cancelBombersTask();
				}
				break;
			case 230744:
			case 230745:
			case 230749:
				addPoints(npc, 1002);
				break;
			case 831333:
				addPoints(npc, -1350);
				sendMsg(1401826);
				break;
			case 831335:
				addPoints(npc, -1350);
				sendMsg(1401824);
				cancelBombersTask();
				break;
			case 209516:
			case 209517:
				cancelBombersTask();
				cancelAssaultsMobsTask();
				cancelAssaultsPodsTask();
				addPoints(npc, -9000);
				cancelFailTask();
				checkRank(0);
				break;
			case 209555:
			case 209557:
				addPoints(npc, -50);
				break;
			case 231130:
				addPoints(npc, 24000);
				cancelFailTask();
				cancelBombersTask();
				cancelAssaultsMobsTask();
				cancelAssaultsPodsTask();
				checkRank(instanceReward.getPoints());
				break;
			case 231128:
				addPoints(npc, 44);
				break;
			case 233312:
			case 233315:
				addPoints(npc, 36);
				break;
			case 233309:
			case 231115:
			case 231116:
				addPoints(npc, 33);
				break;
			case 233313:
				addPoints(npc, 20);
				break;
			default:
				addPoints(npc, 42);
				break;
		}
	}

	private void addPoints(Npc npc, int points) {
		if (instanceReward.getInstanceScoreType().isStartProgress()) {
			instanceReward.addPoints(points);
			sendPacket(npc.getObjectTemplate().getNameId(), points);
		}
	}

	private int getTime() {
		long result = System.currentTimeMillis() - startTime;
		if (result < START_DELAY) {
			return (int) (START_DELAY - result);
		} else if (result < 1852000) {
			return (int) (1800000 - (result - START_DELAY));
		}
		return 0;
	}

	protected void sendPacket(final int nameId, final int point) {
		instance.forEachPlayer((Player player) -> {
			if (nameId != 0) {
				PacketSendUtility.sendPacket(player, new SM_SYSTEM_MESSAGE(1400237, new DescriptionId(nameId * 2 + 1), point));
			}
			PacketSendUtility.sendPacket(player, new SM_INSTANCE_SCORE(new NormalScoreInfo(instanceReward), instanceReward, getTime()));
		});
	}

	private int checkRank(int totalPoints) {
		int rank = 0;
		if (totalPoints >= 92000) {
			instanceReward.setFinalAp(35000);
			instanceReward.setRewardItem1(186000242);
			instanceReward.setRewardItem1Count(4);
			instanceReward.setRewardItem2(188052596);
			instanceReward.setRewardItem2Count(1);
			instanceReward.setRewardItem3(188052594);
			instanceReward.setRewardItem3Count(1);
			rank = 1;
		} else if (totalPoints >= 84000) {
			instanceReward.setFinalAp(25000);
			instanceReward.setRewardItem1(186000242);
			instanceReward.setRewardItem1Count(2);
			instanceReward.setRewardItem2(188052594);
			instanceReward.setRewardItem2Count(1);
			instanceReward.setRewardItem3(188052597);
			instanceReward.setRewardItem3Count(1);
			rank = 2;
		} else if (totalPoints > 76000) {
			instanceReward.setFinalAp(15000);
			instanceReward.setRewardItem1(186000242);
			instanceReward.setRewardItem1Count(1);
			instanceReward.setRewardItem2(188052595);
			instanceReward.setRewardItem2Count(1);
			instanceReward.setRewardItem3(188052598);
			instanceReward.setRewardItem3Count(1);
			rank = 3;
		} else if (totalPoints > 50000) {
			instanceReward.setFinalAp(11000);
			instanceReward.setRewardItem1(188052598);
			instanceReward.setRewardItem1Count(1);
			rank = 4;
		} else if (totalPoints > 10000) {
			instanceReward.setFinalAp(7000);
			rank = 5;
		} else {
			// TODO
			rank = 8;
		}
		instanceReward.setInstanceScoreType(InstanceScoreType.END_PROGRESS);
		instanceReward.setRank(rank);
		for (Npc npc : instance.getNpcs()) {
			npc.getController().delete();
		}
		doReward(rank);
		return rank;
	}

	protected void doReward(int rank) {
		switch (rank) {
			case 1:
				spawn(701913, 744.16663f, 292.8595f, 233.73764f, (byte) 100);
				break;
			case 2:
				spawn(701914, 744.16663f, 292.8595f, 233.73764f, (byte) 100);
				break;
			case 3:
				spawn(701915, 744.16663f, 292.8595f, 233.73764f, (byte) 100);
				break;
			case 4:
				spawn(701916, 744.16663f, 292.8595f, 233.73764f, (byte) 100);
				break;
			case 5:
				spawn(701917, 744.16663f, 292.8595f, 233.73764f, (byte) 100);
				break;
		}

		instance.forEachPlayer(new Consumer<Player>() {

			@Override
			public void accept(Player player) {
				AbyssPointsService.addAp(player, instanceReward.getFinalAp());
				ItemService.addItem(player, instanceReward.getRewardItem1(), instanceReward.getRewardItem1Count());
				ItemService.addItem(player, instanceReward.getRewardItem2(), instanceReward.getRewardItem2Count());
				ItemService.addItem(player, instanceReward.getRewardItem3(), instanceReward.getRewardItem3Count());
				ItemService.addItem(player, instanceReward.getRewardItem4(), instanceReward.getRewardItem4Count());
				sendPacket(0, 0);
			}
		});
	}

	private void checkSiegePhase(List<Integer> npcs, int phase) {
		int deadCount = 0;
		Npc npc = null;
		for (Integer npcId : npcs) {
			npc = getNpc(npcId);
			if (isDead(npc)) {
				deadCount++;
			}
		}
		if (deadCount == npcs.size()) {
			switch (phase) {
				case 1:
					final Npc wall = instance.getNpc(831335);
					sendMsg(1401816);// START 2 phase
					rndSpawn((Npc) spawn(231171, 655.75482f, 212.60637f, 225.09502f, (byte) 80), 233315, 2);
					ThreadPoolManager.getInstance().schedule(new Runnable() {

						@Override
						public void run() {
							instance.getNpc(231171).getAggroList().addHate(wall, 1000);
							sendMsg(1401823);
						}
					}, 1500);
					rndSpawn((Npc) spawn(231172, 581.7766f, 377.6639f, 225.5279f, (byte) 110), 233313, 2);
					rndSpawn((Npc) spawn(231173, 800.51465f, 469.41602f, 226.03f, (byte) 90), 233315, 2);
					break;
				case 2:
					sendMsg(1401817);// START 3 phase
					spawn(231174, 669.85052f, 468.26706f, 225.25f, (byte) 80);
					spawn(231175, 583.83014f, 373.8118f, 225.28f, (byte) 110);
					spawn(231176, 760.21906f, 392.47076f, 247.1223f, (byte) 50);
					break;
				case 3:
					sendMsg(1401818);// START 4 phase
					spawn(231143, 613.2310f, 262.16318f, 227.25543f, (byte) 3);
					spawn(231152, 608.371f, 303.51367f, 226.29489f, (byte) 113);
					spawn(231153, 625.2439f, 352.62399f, 226.29483f, (byte) 113);
					spawn(231154, 668.864f, 405.96982f, 228.5f, (byte) 83);
					spawn(231155, 691.53613f, 409.36713f, 231.72017f, (byte) 98);
					break;
				case 4:
					sendMsg(1401819);// final phase
					spawn(231130, 740.66846f, 298.08191f, 237.59772f, (byte) 100);
					spawn(231133, 713.24158f, 289.97089f, 250.80768f, (byte) 0);
					spawn(231133, 745.82031f, 322.91565f, 253.1721f, (byte) 90);
					cancelBombersTask();
					cancelAssaultsMobsTask();
					cancelAssaultsPodsTask();
					break;
			}
		}
	}

	private boolean isDead(Npc npc) {
		return (npc == null || npc.getLifeStats().isAlreadyDead());
	}

	@Override
	public void onEnterInstance(final Player player) {
		if (!instanceReward.isRewarded()) {
			sendPacket(0, 0);
		}

		if (!isSpawned) {
			isSpawned = true;
			Race race = player.getRace();
			int commander = race == Race.ELYOS ? 209516 : 209517;
			int guard = race == Race.ELYOS ? 209555 : 209557;
			int flag = race == Race.ELYOS ? 701923 : 701924;
			int siegeCannon = race == Race.ELYOS ? 701625 : 701922;
			spawn(commander, 750.1540f, 285.8698f, 233.7515f, (byte) 40);
			spawn(guard, 690.2202f, 341.5325f, 228.6740f, (byte) 40);
			spawn(guard, 690.0455f, 351.8003f, 244.7439f, (byte) 40);
			spawn(guard, 692.7775f, 337.9521f, 228.6740f, (byte) 40);
			spawn(guard, 693.0820f, 354.4323f, 244.7328f, (byte) 40);
			spawn(guard, 749.3892f, 364.9877f, 230.9448f, (byte) 40);
			spawn(guard, 598.8683f, 284.2013f, 226.4238f, (byte) 40);
			spawn(guard, 595.4760f, 284.6801f, 226.3750f, (byte) 40);
			spawn(guard, 602.3284f, 340.9641f, 225.7942f, (byte) 40);
			spawn(guard, 715.4051f, 427.3117f, 230.0245f, (byte) 40);
			spawn(guard, 719.3775f, 428.1010f, 230.1124f, (byte) 40);
			spawn(guard, 611.8168f, 388.8648f, 223.5000f, (byte) 40);
			spawn(guard, 681.7424f, 444.5801f, 226.8180f, (byte) 40);
			spawn(guard, 684.4371f, 447.8484f, 226.7868f, (byte) 40);
			spawn(guard, 607.4504f, 387.6416f, 223.3526f, (byte) 40);
			spawn(flag, 744.1735f, 292.9492f, 233.6979f, (byte) 40);
			spawn(siegeCannon, 640.8621f, 412.7843f, 243.9395f, (byte) 40);
			if (race == Race.ELYOS) {
				spawn(701596, 617.5008f, 248.2060f, 235.7402f, (byte) 60);
				spawn(701597, 612.8020f, 275.2036f, 235.7399f, (byte) 66);
				spawn(701598, 616.1592f, 313.9362f, 235.7399f, (byte) 53);
				spawn(701599, 625.6245f, 339.6094f, 235.7399f, (byte) 53);
				spawn(701600, 650.9138f, 372.9323f, 238.6072f, (byte) 53);
				spawn(701601, 677.8447f, 396.2072f, 238.6320f, (byte) 40);
				spawn(701602, 710.1561f, 410.6727f, 241.0138f, (byte) 30);
				spawn(701603, 736.7967f, 414.1217f, 241.0169f, (byte) 40);
				spawn(701604, 772.9859f, 410.8307f, 241.0142f, (byte) 20);
				spawn(701605, 798.3407f, 401.6078f, 241.0149f, (byte) 30);
				spawn(701606, 709.6014f, 313.5341f, 254.2164f, (byte) 40);
				spawn(701607, 726.7606f, 327.9423f, 254.2164f, (byte) 50);
			} else {
				spawn(701610, 617.5008f, 248.2060f, 235.7402f, (byte) 60);
				spawn(701611, 612.8020f, 275.2036f, 235.7399f, (byte) 66);
				spawn(701612, 616.1592f, 313.9362f, 235.7399f, (byte) 53);
				spawn(701613, 625.6245f, 339.6094f, 235.7399f, (byte) 53);
				spawn(701614, 650.9138f, 372.9323f, 238.6072f, (byte) 53);
				spawn(701615, 677.8447f, 396.2072f, 238.6320f, (byte) 40);
				spawn(701616, 710.1561f, 410.6727f, 241.0138f, (byte) 30);
				spawn(701617, 736.7967f, 414.1217f, 241.0169f, (byte) 40);
				spawn(701618, 772.9859f, 410.8307f, 241.0142f, (byte) 20);
				spawn(701619, 798.3407f, 401.6078f, 241.0149f, (byte) 30);
				spawn(701620, 709.6014f, 313.5341f, 254.2164f, (byte) 40);
				spawn(701621, 726.7606f, 327.9423f, 254.2164f, (byte) 50);
			}
		}
	}

	@Override
	public void onInstanceCreate(WorldMapInstance instance) {
		super.onInstanceCreate(instance);
		instanceReward = new NormalReward(mapId, instanceId);
		instanceReward.setInstanceScoreType(InstanceScoreType.PREPARING);
		instanceReward.setBasicAp(20000);
		instanceReward.setPoints(20000);
		doors = instance.getDoors();

		if (instanceTimer == null) {
			startTime = System.currentTimeMillis();
			instanceTimer = ThreadPoolManager.getInstance().schedule(new Runnable() {

				@Override
				public void run() {
					rndSpawn((Npc) spawn(231168, 652.1909f, 461.2643f, 225.09502f, (byte) 80), 233313, 2);
					rndSpawn((Npc) spawn(231169, 581.7766f, 377.6639f, 225.5279f, (byte) 110), 233315, 2);
					rndSpawn((Npc) spawn(231170, 800.51465f, 469.41602f, 228.58566f, (byte) 90), 233313, 2);
					instanceReward.setInstanceScoreType(InstanceScoreType.START_PROGRESS);
					sendPacket(0, 0);
					openDoors();
					startAssaultsPodsTask();
					startAssaultsMobsTask();
					startBombersTask();
					startFailTask();
					sendMsg(1401815);
				}
			}, START_DELAY + 2000);
		}
	}

	private void startFailTask() {
		failTimerTask = ThreadPoolManager.getInstance().schedule(new Runnable() {

			@Override
			public void run() {
				sendMsg(1401939);
				checkRank(0);
			}
		}, 1800000);
	}

	private void startAssaultsPodsTask() {
		assaultsPodsTask = ThreadPoolManager.getInstance().scheduleAtFixedRate(new Runnable() {

			@Override
			public void run() {
				assaultsPodsPosition++;
				spawnAssaultsPods(assaultsPodsPosition);
			}
		}, 5000, 120000);
	}

	private void startAssaultsMobsTask() {
		assaultsMobsTask = ThreadPoolManager.getInstance().scheduleAtFixedRate(new Runnable() {

			@Override
			public void run() {
				assaultsMobsCount++;
				spawnFlangsAttackers(assaultsMobsCount);
			}
		}, 2 * 60000, 120000);
	}

	private void startBombersTask() {
		bombersTask = ThreadPoolManager.getInstance().scheduleAtFixedRate(new Runnable() {

			@Override
			public void run() {
				spawnBombers();
			}
		}, 10 * 60000, 60000);
	}

	private void spawnAssaultsPods(int position) {
		switch (position) {
			case 1:
				spawn(231162, 747.2734f, 300.1817f, 233.7515f, (byte) 96);
				spawn(231167, 735.2822f, 295.3066f, 233.7515f, (byte) 115);
				break;
			case 2:
				spawn(231163, 727.1748f, 364.43121f, 230.18347f, (byte) 0);
				spawn(231165, 721.4975f, 358.17191f, 230.18347f, (byte) 15);
				break;
			case 3:
				spawn(231141, 666.36053f, 294.43463f, 225.65f, (byte) 15);
				spawn(231164, 667.3504f, 281.04642f, 225.69f, (byte) 30);
				break;
			case 4:
				spawn(231158, 768.33917f, 390.7089f, 243.5498f, (byte) 40);
				break;
			case 5:
				spawn(231156, 641.55145f, 339.26358f, 240.43744f, (byte) 20);
				break;
			case 6:
				spawn(231140, 635.42615f, 243.11667f, 238.48f, (byte) 30);
				break;
			case 7:
				spawn(231159, 697.5636f, 305.42441f, 249.3f, (byte) 100);
				break;
		}
	}

	private void spawnFlangsAttackers(int assaultsMobsCount) {
		spawnMobsIfWallsDestroyed();
		switch (assaultsMobsCount) {
			case 1:
			case 2:
				// Right Flang
				spawnAndMove(231110, 651.31f, 471.77f, 225.84f, (byte) 100, "eternalBastionRight");
				spawnAndMove(231110, 656.24f, 475.18f, 226.36f, (byte) 100, "eternalBastionRight");
				spawnAndMove(231113, 652.08f, 475.94f, 226.12f, (byte) 100, "eternalBastionRight");
				// Left Flang
				spawnAndMove(231110, 597.86f, 407.68f, 224.32f, (byte) 100, "eternalBastionLeft");
				spawnAndMove(231110, 602.27f, 411.74f, 223.76f, (byte) 100, "eternalBastionLeft");
				spawnAndMove(231113, 598.03f, 411.92f, 223.75f, (byte) 100, "eternalBastionLeft");
				break;
			case 3:
			case 4:
			case 5:
				// Right Flang
				spawnAndMove(231110, 651.31f, 471.77f, 225.84f, (byte) 100, "eternalBastionRight");
				spawnAndMove(231110, 656.24f, 475.18f, 226.36f, (byte) 100, "eternalBastionRight");
				spawnAndMove(231113, 652.08f, 475.94f, 226.12f, (byte) 100, "eternalBastionRight");
				spawnAndMove(231110, 653.31f, 472.77f, 225.84f, (byte) 100, "eternalBastionRight");
				spawnAndMove(231110, 650.24f, 470.18f, 226.36f, (byte) 100, "eternalBastionRight");
				spawnAndMove(231113, 659.08f, 473.94f, 226.12f, (byte) 100, "eternalBastionRight");
				// Left Flang
				spawnAndMove(231110, 597.86f, 407.68f, 224.32f, (byte) 100, "eternalBastionLeft");
				spawnAndMove(231110, 602.27f, 411.74f, 223.76f, (byte) 100, "eternalBastionLeft");
				spawnAndMove(231113, 598.03f, 411.92f, 223.75f, (byte) 100, "eternalBastionLeft");
				spawnAndMove(231110, 595.86f, 407.68f, 224.32f, (byte) 100, "eternalBastionLeft");
				spawnAndMove(231110, 605.27f, 413.74f, 223.76f, (byte) 100, "eternalBastionLeft");
				spawnAndMove(231113, 600.03f, 412.92f, 223.75f, (byte) 100, "eternalBastionLeft");
				break;
			case 6:
			case 7:
			case 8:
			case 9:
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
				// Right Flang
				spawnAndMove(231110, 651.31f, 471.77f, 225.84f, (byte) 100, "eternalBastionRight");
				spawnAndMove(231134, 656.24f, 475.18f, 226.36f, (byte) 100, "eternalBastionRight");
				spawnAndMove(231113, 652.08f, 475.94f, 226.12f, (byte) 100, "eternalBastionRight");
				spawnAndMove(231110, 653.31f, 472.77f, 225.84f, (byte) 100, "eternalBastionRight");
				spawnAndMove(231110, 650.24f, 470.18f, 226.36f, (byte) 100, "eternalBastionRight");
				spawnAndMove(231113, 659.08f, 473.94f, 226.12f, (byte) 100, "eternalBastionRight");
				spawnAndMove(231134, 653.31f, 472.77f, 225.84f, (byte) 100, "eternalBastionRight");
				spawnAndMove(231110, 650.24f, 470.18f, 226.36f, (byte) 100, "eternalBastionRight");
				spawnAndMove(231113, 659.08f, 473.94f, 226.12f, (byte) 100, "eternalBastionRight");
				// Left Flang
				spawnAndMove(231110, 597.86f, 407.68f, 224.32f, (byte) 100, "eternalBastionLeft");
				spawnAndMove(231110, 602.27f, 411.74f, 223.76f, (byte) 100, "eternalBastionLeft");
				spawnAndMove(231113, 598.03f, 411.92f, 223.75f, (byte) 100, "eternalBastionLeft");
				spawnAndMove(231110, 595.86f, 407.68f, 224.32f, (byte) 100, "eternalBastionLeft");
				spawnAndMove(231110, 605.27f, 413.74f, 223.76f, (byte) 100, "eternalBastionLeft");
				spawnAndMove(231113, 600.03f, 412.92f, 223.75f, (byte) 100, "eternalBastionLeft");
				spawnAndMove(231134, 600.86f, 409.68f, 224.32f, (byte) 100, "eternalBastionLeft");
				spawnAndMove(231110, 602.27f, 412.74f, 223.76f, (byte) 100, "eternalBastionLeft");
				spawnAndMove(231113, 598.03f, 410.92f, 223.75f, (byte) 100, "eternalBastionLeft");
				break;
		}
	}

	private void spawnMobsIfWallsDestroyed() {
		Npc leftWall = getNpc(831335);
		Npc rightWall = getNpc(831333);
		// TODO search current Npc ids
		if (isDead(leftWall)) {
			spawnAndMove(231110, 671.37946f, 231.4287f, 225.6977f, (byte) 33, "deadLeftWall");
			spawnAndMove(231134, 675.77277f, 239.83878f, 225.69778f, (byte) 36, "deadLeftWall");
			spawnAndMove(231113, 678.79956f, 231.5277f, 225.69778f, (byte) 36, "deadLeftWall");
			spawnAndMove(231110, 685.838f, 234.00035f, 225.697f, (byte) 36, "deadLeftWall");
			spawnAndMove(231134, 682.13605f, 244.586f, 225.69778f, (byte) 36, "deadLeftWall");
		}
		if (isDead(rightWall)) {
			spawnAndMove(231110, 791.6158f, 424.6158f, 232.33652f, (byte) 80, "deadRightWall");
			spawnAndMove(231134, 795.68085f, 422.36362f, 232.24445f, (byte) 100, "deadRightWall");
			spawnAndMove(231113, 792.7093f, 416.915f, 232.25f, (byte) 100, "deadRightWall");
			spawnAndMove(231110, 799.08f, 420.52f, 232.353f, (byte) 100, "deadRightWall");
			spawnAndMove(231110, 799.08f, 420.52f, 232.353f, (byte) 100, "deadRightWall");
			spawnAndMove(231134, 799.08f, 420.52f, 232.353f, (byte) 100, "deadRightWall");
		}
	}

	private void spawnAndMove(final int npcId, final float x, final float y, final float z, final byte h, final String walker) {
		final Npc npc = (Npc) spawn(npcId, x, y, z, h);
		ThreadPoolManager.getInstance().schedule(new Runnable() {

			@Override
			public void run() {
				if (!isInstanceDestroyed) {
					npc.getSpawn().setWalkerId(walker);
					WalkManager.startWalking((NpcAI) npc.getAi());
					npc.setState(CreatureState.ACTIVE, true);
					PacketSendUtility.broadcastPacket(npc, new SM_EMOTION(npc, EmotionType.START_EMOTE2, 0, npc.getObjectId()));
				}
			}
		}, 3000);
	}

	private void spawnBombers() {
		sendMsg(1401825);
		if (getTime() > 60000 * 15) {
			spawn(231142, 801.82086f, 410.5512f, 232.53445f, (byte) 82);
			spawn(231142, 794.6926f, 416.54745f, 232.25f, (byte) 64);
			spawn(231142, 787.48645f, 414.62988f, 232.32147f, (byte) 76);
			spawn(231142, 791.98004f, 420.04877f, 232.25f, (byte) 76);
			spawn(231142, 792.04376f, 410.15372f, 232.01234f, (byte) 85);
			spawn(231142, 781.3186f, 410.16824f, 232.18861f, (byte) 96);
		} else {
			spawn(231150, 789.36f, 415.79f, 232.27f, (byte) 80);
		}
	}

	private void cancelAssaultsPodsTask() {
		if (assaultsPodsTask != null && !assaultsPodsTask.isCancelled()) {
			assaultsPodsTask.cancel(true);
		}
	}

	private void cancelAssaultsMobsTask() {
		if (assaultsMobsTask != null && !assaultsMobsTask.isCancelled()) {
			assaultsMobsTask.cancel(true);
		}
	}

	private void cancelBombersTask() {
		if (bombersTask != null && !bombersTask.isCancelled()) {
			bombersTask.cancel(true);
		}
	}

	private void rndSpawn(Npc npc, int npcId, int count) {
		for (int i = 0; i < count; i++) {
			SpawnTemplate template = rndSpawnInRange(npc, npcId);
			SpawnEngine.spawnObject(template, instance.getInstanceId());
		}
	}

	private SpawnTemplate rndSpawnInRange(Npc npc, int npcId) {
		float direction = Rnd.get(0, 199) / 100f;
		float x1 = (float) (Math.cos(Math.PI * direction) * 5);
		float y1 = (float) (Math.sin(Math.PI * direction) * 5);
		return SpawnEngine.addNewSingleTimeSpawn(instance.getMapId(), npcId, npc.getPosition().getX() + x1, npc.getPosition().getY() + y1, npc
			.getPosition().getZ(), npc.getPosition().getHeading());
	}

	private void openDoors() {
		for (StaticDoor door : doors.values()) {
			if (door != null) {
				door.setOpen(true);
			}
		}
	}

	@Override
	public boolean onDie(final Player player, Creature lastAttacker) {
		PacketSendUtility.sendPacket(player, new SM_DIE(player.haveSelfRezEffect(), player.haveSelfRezItem(), 0, 8));
		return true;
	}

	@Override
	public void handleUseItemFinish(Player player, Npc npc) {
		if (npc.getNpcId() == 701625 || npc.getNpcId() == 701922) {
			SkillEngine.getInstance().applyEffectDirectly(npc.getNpcId() == 701625 ? 21138 : 21139, player, player, 0);
			npc.getController().delete();
			switch (Rnd.get(1, 2)) {
				case 1:
					spawn(231160, 707.70288f, 259.17334f, 253.03772f, (byte) 40);
					break;
				case 2:
					spawn(231157, 778.84534f, 323.28247f, 253.04544f, (byte) 40);
					break;
			}
		}
	}

	@Override
	public boolean onReviveEvent(Player player) {
		PlayerReviveService.revive(player, 100, 100, false, 0);
		player.getGameStats().updateStatsAndSpeedVisually();
		TeleportService.teleportTo(player, mapId, instanceId, 449.5f, 448.9f, 270.74f, (byte) 70);
		return true;
	}

	private void cancelFailTask() {
		if (failTimerTask != null && !failTimerTask.isCancelled()) {
			failTimerTask.cancel(true);
		}
	}

	@Override
	public void onInstanceDestroy() {
		if (instanceTimer != null) {
			instanceTimer.cancel(false);
		}
		cancelFailTask();
		cancelBombersTask();
		cancelAssaultsMobsTask();
		cancelAssaultsPodsTask();
		isInstanceDestroyed = true;
		doors.clear();
	}

	@Override
	public void onExitInstance(Player player) {
		if (instanceReward.getInstanceScoreType().isEndProgress()) {
			TeleportService.moveToInstanceExit(player, mapId, player.getRace());
		}
	}
}
