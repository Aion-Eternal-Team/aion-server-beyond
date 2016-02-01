package instance;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import com.aionemu.commons.utils.Rnd;
import com.aionemu.gameserver.instance.handlers.GeneralInstanceHandler;
import com.aionemu.gameserver.instance.handlers.InstanceID;
import com.aionemu.gameserver.model.DescriptionId;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.instance.InstanceScoreType;
import com.aionemu.gameserver.model.instance.instancereward.LegionDominionReward;
import com.aionemu.gameserver.model.team.legion.Legion;
import com.aionemu.gameserver.model.templates.spawns.SpawnTemplate;
import com.aionemu.gameserver.network.aion.instanceinfo.LegionDominionScoreInfo;
import com.aionemu.gameserver.network.aion.serverpackets.SM_INSTANCE_SCORE;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.services.LegionDominionService;
import com.aionemu.gameserver.services.item.ItemService;
import com.aionemu.gameserver.spawnengine.SpawnEngine;
import com.aionemu.gameserver.utils.MathUtil;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.world.WorldMapInstance;
import com.aionemu.gameserver.world.WorldPosition;
import com.aionemu.gameserver.world.knownlist.Visitor;

import javolution.util.FastTable;

/**
 * @author Yeats
 */
@InstanceID(301500000)
public class StonespearRanchInstance extends GeneralInstanceHandler {

	private LegionDominionReward reward;
	private Long startTime, endTime;
	private List<Future<?>> tasks = new FastTable<>();
	private List<WorldPosition> points = new FastTable<>();
	private Future<?> timer, failTask;
	private Race instanceRace;
	private Legion instanceLegion;
	private AtomicInteger kills = new AtomicInteger(0);

	@Override
	public void onInstanceCreate(WorldMapInstance instance) {
		super.onInstanceCreate(instance);
		reward = new LegionDominionReward(mapId, instanceId);
		reward.setInstanceScoreType(InstanceScoreType.PREPARING);
		SpawnTemplate temp = SpawnEngine.addNewSingleTimeSpawn(mapId, 855765, 231.14f, 264.399f, 96.23f, (byte) 1); // TODO change npcId
		temp.setStaticId(14);
		SpawnEngine.spawnObject(temp, instanceId);
		addWorldPoints();
		if (timer == null) {
			startTime = System.currentTimeMillis();
			timer = ThreadPoolManager.getInstance().schedule(new Runnable() {

				@Override
				public void run() {
					startTime = System.currentTimeMillis();
					reward.setInstanceScoreType(InstanceScoreType.START_PROGRESS);
					sendPacket(0, 0);
					startInstance();
					startFailTask();
				}
			}, 180000); // 3min
		}
	}

	@Override
	public void onEnterInstance(Player player) {
		if (!reward.isRewarded()) {
			sendPacket(0, 0);
		}
		if (instanceLegion == null) {
			instanceLegion = player.getLegion();
		}
		if (instanceRace == null) {
			instanceRace = player.getRace();
		}
	}

	@Override
	public void onDie(Npc npc) {
		switch (npc.getNpcId()) {
			//trash mobs
			case 855765:
			case 855766:
			case 855767:
			case 855768:
			case 855769:
			case 855770:
			case 855771:
			case 855772:
			case 855773:
				addPoints(npc, 100);
				kills.incrementAndGet();
				break;
			case 855788:
			case 855789:
			case 855790:
			case 855791:
			case 855792:
			case 855793:
			case 855794:
			case 855795:
			case 855796:
				addPoints(npc, 200);
				kills.set(kills.get() + 2);
				break;
			case 855811:
			case 855812:
			case 855813:
			case 855814:
			case 855815:
			case 855816:
			case 855817:
			case 855818:
			case 855819:
				addPoints(npc, 300);
				kills.set(kills.get() + 3);
				break;
			case 855834:
			case 855835:
			case 855836:
			case 855837:
			case 855838:
			case 855839:
			case 855840:
			case 855841:
			case 855842:
				addPoints(npc, 400);
				kills.set(kills.get() + 4);
				break;
			//aetherfield
			case 855764:
				addPoints(npc, 500);
				kills.incrementAndGet();
				break;
			case 855787:
				addPoints(npc, 1000);
				kills.set(kills.get() + 2);
				break;
			case 856303: //kebbit
			case 855810:
				addPoints(npc, 1500);
				kills.set(kills.get() + 3);
				break;
			case 855833:
				addPoints(npc, 2000);
				kills.set(kills.get() + 4);
				break;
			// bosses
			case 856305: //clown
			case 855774: //hamerun
			case 855775: //kromede
			case 855776: //kalliga
				addPoints(npc, 12000);
				kills.set(kills.get() + 12);
				break;
			case 855797: //bakarma
			case 855798: //triroan
			case 855799: //lanmark
				addPoints(npc, 21000);
				kills.set(kills.get() + 21);
				break;
			case 855820: //calindi
			case 855821: //tahabata
			case 855822: //stormwing
				addPoints(npc, 30000);
				kills.set(kills.get() + 30);
				break;
			case 855843: //guardian general
				addPoints(npc, 42000);
				checkRank(reward.getPoints(), true);
				break;
			//guardian stone
			case 855763:
			case 855832:
			case 855786:
			case 856466:
			case 856467:
			case 856468:	
				break;
				default:
					break;
		}
		if (npc != null)
			npc.getController().onDelete();
	}

