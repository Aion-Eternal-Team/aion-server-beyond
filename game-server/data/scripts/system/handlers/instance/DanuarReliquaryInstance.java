package instance;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.aionemu.commons.utils.Rnd;
import com.aionemu.gameserver.instance.handlers.GeneralInstanceHandler;
import com.aionemu.gameserver.instance.handlers.InstanceID;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.teleport.TeleportService;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;

/**
 * @author Yeats
 */
@InstanceID(301110000)
public class DanuarReliquaryInstance extends GeneralInstanceHandler {

	private AtomicBoolean isCursedModorActive = new AtomicBoolean();
	private AtomicInteger cloneKills = new AtomicInteger();
	private ScheduledFuture<?> wipeTask;

	protected int getExitId() {
		return 730843;
	}

	protected int getTreasureBoxId() {
		return 701795;
	}

	protected int getEnragedModorId() {
		return 231305;
	}

	protected int getCursedModorId() {
		return 231304;
	}

	protected int getRealCloneId() {
		return 284383; // 855244
	}

	protected int getFakeCloneId() {
		return 284384; // 855244
	}

	@Override
	public void onDie(Npc npc) {
		final int npcId = npc.getNpcId();
		switch (npcId) {
			case 284377: // Idean Obscura
			case 284378: // Idean Lapilima
			case 284379: // Danuar Reliquary Novun
				despawnNpc(npc);
				if (isNullOrDead(284377) && isNullOrDead(284378) && isNullOrDead(284379) && isCursedModorActive.compareAndSet(false, true)) {
					spawn(getCursedModorId(), 256.62f, 257.79f, 241.79f, (byte) 90);
					Npc cursedModor = getNpc(getCursedModorId());
					if (cursedModor != null) {
						//SkillEngine.getInstance().getSkill(cursedModor, 21168, 1, cursedModor).useWithoutPropSkill();
						//sendMsg(SM_SYSTEM_MESSAGE.STR_MSG_IDLDF5_INDER_RUNE_START());
						scheduleWipe();
					}
				}
				break;
			case 284383: // Modor's Clone
			case 855244: // Modor's Clone
				despawnNpcs(getFakeCloneId());
				despawnNpc(npc);
				ThreadPoolManager.getInstance().schedule(new Runnable() {
					@Override
					public void run() {
						if (cloneKills.incrementAndGet() >= 3) {
							spawn(getEnragedModorId(), 256.62f, 257.79f, 241.79f, (byte) 90);
						} else {
							spawnClones();
						}
					}
				}, 2000);
				break;
			case 231305: // Enraged Queen Modor
			case 234691: // Crazed Modor
				onInstanceEnd(true);
				break;
			case 701795: // Treasure Box
			case 802183:
				break;
			default:
				despawnNpc(npc);
				break;
		}
	}

	private void spawnClones() {
		int spawnCase = Rnd.get(1, 5);
		switch (spawnCase) {
			case 1:
				spawn(getRealCloneId(), 255.5489f, 293.42154f, 253.78925f, (byte) 90);
				break;
			case 2:
				spawn(getRealCloneId(), 232.5363f, 263.90112f, 248.65384f, (byte) 114);
				break;
			case 3:
				spawn(getRealCloneId(), 240.11194f, 235.08876f, 251.14906f, (byte) 17);
				break;
			case 4:
				spawn(getRealCloneId(), 271.23627f, 230.30913f, 250.92981f, (byte) 42);
				break;
			case 5:
				spawn(getRealCloneId(), 284.6919f, 262.7201f, 248.75252f, (byte) 63);
				break;
		}

		if (spawnCase != 1)
			spawn(getFakeCloneId(), 255.5489f, 293.42154f, 253.78925f, (byte) 90);
		if (spawnCase != 2)
			spawn(getFakeCloneId(), 232.5363f, 263.90112f, 248.65384f, (byte) 114);
		if (spawnCase != 3)
			spawn(getFakeCloneId(), 240.11194f, 235.08876f, 251.14906f, (byte) 17);
		if (spawnCase != 4)
			spawn(getFakeCloneId(), 271.23627f, 230.30913f, 250.92981f, (byte) 42);
		if (spawnCase != 5)
			spawn(getFakeCloneId(), 284.6919f, 262.7201f, 248.75252f, (byte) 63);
	}

	private void despawnNpc(Npc npc) {
		if (npc != null)
			npc.getController().delete();
	}

	private boolean isNullOrDead(int npcId) {
		return getNpc(npcId) == null || getNpc(npcId).isDead();
	}

	private void despawnNpcs(int npcId) {
		for (Npc npc : instance.getNpcs(npcId))
			despawnNpc(npc);
	}

	protected void onInstanceEnd(boolean successful) {
		cancelWipeTask();
		Npc modor = getNpc(getEnragedModorId());
		if (modor == null) {
			modor = getNpc(getCursedModorId());
		}
		if (modor != null) {
			if (successful) {
				PacketSendUtility.broadcastMessage(modor, 343629);
			} else {
				PacketSendUtility.broadcastMessage(modor, 1500739);
			}
		}
		instance.forEachNpc(npc -> npc.getController().delete());
		spawn(getExitId(), 255.66669f, 263.78525f, 241.7986f, (byte) 86); // Spawn exit portal
		if (successful)
			spawn(getTreasureBoxId(), 256.65f, 258.09f, 241.78f, (byte) 100); // Treasure Box
	}

	private void scheduleWipe() {
		wipeTask = ThreadPoolManager.getInstance().schedule(() -> {
			spawn(284386, 256.60f, 257.99f, 241.78f, (byte) 0);
			onInstanceEnd(false);
		}, 15 * 60000);
	}

	void cancelWipeTask() {
		if (wipeTask != null && !wipeTask.isCancelled())
			wipeTask.cancel(false);
	}

	@Override
	public void onPlayerLogOut(Player player) {
		super.onPlayerLogOut(player);
		if (player.isDead())
			TeleportService.moveToBindLocation(player);
	}

	@Override
	public void onInstanceDestroy() {
		cancelWipeTask();
	}

	@Override
	public void onBackHome(Npc npc) {
		if (npc.getNpcId() == getEnragedModorId() || npc.getNpcId() == getCursedModorId()) {
			instance.forEachNpc(other -> {
				if (other.getNpcId() != npc.getNpcId()) {
					other.getController().delete();
				}
			});
		}
	}
}
