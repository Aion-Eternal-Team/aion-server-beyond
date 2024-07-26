package ai.instance.empyreanCrucible;

import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.ai.NpcAI;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.utils.PacketSendUtility;

/**
 * @author xTz, w4terbomb
 */
@AIName("empadministratorarminos")
public class EmpyreanAdministratorArminosAI extends NpcAI {

	public EmpyreanAdministratorArminosAI(Npc owner) {
		super(owner);
	}

	@Override
	protected void handleSpawned() {
		super.handleSpawned();
		startEvent();
	}

	private void startEvent() {
		switch (getNpcId()) {
			case 217744 -> handleEventForNpcId217744();
			case 217749 -> handleEventForNpcId217749();
			default -> {
			}
		}
	}

	private void handleEventForNpcId217744() {
		sendBroadcastMessages(new int[] { 1500247, 1500250, 1500251 }, new int[] { 8000, 20000, 60000 });
	}

	private void handleEventForNpcId217749() {
		sendBroadcastMessages(new int[] { 1500252, 1500253, 1500255 }, new int[] { 8000, 16000, 118000 });

		broadcastToMap(new int[] { 1400982, 1400988, 1400989, 1400990, 1401013, 1401014, 1401015 },
			new int[] { 25000, 27000, 29000, 31000, 93000, 113000, 118000 });
	}

	private void sendBroadcastMessages(int[] messageIds, int[] delays) {
		for (int i = 0; i < messageIds.length; i++) {
			PacketSendUtility.broadcastMessage(getOwner(), messageIds[i], delays[i]);
		}
	}

	private void broadcastToMap(int[] mapMessageIds, int[] delays) {
		for (int i = 0; i < mapMessageIds.length; i++) {
			PacketSendUtility.broadcastToMap(getOwner(), mapMessageIds[i], delays[i]);
		}
	}
}