	private synchronized void checkRank(int points, boolean bossKilled) {
		cancelAllTasks();
		int rank = 8;
		if (points > 200000 && bossKilled) { //S-Rank
			reward.setFinalGP(120);
			reward.setRewardItem1(186000242); //C-Med
			reward.setRewardItem1Count(1);
			reward.setRewardItem2(188053801); //Stonespear Siege Champion Reward Chest
			reward.setRewardItem2Count(1);
			reward.setRewardItem3(188053804); //Stonespear Siege Champion Relic Chest
			reward.setRewardItem3Count(1);
			rank = 1;
		} else if (points > 41000) { //A-Rank
			reward.setFinalGP(100);
			reward.setRewardItem1(186000243); //C-Fragment
			reward.setRewardItem1Count(10);
			reward.setRewardItem2(188053800); //Stonespear Siege Runner-Up Reward Chest
			reward.setRewardItem2Count(1);
			reward.setRewardItem3(188053803); //Stonespear Siege Runner-Up Relic Chest
			reward.setRewardItem3Count(1);
			rank = 2;
		} else if (points > 25000) { //B-Rank
			reward.setFinalGP(80);
			reward.setRewardItem1(186000243); //C-Fragment
			reward.setRewardItem1Count(5);
			reward.setRewardItem2(188053799); //Stonespear Siege Reward Chest
			reward.setRewardItem2Count(1);
			reward.setRewardItem3(188053802); //Stonespear Siege Relic Chest
			reward.setRewardItem3Count(1);
			rank = 3;
		} 
		

		
		if (!reward.isRewarded()) {
			reward.setInstanceScoreType(InstanceScoreType.END_PROGRESS);
			reward.setRank(rank);
			despawnAll();
			endTime = System.currentTimeMillis();
			sendPacket(0, 0);
			reward();
			LegionDominionService.getInstance().onFinishInstance(instanceLegion, reward.getPoints(), (endTime - startTime));
		}
	}

	private void reward() {
		instance.doOnAllPlayers(new Visitor<Player>() {

			@Override
			public void visit(Player player) {
				ItemService.addItem(player, reward.getRewardItem1(), reward.getRewardItem1Count());
				ItemService.addItem(player, reward.getRewardItem2(), reward.getRewardItem2Count());
				ItemService.addItem(player, reward.getRewardItem3(), reward.getRewardItem3Count());
			}
			
		});
	}
	
	private void startInstance() {
		startStage1_1();
	}

	private void startStage1_1() { //minute 0 - 1
		int npcId = 855765 + Rnd.get(0, 8);
		spawnAtPointsTask(50, npcId, -1); 
		spawnAtPointsTask(8000, 856305, 0);
		spawnAtPointsTask(8000, npcId, -1); 
		spawnAtPointsTask(16000, npcId, -1); 
		spawnAtPointsTask(24000, npcId, -1); 
		spawnAtPointsTask(32000, npcId, -1); 
		spawnAtPointsTask(40000, npcId, -1);
		rndSpawnTask(36000, 856303, 1, 22, 23);
		startStage1_2();
	}

