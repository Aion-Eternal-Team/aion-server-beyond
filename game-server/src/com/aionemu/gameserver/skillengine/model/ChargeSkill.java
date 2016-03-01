package com.aionemu.gameserver.skillengine.model;

import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.skillengine.properties.Properties.CastState;

/**
 * @author Cheatkiller
 */
public class ChargeSkill extends Skill {

	public ChargeSkill(SkillTemplate skillTemplate, Player effector, int skillLevel, Creature firstTarget, ItemTemplate itemTemplate) {
		super(skillTemplate, effector, skillLevel, firstTarget, null, false);
	}

	@Override
	public void calculateSkillDuration() {

	}

	@Override
	public boolean useSkill() {
		if (!canUseSkill(CastState.CAST_END)) {
			effector.getController().cancelCurrentSkill(null);
			return false;
		}
		effector.getObserveController().notifyStartSkillCastObservers(this);
		effector.setCasting(this);
		effector.getObserveController().attach(conditionChangeListener);
		endCast();
		return true;
	}
}
