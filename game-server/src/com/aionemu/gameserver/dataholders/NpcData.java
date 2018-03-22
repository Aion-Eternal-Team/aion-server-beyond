package com.aionemu.gameserver.dataholders;

import java.util.List;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.slf4j.LoggerFactory;

import com.aionemu.gameserver.model.DialogAction;
import com.aionemu.gameserver.model.TribeClass;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.stats.calc.NpcStatCalculation;
import com.aionemu.gameserver.model.stats.container.StatEnum;
import com.aionemu.gameserver.model.templates.npc.NpcRank;
import com.aionemu.gameserver.model.templates.npc.NpcRating;
import com.aionemu.gameserver.model.templates.npc.NpcTemplate;
import com.aionemu.gameserver.model.templates.stats.StatsTemplate;

import gnu.trove.map.hash.TIntObjectHashMap;

/**
 * This is a container holding and serving all {@link NpcTemplate} instances.<br>
 * Briefly: Every {@link Npc} instance represents some class of NPCs among which each have the same id, name, items, statistics. Data for such NPC
 * class is defined in {@link NpcTemplate} and is uniquely identified by npc id.
 * 
 * @author Luno
 */
@XmlRootElement(name = "npc_templates")
@XmlAccessorType(XmlAccessType.FIELD)
public class NpcData {

	@XmlElement(name = "npc_template")
	private List<NpcTemplate> npcs;

	/** A map containing all npc templates */
	@XmlTransient
	private TIntObjectHashMap<NpcTemplate> npcData = new TIntObjectHashMap<>();

	void afterUnmarshal(Unmarshaller u, Object parent) {
		for (NpcTemplate npc : npcs) {
			npcData.put(npc.getTemplateId(), npc);
			if (npc.getTribe() != null && !npc.getTribe().isUsed())
				npc.getTribe().setUsed(true);
			if (npc.getFuncDialogIds() != null) {
				for (Integer dialogActionId : npc.getFuncDialogIds()) {
					if (DialogAction.nameOf(dialogActionId) == null)
						LoggerFactory.getLogger(NpcData.class).warn("Unknown dialog action " + dialogActionId + " for Npc " + npc.getTemplateId());
				}
			}
			if (npc.getTribe() != TribeClass.PET && npc.getTribe() != TribeClass.PET_DARK) { // summons and siege weapons have fixed stats
				NpcRating rating = npc.getRating();
				NpcRank rank = npc.getRank();
				byte level = npc.getLevel();
				StatsTemplate template = npc.getStatsTemplate();
				if (template.getAttack() == 0)
					template.setAttack(NpcStatCalculation.calculateStat(StatEnum.PHYSICAL_ATTACK, rating, rank, level));
				if (template.getAccuracy() == 0)
					template.setAccuracy(NpcStatCalculation.calculateStat(StatEnum.PHYSICAL_ACCURACY, rating, rank, level));
				if (template.getMagicalAttack() == 0)
					template.setMagicalAttack(NpcStatCalculation.calculateStat(StatEnum.MAGICAL_ATTACK, rating, rank, level));
				if (template.getMacc() == 0)
					template.setMacc(NpcStatCalculation.calculateStat(StatEnum.MAGICAL_ACCURACY, rating, rank, level));
				if (template.getMresist() == 0)
					template.setMresist(NpcStatCalculation.calculateStat(StatEnum.MAGICAL_RESIST, rating, rank, level));
				if (template.getMdef() == 0)
					template.setMdef(NpcStatCalculation.calculateStat(StatEnum.MAGICAL_DEFEND, rating, rank, level));
				if (template.getMcrit() == 0)
					template.setMcrit(50);
				if (template.getPcrit() == 0)
					template.setPcrit(10);
				if (template.getPdef() == 0)
					template.setPdef(NpcStatCalculation.calculateStat(StatEnum.PHYSICAL_DEFENSE, rating, rank, level));
				if (template.getParry() == 0)
					template.setParry(NpcStatCalculation.calculateStat(StatEnum.PARRY, rating, rank, level));
				if (level >= 60 && template.getStrikeResist() == 0)
					template.setStrikeResist(NpcStatCalculation.calculateStat(StatEnum.PHYSICAL_CRITICAL_RESIST, rating, rank, level));

				template.setAbnormalResistance(NpcStatCalculation.calculateStat(StatEnum.ABNORMAL_RESISTANCE_ALL, rating, rank, level));
			}
		}
		npcs.clear();
		npcs = null;
	}

	public int size() {
		return npcData.size();
	}

	/**
	 * /** Returns an {@link NpcTemplate} object with given id.
	 * 
	 * @param id
	 *          id of NPC
	 * @return NpcTemplate object containing data about NPC with that id.
	 */
	public NpcTemplate getNpcTemplate(int id) {
		return npcData.get(id);
	}

	/**
	 * @return the npcData
	 */
	public TIntObjectHashMap<NpcTemplate> getNpcData() {
		return npcData;
	}
}