	private void startStage1_2() { //minute 1 - 2
		tasks.add(ThreadPoolManager.getInstance().schedule(new Runnable() {

			@Override
			public void run() {
				int npcId = 855765 + Rnd.get(0, 8);
				rndSpawnTask(50, npcId, 3, 8, 16);
				rndSpawnTask(2000, npcId, 3, 7, 18);
				rndSpawnTask(4000, npcId, 6, 7, 22);
				npcId = 855765 + Rnd.get(0, 8);
				rndSpawnTask(6000, npcId, 4, 7, 18);
				rndSpawnTask(7000, npcId, 4, 7, 18);
				rndSpawnTask(8000, npcId, 6, 7, 18);
				rndSpawnTask(11000, npcId, 8, 7, 18);
				rndSpawnTask(25000, 856303, 1, 22, 23);
				startStage1_3();
			}
		}, 60000));
	}

	private void startStage1_3() { //minute 2 - 3
		tasks.add(ThreadPoolManager.getInstance().schedule(new Runnable() {

			@Override
			public void run() {
				int npcId = 855765 + Rnd.get(0, 8);
				//2k points
				SpawnEngine.spawnObject(SpawnEngine.addNewSingleTimeSpawn(mapId, 855764, 230.8977f, 285.5198f, 96.42f, (byte) 80), instanceId);
				SpawnEngine.spawnObject(SpawnEngine.addNewSingleTimeSpawn(mapId, 855764, 211.254f, 264.134f, 96.53f, (byte) 0), instanceId);
				SpawnEngine.spawnObject(SpawnEngine.addNewSingleTimeSpawn(mapId, 855764, 231.2034f, 243.8273f, 96.37f, (byte) 30), instanceId);
				SpawnEngine.spawnObject(SpawnEngine.addNewSingleTimeSpawn(mapId, 855764, 251.3068f, 264.307f, 96.31f, (byte) 60), instanceId);

				rndSpawnTask(500, npcId, 5, 8, 12);
				rndSpawnTask(2500, npcId, 5, 7, 11);
				rndSpawnTask(5000, npcId, 5, 12, 22);
				npcId = 855765 + Rnd.get(0, 8);
				rndSpawnTask(10000, npcId, 5, 12, 22);
				rndSpawnTask(13000, npcId, 5, 7, 11);
				rndSpawnTask(16000, npcId, 5, 7, 11);
				rndSpawnTask(26000, 856303, 1, 22, 23);
				startStage1_4();
			}
		}, 60000));
	}

	private void startStage1_4() { //minute 3 - 5
		tasks.add(ThreadPoolManager.getInstance().schedule(new Runnable() {

			@Override
			public void run() {
				//12k points + 3k points
				SpawnEngine.spawnObject(SpawnEngine.addNewSingleTimeSpawn(mapId, 855776, 231.14f, 264.399f, 96.5f, (byte) 10), instanceId); // kaliga 855764
				rndSpawnTask(35000, 856303, 2, 22, 23);
				startStage2_1();
			}
		}, 60000));
	}
	
	private void startStage2_1() { //minute 5 - 6
		tasks.add(ThreadPoolManager.getInstance().schedule(new Runnable() {

			@Override
			public void run() {
				int npcId = 855765 + Rnd.get(0, 8);
				int npcId2 = 855788 + Rnd.get(0, 8);
				//2k + 800 = 2,8k
				spawnAtPointsTask(50, npcId, 0);
				spawnAtPointsTask(50, npcId, 2);
				spawnAtPointsTask(50, npcId, 4);
				spawnAtPointsTask(50, npcId, 6);
				spawnAtPointsTask(250, npcId2, 1);
				spawnAtPointsTask(250, npcId2, 3);
				spawnAtPointsTask(250, npcId2, 5);
				spawnAtPointsTask(250, npcId2, 7);
				spawnAtPointsTask(6000, npcId, -1);
				spawnAtPointsTask(11000, npcId, -1);
				//12k points
				if (instance != null && instance.getNpc(856305) == null) {
					spawnAtPointsTask(1000, 856305, 1);
				} 
				//1,5k points
				rndSpawnTask(20000, 856303, 1, 22, 23);
				startStage2_2();
			}
		}, 120000));
	}

