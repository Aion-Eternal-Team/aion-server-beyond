package consolecommands;

import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.instance.InstanceService;
import com.aionemu.gameserver.services.teleport.TeleportService2;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.chathandlers.ConsoleCommand;
import com.aionemu.gameserver.world.World;
import com.aionemu.gameserver.world.WorldMap;
import com.aionemu.gameserver.world.WorldMapInstance;
import com.aionemu.gameserver.world.WorldMapType;

/**
 * @author ginho1
 */

public class Teleportto extends ConsoleCommand {

	public Teleportto() {
		super("teleportto");
	}

	@Override
	public void execute(Player admin, String... params) {
		if (params == null || params.length < 1) {
			info(admin, null);
			return;
		}

		StringBuilder sbDestination = new StringBuilder();
		for(String p : params)
			sbDestination.append(p + " ");
		
		String destination = sbDestination.toString().trim();
		
		/**
		 * Elysea
		 */
		// Sanctum
		if (destination.equalsIgnoreCase("Sanctum"))
			goTo(admin, WorldMapType.SANCTUM.getId(), 1322, 1511, 568);
		// Kaisinel
		else if (destination.equalsIgnoreCase("Kaisinel"))
			goTo(admin, WorldMapType.KAISINEL.getId(), 2155, 1567, 1205);
		// Poeta
		else if (destination.equalsIgnoreCase("Poeta"))
			goTo(admin, WorldMapType.POETA.getId(), 806, 1242, 119);
		else if (destination.equalsIgnoreCase("Melponeh"))
			goTo(admin, WorldMapType.POETA.getId(), 426, 1740, 119);
		// Verteron
		else if (destination.equalsIgnoreCase("Verteron"))
			goTo(admin, WorldMapType.VERTERON.getId(), 1643, 1500, 119);
		else if (destination.equalsIgnoreCase("Cantas") || destination.equalsIgnoreCase("Cantas Coast"))
			goTo(admin, WorldMapType.VERTERON.getId(), 2384, 788, 102);
		else if (destination.equalsIgnoreCase("Ardus") || destination.equalsIgnoreCase("Ardus Shrine"))
			goTo(admin, WorldMapType.VERTERON.getId(), 2333, 1817, 193);
		else if (destination.equalsIgnoreCase("Pilgrims") || destination.equalsIgnoreCase("Pilgrims Respite"))
			goTo(admin, WorldMapType.VERTERON.getId(), 2063, 2412, 274);
		else if (destination.equalsIgnoreCase("Tolbas") || destination.equalsIgnoreCase("Tolbas Village"))
			goTo(admin, WorldMapType.VERTERON.getId(), 1291, 2206, 142);
		// Eltnen
		else if (destination.equalsIgnoreCase("Eltnen"))
			goTo(admin, WorldMapType.ELTNEN.getId(), 343, 2724, 264);
		else if (destination.equalsIgnoreCase("Golden") || destination.equalsIgnoreCase("Golden Bough Garrison"))
			goTo(admin, WorldMapType.ELTNEN.getId(), 688, 431, 332);
		else if (destination.equalsIgnoreCase("Eltnen Observatory"))
			goTo(admin, WorldMapType.ELTNEN.getId(), 1779, 883, 422);
		else if (destination.equalsIgnoreCase("Novan"))
			goTo(admin, WorldMapType.ELTNEN.getId(), 947, 2215, 252);
		else if (destination.equalsIgnoreCase("Agairon"))
			goTo(admin, WorldMapType.ELTNEN.getId(), 1921, 2045, 361);
		else if (destination.equalsIgnoreCase("Kuriullu"))
			goTo(admin, WorldMapType.ELTNEN.getId(), 2411, 2724, 361);
		// Theobomos
		else if (destination.equalsIgnoreCase("Theobomos"))
			goTo(admin, WorldMapType.THEOBOMOS.getId(), 1398, 1557, 31);
		else if (destination.equalsIgnoreCase("Jamanok") || destination.equalsIgnoreCase("Jamanok Inn"))
			goTo(admin, WorldMapType.THEOBOMOS.getId(), 458, 1257, 127);
		else if (destination.equalsIgnoreCase("Meniherk"))
			goTo(admin, WorldMapType.THEOBOMOS.getId(), 1396, 1560, 31);
		else if (destination.equalsIgnoreCase("obsvillage"))
			goTo(admin, WorldMapType.THEOBOMOS.getId(), 2234, 2284, 50);
		else if (destination.equalsIgnoreCase("Josnack"))
			goTo(admin, WorldMapType.THEOBOMOS.getId(), 901, 2774, 62);
		else if (destination.equalsIgnoreCase("Anangke"))
			goTo(admin, WorldMapType.THEOBOMOS.getId(), 2681, 847, 138);
		// Heiron
		else if (destination.equalsIgnoreCase("Heiron"))
			goTo(admin, WorldMapType.HEIRON.getId(), 2540, 343, 411);
		else if (destination.equalsIgnoreCase("Heiron Observatory"))
			goTo(admin, WorldMapType.HEIRON.getId(), 1423, 1334, 175);
		else if (destination.equalsIgnoreCase("Senemonea"))
			goTo(admin, WorldMapType.HEIRON.getId(), 971, 686, 135);
		else if (destination.equalsIgnoreCase("Jeiaparan"))
			goTo(admin, WorldMapType.HEIRON.getId(), 1635, 2693, 115);
		else if (destination.equalsIgnoreCase("Changarnerk"))
			goTo(admin, WorldMapType.HEIRON.getId(), 916, 2256, 157);
		else if (destination.equalsIgnoreCase("Kishar"))
			goTo(admin, WorldMapType.HEIRON.getId(), 1999, 1391, 118);
		else if (destination.equalsIgnoreCase("Arbolu"))
			goTo(admin, WorldMapType.HEIRON.getId(), 170, 1662, 120);
			
		
		/**
		 * Asmodae
		 */
		// Pandaemonium
		else if  (destination.equalsIgnoreCase("Pandaemonium"))
			goTo(admin, WorldMapType.PANDAEMONIUM.getId(), 1679, 1400, 195);
		// Marchutran
		else if (destination.equalsIgnoreCase("Marchutan"))
			goTo(admin, WorldMapType.MARCHUTAN.getId(), 1557, 1429, 266);
		// Ishalgen
		else if (destination.equalsIgnoreCase("Ishalgen"))
			goTo(admin, WorldMapType.ISHALGEN.getId(), 529, 2449, 281);
		else if (destination.equalsIgnoreCase("Anturoon"))
			goTo(admin, WorldMapType.ISHALGEN.getId(), 940, 1707, 259);
		// Altgard
		else if (destination.equalsIgnoreCase("Altgard"))
			goTo(admin, WorldMapType.ALTGARD.getId(), 1748, 1807, 254);
		else if (destination.equalsIgnoreCase("Basfelt"))
			goTo(admin, WorldMapType.ALTGARD.getId(), 1903, 696, 260);
		else if (destination.equalsIgnoreCase("Trader"))
			goTo(admin, WorldMapType.ALTGARD.getId(), 2680, 1024, 311);
		else if (destination.equalsIgnoreCase("Impetusiom"))
			goTo(admin, WorldMapType.ALTGARD.getId(), 2643, 1658, 324);
		else if (destination.equalsIgnoreCase("Altgard Observatory"))
			goTo(admin, WorldMapType.ALTGARD.getId(), 1468, 2560, 299);
		// Morheim
		else if (destination.equalsIgnoreCase("Morheim"))
			goTo(admin, WorldMapType.MORHEIM.getId(), 308, 2274, 449);
		else if (destination.equalsIgnoreCase("Desert"))
			goTo(admin, WorldMapType.MORHEIM.getId(), 634, 900, 360);
		else if (destination.equalsIgnoreCase("Slag"))
			goTo(admin, WorldMapType.MORHEIM.getId(), 1772, 1662, 197);
		else if (destination.equalsIgnoreCase("Kellan"))
			goTo(admin, WorldMapType.MORHEIM.getId(), 1070, 2486, 239);
		else if (destination.equalsIgnoreCase("Alsig"))
			goTo(admin, WorldMapType.MORHEIM.getId(), 2387, 1742, 102);
		else if (destination.equalsIgnoreCase("Morheim Observatory"))
			goTo(admin, WorldMapType.MORHEIM.getId(), 2794, 1122, 171);
		else if (destination.equalsIgnoreCase("Halabana"))
			goTo(admin, WorldMapType.MORHEIM.getId(), 2346, 2219, 127);
		// Brusthonin
		else if (destination.equalsIgnoreCase("Brusthonin"))
			goTo(admin, WorldMapType.BRUSTHONIN.getId(), 2917, 2421, 15);
		else if (destination.equalsIgnoreCase("Baltasar"))
			goTo(admin, WorldMapType.BRUSTHONIN.getId(), 1413, 2013, 51);
		else if (destination.equalsIgnoreCase("Bollu"))
			goTo(admin, WorldMapType.BRUSTHONIN.getId(), 840, 2016, 307);
		else if (destination.equalsIgnoreCase("Edge"))
			goTo(admin, WorldMapType.BRUSTHONIN.getId(), 1523, 374, 231);
		else if (destination.equalsIgnoreCase("Bubu"))
			goTo(admin, WorldMapType.BRUSTHONIN.getId(), 526, 848, 76);
		else if (destination.equalsIgnoreCase("Settlers"))
			goTo(admin, WorldMapType.BRUSTHONIN.getId(), 2917, 2417, 15);
		// Beluslan
		else if (destination.equalsIgnoreCase("Beluslan"))
			goTo(admin, WorldMapType.BELUSLAN.getId(), 398, 400, 222);
		else if (destination.equalsIgnoreCase("Besfer"))
			goTo(admin, WorldMapType.BELUSLAN.getId(), 533, 1866, 262);
		else if (destination.equalsIgnoreCase("Kidorun"))
			goTo(admin, WorldMapType.BELUSLAN.getId(), 1243, 819, 260);
		else if (destination.equalsIgnoreCase("Red Mane"))
			goTo(admin, WorldMapType.BELUSLAN.getId(), 2358, 1241, 470);
		else if (destination.equalsIgnoreCase("Kistenian"))
			goTo(admin, WorldMapType.BELUSLAN.getId(), 1942, 513, 412);
		else if (destination.equalsIgnoreCase("Hoarfrost"))
			goTo(admin, WorldMapType.BELUSLAN.getId(), 2431, 2063, 579);
		
		/**
		 * Balaurea
		 */
		// Inggison
		else if (destination.equalsIgnoreCase("Inggison"))
			goTo(admin, WorldMapType.INGGISON.getId(), 1335, 276, 590);
		else if (destination.equalsIgnoreCase("Ufob"))
			goTo(admin, WorldMapType.INGGISON.getId(), 382, 951, 460);
		else if (destination.equalsIgnoreCase("Soteria"))
			goTo(admin, WorldMapType.INGGISON.getId(), 2713, 1477, 382);
		else if (destination.equalsIgnoreCase("Hanarkand"))
			goTo(admin, WorldMapType.INGGISON.getId(), 1892, 1748, 327);
		// Gelkmaros
		else if (destination.equalsIgnoreCase("Gelkmaros"))
			goTo(admin, WorldMapType.GELKMAROS.getId(), 1763, 2911, 554);
		else if (destination.equalsIgnoreCase("Subterranea"))
			goTo(admin, WorldMapType.GELKMAROS.getId(), 2503, 2147, 464);
		else if (destination.equalsIgnoreCase("Rhonnam"))
			goTo(admin, WorldMapType.GELKMAROS.getId(), 845, 1737, 354);
		// Silentera
		else if (destination.equalsIgnoreCase("Silentera"))
			goTo(admin, 600010000, 583, 767, 300);
		
		/**
		 * Abyss
		 */
		else if (destination.equalsIgnoreCase("Reshanta"))
			goTo(admin, WorldMapType.RESHANTA.getId(), 951, 936, 1667);
		else if (destination.equalsIgnoreCase("Abyss 1"))
			goTo(admin, WorldMapType.RESHANTA.getId(), 2867, 1034, 1528);
		else if (destination.equalsIgnoreCase("Abyss 2"))
			goTo(admin, WorldMapType.RESHANTA.getId(), 1078, 2839, 1636);
		else if (destination.equalsIgnoreCase("Abyss 3"))
			goTo(admin, WorldMapType.RESHANTA.getId(), 1596, 2952, 2943);
		else if (destination.equalsIgnoreCase("Abyss 4"))
			goTo(admin, WorldMapType.RESHANTA.getId(), 2054, 660, 2843);
		else if (destination.equalsIgnoreCase("Eye of Reshanta") ||destination.equalsIgnoreCase("Eye"))
			goTo(admin, WorldMapType.RESHANTA.getId(), 1979, 2114, 2291);
		else if (destination.equalsIgnoreCase("Divine Fortress") || destination.equalsIgnoreCase("Divine"))
			goTo(admin, WorldMapType.RESHANTA.getId(), 2130, 1925, 2322);

		/**
		 * Instances
		 */
		else if (destination.equalsIgnoreCase("Haramel"))
			goTo(admin, 300200000, 176, 21, 144);
		else if (destination.equalsIgnoreCase("Nochsana") || destination.equalsIgnoreCase("NTC"))
			goTo(admin, 300030000, 513, 668, 331);
		else if (destination.equalsIgnoreCase("Arcanis") || destination.equalsIgnoreCase("Sky Temple of Arcanis"))
			goTo(admin, 320050000, 177, 229, 536);
		else if (destination.equalsIgnoreCase("Fire Temple") ||destination.equalsIgnoreCase("FT"))
			goTo(admin, 320100000, 144, 312, 123);
		else if (destination.equalsIgnoreCase("Kromede") || destination.equalsIgnoreCase("Kromede Trial"))
			goTo(admin, 300230000, 248, 244, 189);
		// Steel Rake
		else if (destination.equalsIgnoreCase("Steel Rake") || destination.equalsIgnoreCase("SR"))
			goTo(admin, 300100000, 237, 506, 948);
		else if (destination.equalsIgnoreCase("Steel Rake Lower") || destination.equalsIgnoreCase("SR Low"))
			goTo(admin, 300100000, 283, 453, 903);
		else if (destination.equalsIgnoreCase("Steel Rake Middle") || destination.equalsIgnoreCase("SR Mid"))
			goTo(admin, 300100000, 283, 453, 953);
		else if (destination.equalsIgnoreCase("Indratu") || destination.equalsIgnoreCase("Indratu Fortress"))
			goTo(admin, 310090000, 562, 335, 1015);
		else if (destination.equalsIgnoreCase("Azoturan") || destination.equalsIgnoreCase("Azoturan Fortress"))
			goTo(admin, 310100000, 458, 428, 1039);
		else if (destination.equalsIgnoreCase("Bio Lab") || destination.equalsIgnoreCase("Aetherogenetics Lab"))
			goTo(admin, 310050000, 225, 244, 133);
		else if (destination.equalsIgnoreCase("Adma") || destination.equalsIgnoreCase("Adma Stronghold"))
			goTo(admin, 320130000, 450, 200, 168);
		else if (destination.equalsIgnoreCase("Alquimia") || destination.equalsIgnoreCase("Alquimia Research Center"))
			goTo(admin, 320110000, 603, 527, 200);
		else if (destination.equalsIgnoreCase("Draupnir") || destination.equalsIgnoreCase("Draupnir Cave"))
			goTo(admin, 320080000, 491, 373, 622);
		else if (destination.equalsIgnoreCase("Theobomos Lab") || destination.equalsIgnoreCase("Theobomos Research Lab"))
			goTo(admin, 310110000, 477, 201, 170);
		else if (destination.equalsIgnoreCase("Dark Poeta") || destination.equalsIgnoreCase("DP"))
			goTo(admin, 300040000, 1214, 412, 140);
		// Lower Abyss
		else if (destination.equalsIgnoreCase("Sulfur") || destination.equalsIgnoreCase("Sulfur Tree Nest"))
			goTo(admin, 300060000, 462, 345, 163);
		else if (destination.equalsIgnoreCase("Right Wing") || destination.equalsIgnoreCase("Right Wing Chamber"))
			goTo(admin, 300090000, 263, 386, 103);
		else if (destination.equalsIgnoreCase("Left Wing") || destination.equalsIgnoreCase("Left Wing Chamber"))
			goTo(admin, 300080000, 672, 606, 321);
		// Upper Abyss
		else if (destination.equalsIgnoreCase("Asteria Chamber"))
			goTo(admin, 300050000, 469, 568, 202);
		else if (destination.equalsIgnoreCase("Miren Chamber"))
			goTo(admin, 300130000, 527, 120, 176);
		else if (destination.equalsIgnoreCase("Miren Legion Barracks"))
			goTo(admin, 301250000, 528, 121, 176);
		else if (destination.equalsIgnoreCase("Miren Barracks"))
			goTo(admin, 301290000, 528, 121, 176);
		else if (destination.equalsIgnoreCase("Kysis Chamber"))
			goTo(admin, 300120000, 528, 121, 176);
		else if (destination.equalsIgnoreCase("Kysis Legion Barracks"))
			goTo(admin, 301240000, 528, 121, 176);
		else if (destination.equalsIgnoreCase("Kysis Barracks"))
			goTo(admin, 301280000, 528, 121, 176);
		else if (destination.equalsIgnoreCase("Krotan Chamber"))
			goTo(admin, 300140000, 528, 109, 176);
		else if (destination.equalsIgnoreCase("Krotan Legion Barracks"))
			goTo(admin, 301260000, 528, 121, 176);
		else if (destination.equalsIgnoreCase("Krotan Barracks"))
			goTo(admin, 301300000, 528, 121, 176);
		else if (destination.equalsIgnoreCase("Roah Chamber"))
			goTo(admin, 300070000, 504, 396, 94);
		// Divine
		else if (destination.equalsIgnoreCase("Abyssal Splinter") || destination.equalsIgnoreCase("Core"))
			goTo(admin, 300220000, 704, 153, 453);
		else if (destination.equalsIgnoreCase("Dredgion"))
			goTo(admin, 300110000, 414, 193, 431);
		else if (destination.equalsIgnoreCase("Chantra") || destination.equalsIgnoreCase("Chantra Dredgion"))
			goTo(admin, 300210000, 414, 193, 431);
		else if (destination.equalsIgnoreCase("Terath") || destination.equalsIgnoreCase("Terath Dredgion"))
			goTo(admin, 300440000, 414, 193, 431);
		else if (destination.equalsIgnoreCase("Taloc") || destination.equalsIgnoreCase("Taloc's Hollow"))
			goTo(admin, 300190000, 200, 214, 1099);
		// Udas
		else if (destination.equalsIgnoreCase("Udas") || destination.equalsIgnoreCase("Udas Temple"))
			goTo(admin, 300150000, 637, 657, 134);
		else if (destination.equalsIgnoreCase("Udas Lower") || destination.equalsIgnoreCase("Udas Lower Temple"))
			goTo(admin, 300160000, 1146, 277, 116);
		else if (destination.equalsIgnoreCase("Beshmundir") || destination.equalsIgnoreCase("BT") || destination.equalsIgnoreCase("Beshmundir Temple"))
			goTo(admin, 300170000, 1477, 237, 243);
		// Padmaraska Cave
		else if (destination.equalsIgnoreCase("Padmaraska Cave"))
			goTo(admin, 320150000, 385, 506, 66);
		// 4.0 Instances
		else if (destination.equalsIgnoreCase("Sauro") || destination.equalsIgnoreCase("Sauro Supply Base"))
			goTo(admin, 301130000, 640.7884f, 174.29156f, 195.625f);
		else if (destination.equalsIgnoreCase("Eternal Bastion") || destination.equalsIgnoreCase("EB"))
			goTo(admin, 300540000, 745.86206f, 291.18323f, 233.7940f);
		else if (destination.equalsIgnoreCase("Danuar Mysticarium") || destination.equalsIgnoreCase("DM"))
			goTo(admin, 300480000, 184.35f, 121.3f, 231.3f);
		else if (destination.equalsIgnoreCase("Legion Danuar Mysticarium") || destination.equalsIgnoreCase("LDM"))
			goTo(admin, 301190000, 184.35f, 121.3f, 231.3f);
		else if (destination.equalsIgnoreCase("Void Cube") || destination.equalsIgnoreCase("VC"))
			goTo(admin, 300580000, 183.08714f, 261.59024f, 310.0941f);
		else if (destination.equalsIgnoreCase("Legion Void Cube") || destination.equalsIgnoreCase("LVC"))
			goTo(admin, 301180000, 183.08714f, 261.59024f, 310.0941f);
		else if (destination.equalsIgnoreCase("Ophidan Bridge") || destination.equalsIgnoreCase("OB"))
			goTo(admin, 300590000, 755.41864f, 560.617f, 572.9637f);
		else if (destination.equalsIgnoreCase("Idgel Research Center") || destination.equalsIgnoreCase("IRC"))
			goTo(admin, 300530000,560.5229f, 508.58438f, 102.67931f);
		else if (destination.equalsIgnoreCase("Legion Idgel Research Center") || destination.equalsIgnoreCase("LIRC"))
			goTo(admin, 301170000,560.5229f, 508.58438f, 102.67931f);
		else if (destination.equalsIgnoreCase("Danuar Reliquary") || destination.equalsIgnoreCase("DR"))
			goTo(admin, 301110000, 256.60f, 257.99f, 241.78f);
		else if (destination.equalsIgnoreCase("Idgel Research Center Legion") || destination.equalsIgnoreCase("IRCL"))
			goTo(admin, 301170000,560.5229f, 508.58438f, 102.67931f);
		else if (destination.equalsIgnoreCase("Nightmare Circus") || destination.equalsIgnoreCase("NC"))
			goTo(admin, 301160000, 467.64f, 568.34f, 201.67f);
		// 4.0 instances
		else if (destination.equalsIgnoreCase("Danuar Reliquary") || destination.equalsIgnoreCase("KB"))
			goTo(admin, 301110000, 255, 245, 242);
		else if (destination.equalsIgnoreCase("Kamar Battlefield") || destination.equalsIgnoreCase("KB"))
			goTo(admin, 301120000, 1329, 1501, 593);
		// 4.5 instances
		else if (destination.equalsIgnoreCase("Iron Wall Warfront") || destination.equalsIgnoreCase("IWW") || destination.equalsIgnoreCase("Iron Wall"))
			goTo(admin, 301220000, 550, 477, 213);
		else if (destination.equalsIgnoreCase("Lucky Ophidian Bridge") || destination.equalsIgnoreCase("LOB") || destination.equalsIgnoreCase("Lucky Ophidian"))
			goTo(admin, 301320000, 750, 554, 574);
		else if (destination.equalsIgnoreCase("Lucky Danuar Reliquary") || destination.equalsIgnoreCase("LDR") || destination.equalsIgnoreCase("Lucky Danuar"))
			goTo(admin, 301330000, 256, 246, 242);
		
		/**
		 * Quest Instance Maps
		 */
		// TODO : Changer id maps
		else if (destination.equalsIgnoreCase("Karamatis 0"))
			goTo(admin, 310010000, 221, 250, 206);
		else if (destination.equalsIgnoreCase("Karamatis 1"))
			goTo(admin, 310020000, 312, 274, 206);
		else if (destination.equalsIgnoreCase("Karamatis 2"))
			goTo(admin, 310120000, 221, 250, 206);
		else if (destination.equalsIgnoreCase("Aerdina"))
			goTo(admin, 310030000, 275, 168, 205);
		else if (destination.equalsIgnoreCase("Geranaia"))
			goTo(admin, 310040000, 275, 168, 205);
		// Stigma quest
		else if (destination.equalsIgnoreCase("Sliver") || destination.equalsIgnoreCase("Sliver of Darkness"))
			goTo(admin, 310070000, 247, 249, 1392);
		else if (destination.equalsIgnoreCase("Space") || destination.equalsIgnoreCase("Space of Destiny"))
			goTo(admin, 320070000, 246, 246, 125);
		else if (destination.equalsIgnoreCase("Ataxiar 1"))
			goTo(admin, 320010000, 221, 250, 206);
		else if (destination.equalsIgnoreCase("Ataxiar 2"))
			goTo(admin, 320020000, 221, 250, 206);
		else if (destination.equalsIgnoreCase("Bregirun"))
			goTo(admin, 320030000, 275, 168, 205);
		else if (destination.equalsIgnoreCase("Nidalber"))
			goTo(admin, 320040000, 275, 168, 205);
		
		/**
		 * Arenas
		 */
		else if (destination.equalsIgnoreCase("Sanctum Arena"))
			goTo(admin, 310080000, 275, 242, 159);
		else if (destination.equalsIgnoreCase("Triniel Arena"))
			goTo(admin, 320090000, 275, 239, 159);
		// Empyrean Crucible
		else if (destination.equalsIgnoreCase("Crucible 1-0"))
			goTo(admin, 300300000, 380, 350, 95);
		else if (destination.equalsIgnoreCase("Crucible 1-1"))
			goTo(admin, 300300000, 346, 350, 96);
		else if (destination.equalsIgnoreCase("Crucible 5-0"))
			goTo(admin, 300300000, 1265, 821, 359);
		else if (destination.equalsIgnoreCase("Crucible 5-1"))
			goTo(admin, 300300000, 1256, 797, 359);
		else if (destination.equalsIgnoreCase("Crucible 6-0"))
			goTo(admin, 300300000, 1596, 150, 129);
		else if (destination.equalsIgnoreCase("Crucible 6-1"))
			goTo(admin, 300300000, 1628, 155, 126);
		else if (destination.equalsIgnoreCase("Crucible 7-0"))
			goTo(admin, 300300000, 1813, 797, 470);
		else if (destination.equalsIgnoreCase("Crucible 7-1"))
			goTo(admin, 300300000, 1785, 797, 470);
		else if (destination.equalsIgnoreCase("Crucible 8-0"))
			goTo(admin, 300300000, 1776, 1728, 304);
		else if (destination.equalsIgnoreCase("Crucible 8-1"))
			goTo(admin, 300300000, 1776, 1760, 304);
		else if (destination.equalsIgnoreCase("Crucible 9-0"))
			goTo(admin, 300300000, 1357, 1748, 320);
		else if (destination.equalsIgnoreCase("Crucible 9-1"))
			goTo(admin, 300300000, 1334, 1741, 316);
		else if (destination.equalsIgnoreCase("Crucible 10-0"))
			goTo(admin, 300300000, 1750, 1255, 395);
		else if (destination.equalsIgnoreCase("Crucible 10-1"))
			goTo(admin, 300300000, 1761, 1280, 395);
		// Arena Of Chaos
		else if (destination.equalsIgnoreCase("Arena Of Chaos - 1"))
			goTo(admin, 300350000, 1332, 1078, 340);
		else if (destination.equalsIgnoreCase("Arena Of Chaos - 2"))
			goTo(admin, 300350000, 599, 1854, 227);
		else if (destination.equalsIgnoreCase("Arena Of Chaos - 3"))
			goTo(admin, 300350000, 663, 265, 512);
		else if (destination.equalsIgnoreCase("Arena Of Chaos - 4"))
			goTo(admin, 300350000, 1840, 1730, 302);
		else if (destination.equalsIgnoreCase("Arena Of Chaos - 5"))
			goTo(admin, 300350000, 1932, 1228, 270);
		else if (destination.equalsIgnoreCase("Arena Of Chaos - 6"))
			goTo(admin, 300350000, 1949, 946, 224);

		/**
		 * Miscellaneous
		 */
		// Prison
		else if (destination.equalsIgnoreCase("Prison LF") || destination.equalsIgnoreCase("Prison Elyos"))
			goTo(admin, 510010000, 256, 256, 49);
		else if (destination.equalsIgnoreCase("Prison DF") || destination.equalsIgnoreCase("Prison Asmos"))
			goTo(admin, 520010000, 256, 256, 49);
		// Test
		else if (destination.equalsIgnoreCase("Test Dungeon"))
			goTo(admin, 300020000, 104, 66, 25);
		else if (destination.equalsIgnoreCase("Test Basic"))
			goTo(admin, 900020000, 144, 136, 20);
		else if (destination.equalsIgnoreCase("Test Server"))
			goTo(admin, 900030000, 228, 171, 49);
		else if (destination.equalsIgnoreCase("Test GiantMonster"))
			goTo(admin, 900100000, 196, 187, 20);
		// Unknown
		else if (destination.equalsIgnoreCase("IDAbPro"))
			goTo(admin, 300010000, 270, 200, 206);
		// GM zone
		else if (destination.equalsIgnoreCase("gm"))
			goTo(admin, 120020000, 1442, 1133, 302);
		
		/**
		 * 2.5 Maps
		 */
		else if (destination.equalsIgnoreCase("Kaisinel Academy"))
			goTo(admin, 110070000, 459, 251, 128);
		else if (destination.equalsIgnoreCase("Marchutan Priory"))
			goTo(admin, 120080000, 577, 250, 94);
		else if (destination.equalsIgnoreCase("Esoterrace"))
			goTo(admin, 300250000, 333, 437, 326);
			
		/**
		 * 3.0 Maps
		 */
		else if (destination.equalsIgnoreCase("Pernon"))
			goTo(admin, 710010000, 1069, 1539, 98);
		else if (destination.equalsIgnoreCase("Oriel"))
			goTo(admin, 700010000, 1261, 1845, 98);
		else if (destination.equalsIgnoreCase("Sarpan"))
			goTo(admin, 600020000, 1374, 1455, 600);
		else if (destination.equalsIgnoreCase("Tiamaranta"))
			goTo(admin, 600030000, 40, 1732, 297);
		else if (destination.equalsIgnoreCase("Tiamaranta Eye"))
			goTo(admin, 600040000, 159, 768, 1202);
		else if (destination.equalsIgnoreCase("Steel Rake Cabin") || destination.equalsIgnoreCase("Steel Rake Solo"))
			goTo(admin, 300460000, 248, 244, 189);
		else if (destination.equalsIgnoreCase("Aturam") || destination.equalsIgnoreCase("Aturam Sky Fortress"))
			goTo(admin, 300240000, 636, 446, 655);
		else if (destination.equalsIgnoreCase("Elementis") || destination.equalsIgnoreCase("Elementis Forest"))
			goTo(admin, 300260000, 176, 612, 231);
		else if (destination.equalsIgnoreCase("Argent") || destination.equalsIgnoreCase("Argent Manor"))
			goTo(admin, 300270000, 1005, 1089, 70);
		else if (destination.equalsIgnoreCase("Rentus") || destination.equalsIgnoreCase("Rentus Base"))
			goTo(admin, 300280000, 579, 606, 153);
		else if (destination.equalsIgnoreCase("Raksang"))
			goTo(admin, 300310000, 665, 735, 1188);
		else if (destination.equalsIgnoreCase("Muada") || destination.equalsIgnoreCase("Muada's Trencher"))
			goTo(admin, 300380000, 492, 553, 106);
		else if (destination.equalsIgnoreCase("Satra"))
			goTo(admin, 300470000, 510, 180, 159);
		
		/**
		 * 3.5
		 */
		else if(destination.equalsIgnoreCase("Dragon Lords Refuge"))
			goTo(admin, 300520000, 506, 516, 242);
		
		else if(destination.equalsIgnoreCase("Throne of Blood") || destination.equalsIgnoreCase("Tiamat"))
			goTo(admin, 300520000, 495, 528, 417);
		

		/**
		* 4.3 
		*/
		
		else if (destination.equalsIgnoreCase("Beacon"))
			goTo(admin, WorldMapType.NORHTERN_KATALAM.getId(), 400, 2717, 143);
		else if (destination.equalsIgnoreCase("Danuar"))
			goTo(admin, WorldMapType.NORHTERN_KATALAM.getId(), 364, 385, 282);		
		else if (destination.equalsIgnoreCase("Danaria"))
			goTo(admin, WorldMapType.SOUTHERN_KATALAM.getId(), 2544, 1699, 142);
		else if (destination.equalsIgnoreCase("Idian") || destination.equalsIgnoreCase("Idian Depths"))
			goTo(admin, WorldMapType.UNDERGROUND_KATALAM.getId(), 666, 644, 515);
		else
			PacketSendUtility.sendMessage(admin, "Could not find the specified destination !");
	}
	
	private static void goTo(final Player admin, int worldId, float x, float y, float z) {
		WorldMap destinationMap = World.getInstance().getWorldMap(worldId);
		if (destinationMap.isInstanceType())
			TeleportService2.teleportTo(admin, worldId, getInstanceId(worldId, admin), x, y, z);
		else
			TeleportService2.teleportTo(admin, worldId, x, y, z);
	}
	
	private static int getInstanceId(int worldId, Player admin) {
		if (admin.getWorldId() == worldId)	{
			WorldMapInstance registeredInstance = InstanceService.getRegisteredInstance(worldId, admin.getObjectId());
			if (registeredInstance != null)
				return registeredInstance.getInstanceId();
		}
		WorldMapInstance newInstance = InstanceService.getNextAvailableInstance(worldId);
		InstanceService.registerPlayerWithInstance(newInstance, admin);
		return newInstance.getInstanceId();
	}
	@Override
	public void info(Player admin, String message) {
		PacketSendUtility.sendMessage(admin, "syntax ///teleportto <MapName>");
	}

}