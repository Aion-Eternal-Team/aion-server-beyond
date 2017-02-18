package ai.instance.rakes;

import com.aionemu.gameserver.ai.AIActions;
import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.skillengine.SkillEngine;

import ai.ActionItemNpcAI;

@AIName("big_badaboom")
public class BigBadaboomAI extends ActionItemNpcAI {

	@Override
	protected void handleUseItemFinish(Player player) {
		if (player.isProtectionActive())
			player.getController().stopProtectionActiveTask();
		int morphSkill = 0;
		switch (getNpcId()) {
			case 231016: // Big Badaboom.
			case 231017: // Bigger Badaboom.
				morphSkill = 0x4E502E;
				break;
		}
		SkillEngine.getInstance().getSkill(getOwner(), morphSkill >> 8, morphSkill & 0xFF, player).useNoAnimationSkill();
		AIActions.deleteOwner(this);
	}
}