	private void startStage2_2() { //minute 6 - 7
		tasks.add(ThreadPoolManager.getInstance().schedule(new Runnable() {

			@Override
			public void run() {
				int npcId = 855765 + Rnd.get(0, 8);
				int npcId2 = 855788 + Rnd.get(0, 8);
				//2,2k points + 1,4k points = 3,6k points
				rndSpawnTask(50, npcId, 6, 11, 21);
				rndSpawnTask(500, npcId, 6, 11, 21);
				rndSpawnTask(1000, npcId, 10, 11, 21);
				rndSpawnTask(1500, npcId2, 7, 11, 21);
				//1,5k points
				rndSpawnTask(28000, 856303, 1, 22, 23);
				startStage2_3();
			}
		}, 60000));
	}

	private void startStage2_3() { //minute 7 - 8
		tasks.add(ThreadPoolManager.getInstance().schedule(new Runnable() {

			@Override
			public void run() {
				int npcId = 855765 + Rnd.get(0, 8);
				int npcId2 = 855788 + Rnd.get(0, 8);
				//4k points
				SpawnEngine.spawnObject(SpawnEngine.addNewSingleTimeSpawn(mapId, 855787, 230.8977f, 285.5198f, 96.42f, (byte) 80), instanceId);
				SpawnEngine.spawnObject(SpawnEngine.addNewSingleTimeSpawn(mapId, 855787, 211.254f, 264.134f, 96.53f, (byte) 0), instanceId);
				SpawnEngine.spawnObject(SpawnEngine.addNewSingleTimeSpawn(mapId, 855787, 231.2034f, 243.8273f, 96.37f, (byte) 30), instanceId);
				SpawnEngine.spawnObject(SpawnEngine.addNewSingleTimeSpawn(mapId, 855787, 251.3068f, 264.307f, 96.31f, (byte) 60), instanceId);

				//2,6k + 1,2k = 3,8k
				rndSpawnTask(500, npcId, 10, 11, 21);
				rndSpawnTask(4000, npcId, 10, 11, 21);
				rndSpawnTask(7000, npcId, 6, 11, 21);
				rndSpawnTask(10000, npcId2, 6, 11, 21);
				
				//3k points
				rndSpawnTask(28000, 856303, 2, 22, 23);
				startStage2_4();
			}
		}, 60000));
	}

	private void startStage2_4() { //minute 8 - 10
		tasks.add(ThreadPoolManager.getInstance().schedule(new Runnable() {
			
			@Override
			public void run() {
				//21k
				SpawnEngine.spawnObject(SpawnEngine.addNewSingleTimeSpawn(mapId, 855799, 231.14f, 264.399f, 96.5f, (byte) 10), instanceId); // lanmark
				//3k
				rndSpawnTask(35000, 856303, 1, 22, 23);
				rndSpawnTask(45000, 856303, 1, 22, 23);
				startStage3_1();
			}
		}, 60000));
	}

	private void startStage3_1() { //minute 10 - 11
		tasks.add(ThreadPoolManager.getInstance().schedule(new Runnable() {

			@Override
			public void run() {
				int npcId = 855765 + Rnd.get(0, 8);
				int npcId2 = 855788 + Rnd.get(0, 8);
				int npcId3 = 855811 + Rnd.get(0, 8);
				//1,6k + 4,8k + 2,4k 8,8k
				spawnAtPointsTask(50, npcId, -1);
				spawnAtPointsTask(6000, npcId, -1);
				spawnAtPointsTask(13000, npcId2, -1);
				spawnAtPointsTask(20000, npcId2, -1);
				spawnAtPointsTask(27000, npcId2, -1);
				spawnAtPointsTask(33000, npcId3, -1);
			
				//12k points
				if (instance != null && instance.getNpc(856305) == null) {
					spawnAtPointsTask(1000, 856305, 2);
				} 
				//1,5k points
				rndSpawnTask(36000, 856303, 1, 22, 23);
				startStage3_2();
			}
		}, 120000));
	}

