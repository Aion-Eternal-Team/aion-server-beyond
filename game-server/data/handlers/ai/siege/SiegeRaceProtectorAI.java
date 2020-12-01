package ai.siege;

import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.ai.poll.AIQuestion;
import com.aionemu.gameserver.model.gameobjects.Npc;

/**
 * @author Source
 */
@AIName("siege_raceprotector")
public class SiegeRaceProtectorAI extends SiegeNpcAI {

	public SiegeRaceProtectorAI(Npc owner) {
		super(owner);
	}

	@Override
	public boolean ask(AIQuestion question) {
		switch (question) {
			case SHOULD_DECAY:
			case SHOULD_REWARD:
			case SHOULD_LOOT:
				return true;
			default:
				return super.ask(question);
		}
	}
}
