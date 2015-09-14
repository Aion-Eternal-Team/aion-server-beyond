package com.aionemu.gameserver.skillengine.effect;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;

import com.aionemu.gameserver.controllers.observer.ActionObserver;
import com.aionemu.gameserver.controllers.observer.ObserverType;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.stats.container.CreatureGameStats;
import com.aionemu.gameserver.network.aion.serverpackets.SM_ATTACK_STATUS.LOG;
import com.aionemu.gameserver.network.aion.serverpackets.SM_ATTACK_STATUS.TYPE;
import com.aionemu.gameserver.skillengine.model.Effect;
import com.aionemu.gameserver.skillengine.model.Skill;
import com.aionemu.gameserver.skillengine.model.Skill.SkillMethod;
import com.aionemu.gameserver.skillengine.model.SkillType;

/**
 * @author ViAl
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "MagicCounterAtkEffect")
public class MagicCounterAtkEffect extends EffectTemplate {

	@XmlAttribute
	protected int maxdmg;

	// TODO bosses are resistent to this?
	@Override
	public void applyEffect(Effect effect) {
		effect.addToEffectedController();
	}

	@Override
	public void startEffect(final Effect effect) {
		final Creature effector = effect.getEffector();
		final Creature effected = effect.getEffected();
		final CreatureGameStats<? extends Creature> cls = effect.getEffected().getGameStats();
		if (effected instanceof Player) {
			ActionObserver observer = new ActionObserver(ObserverType.ENDSKILLCAST) {

				@Override
				public void endSkillCast(final Skill skill) {
					if (skill.getSkillMethod() == SkillMethod.ITEM)
						return;
					if (skill.getSkillTemplate().getType() == SkillType.MAGICAL) {
						int damage = ((int) (cls.getMaxHp().getBase() / 100f * value) > maxdmg ? maxdmg : (int) (cls.getMaxHp().getBase() / 100f * value));
						effected.getController().onAttack(effector, effect.getSkillId(), TYPE.MAGICCOUNTERATK, damage, true, LOG.MAGICCOUNTERATK);
					}
				}
			};
			effect.setActionObserver(observer, position);
			effected.getObserveController().addObserver(observer);
		} else {
			ActionObserver observer = new ActionObserver(ObserverType.STARTSKILLCAST) {

				@Override
				public void endSkillCast(final Skill skill) {
					if (skill.getSkillMethod() == SkillMethod.ITEM)
						return;
					if (skill.getSkillTemplate().getType() == SkillType.MAGICAL) {
						int damage = ((int) (cls.getMaxHp().getBase() / 100f * value) > maxdmg ? maxdmg : (int) (cls.getMaxHp().getBase() / 100f * value));
						effected.getController().onAttack(effector, effect.getSkillId(), TYPE.MAGICCOUNTERATK, damage, true, LOG.MAGICCOUNTERATK);
					}
				}
			};
			effect.setActionObserver(observer, position);
			effected.getObserveController().addObserver(observer);
		}
	}

	@Override
	public void endEffect(Effect effect) {
		ActionObserver observer = effect.getActionObserver(position);
		if (observer != null)
			effect.getEffected().getObserveController().removeObserver(observer);
	}

}