	private void startStage3_2() { //minute 11 - 12
		tasks.add(ThreadPoolManager.getInstance().schedule(new Runnable() {

			@Override
			public void run() {
				int npcId = 855765 + Rnd.get(0, 8);
				int npcId2 = 855811 + Rnd.get(0, 8);
				//4,2k points + 3,8k = 8k
				rndSpawnTask(100, npcId2, 7, 6, 9);
				spawnAtPointsTask(5000, npcId, -1); 
				rndSpawnTask(10000, npcId2, 7, 6, 9);
				spawnAtPointsTask(15000, npcId, -1); 
				rndSpawnTask(20000, npcId, 7, 6, 9);
				spawnAtPointsTask(25000, npcId, -1);
				rndSpawnTask(30000, npcId, 7, 6, 9);
				
				//1,5k points
				rndSpawnTask(36000, 856303, 1, 22, 23);
				startStage3_3();
			}
		}, 60000));
	}

	private void startStage3_3() { //minute 12 - 13
		tasks.add(ThreadPoolManager.getInstance().schedule(new Runnable() {

			@Override
			public void run() {
				int npcId = 855765 + Rnd.get(0, 8);
				//6k points
				SpawnEngine.spawnObject(SpawnEngine.addNewSingleTimeSpawn(mapId, 855810, 230.8977f, 285.5198f, 96.42f, (byte) 80), instanceId);
				SpawnEngine.spawnObject(SpawnEngine.addNewSingleTimeSpawn(mapId, 855810, 211.254f, 264.134f, 96.53f, (byte) 0), instanceId);
				SpawnEngine.spawnObject(SpawnEngine.addNewSingleTimeSpawn(mapId, 855810, 231.2034f, 243.8273f, 96.37f, (byte) 30), instanceId);
				SpawnEngine.spawnObject(SpawnEngine.addNewSingleTimeSpawn(mapId, 855810, 251.3068f, 264.307f, 96.31f, (byte) 60), instanceId);
				
				//2,5k points
				rndSpawnTask(500, npcId, 25, 7, 14);
			
				//1,5k points
				rndSpawnTask(25000, 856303, 1, 22, 23);
				startStage3_4();
			}
		}, 60000));
	}
	
	private void startStage3_4() { //minute 13 - 14
		tasks.add(ThreadPoolManager.getInstance().schedule(new Runnable() {

			@Override
			public void run() {
				int npcId = 855765 + Rnd.get(0, 8);
				//3k + 2k + 1,5k = 6,5k
				rndSpawnTask(500, 855813, 10, 7, 10);
				rndSpawnTask(2000, npcId, 10, 7, 14);
				rndSpawnTask(6000, npcId, 15, 7, 14);
				rndSpawnTask(10000, npcId, 10, 7, 14);
			
				//1,5k points
				rndSpawnTask(15000, 856303, 1, 22, 23);
				startStage3_5();
			}
		}, 60000));
	}

	private void startStage3_5() { //minute 14 - 16
		tasks.add(ThreadPoolManager.getInstance().schedule(new Runnable() {

			@Override
			public void run() {
				//30k points
				SpawnEngine.spawnObject(SpawnEngine.addNewSingleTimeSpawn(mapId, 855822, 231.14f, 264.399f, 96.5f, (byte) 10), instanceId); // stormwing

				//3k points
				rndSpawnTask(25000, 856303, 1, 22, 23);
				rndSpawnTask(30000, 856303, 1, 22, 23);
				startStage4_1();
			}
		}, 60000));
	}

	private void startStage4_1() { // minute 16 - 17
		tasks.add(ThreadPoolManager.getInstance().schedule(new Runnable() {

			@Override
			public void run() {
				int npcId = 855765 + Rnd.get(0, 8);
				int npcId2 = 855788 + Rnd.get(0, 8);
				int npcId3 = 855811 + Rnd.get(0, 8);
				//2k + 1,6k + 4,8k = 8,4k
				rndSpawnTask(500, npcId, 10, 9, 13);
				rndSpawnTask(7000, npcId, 10, 9, 13);
				rndSpawnTask(15000, npcId2, 8, 9, 13);
				rndSpawnTask(22000, 855835, 8, 15, 20);
				rndSpawnTask(30000, npcId3, 8, 9, 13);
				
				//1,5k points
				rndSpawnTask(25000, 856303, 1, 22, 23);
				startStage4_2();
			}
		}, 120000));
	}

