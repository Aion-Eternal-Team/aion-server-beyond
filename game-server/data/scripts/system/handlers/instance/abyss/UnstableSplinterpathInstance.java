package instance.abyss;

import java.util.List;

import com.aionemu.commons.utils.Rnd;
import com.aionemu.gameserver.instance.handlers.GeneralInstanceHandler;
import com.aionemu.gameserver.instance.handlers.InstanceID;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.network.aion.serverpackets.SM_DIE;
import com.aionemu.gameserver.skillengine.SkillEngine;
import com.aionemu.gameserver.utils.MathUtil;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;

/**
 * @author zhkchi
 * @reworked vlog, Luzien
 * @edit Cheatkiller
 */
@InstanceID(300600000)
public class UnstableSplinterpathInstance extends GeneralInstanceHandler {

	private int destroyedFragments;
	private int killedPazuzuWorms = 0;

	@Override
	public void onDie(Npc npc) {
		final int npcId = npc.getNpcId();
		switch (npcId) {
			case 219554: // (ex 219942) Pazuzu the Life Current
				spawnPazuzuHugeAetherFragment();
				spawnPazuzuGenesisTreasureBoxes();
				spawnPazuzuAbyssalTreasureBox();
				spawnPazuzusTreasureBox();
				break;
			case 219553: // (ex 219941) Kaluva the Fourth Fragment
				spawnKaluvaHugeAetherFragment();
				spawnKaluvaGenesisTreasureBoxes();
				spawnKaluvaAbyssalTreasureBox();
				break;
			case 219551: // (ex 219939) Unstable rukril
			case 219552: // (ex 219940) Unstable ebonsoul
				if (getNpc(npcId == 219552 ? 219551 : 219552) == null) {
					spawnDayshadeAetherFragment();
					spawnDayshadeGenesisTreasureBoxes();
					spawnDayshadeAbyssalTreasureChest();
				} else {
					sendMsg(npcId == 219551 ? 1400634 : 1400635); // Defeat Rukril/Ebonsoul in 1 min!
					ThreadPoolManager.getInstance().schedule(new Runnable() {

						@Override
						public void run() {

							if (getNpc(npcId == 219552 ? 219551 : 219552) != null) {
								switch (npcId) {
									case 219551:
										spawn(219552, 447.1937f, 683.72217f, 433.1805f, (byte) 108); // rukril
										break;
									case 219552:
										spawn(219551, 455.5502f, 702.09485f, 433.13727f, (byte) 108); // ebonsoul
										break;
								}
							}
						}

					}, 60000);
				}
				npc.getController().onDelete();
				break;
			case 283204: // ex 284022
				Npc ebonsoul = getNpc(219552);
				if (ebonsoul != null && !ebonsoul.getLifeStats().isAlreadyDead()) {
					if (MathUtil.isIn3dRange(npc, ebonsoul, 5)) {
						ebonsoul.getEffectController().removeEffect(19159);
						deleteNpcs(instance.getNpcs(281907));
						break;
					}
				}
				npc.getController().onDelete();
				break;
			case 283205: // ex 284023:
				Npc rukril = getNpc(219551);
				if (rukril != null && !rukril.getLifeStats().isAlreadyDead()) {
					if (MathUtil.isIn3dRange(npc, rukril, 5)) {
						rukril.getEffectController().removeEffect(19266);
						deleteNpcs(instance.getNpcs(281908));
						break;
					}
				}
				npc.getController().onDelete();
				break;
			case 219563: // ex 219951 unstable Yamennes Painflare
			case 219555: // ex 219943 strengthened Yamennes Blindsight
				spawnYamennesGenesisTreasureBoxes();
				spawnYamennesAbyssalTreasureBox(npcId == 219563 ? 701579 : 701580);
				deleteNpcs(instance.getNpcs(219586)); // Ex 219974
				spawn(730317, 328.476f, 762.585f, 197.479f, (byte) 90); // Exit
				for (Player p : instance.getPlayersInside())
					SkillEngine.getInstance().applyEffectDirectly(19283, p, p, 0);
				break;
			case 701588: // HugeAetherFragment
				destroyedFragments++;
				onFragmentKill();
				npc.getController().onDelete();
				break;

			case 283206: // ex 284024:
				if (++killedPazuzuWorms == 4) {
					killedPazuzuWorms = 0;
					Npc pazuzu = getNpc(219554);
					if (pazuzu != null && !pazuzu.getLifeStats().isAlreadyDead()) {
						pazuzu.getEffectController().removeEffect(19145);
						pazuzu.getEffectController().removeEffect(19291);
					}
				}
				npc.getController().onDelete();
				break;
			case 219567: // ex 219955
			case 219579: // ex 219967
			case 219580: // ex 219968 Spawn Gate
				removeSummoned();
				npc.getController().onDelete();
				break;
		}
	}

	@Override
	public void onInstanceDestroy() {
		destroyedFragments = 0;
	}

