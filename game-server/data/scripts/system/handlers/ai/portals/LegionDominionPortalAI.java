package ai.portals;

import java.time.DayOfWeek;
import java.time.ZonedDateTime;

import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.instance.handlers.InstanceHandler;
import com.aionemu.gameserver.model.DescriptionId;
import com.aionemu.gameserver.model.DialogAction;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.portal.PortalPath;
import com.aionemu.gameserver.network.aion.serverpackets.SM_DIALOG_WINDOW;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.services.instance.InstanceService;
import com.aionemu.gameserver.services.teleport.PortalService;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.time.ServerTime;
import com.aionemu.gameserver.world.WorldMapInstance;

/**
 * Created by Yeats on 19.02.2016.
 */
@AIName("legion_dominion_portal")
public class LegionDominionPortalAI extends PortalDialogAI {

	@Override
	public boolean onDialogSelect(Player player, int dialogId, int questId, int extendedRewardIndex) {
		if (dialogId != DialogAction.SETPRO1.id()) {
			PacketSendUtility.sendPacket(player, new SM_DIALOG_WINDOW(getObjectId(), 0, questId));
			return true;
		}
		ZonedDateTime now = ServerTime.now();
		if (now.getDayOfWeek() == DayOfWeek.WEDNESDAY && now.getHour() >= 8 && now.getHour() <= 10) {
			PacketSendUtility.sendPacket(player, new SM_SYSTEM_MESSAGE(1401306, new DescriptionId(301500000)));
			PacketSendUtility.sendPacket(player, new SM_DIALOG_WINDOW(getObjectId(), 0, questId));
			return true;
		}

		if (player.getLevel() < 65) {
			PacketSendUtility.sendPacket(player, new SM_SYSTEM_MESSAGE(1400179));
			PacketSendUtility.sendPacket(player, new SM_DIALOG_WINDOW(getObjectId(), 0, questId));
			return true;
		}

		if (player.isInAlliance() && !player.isInLeague()) {
			PortalPath portalPath = DataManager.PORTAL2_DATA.getPortalDialog(getNpcId(), dialogId, player.getRace());
			WorldMapInstance instance = InstanceService.getRegisteredInstance(301500000, player.getPlayerAlliance().getObjectId());
			if (portalPath == null) {
				PacketSendUtility.sendPacket(player, new SM_SYSTEM_MESSAGE(1401106));
				PacketSendUtility.sendPacket(player, new SM_DIALOG_WINDOW(getObjectId(), 0, questId));
				return true;
			}
			if (instance == null) {
				if (hasCd(player)) {
					return true;
				}
				// only alliance leader can open this instance
				if (player.getPlayerAlliance().isSomeCaptain(player)) {
					if (player.getInventory().decreaseByItemId(185000230, 1)) {
						PortalService.port(portalPath, player, getObjectId());
						return true;
					} else {
						PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_MSG_INSTANCE_CANT_ENTER_WITHOUT_ITEM());
						PacketSendUtility.sendPacket(player, new SM_DIALOG_WINDOW(getObjectId(), 0, 0));
						return true;
					}
				} else {
					PacketSendUtility.sendPacket(player, new SM_SYSTEM_MESSAGE(1400182));
					PacketSendUtility.sendPacket(player, new SM_DIALOG_WINDOW(getObjectId(), 0, questId));
					return true;
				}
			} else if (!instance.isRegistered(player.getObjectId())) {
				if (hasCd(player)) {
					return true;
				}
				InstanceHandler handler = instance.getInstanceHandler();

				if (handler != null && handler.canEnter(player)) {
					PortalService.port(portalPath, player, getObjectId());
				} else {
					PacketSendUtility.sendPacket(player, new SM_DIALOG_WINDOW(getObjectId(), 0, questId));
					return true;
				}
			} else if (instance.isRegistered(player.getObjectId())) {
				InstanceHandler handler = instance.getInstanceHandler();

				if (handler != null && handler.canEnter(player)) {
					PortalService.port(portalPath, player, getObjectId());
				} else {
					PacketSendUtility.sendPacket(player, new SM_DIALOG_WINDOW(getObjectId(), 0, questId));
					return true;
				}
				return true;
			} else {
				PacketSendUtility.sendPacket(player, new SM_SYSTEM_MESSAGE(1400185));
				PacketSendUtility.sendPacket(player, new SM_DIALOG_WINDOW(getObjectId(), 0, questId));
				return true;
			}
		} else {
			PacketSendUtility.sendPacket(player, new SM_SYSTEM_MESSAGE(1400544));
		}

		PacketSendUtility.sendPacket(player, new SM_DIALOG_WINDOW(getObjectId(), 0, questId));
		return true;
	}

	private boolean hasCd(Player player) {
		if (player.getPortalCooldownList().isPortalUseDisabled(301500000)) {
			PacketSendUtility.sendPacket(player, new SM_DIALOG_WINDOW(getObjectId(), 0, 0));
			PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_MSG_CANNOT_MAKE_INSTANCE_COOL_TIME());
			return true;
		}
		return false;
	}
}