	private void startStage4_2() { //minute 17 - 18
		tasks.add(ThreadPoolManager.getInstance().schedule(new Runnable() {

			@Override
			public void run() {
				//1k + 2k + 6k = 9k
				rndSpawnTask(500, 855765, 10, 9, 13);
				rndSpawnTask(2000, 855788, 10, 9, 13);
					
				spawnAtPointsTask(13000, 855835, 0);
				spawnAtPointsTask(14500, 855836, 1);
				spawnAtPointsTask(16000, 855835, 2);
				spawnAtPointsTask(17500, 855836, 3);
				spawnAtPointsTask(19000, 855835, 4);
				spawnAtPointsTask(20500, 855836, 5);
				spawnAtPointsTask(22000, 855835, 6);
				spawnAtPointsTask(23500, 855836, 7);

				rndSpawnTask(27000, 855835, 7, 12, 20);
				
				//1,5k points
				rndSpawnTask(15000, 856303, 1, 22, 23);
				
				startStage4_3();
			}
		}, 60000));
	}

	private void startStage4_3() { //minute 18 - 19
		tasks.add(ThreadPoolManager.getInstance().schedule(new Runnable() {
		
			@Override
			public void run() {
				// 1,5k + 1k + 9,6k = 12,1k
				rndSpawnTask(500, 855765, 15, 9, 13);
				rndSpawnTask(2000, 855788, 5, 9, 13);
				
				spawnAtPointsTask(1000, 855836, 0);
				spawnAtPointsTask(1000, 855835, 1);
				spawnAtPointsTask(3000, 855836, 2);
				spawnAtPointsTask(3000, 855835, 3);
				spawnAtPointsTask(5000, 855836, 4);
				spawnAtPointsTask(5000, 855835, 5);
				spawnAtPointsTask(7000, 855836, 6);
				spawnAtPointsTask(7000, 855835, 7);

				spawnAtPointsTask(9000, 855836, 0);
				spawnAtPointsTask(90000, 855835, 1);
				spawnAtPointsTask(11000, 855836, 2);
				spawnAtPointsTask(11000, 855835, 3);
				spawnAtPointsTask(13000, 855836, 4);
				spawnAtPointsTask(13000, 855835, 5);
				spawnAtPointsTask(15000, 855836, 6);
				spawnAtPointsTask(15000, 855835, 7);

				spawnAtPointsTask(18000, 855836, 0);
				spawnAtPointsTask(18000, 855835, 1);
				spawnAtPointsTask(20000, 855836, 2);
				spawnAtPointsTask(20000, 855835, 3);
				spawnAtPointsTask(22000, 855836, 4);
				spawnAtPointsTask(22000, 855835, 5);
				spawnAtPointsTask(24000, 855836, 6);
				spawnAtPointsTask(24000, 855835, 7);
			
				//3k points
				rndSpawnTask(10000, 856303, 1, 22, 23);
				rndSpawnTask(20000, 856303, 1, 22, 23);
				startStage4_4();
			}
		}, 60000));
	}

	private void startStage4_4() { //minute 19 - 20
		tasks.add(ThreadPoolManager.getInstance().schedule(new Runnable() {

			@Override
			public void run() {
				//12k points + 8k points = 20k points
				//2k + 6,4k + 6,4k = 14,8k
				int npcId = 855788 + Rnd.get(0, 8);
				rndSpawnTask(500, 855765, 20, 9, 20);
				
				rndSpawnTask(8000, 855837, 4, 7, 9);
				rndSpawnTask(9000, npcId, 4, 7, 20);
				rndSpawnTask(10000, npcId, 4, 7, 20);

				npcId = 855765 + Rnd.get(0, 8);
				rndSpawnTask(16000, 855837, 4, 7, 9);
				rndSpawnTask(17000, npcId, 4, 7, 20);
				rndSpawnTask(18000, npcId, 4, 7, 20);

				npcId = 855765 + Rnd.get(0, 8);
				rndSpawnTask(24000, 855837, 4, 7, 9);
				rndSpawnTask(25000, npcId, 4, 7, 20);
				rndSpawnTask(26000, npcId, 4, 7, 20);

				npcId = 855765 + Rnd.get(0, 8);
				rndSpawnTask(32000, 855837, 4, 7, 9);
				rndSpawnTask(33000, npcId, 4, 7, 20);
				rndSpawnTask(34000, npcId, 4, 7, 20);

				//3k points
				rndSpawnTask(20000, 856303, 1, 22, 23);
				rndSpawnTask(25000, 856303, 1, 22, 23);
				startStage4_5();
			}
		}, 60000));
	}

