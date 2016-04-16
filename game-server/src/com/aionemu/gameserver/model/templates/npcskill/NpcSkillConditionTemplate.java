package com.aionemu.gameserver.model.templates.npcskill;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;

/**
 * @author Yeats
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "cond")
public class NpcSkillConditionTemplate {

	@XmlAttribute(name = "cond_type")
	protected NpcSkillCondition condType = NpcSkillCondition.NONE;
	@XmlAttribute(name = "hp_below")
	protected int hpBelow = 50;
	@XmlAttribute(name = "skill_id")
	protected int skillId;
	@XmlAttribute(name = "range")
	protected int range = 10;
	@XmlAttribute(name = "npc_id")
	protected int npc_id;
	@XmlAttribute(name = "delay")
	protected int delay;
	@XmlAttribute(name = "min_distance")
	protected int min_distance;
	@XmlAttribute(name = "max_distance")
	protected int max_distance;
	@XmlAttribute(name = "direction")
	protected int direction;
	@XmlAttribute(name = "random_direction")
	protected boolean random_direction;
	@XmlAttribute(name = "min_amount")
	protected int min_amount = 1;
	@XmlAttribute(name = "max_amount")
	protected int max_amount = 0;
	@XmlAttribute(name = "can_die")
	protected boolean canDie = true;
	@XmlAttribute(name = "despawn_time")
	protected int despawn_time = 500;

	public NpcSkillCondition getCondType() {
		return condType;
	}

	public int getHpBelow() {
		return hpBelow;
	}

	public int getSkillId() {
		return skillId;
	}

	public int getRange() {
		return range;
	}

	public int getNpcId() {
		return npc_id;
	}

	public int getDelay() {
		return delay;
	}

	public int getMinDistance() {
		return min_distance;
	}

	public int getMaxDistance() {
		return max_distance;
	}

	public int getDirection() {
		return direction;
	}

	public boolean isRandomDirection() {
		return random_direction;
	}

	public int getMinAmount() {
		return min_amount;
	}

	public int getMaxAmount() {
		return max_amount;
	}

	public boolean canDie() {
		return canDie;
	}

	public int getDespawnTime() {
		return despawn_time;
	}
}
