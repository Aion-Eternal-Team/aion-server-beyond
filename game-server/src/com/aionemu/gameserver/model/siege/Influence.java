package com.aionemu.gameserver.model.siege;

import java.util.Iterator;

import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.network.aion.serverpackets.SM_INFLUENCE_RATIO;
import com.aionemu.gameserver.services.SiegeService;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.world.World;

/**
 * Calculates only fortresses (artifacts arent included for Influence) with their template Values
 *
 * @author Sarynth
 * @reworked Whoop
 */
public class Influence {

	private static final Influence instance = new Influence();
	private float inggison_e = 0;
	private float inggison_a = 0;
	private float inggison_b = 0;
	private float gelkmaros_e = 0;
	private float gelkmaros_a = 0;
	private float gelkmaros_b = 0;
	private float abyss_e = 0;
	private float abyss_a = 0;
	private float abyss_b = 0;
	private float tiamaranta_e = 0;
	private float tiamaranta_a = 0;
	private float tiamaranta_b = 0;
	private float katalam_e = 0;
	private float katalam_a = 0;
	private float katalam_b = 0;
	private float danaria_e = 0;
	private float danaria_a = 0;
	private float danaria_b = 0;
	private float kaldor_e = 0;
	private float kaldor_a = 0;
	private float kaldor_b = 0;
	private float global_e = 0;
	private float global_a = 0;
	private float global_b = 0;

	private Influence() {
		calculateInfluence();
	}

	public static Influence getInstance() {
		return instance;
	}

	/**
	 * Recalculates Influence
	 */
	public void recalculateInfluence() {
		calculateInfluence();
	}

	/**
	 * calculate influence
	 */
	private void calculateInfluence() {
		float e_inggison = 0;
		float a_inggison = 0;
		float b_inggison = 0;
		float e_gelkmaros = 0;
		float a_gelkmaros = 0;
		float b_gelkmaros = 0;
		float e_abyss = 0;
		float a_abyss = 0;
		float b_abyss = 0;
		float e_katalam = 0;
		float a_katalam = 0;
		float b_katalam = 0;
		float e_danaria = 0;
		float a_danaria = 0;
		float b_danaria = 0;
		float e_kaldor = 0;
		float a_kaldor = 0;
		float b_kaldor = 0;

		for (SiegeLocation sLoc : SiegeService.getInstance().getSiegeLocations().values()) {
			if (sLoc instanceof FortressLocation) {
				switch (sLoc.getWorldId()) {
					case 210050000:					
						switch (sLoc.getRace()) {
							case ELYOS:
								e_inggison += sLoc.getInfluenceValue();
								break;
							case ASMODIANS:
								a_inggison += sLoc.getInfluenceValue();
								break;
							case BALAUR:
								b_inggison += sLoc.getInfluenceValue();
								break;
						}
						break;
					case 220070000:
						switch (sLoc.getRace()) {
							case ELYOS:
								e_gelkmaros += sLoc.getInfluenceValue();
								break;
							case ASMODIANS:
								a_gelkmaros += sLoc.getInfluenceValue();
								break;
							case BALAUR:
								b_gelkmaros += sLoc.getInfluenceValue();
								break;
						}
						break;
					case 400010000:
						switch (sLoc.getRace()) {
							case ELYOS:
								e_abyss += sLoc.getInfluenceValue();
								break;
							case ASMODIANS:
								a_abyss += sLoc.getInfluenceValue();
								break;
							case BALAUR:
								b_abyss += sLoc.getInfluenceValue();
								break;
						}
						break;
					case 600050000:
						switch (sLoc.getRace()) {
							case ELYOS:
								e_katalam += sLoc.getInfluenceValue();
								break;
							case ASMODIANS:
								a_katalam += sLoc.getInfluenceValue();
								break;
							case BALAUR:
								b_katalam += sLoc.getInfluenceValue();
								break;
						}
						break;
					case 600060000:
						switch (sLoc.getRace()) {
							case ELYOS:
								e_danaria += sLoc.getInfluenceValue();
								break;
							case ASMODIANS:
								a_danaria += sLoc.getInfluenceValue();
								break;
							case BALAUR:
								b_danaria += sLoc.getInfluenceValue();
								break;
						}
						break;
					case 600090000:
						switch (sLoc.getRace()) {
							case ELYOS:
								e_kaldor += sLoc.getInfluenceValue();
								break;
							case ASMODIANS:
								a_kaldor += sLoc.getInfluenceValue();
								break;
							case BALAUR:
								b_kaldor += sLoc.getInfluenceValue();
								break;
						}
						break;
				}
			}
		}
		//reset Values
		abyss_e = e_abyss;
		abyss_a = a_abyss;
		abyss_b = b_abyss;
		inggison_e = e_inggison;
		inggison_a = a_inggison;
		inggison_b = b_inggison;
		gelkmaros_e = e_gelkmaros;
		gelkmaros_a = a_gelkmaros;
		gelkmaros_b = b_gelkmaros;
		katalam_e = e_katalam;
		katalam_a = a_katalam;
		katalam_b = b_katalam;
		danaria_e = e_danaria;
		danaria_a = a_danaria;
		danaria_b = b_danaria;
		kaldor_e = e_kaldor;
		kaldor_a = a_kaldor;
		kaldor_b = b_kaldor;		

		global_e = (abyss_e + inggison_e + gelkmaros_e + katalam_e + danaria_e + kaldor_e) / 100f;
		global_a = (abyss_a + inggison_a + gelkmaros_a + katalam_a + danaria_a + kaldor_a) / 100f;
		global_b = (abyss_b + inggison_b + gelkmaros_b + katalam_b + danaria_b + kaldor_b) / 100f;
	}

