package ai.worlds.inggison;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.ai.NpcAI;
import com.aionemu.gameserver.ai.poll.AIQuestion;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.VisibleObject;
import com.aionemu.gameserver.model.skill.QueuedNpcSkillEntry;
import com.aionemu.gameserver.model.templates.npcskill.QueuedNpcSkillTemplate;
import com.aionemu.gameserver.utils.ThreadPoolManager;

/**
 * @author Estrayl
 */
@AIName("sematariux_egg")
public class SematariuxEggAI extends NpcAI {

	private Future<?> spawnTask;

	public SematariuxEggAI(Npc owner) {
		super(owner);
	}

	@Override
	protected void handleSpawned() {
		super.handleSpawned();
		spawnTask = ThreadPoolManager.getInstance().schedule(() -> spawn(281456, getOwner().getX(), getOwner().getY(), getOwner().getZ(), (byte) 0), 4,
			TimeUnit.MINUTES);
	}

	@Override
	protected void handleDied() {
		for (VisibleObject vo : getKnownList().getKnownObjects().values()) {
			if (vo instanceof Npc && ((Npc) vo).getNpcId() == 216520) {
				((Npc) vo).getEffectController().removeEffect(18726);
				((Npc) vo).getQueuedSkills().offer(new QueuedNpcSkillEntry(new QueuedNpcSkillTemplate(19199, 1, 100, 0, 3000)));
				break;
			}
		}
		cancelSpawnTask();
		super.handleDied();
	}

	@Override
	protected void handleDespawned() {
		cancelSpawnTask();
		super.handleDespawned();
	}

	private void cancelSpawnTask() {
		if (spawnTask != null && !spawnTask.isCancelled())
			spawnTask.cancel(true);
	}

	@Override
	public boolean ask(AIQuestion question) {
		switch (question) {
			case SHOULD_DECAY:
			case SHOULD_LOOT:
			case SHOULD_REWARD:
				return false;
			default:
				return super.ask(question);
		}
	}
}
