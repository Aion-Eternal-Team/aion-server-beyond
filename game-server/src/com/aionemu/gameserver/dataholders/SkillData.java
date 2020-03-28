package com.aionemu.gameserver.dataholders;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.slf4j.LoggerFactory;

import com.aionemu.gameserver.skillengine.model.Motion;
import com.aionemu.gameserver.skillengine.model.MotionTime;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;

import gnu.trove.map.hash.TIntObjectHashMap;

/**
 * @author ATracer
 * @modified Neon
 */
@XmlRootElement(name = "skill_data")
@XmlAccessorType(XmlAccessType.FIELD)
public class SkillData {

	@XmlElement(name = "skill_template")
	private List<SkillTemplate> skillTemplates;

	/**
	 * Map that contains skillId - SkillTemplate key-value pair
	 */
	@XmlTransient
	private TIntObjectHashMap<SkillTemplate> skillData = new TIntObjectHashMap<>();

	@XmlTransient
	private Map<String, List<SkillTemplate>> skillTemplatesByGroup = new LinkedHashMap<>();

	@XmlTransient
	private Map<String, List<SkillTemplate>> skillTemplatesByStack = new LinkedHashMap<>();

	/**
	 * Map that contains cooldownId - skillId List
	 */
	@XmlTransient
	private TIntObjectHashMap<List<Integer>> cooldownGroups = new TIntObjectHashMap<>();

	void afterUnmarshal(Unmarshaller u, Object parent) {
		skillData.clear();
		skillTemplatesByGroup.clear();
		skillTemplatesByStack.clear();
		cooldownGroups.clear();
		for (SkillTemplate skillTemplate : skillTemplates) {
			int skillId = skillTemplate.getSkillId();
			int cooldownId = skillTemplate.getCooldownId();
			skillData.put(skillId, skillTemplate);
			if (!cooldownGroups.containsKey(cooldownId))
				cooldownGroups.put(cooldownId, new ArrayList<>());
			cooldownGroups.get(cooldownId).add(skillId);

			if (skillTemplate.getGroup() != null) {
				if (!skillTemplatesByGroup.containsKey(skillTemplate.getGroup()))
					skillTemplatesByGroup.put(skillTemplate.getGroup(), new ArrayList<>());
				skillTemplatesByGroup.get(skillTemplate.getGroup()).add(skillTemplate);
			}
			if (skillTemplate.getStack() != null) {
				if (!skillTemplatesByStack.containsKey(skillTemplate.getGroup()))
					skillTemplatesByStack.put(skillTemplate.getGroup(), new ArrayList<>());
				skillTemplatesByStack.get(skillTemplate.getGroup()).add(skillTemplate);
			}
		}
		skillTemplates = null;
	}

	/**
	 * @param skillId
	 * @return SkillTemplate
	 */
	public SkillTemplate getSkillTemplate(int skillId) {
		return skillData.get(skillId);
	}

	/**
	 * @param skillGroup
	 * @return All skill templates of this group. A group is less precise and may be null.
	 */
	public List<SkillTemplate> getSkillTemplatesByGroup(String skillGroup) {
		return skillTemplatesByGroup.get(skillGroup);
	}

	/**
	 * @param skillStack
	 * @return All skill templates of this stack. A stack is more precise than a group.
	 *         For example: Charge skills have their own stack per charging level. Also, skills that are similar for both factions have the same group,
	 *         but different stacks.
	 */
	public List<SkillTemplate> getSkillTemplatesByStack(String skillStack) {
		return skillTemplatesByStack.get(skillStack);
	}

	/**
	 * @return skillData.size()
	 */
	public int size() {
		return skillData.size();
	}

	/**
	 * @return the skillTemplates
	 */
	public Collection<SkillTemplate> getSkillTemplates() {
		return skillData.valueCollection();
	}

	/**
	 * This method is used to get all skills assigned to a specific cooldownId
	 * 
	 * @param cooldownId
	 * @return List including all skills for asked cooldownId
	 */
	public List<Integer> getSkillsForCooldownId(int cooldownId) {
		return cooldownGroups.get(cooldownId);
	}

	public void validateMotions() {
		StringBuilder names = new StringBuilder();
		StringBuilder missing = new StringBuilder();
		Set<String> motionNames = new HashSet<>();
		for (SkillTemplate t : getSkillTemplates()) {
			Motion m = t.getMotion();
			if (m != null) {
				if (m.getName() == null && !m.isInstantSkill()) {// TODO initialize a default motion? some skills have explicit motion speeds but no motion name o.O
					names.append(t.getSkillId()).append(", ");
				} else if (m.getName() != null && motionNames.add(m.getName())) {
					MotionTime mt = DataManager.MOTION_DATA.getMotionTime(m.getName());
					if (mt == null)
						missing.append('"').append(m.getName()).append("\" (skill id ").append(t.getSkillId()).append("), ");
				}
			}
		}
		if (names.length() > 0)
			LoggerFactory.getLogger(SkillData.class).warn("Missing motion names for these skills: {}", names.substring(0, names.length() - 2));
		if (missing.length() > 0)
			LoggerFactory.getLogger(SkillData.class).warn("Missing motion times for these motion names: {}", missing.substring(0, missing.length() - 2));
	}
}
