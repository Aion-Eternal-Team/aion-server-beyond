package ai.instance.RukibukiCircusTroupe;

import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.model.actions.NpcActions;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;

import ai.AggressiveNpcAI;

/**
 * @author Ritsu
 */
@AIName("harlequinlordreshkasummon")
public class HarlequinLordReshkaSummonAI extends AggressiveNpcAI {

	private Npc boss;

	@Override
	protected void handleSpawned() {
		boss = getPosition().getWorldMapInstance().getNpc(233453);
		if (boss != null && !NpcActions.isAlreadyDead(boss)) {
			Creature player = boss.getPosition().getWorldMapInstance().getNpc(233453).getAggroList().getMostHated();
			getAggroList().addHate(player, 1);
		}
		super.handleSpawned();
	}
}