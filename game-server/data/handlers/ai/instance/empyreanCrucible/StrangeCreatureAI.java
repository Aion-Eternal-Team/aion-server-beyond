package ai.instance.empyreanCrucible;

import com.aionemu.gameserver.ai.AIActions;
import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;

import ai.GeneralNpcAI;

/**
 * @author Luzien, w4terbomb
 */
@AIName("strange_creature")
public class StrangeCreatureAI extends GeneralNpcAI {

	public StrangeCreatureAI(Npc owner) {
		super(owner);
	}

	@Override
	protected void handleSpawned() {
		super.handleSpawned();
		startEventTask();
		startLifeTask();
	}

	private void startEventTask() {
		ThreadPoolManager.getInstance().schedule(() -> {
			if (!isDead())
				PacketSendUtility.broadcastMessage(getOwner(), 341444);
			getOwner().queueSkill(17914, 34, 0);
		}, 500);
	}

	private void startLifeTask() {
		ThreadPoolManager.getInstance().schedule(() -> AIActions.deleteOwner(StrangeCreatureAI.this), 6500);
	}

}
