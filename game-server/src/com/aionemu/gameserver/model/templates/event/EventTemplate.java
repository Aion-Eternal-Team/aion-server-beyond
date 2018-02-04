package com.aionemu.gameserver.model.templates.event;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlList;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import com.aionemu.gameserver.dataholders.SpawnsData;
import com.aionemu.gameserver.dataholders.loadingutils.adapters.LocalDateTimeAdapter;
import com.aionemu.gameserver.model.EventTheme;
import com.aionemu.gameserver.model.templates.globaldrops.GlobalRule;

/**
 * @author Rolandas, Neon
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "EventTemplate")
public class EventTemplate {

	@XmlAttribute(name = "name", required = true)
	private String name;

	@XmlAttribute(name = "start")
	@XmlJavaTypeAdapter(LocalDateTimeAdapter.class)
	private LocalDateTime startDate;

	@XmlAttribute(name = "end")
	@XmlJavaTypeAdapter(LocalDateTimeAdapter.class)
	private LocalDateTime endDate;

	@XmlAttribute(name = "theme")
	private EventTheme theme;

	@XmlElementWrapper(name = "event_drops")
	@XmlElement(name = "gd_rule")
	private List<GlobalRule> eventDropRules;

	@XmlElement(name = "quests")
	private EventQuestList quests;

	@XmlElement(name = "spawns")
	private SpawnsData spawns;

	@XmlElement(name = "inventory_drop")
	private InventoryDrop inventoryDrop;

	@XmlList
	@XmlElement(name = "surveys")
	private List<String> surveys;

	@XmlElementWrapper(name = "buffs")
	@XmlElement(name = "buff")
	private List<Buff> buffs;

	public String getName() {
		return name;
	}

	public LocalDateTime getStartDate() {
		return startDate;
	}

	public LocalDateTime getEndDate() {
		return endDate;
	}

	public EventTheme getTheme() {
		return theme;
	}

	public List<GlobalRule> getEventDropRules() {
		return eventDropRules;
	}

	public SpawnsData getSpawns() {
		return spawns;
	}

	public InventoryDrop getInventoryDrop() {
		return inventoryDrop;
	}

	public List<Integer> getStartableQuests() {
		return quests == null ? Collections.emptyList() : quests.getStartableQuests();
	}

	public List<Integer> getMaintainableQuests() {
		return quests == null ? Collections.emptyList() : quests.getMaintainQuests();
	}

	public boolean isInEventPeriod(LocalDateTime time) {
		return (startDate == null || !time.isBefore(startDate)) && (endDate == null || time.isBefore(endDate));
	}

	public List<String> getSurveys() {
		return surveys;
	}

	public List<Buff> getBuffs() {
		return buffs;
	}

}
