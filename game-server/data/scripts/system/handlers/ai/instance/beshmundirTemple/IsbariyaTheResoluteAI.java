package ai.instance.beshmundirTemple;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import com.aionemu.commons.utils.Rnd;
import com.aionemu.gameserver.ai.AIActions;
import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.geometry.Point3D;
import com.aionemu.gameserver.model.templates.spawns.SpawnTemplate;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.skillengine.SkillEngine;
import com.aionemu.gameserver.spawnengine.SpawnEngine;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.utils.ThreadPoolManager;

import ai.AggressiveNpcAI;

/**
 * @author Luzien
 */
@AIName("isbariya")
public class IsbariyaTheResoluteAI extends AggressiveNpcAI {

	private volatile int stage = 0;
	private AtomicBoolean isStart = new AtomicBoolean();
	private List<Point3D> soulLocations = new ArrayList<>();
	private Future<?> basicSkillTask;
	private Future<?> spawnTask;

	public IsbariyaTheResoluteAI(Npc owner) {
		super(owner);
	}

	@Override
	protected void handleAttack(Creature creature) {
		super.handleAttack(creature);
		if (isStart.compareAndSet(false, true)) {
			PacketSendUtility.broadcastMessage(getOwner(), 342051, 1000);
			getPosition().getWorldMapInstance().setDoorState(535, false);
			startBasicSkillTask();
		}
		checkPercentage(getLifeStats().getHpPercentage());
	}

	@Override
	protected void handleSpawned() {
		super.handleSpawned();
		soulLocations.add(new Point3D(1580.5f, 1572.8f, 304.64f));
		soulLocations.add(new Point3D(1582.1f, 1571.2f, 304.64f));
		soulLocations.add(new Point3D(1583.3f, 1569.9f, 304.64f));
		soulLocations.add(new Point3D(1585.3f, 1568.1f, 304.64f));
		soulLocations.add(new Point3D(1586.4f, 1567.1f, 304.64f));
		soulLocations.add(new Point3D(1588.3f, 1566.2f, 304.64f));
	}

	@Override
	protected void handleDied() {
		cancelTasks(spawnTask, basicSkillTask);
		super.handleDied();
		PacketSendUtility.broadcastMessage(getOwner(), 342055);
		getPosition().getWorldMapInstance().setDoorState(535, true);
	}

	@Override
	protected void handleDespawned() {
		cancelTasks(spawnTask, basicSkillTask);
		super.handleDespawned();
	}

	@Override
	protected void handleBackHome() {
		PacketSendUtility.broadcastMessage(getOwner(), 342056);
		cancelTasks(spawnTask, basicSkillTask);
		super.handleBackHome();
		isStart.set(false);
		getPosition().getWorldMapInstance().setDoorState(535, true);
		stage = 0;
	}

	private void checkPercentage(int hpPercentage) {
		if (hpPercentage <= 75 && stage < 1) {
			stage = 1;
			PacketSendUtility.broadcastToMap(getOwner(), SM_SYSTEM_MESSAGE.STR_MSG_IDCatacombs_Boss_ArchPriest_3phase());
			launchSpecial();
		}
		if (hpPercentage <= 50 && stage < 2) {
			PacketSendUtility.broadcastToMap(getOwner(), SM_SYSTEM_MESSAGE.STR_MSG_IDCatacombs_Boss_ArchPriest_2phase());
			stage = 2;
		}
		if (hpPercentage <= 25 && stage < 3) {
			stage = 3;
		}
	}

	private void startBasicSkillTask() {
		basicSkillTask = ThreadPoolManager.getInstance().scheduleAtFixedRate(new Runnable() {

			@Override
			public void run() {
				if (isDead())
					cancelTasks(basicSkillTask);
				else
					SkillEngine.getInstance().getSkill(getOwner(), 18912 + Rnd.get(2), 55, getOwner()).useNoAnimationSkill();
			}

		}, 0, 24000);
	}

	private void launchSpecial() {
		if (isDead() || stage == 0 || getOwner().getPosition().getWorldMapInstance() == null) {
			cancelTasks(basicSkillTask);
			return;
		}
		int delay = 10000;

		switch (stage) {
			case 1:
				SkillEngine.getInstance().getSkill(getOwner(), 18959, 50, getTargetPlayer()).useNoAnimationSkill();
				spawnSouls();
				delay = 25000;
				break;
			case 2:
				rndSpawn(281660, 5);
				break;
			case 3:
				rndSpawn(281659, 1);
				AIActions.useSkill(this, 18993);
				delay = 20000;
				break;
		}
		scheduleSpecial(delay);
	}

	private void rndSpawn(int npcId, int count) {
		for (int i = 0; i < count; i++) {
			SpawnTemplate template = rndSpawnInRange(npcId);
			SpawnEngine.spawnObject(template, getPosition().getInstanceId());
		}
	}

	private void spawnSouls() {
		List<Point3D> points = new ArrayList<>();
		points.addAll(soulLocations);
		int count = Rnd.get(3, 6);
		for (int i = 0; i < count; i++) {
			if (!points.isEmpty()) {
				Point3D spawn = points.remove(Rnd.get(points.size()));
				spawn(281645, spawn.getX(), spawn.getY(), spawn.getZ(), (byte) 18);
			}
		}
	}

	private Player getTargetPlayer() {
		List<Player> players = new ArrayList<>();
		getKnownList().forEachPlayer(player -> {
			if (!player.isDead() && PositionUtil.isInRange(player, getOwner(), 40) && player != getTarget()) {
				players.add(player);
			}
		});
		return Rnd.get(players);
	}

	private void scheduleSpecial(int delay) {
		spawnTask = ThreadPoolManager.getInstance().schedule(this::launchSpecial, delay);
	}

	private SpawnTemplate rndSpawnInRange(int npcId) {
		float direction = Rnd.get(0, 199) / 100f;
		float x1 = (float) (Math.cos(Math.PI * direction) * 5);
		float y1 = (float) (Math.sin(Math.PI * direction) * 5);
		return SpawnEngine.newSingleTimeSpawn(getPosition().getMapId(), npcId, getPosition().getX() + x1, getPosition().getY() + y1, getPosition().getZ(),
			getPosition().getHeading());
	}

	private void cancelTasks(Future<?>... tasks) {
		for (Future<?> task : tasks)
			if (task != null && !task.isCancelled())
				task.cancel(true);
	}

}