	private void startStage4_5() { //minute 20 - 21
		tasks.add(ThreadPoolManager.getInstance().schedule(new Runnable() {

			@Override
			public void run() {
				int npcId = 855765 + Rnd.get(0, 8);
				int npcId2 = 855788 + Rnd.get(0, 8);
				//8k
				SpawnEngine.spawnObject(SpawnEngine.addNewSingleTimeSpawn(mapId, 855833, 230.8977f, 285.5198f, 96.42f, (byte) 80), instanceId);
				SpawnEngine.spawnObject(SpawnEngine.addNewSingleTimeSpawn(mapId, 855833, 211.254f, 264.134f, 96.53f, (byte) 0), instanceId);
				SpawnEngine.spawnObject(SpawnEngine.addNewSingleTimeSpawn(mapId, 855833, 231.2034f, 243.8273f, 96.37f, (byte) 30), instanceId);
				SpawnEngine.spawnObject(SpawnEngine.addNewSingleTimeSpawn(mapId, 855833, 251.3068f, 264.307f, 96.31f, (byte) 60), instanceId);

				//2k + 2k + 3,2k = 7,2k
				rndSpawnTask(500, npcId, 20, 9, 20);
				rndSpawnTask(5000, npcId2, 10, 7, 20);
				rndSpawnTask(9000, 855835, 4, 7, 20);
				rndSpawnTask(11000, 855836, 4, 7, 20);
			
				//3k
				rndSpawnTask(20000, 856303, 1, 22, 23);
				rndSpawnTask(25000, 856303, 1, 22, 23);
				
				startStage4_6();
			}
		}, 60000));
	}

	private void startStage4_6() { //minute 21 - 30
		//42k
		SpawnEngine.spawnObject(SpawnEngine.addNewSingleTimeSpawn(mapId, 855843, 231.14f, 264.399f, 96.5f, (byte) 10), instanceId); // general of illusion
		//6k
		rndSpawnTask(25000, 856303, 1, 22, 23);
		rndSpawnTask(30000, 856303, 1, 22, 23);
		rndSpawnTask(35000, 856303, 1, 22, 23);
	}

	private void startFailTask() {
		failTask = ThreadPoolManager.getInstance().schedule(new Runnable() {

			@Override
			public void run() {
				checkRank(reward.getPoints(), false);
			}
		}, 1800000); // 30min
	}

	public void addPoints(Npc npc, int points) {
		if (reward.isStartProgress()) {
			reward.addPoints(points);
			sendPacket(npc.getObjectTemplate().getNameId(), points);
			//TODO points >= 100mio -> stop. But its actually impossible to reach 100mio points.. I'll just leave it out
		}
	}

	private void sendPacket(final int nameId, final int point) {
		for (Player p : instance.getPlayersInside()) {
			if (p != null && p.isOnline()) {
				if (nameId != 0) {
					PacketSendUtility.sendPacket(p, new SM_SYSTEM_MESSAGE(1400237, new DescriptionId(nameId * 2 + 1), point));
				}
				PacketSendUtility.sendPacket(p, new SM_INSTANCE_SCORE(new LegionDominionScoreInfo(reward), reward, getTime()));
			}
		}
	}

	private int getTime() {
		long result = System.currentTimeMillis() - startTime;
		if (reward.isPreparing()) {
			return (int) (180000 - result);
		} else if (result < 1800000) {
			return (int) (1800000 - result);
		}
		return 0;
	}

	private void spawnAtPointsTask(int delay, int npcId, int index) {
		if (!reward.isStartProgress()) {
			return;
		}
		tasks.add(ThreadPoolManager.getInstance().schedule(new Runnable() {

			@Override
			public void run() {
				if (reward.isStartProgress()) {
					spawn(npcId, index);
				}
			}
		}, delay));
	}

