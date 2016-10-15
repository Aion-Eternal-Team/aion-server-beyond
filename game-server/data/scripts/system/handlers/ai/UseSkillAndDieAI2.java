package ai;

import com.aionemu.gameserver.ai2.AIName;
import com.aionemu.gameserver.ai2.NpcAI2;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.VisibleObject;
import com.aionemu.gameserver.model.skill.NpcSkillList;
import com.aionemu.gameserver.skillengine.SkillEngine;
import com.aionemu.gameserver.skillengine.model.Skill;
import com.aionemu.gameserver.utils.ThreadPoolManager;

/**
 * @author Yeats
 *
 */
@AIName("useSkillAndDie")
public class UseSkillAndDieAI2 extends NpcAI2 {

	boolean canDie = true;
	
	@Override
	public void handleSpawned() {
		super.handleSpawned();
		scheduleSkill();
	}
	
	private void scheduleSkill() {
		if (getOwner().getCreatorId() == 0) {
			despawn(1);
			return;
		}
		NpcSkillList skillList = getOwner().getSkillList();
		if (skillList == null || skillList.getNpcSkills().isEmpty()) {
			despawn(1);
			return;
		}
		if (skillList.getNpcSkills().get(0).getConditionTemplate() != null) {
			canDie = skillList.getNpcSkills().get(0).getConditionTemplate().canDie();
			int despawn_time = skillList.getNpcSkills().get(0).getConditionTemplate().getDespawnTime();
			int delay = skillList.getNpcSkills().get(0).getConditionTemplate().getDelay();
			ThreadPoolManager.getInstance().schedule(new Runnable() {
				@Override
				public void run() {
					if (getOwner() != null && !getOwner().getLifeStats().isAlreadyDead()) {
						Creature spawner = findCreator(getOwner().getCreatorId());
						if (spawner != null && !spawner.getLifeStats().isAlreadyDead()) {
							SkillEngine.getInstance().getSkill(getOwner(), skillList.getNpcSkills().get(0).getSkillId(),  skillList.getNpcSkills().get(0).getSkillLevel(), getOwner()).useSkill();
						}
					}
					despawn(despawn_time);
				}
			}, delay);
		}
	}
	
	private Creature findCreator(int objId) {
		for (VisibleObject obj : getOwner().getKnownList().getKnownObjects().values()) {
			if (obj instanceof Creature) {
				if (obj.getObjectId() == objId) {
					return (Creature) obj;
				}
			}
		}
		return null;
	}
	
	private void despawn(int despawn_time) {
		ThreadPoolManager.getInstance().schedule(new Runnable() {
			@Override
			public void run() {
				if (getOwner() != null && !getOwner().getLifeStats().isAlreadyDead())
					getOwner().getController().delete();
			}
		}, despawn_time);
	}
	
	@Override
	public int modifyDamage(Creature creature, int damage) {
		if (!canDie)
			return 0;
		else
			return damage;
	}
	
	@Override
	public int modifyDamage(Skill skill, Creature creature, int damage) {
		if (!canDie)
			return 0;
		else
			return damage;
	}
	
	@Override
	public void handleDied() {
		super.handleDied();
		getOwner().getController().delete();
	}
}
