package ai.instance.drakenspire;

import java.util.concurrent.atomic.AtomicBoolean;

import com.aionemu.gameserver.ai.AIActions;
import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.ai.poll.AIQuestion;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.skillengine.model.Effect;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;
import com.aionemu.gameserver.utils.ThreadPoolManager;

import ai.GeneralNpcAI;

/**
 * @author Estrayl
 */
@AIName("orissans_summon")
public class OrissansSummonAI extends GeneralNpcAI {

	private final AtomicBoolean isActive = new AtomicBoolean();

	public OrissansSummonAI(Npc owner) {
		super(owner);
	}

	@Override
	public float modifyDamage(Creature attacker, float damage, Effect effect) {
		if (attacker != getOwner() && attacker instanceof Npc) {
			if (isActive.compareAndSet(false, true)) {
				ThreadPoolManager.getInstance().schedule(() -> {
					if (getNpcId() == 855699) {
						AIActions.targetSelf(this);
						AIActions.useSkill(this, 21638, 67);
					} else {
						AIActions.targetCreature(this, attacker);
						AIActions.useSkill(this, 21639, 13);
					}
				}, 1000);
			}
			return 10000;
		}
		return damage;
	}

	@Override
	public void onEndUseSkill(SkillTemplate skillTemplate, int skillLevel) {
		getOwner().getController().die();
	}

	@Override
	protected void handleBackHome() {
	}

	@Override
	public boolean ask(AIQuestion question) {
		return switch (question) {
			case CAN_RESIST_ABNORMAL -> true;
			case SHOULD_REWARD -> false;
			default -> super.ask(question);
		};
	}
}
