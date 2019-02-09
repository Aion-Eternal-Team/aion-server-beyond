package instance;

import java.util.concurrent.Future;

import com.aionemu.gameserver.instance.handlers.GeneralInstanceHandler;
import com.aionemu.gameserver.instance.handlers.InstanceID;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.animations.TeleportAnimation;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.network.aion.serverpackets.SM_DIE;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.services.teleport.TeleportService;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.world.zone.ZoneInstance;
import com.aionemu.gameserver.world.zone.ZoneName;

/**
 * @author Cheatkiller
 */
@InstanceID(301270000)
public class LinkgateFoundryInstance extends GeneralInstanceHandler {

	private Future<?> timeCheckTask;
	private byte timeInMin = -1;
	private byte secretLabEntranceCount = 0;
	private boolean isAgentSpawned = false;

	@Override
	public void onEnterInstance(Player player) {
		if (!isAgentSpawned) {
			isAgentSpawned = true;
			spawn(player.getRace() == Race.ELYOS ? 206361 : 206362, 348.00464f, 252.13882f, 311.36136f, (byte) 10);
		}
	}

	@Override
	public void onDie(Npc npc) {
		Player player = npc.getAggroList().getMostPlayerDamage();
		switch (npc.getNpcId()) {
			case 233898:
			case 234990:
			case 234991:
				spawn(player.getRace() == Race.ELYOS ? 702338 : 702389, 246.74345f, 258.35843f, 312.32327f, (byte) 10);
				break;
		}
	}

	@Override
	public void handleUseItemFinish(Player player, Npc npc) {
		switch (npc.getNpcId()) {
			case 804578:
				sendMsg(SM_SYSTEM_MESSAGE.STR_MSG_IDLDF4_Re_01_Time_01()); // 20 min
				startTimeCheck();
				npc.getController().die(); // not a static door in client data o.O
				break;
			case 234193:
				npc.getController().die();
				break;
			case 804629:
				TeleportService.teleportTo(player, 301270000, 228.37f, 262.7f, 313, (byte) 120);
				break;
			case 702592:
				TeleportService.teleportTo(player, 301270000, 211.32f, 260, 314, (byte) 0, TeleportAnimation.FADE_OUT_BEAM);
				break;
			case 702590:
				TeleportService.teleportTo(player, 301270000, 257.11f, 323, 271, (byte) 60, TeleportAnimation.FADE_OUT_BEAM);
				npc.getController().delete();
				secretLabEntranceCount++;
				if (secretLabEntranceCount < 3) {
					spawn(234992, 244.1839f, 322.5356f, 270.9474f, (byte) 0);
				} else
					sendMsg(SM_SYSTEM_MESSAGE.STR_MSG_IDLDF4_Re_01_secret_room_03());
				break;
		}
	}

	private void startTimeCheck() {
		timeCheckTask = ThreadPoolManager.getInstance().scheduleAtFixedRate(new Runnable() {

			@Override
			public void run() {
				timeInMin++;
				switch (timeInMin) {
					case 5:
						sendMsg(SM_SYSTEM_MESSAGE.STR_MSG_IDLDF4_Re_01_Time_02());
						break;
					case 10:
						sendMsg(SM_SYSTEM_MESSAGE.STR_MSG_IDLDF4_Re_01_Time_03());
						break;
					case 15:
						sendMsg(SM_SYSTEM_MESSAGE.STR_MSG_IDLDF4_Re_01_Time_04());
						break;
					case 17:
						sendMsg(SM_SYSTEM_MESSAGE.STR_MSG_IDLDF4_Re_01_Time_05());
						break;
					case 19:
						sendMsg(SM_SYSTEM_MESSAGE.STR_MSG_IDLDF4_Re_01_Time_06());
						break;
					case 20:
						sendMsg(SM_SYSTEM_MESSAGE.STR_MSG_IDLDF4_Re_01_Time_07());
						instance.forEachNpc(npc -> {
							if (npc.getNpcId() != 233898 && npc.getNpcId() != 234990 && npc.getNpcId() != 234991 // belsagos does not despawn
								&& npc.getNpcId() != 702339 && npc.getNpcId() != 804629) { // teleport device does not despawn
								npc.getController().delete();
							}
						});
						if (timeCheckTask != null && !timeCheckTask.isDone()) {
							timeCheckTask.cancel(true);
						}
				}
			}

		}, 0, 60000);
	}

	@Override
	public void onEnterZone(Player player, ZoneInstance zone) {
		if (zone.getAreaTemplate().getZoneName() == ZoneName.get("IDLDF4RE_01_ITEMUSEAREA_BOSS_301270000")) {
			if (timeCheckTask != null && !timeCheckTask.isDone()) {
				timeCheckTask.cancel(true);
			}
		}
	}

	@Override
	public boolean onDie(final Player player, Creature lastAttacker) {
		PacketSendUtility.sendPacket(player, new SM_DIE(player, 8));
		return true;
	}

	@Override
	public void onInstanceDestroy() {
		if (timeCheckTask != null && !timeCheckTask.isDone()) {
			timeCheckTask.cancel(true);
		}
	}
}