	/**
	 * Broadcast packet with influence update to all players. - Responsible for
	 * the message "The Divine Fortress is now vulnerable."
	 */
	@SuppressWarnings("unused")
	private void broadcastInfluencePacket() {
		SM_INFLUENCE_RATIO pkt = new SM_INFLUENCE_RATIO();

		Player player;
		Iterator<Player> iter = World.getInstance().getPlayersIterator();
		while (iter.hasNext()) {
			player = iter.next();
			PacketSendUtility.sendPacket(player, pkt);
		}
	}

	/**
	 * @return elyos control
	 */
	public float getGlobalElyosInfluence() {
		return this.global_e;
	}

	/**
	 * @return asmos control
	 */
	public float getGlobalAsmodiansInfluence() {
		return this.global_a;
	}

	/**
	 * @return balaur control
	 */
	public float getGlobalBalaursInfluence() {
		return this.global_b;
	}

	/**
	 * @return elyos control
	 */
	public float getInggisonElyosInfluence() {
		return this.inggison_e;
	}

	/**
	 * @return asmos control
	 */
	public float getInggisonAsmodiansInfluence() {
		return this.inggison_a;
	}

	/**
	 * @return balaur control
	 */
	public float getInggisonBalaursInfluence() {
		return this.inggison_b;
	}

	/**
	 * @return elyos control
	 */
	public float getGelkmarosElyosInfluence() {
		return this.gelkmaros_e;
	}

	/**
	 * @return asmos control
	 */
	public float getGelkmarosAsmodiansInfluence() {
		return this.gelkmaros_a;
	}

	/**
	 * @return balaur control
	 */
	public float getGelkmarosBalaursInfluence() {
		return this.gelkmaros_b;
	}

	/**
	 * @return elyos control
	 */
	public float getAbyssElyosInfluence() {
		return this.abyss_e;
	}

	/**
	 * @return asmos control
	 */
	public float getAbyssAsmodiansInfluence() {
		return this.abyss_a;
	}

	/**
	 * @return balaur control
	 */
	public float getAbyssBalaursInfluence() {
		return this.abyss_b;
	}

	/**
	 * @return elyos control
	 */
	public float getTiamarantaElyosInfluence() {
		return this.tiamaranta_e;
	}

	/**
	 * @return asmos control
	 */
	public float getTiamarantaAsmodiansInfluence() {
		return this.tiamaranta_a;
	}

	/**
	 * @return balaur control
	 */
	public float getTiamarantaBalaursInfluence() {
		return this.tiamaranta_b;
	}

	
	/**
	 * @return elyos control
	 */
	public float getKatalamElyosInfluence() {
		return this.katalam_e;
	}

	
	/**
	 * @return asmos control
	 */
	public float getKatalamAsmodiansInfluence() {
		return this.katalam_a;
	}

	
	/**
	 * @return balaur control
	 */
	public float getKatalamBalaursInfluence() {
		return this.katalam_b;
	}

	
	/**
	 * @return elyos control
	 */
	public float getDanariaElyosInfluence() {
		return this.danaria_e;
	}

	
	/**
	 * @return asmos control
	 */
	public float getDanariaAsmodiansInfluence() {
		return this.danaria_a;
	}

	
	/**
	 * @return balaur control
	 */
	public float getDanariaBalaursInfluence() {
		return this.danaria_b;
	}

	
	/**
	 * @return elyos control
	 */
	public float getKaldorElyosInfluence() {
		return kaldor_e;
	}

	
	/**
	 * @return asmos control
	 */
	public float getKaldorAsmodiansInfluence() {
		return kaldor_a;
	}

	
	/**
	 * @return balaur control
	 */
	public float getKaldorBalaursInfluence() {
		return kaldor_b;
	}

	/**
	 * @return float containing dmg modifier for disadvantaged race
	 */
	public float getPvpRaceBonus(Race attRace) {
		float bonus = 1;
		float elyos = getGlobalElyosInfluence();
		float asmo = getGlobalAsmodiansInfluence();
		switch (attRace) {
			case ASMODIANS:
				if (elyos >= 0.81f && asmo <= 0.10f)
					bonus = 1.2f;
				else if (elyos >= 0.81f || (elyos >= 0.71f && asmo <= 0.10f))
					bonus = 1.15f;
				else if (elyos >= 0.71f)
					bonus = 1.1f;
				break;
			case ELYOS:
				if (asmo >= 0.81f && elyos <= 0.10f)
					bonus = 1.2f;
				else if (asmo >= 0.81f || (asmo >= 0.71f && elyos <= 0.10f))
					bonus = 1.15f;
				else if (asmo >= 0.71f)
					bonus = 1.1f;
				break;
		}
		return bonus;
	}

}