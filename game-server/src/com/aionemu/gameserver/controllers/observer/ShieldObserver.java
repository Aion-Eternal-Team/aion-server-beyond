package com.aionemu.gameserver.controllers.observer;

import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.shield.Shield;
import com.aionemu.gameserver.model.utils3d.Point3D;
import com.aionemu.gameserver.services.SiegeService;
import com.aionemu.gameserver.utils.MathUtil;

/**
 * @author Wakizashi, Source
 */
public class ShieldObserver extends ActionObserver {

	private Creature creature;
	private Shield shield;
	private Point3D oldPosition;

	public ShieldObserver(Shield shield, Creature creature) {
		super(ObserverType.MOVE);
		this.creature = creature;
		this.shield = shield;
		this.oldPosition = new Point3D(creature.getX(), creature.getY(), creature.getZ());
	}

	@Override
	public void moved() {
		boolean passedThrough = false;

		if (SiegeService.getInstance().getFortress(shield.getId()).isUnderShield())
			if (!(creature.getZ() < shield.getZ() && oldPosition.getZ() < shield.getZ()))
				if (MathUtil.isInSphere(shield, (float) oldPosition.getX(), (float) oldPosition.getY(), (float) oldPosition.getZ(),
					shield.getTemplate().getRadius()) != MathUtil.isIn3dRange(shield, creature, shield.getTemplate().getRadius()))
					passedThrough = true;

		if (passedThrough && creature.getController().die()) {
			if (creature instanceof Player)
				((Player) creature).getFlyController().endFly(true);
			creature.getObserveController().removeObserver(this);
		} else {
			oldPosition.x = creature.getX();
			oldPosition.y = creature.getY();
			oldPosition.z = creature.getZ();
		}
	}
}
