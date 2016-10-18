package instance;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.aionemu.gameserver.ai2.event.AIEventType;
import com.aionemu.gameserver.instance.handlers.GeneralInstanceHandler;
import com.aionemu.gameserver.instance.handlers.InstanceID;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.network.aion.serverpackets.SM_DIE;
import com.aionemu.gameserver.network.aion.serverpackets.SM_PLAY_MOVIE;
import com.aionemu.gameserver.skillengine.SkillEngine;
import com.aionemu.gameserver.skillengine.effect.AbnormalState;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.world.zone.ZoneInstance;
import com.aionemu.gameserver.world.zone.ZoneName;

/**
 * @author Ritsu, Luzien
 * @see http://gameguide.na.aiononline.com/aion/Padmarashka%27s+Cave+Walkthrough
 */
@InstanceID(320150000)
public class PadmarashkasCaveInstance extends GeneralInstanceHandler {

	private AtomicBoolean moviePlayed = new AtomicBoolean();
	private AtomicInteger killedPadmarashkaProtector = new AtomicInteger();
	private AtomicInteger killedEggs = new AtomicInteger();

	@Override
	public void onDie(Npc npc) {
		final int npcId = npc.getNpcId();
		switch (npcId) {
			case 218670:
			case 218671:
			case 218673:
			case 218674:
				if (killedPadmarashkaProtector.incrementAndGet() == 4) {
					killedPadmarashkaProtector.set(0);
					final Npc padmarashka = getNpc(218756);
					if (padmarashka != null && !padmarashka.getLifeStats().isAlreadyDead()) {
						padmarashka.getEffectController().unsetAbnormal(AbnormalState.SLEEP.getId());
						// padmarashka.getEffectController().broadCastEffects(0);
						SkillEngine.getInstance().getSkill(padmarashka, 19187, 55, padmarashka).useNoAnimationSkill();
						padmarashka.getEffectController().removeEffect(19186); // skill should handle this TODO: fix
						ThreadPoolManager.getInstance().schedule(new Runnable() {

							@Override
							public void run() {
								padmarashka.getAi2().onCreatureEvent(AIEventType.CREATURE_AGGRO, instance.getPlayersInside().get(0));
							}
						}, 1000);
					}
				}
				break;
			case 282613:
			case 282614:
				if (killedEggs.incrementAndGet() == 20) { // TODO: find value
					final Npc padmarashka = getNpc(218756);
					if (padmarashka != null && !padmarashka.getLifeStats().isAlreadyDead()) {
						SkillEngine.getInstance().applyEffectDirectly(20101, padmarashka, padmarashka, 0);
					}
				}
				break;
		}
	}

	@Override
	public void onEnterZone(Player player, ZoneInstance zone) {
		if (zone.getAreaTemplate().getZoneName() == ZoneName.get("PADMARASHKAS_NEST_320150000")) {
			if (moviePlayed.compareAndSet(false, true))
				sendMovie();
		}
	}

	@Override
	public boolean onDie(final Player player, Creature lastAttacker) {
		PacketSendUtility.sendPacket(player, new SM_DIE(player.haveSelfRezEffect(), player.haveSelfRezItem(), 0, 8));
		return true;
	}

	private void sendMovie() {
		instance.forEachPlayer(new Consumer<Player>() {

			@Override
			public void accept(Player player) {
				PacketSendUtility.sendPacket(player, new SM_PLAY_MOVIE(0, 488));
			}
		});
	}

	@Override
	public void onInstanceDestroy() {
		moviePlayed.set(false);
		killedPadmarashkaProtector.set(0);
	}
}