	private void spawn(int npcId, int index) {
		if (index >= 0) {
			WorldPosition point = points.get(index);
			if (point != null) {
				SpawnTemplate template = SpawnEngine.addNewSingleTimeSpawn(mapId, npcId, point.getX(), point.getY(), point.getZ(), point.getHeading());
				if (template != null) {
					SpawnEngine.spawnObject(template, instanceId);
				}
			}
		} else {
			for (WorldPosition point : points) {
				if (!reward.isStartProgress()) {
					break;
				}
				SpawnTemplate template = SpawnEngine.addNewSingleTimeSpawn(mapId, npcId, point.getX(), point.getY(), point.getZ(), point.getHeading());
				if (template != null) {
					SpawnEngine.spawnObject(template, instanceId);
				}
			}
		}
	}

	private void rndSpawnTask(int delay, int npcId, int amount, int minRange, int maxRange) {
		if (!reward.isStartProgress()) {
			return;
		}
		tasks.add(ThreadPoolManager.getInstance().schedule(new Runnable() {

			@Override
			public void run() {
				if (reward.isStartProgress()) {
					rndSpawnInRange(npcId, amount, minRange, maxRange);
				}
			}
		}, delay));
	}

	private void rndSpawnInRange(int npcId, int amount, int minRange, int maxRange) {
		for (int i = 0; i < amount; i++) {
			if (!reward.isStartProgress()) {
				break;
			}
			SpawnTemplate template = getRndSpawnInRangeTemplate(npcId, minRange, maxRange);
			if (template != null) {
				SpawnEngine.spawnObject(template, instanceId);
			}
		}
	}

	private SpawnTemplate getRndSpawnInRangeTemplate(int npcId, int minRange, int maxRange) {
		for (int i = 0; i < 10; i++) { // 10 tries should be enough to find a spot. if not fuck it. I'm not going to implement a while loop
			if (!reward.isStartProgress()) {
				break;
			}
			float direction = (Rnd.get(0, 199) / 100f);
			int range = Rnd.get(minRange, maxRange);
			float x = 231.14f + (float) (Math.cos(Math.PI * direction) * range);
			float y = 264.399f + (float) (Math.sin(Math.PI * direction) * range);
			if (isValidPoint(x, y)) {
				return SpawnEngine.addNewSingleTimeSpawn(mapId, npcId, x, y, 96.51f, (byte) 50);
			}
		}
		return null;
	}

	private boolean isValidPoint(float x, float y) {
		if ((MathUtil.getDistance(x, y, 211.254f, 264.134f) >= 2.5) && (MathUtil.getDistance(x, y, 230.8977f, 285.5198f) >= 2.5)
			&& (MathUtil.getDistance(x, y, 251.3068f, 264.307f) >= 2.5) && (MathUtil.getDistance(x, y, 231.2034f, 243.8273f) >= 2.5)) {
			return true;
		}
		return false;
	}

	private void addWorldPoints() {
		points.add(new WorldPosition(mapId, 208.4323f, 264.0647f, 96.223f, (byte) 1));
		points.add(new WorldPosition(mapId, 214.5657f, 281.1983f, 96.1398f, (byte) 99));
		points.add(new WorldPosition(mapId, 230.9258f, 288.3556f, 96.5095f, (byte) 87));
		points.add(new WorldPosition(mapId, 248.5251f, 281.1702f, 96.3423f, (byte) 74));
		points.add(new WorldPosition(mapId, 254.0089f, 264.1608f, 96.1255f, (byte) 60));
		points.add(new WorldPosition(mapId, 248.0067f, 247.8215f, 96.0116f, (byte) 47));
		points.add(new WorldPosition(mapId, 231.1899f, 240.6733f, 96.1348f, (byte) 30));
		points.add(new WorldPosition(mapId, 214.2921f, 247.5521f, 96.267f, (byte) 20));
	}
	
	private void despawnAll() {
		for (Npc npc : instance.getNpcs()) {
			if (npc != null) {
				npc.getController().onDelete();
			}
		}
	}
	
	private void cancelAllTasks() {
		if (failTask != null && failTask.isCancelled()) {
			failTask.cancel(false);
		}
		for (Future<?> future : tasks) {
			if (future != null && !future.isCancelled()) {
				future.cancel(true);
			}
		}
	}
}
