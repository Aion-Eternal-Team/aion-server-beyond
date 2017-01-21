package ai.instance.unstableSplinterpath;

import com.aionemu.commons.utils.Rnd;
import com.aionemu.gameserver.ai.AIActions;
import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.ai.AIState;
import com.aionemu.gameserver.ai.manager.EmoteManager;
import com.aionemu.gameserver.model.EmotionType;
import com.aionemu.gameserver.model.actions.NpcActions;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.state.CreatureState;
import com.aionemu.gameserver.network.aion.serverpackets.SM_EMOTION;
import com.aionemu.gameserver.skillengine.SkillEngine;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;

import ai.AggressiveNpcAI;

/**
 * @author Luzien
 * @edit Cheatkiller
 */
@AIName("unstablekaluva")
public class UnstableKaluvaAI extends AggressiveNpcAI {

	private boolean canThink = true;
	private boolean isInMove = false;

	@Override
	protected void handleAttack(Creature creature) {
		super.handleAttack(creature);
		if (Rnd.chance() < 3) {
			if (!isInMove) {
				isInMove = true;
				moveToSpawner(randomEgg());
			}
		}
	}

	private void moveToSpawner(int egg) {
		Npc spawner = getPosition().getWorldMapInstance().getNpc(egg);
		if (spawner != null) {
			SkillEngine.getInstance().getSkill(getOwner(), 19152, 55, getOwner()).useNoAnimationSkill();
			canThink = false;
			EmoteManager.emoteStopAttacking(getOwner());
			setStateIfNot(AIState.FOLLOWING);
			getOwner().setState(CreatureState.ACTIVE, true);
			PacketSendUtility.broadcastPacket(getOwner(), new SM_EMOTION(getOwner(), EmotionType.START_EMOTE2, 0, getObjectId()));
			AIActions.targetCreature(this, getPosition().getWorldMapInstance().getNpc(egg));
			getMoveController().moveToTargetObject();
		}
	}

	@Override
	protected void handleMoveArrived() {
		if (canThink == false) {
			if (getOwner().getTarget() instanceof Npc) {
				Npc spawner = (Npc) getOwner().getTarget();
				if (spawner != null) {
					spawner.getEffectController().removeEffect(19222);
					SkillEngine.getInstance().getSkill(getOwner(), 19223, 55, spawner).useNoAnimationSkill();
					getEffectController().removeEffect(19152);
				}
			}

			ThreadPoolManager.getInstance().schedule(new Runnable() {

				@Override
				public void run() {
					canThink = true;
					Creature creature = getAggroList().getMostHated();
					if (creature == null || !getOwner().canSee(creature) || NpcActions.isAlreadyDead(creature)) {
						setStateIfNot(AIState.FIGHT);
						think();
					} else {
						getOwner().setTarget(creature);
						getOwner().getGameStats().renewLastAttackTime();
						getOwner().getGameStats().renewLastAttackedTime();
						getOwner().getGameStats().renewLastChangeTargetTime();
						getOwner().getGameStats().renewLastSkillTime();
						setStateIfNot(AIState.FIGHT);
						think();
					}
				}
			}, 2000);
			isInMove = false;
		}
		super.handleMoveArrived();
	}

	@Override
	protected void handleBackHome() {
		super.handleBackHome();
		isInMove = false;
	}

	private int randomEgg() {
		int[] npcId = { 219971, 219952, 219970, 219969 };
		return npcId[Rnd.get(0, npcId.length - 1)];
	}

	@Override
	public boolean canThink() {
		return canThink;
	}

}
