package com.aionemu.gameserver.skillengine.task;

import com.aionemu.gameserver.model.gameobjects.VisibleObject;
import com.aionemu.gameserver.model.gameobjects.player.Player;

/**
 * @author ATracer, synchro2
 */
public abstract class AbstractCraftTask extends AbstractInteractionTask {

	protected int completeValue = 1000;
	protected int currentSuccessValue;
	protected int currentFailureValue;
	protected int skillLvlDiff;
	protected CraftType craftType = CraftType.NORMAL;
	
	protected enum CraftType {
		NORMAL(1), 
		CRIT_BLUE(2), 
		CRIT_PURPLE(3);
		
		private int critId;
		
		private CraftType(int critId) {
			this.critId = critId;			
		}
		
		public int getCritId() {
			return critId;
		}
	}
	
	/**
	 * @param requestor
	 * @param responder
	 * @param successValue
	 * @param failureValue
	 */
	public AbstractCraftTask(Player requestor, VisibleObject responder, int skillLvlDiff) {
		super(requestor, responder);
		this.skillLvlDiff = skillLvlDiff;
	}

	@Override
	protected boolean onInteraction() {
		if (currentSuccessValue == completeValue) {
			return onSuccessFinish();
		}
		if (currentFailureValue == completeValue) {
			onFailureFinish();
			return true;
		}

		analyzeInteraction();

		sendInteractionUpdate();
		return false;
	}

	/**
	 * Perform interaction calculation
	 */
	protected abstract void analyzeInteraction();

	protected abstract void sendInteractionUpdate();

	protected abstract boolean onSuccessFinish();

	protected abstract void onFailureFinish();
}