	@Override
	public void handleUseItemFinish(Player player, Npc npc) {
		switch (npc.getNpcId()) {
			case 700957:
				sendMsg(1400732);
				spawn(219563, 329.70886f, 733.8744f, 197.60938f, (byte) 0);
				npc.getController().onDelete();
				break;
			case 701589:
				sendMsg(1400731);
				spawn(219555, 329.70886f, 733.8744f, 197.60938f, (byte) 0);
				npc.getController().onDelete();
				break;
		}
	}

	@Override
	public boolean onDie(final Player player, Creature lastAttacker) {
		PacketSendUtility.sendPacket(player, new SM_DIE(player.haveSelfRezEffect(), player.haveSelfRezItem(), 0, 8));
		return true;
	}

	private void spawnPazuzuHugeAetherFragment() {
		spawn(701588, 669.576f, 335.135f, 465.895f, (byte) 0);
	}

	private void spawnPazuzuGenesisTreasureBoxes() {
		spawn(701576, 651.53204f, 357.085f, 466.1315f, (byte) 66);
		spawn(701576, 647.00446f, 357.2484f, 465.8960f, (byte) 0);
		spawn(701576, 653.8384f, 360.39508f, 466.4391f, (byte) 100);
	}

	private void spawnPazuzuAbyssalTreasureBox() {
		spawn(701575, 649.24286f, 361.33755f, 466.0427f, (byte) 33);
	}

	private void spawnPazuzusTreasureBox() {
		if (Rnd.get(0, 100) >= 80) { // 20% chance, not retail
			spawn(700861, 649.243f, 362.338f, 466.0118f, (byte) 0);
		}
	}

	private void spawnKaluvaHugeAetherFragment() {
		spawn(701588, 633.7498f, 557.8822f, 424.99347f, (byte) 6);
	}

	private void spawnKaluvaGenesisTreasureBoxes() {
		spawn(701576, 601.2931f, 584.66705f, 422.9955f, (byte) 6);
		spawn(701576, 597.2156f, 583.95416f, 423.3474f, (byte) 66);
		spawn(701576, 602.9586f, 589.2678f, 422.8296f, (byte) 100);
	}

	private void spawnKaluvaAbyssalTreasureBox() {
		spawn(701577, 598.82776f, 588.25946f, 422.7739f, (byte) 113);
	}

	private void spawnDayshadeAetherFragment() {
		spawn(701588, 452.89706f, 692.36084f, 433.96838f, (byte) 6);
	}

	private void spawnDayshadeGenesisTreasureBoxes() {
		spawn(701576, 408.10938f, 650.9015f, 439.28332f, (byte) 66);
		spawn(701576, 402.40375f, 655.55237f, 439.26288f, (byte) 33);
		spawn(701576, 406.74445f, 655.5914f, 439.2548f, (byte) 100);
	}

	private void spawnDayshadeAbyssalTreasureChest() {
		sendMsg(1400636); // A Treasure Box Appeared
		spawn(701578, 404.891f, 650.2943f, 439.2548f, (byte) 130);
	}

	private void spawnYamennesGenesisTreasureBoxes() {
		spawn(701576, 326.978f, 729.8414f, 197.7078f, (byte) 16);
		spawn(701576, 326.5296f, 735.13324f, 197.6681f, (byte) 66);
		spawn(701576, 329.8462f, 738.41095f, 197.7329f, (byte) 3);
	}

	private void spawnYamennesAbyssalTreasureBox(int npcId) {
		spawn(npcId, 330.891f, 733.2943f, 197.6404f, (byte) 113);
	}

	private void deleteNpcs(List<Npc> npcs) {
		for (Npc npc : npcs) {
			if (npc != null) {
				npc.getController().onDelete();
			}
		}
	}

	private void removeSummoned() {
		Npc gate1 = getNpc(219567);
		Npc gate2 = getNpc(219579);
		Npc gate3 = getNpc(219580);
		if ((gate1 == null || gate1.getLifeStats().isAlreadyDead()) && (gate2 == null || gate2.getLifeStats().isAlreadyDead())
			&& (gate3 == null || gate3.getLifeStats().isAlreadyDead())) {
			deleteNpcs(instance.getNpcs(219565));// Summoned Orkanimum
			deleteNpcs(instance.getNpcs(219566));// Summoned Lapilima
		}
	}

	private void onFragmentKill() {
		switch (destroyedFragments) {
			case 1:
				// The destruction of the Huge Aether Fragment has destabilized the artifact!
				sendMsg(1400689);
				break;
			case 2:
				// The destruction of the Huge Aether Fragment has put the artifact protector on alert!
				sendMsg(1400690);
				break;
			case 3:
				// The destruction of the Huge Aether Fragment has caused abnormality on the artifact. The artifact protector is
				// furious!
				deleteNpcs(instance.getNpcs(701589));
				spawn(700957, 326.1821f, 766.9640f, 202.1832f, (byte) 100, 79);
				sendMsg(1400691);
				break;
		}
	}
}
