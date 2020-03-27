package com.aionemu.gameserver.skillengine.model;

import java.util.HashMap;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

import com.aionemu.gameserver.model.templates.item.enums.ItemGroup;

/**
 * @author kecims
 */

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Times")
public class Times {

	@XmlAttribute(name = "weapon")
	private String weapon;

	@XmlAttribute(name = "id")
	private int id;

	@XmlAttribute(name = "min")
	private float minTime;

	@XmlAttribute(name = "max")
	private float maxTime;

	@XmlAttribute(name = "animation_length")
	private float animationLength;

	public int getId() {
		return id;
	}

	public float getMinTime() {
		return minTime;
	}

	public float getMaxTime() {
		return maxTime;
	}

	public float getAnimationLength() {
		return animationLength;
	}

	public String getWeapon() {
		return weapon;
	}

}
