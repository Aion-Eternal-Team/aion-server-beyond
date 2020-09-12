package com.aionemu.gameserver.skillengine.model;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import com.aionemu.commons.utils.Rnd;
import com.aionemu.gameserver.controllers.attack.AggroList;
import com.aionemu.gameserver.controllers.attack.AttackStatus;
import com.aionemu.gameserver.controllers.observer.ActionObserver;
import com.aionemu.gameserver.controllers.observer.AttackCalcObserver;
import com.aionemu.gameserver.controllers.observer.ObserverType;
import com.aionemu.gameserver.model.SkillElement;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.stats.calc.StatOwner;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.network.aion.serverpackets.SM_ATTACK_STATUS.TYPE;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SKILL_ACTIVATION;
import com.aionemu.gameserver.services.event.Event;
import com.aionemu.gameserver.skillengine.condition.Conditions;
import com.aionemu.gameserver.skillengine.effect.*;
import com.aionemu.gameserver.skillengine.model.EffectReserved.ResourceType;
import com.aionemu.gameserver.skillengine.periodicaction.PeriodicAction;
import com.aionemu.gameserver.skillengine.periodicaction.PeriodicActions;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.utils.stats.StatFunctions;

/**
 * @author ATracer
 * @modified Wakizashi, Sippolo, kecimis, Neon
 */
public class Effect implements StatOwner {

	private final Creature effector;
	private final Creature effected;
	private final SkillTemplate skillTemplate;
	private Skill skill;
	private int skillLevel;
	private Integer duration;
	private long endTime;
	private SkillMoveType skillMoveType = SkillMoveType.DEFAULT;
	private Future<?> endTask = null;
	private Future<?>[] periodicTasks = null;
	private Future<?> periodicActionsTask = null;

	private int effectedHp = -1;

	private HashSet<EffectReserved> reserveds = new HashSet<>();

	private SpellStatus spellStatus = SpellStatus.NONE;
	private DashStatus dashStatus = DashStatus.NONE;
	private AttackStatus attackStatus = AttackStatus.NORMALHIT;

	/**
	 * shield effects related
	 */
	private int shieldDefense;
	private int reflectedDamage = 0;
	private int reflectedSkillId = 0;
	private int protectedSkillId = 0;
	private int protectedDamage = 0;
	private int protectorId = 0;
	private int mpAbsorbed = 0;
	private int mpShieldSkillId = 0;

	private boolean addedToController;
	private AttackCalcObserver[] attackStatusObserver;

	private AttackCalcObserver[] attackShieldObserver;

	private boolean launchSubEffect = true;
	private Effect subEffect;

	private AtomicBoolean hasEnded = new AtomicBoolean();
	private boolean isCancelOnDmg;
	private boolean subEffectAbortedBySubConditions;

	/**
	 * Hate that will be placed on effected list
	 */
	private int tauntHate;
	/**
	 * Total hate that will be broadcasted
	 */
	private int effectHate;

	private final Map<Integer, EffectTemplate> successEffects = new ConcurrentHashMap<>();

	private int carvedSignet = 0;

	private int signetBurstedCount = 0;

	private int abnormals;

	/**
	 * Action observer that should be removed after effect end
	 */
	private ActionObserver[] actionObserver;

	private float targetX, targetY, targetZ;
	private float x, y, z;
	private int worldId, instanceId;

	private ForceType forceType;

	/**
	 * power of effect ( used for dispels)
	 */
	private int power;
	/**
	 * accModBoost used for SignetBurstEffect
	 */
	private int accModBoost = 0;

	private EffectResult effectResult = EffectResult.NORMAL;

	private boolean endedByTime = false;

	private Effect designatedDispelEffect = null;

	public Effect(Skill skill, Creature effected) {
		this(skill.getEffector(), effected, skill.getSkillTemplate(), skill.getSkillLevel(), null, null);
		this.effectHate = skill.getHate();
		this.skill = skill;
	}

	public Effect(Creature effector, Creature effected, SkillTemplate skillTemplate, int skillLevel) {
		this(effector, effected, skillTemplate, skillLevel, null, null);
	}

	/**
	 * If duration is null, it will be calculated upon execution, else the forced value will be used.
	 */
	public Effect(Creature effector, Creature effected, SkillTemplate skillTemplate, int skillLevel, Integer duration, ForceType forceType) {
		this.effector = effector;
		this.effected = effected;
		this.skillTemplate = skillTemplate;
		this.skillLevel = skillLevel;
		this.duration = duration;
		this.forceType = forceType;

		this.power = initializePower();
	}

	public void setWorldPosition(int worldId, int instanceId, float x, float y, float z) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.worldId = worldId;
		this.instanceId = instanceId;
	}

	public int getEffectorId() {
		return effector.getObjectId();
	}

	public final Skill getSkill() {
		return skill;
	}

	public void setAbnormal(AbnormalState state) {
		abnormals |= state.getId();
	}

	public int getAbnormals() {
		return abnormals;
	}

	public int getSkillId() {
		return skillTemplate.getSkillId();
	}

	public String getSkillName() {
		return skillTemplate.getName();
	}

	public final SkillTemplate getSkillTemplate() {
		return skillTemplate;
	}

	public SkillSubType getSkillSubType() {
		return skillTemplate.getSubType();
	}

	public String getStack() {
		return skillTemplate.getStack();
	}

	public int getSkillLevel() {
		return skillLevel;
	}

	public int getSkillStackLvl() {
		return skillTemplate.getLvl();
	}

	public SkillType getSkillType() {
		return skillTemplate.getType();
	}

	public int getDuration() {
		return duration == null ? 0 : duration;
	}

	/**
	 * @return The effected from effect constructor, can differ from getEffected if effect got reflected via shield observer.
	 */
	public Creature getOriginalEffected() {
		return effected;
	}

	/**
	 * @return The creature which receives the damage/skill results. It will be the effector if {@link #isReflected()} returns true.
	 */
	public Creature getEffected() {
		return isReflected() ? effector : effected;
	}

	public Creature getEffector() {
		return effector;
	}

	public boolean isPassive() {
		return skillTemplate.isPassive();
	}

	public boolean isPeriodic() {
		return periodicTasks != null;
	}

	public void setPeriodicTask(Future<?> periodicTask, int position) {
		if (periodicTasks == null)
			periodicTasks = new Future<?>[4];
		else if (periodicTasks[position - 1] != null && periodicTask != null) {
			periodicTask.cancel(false);
			throw new IllegalStateException(getClass().getSimpleName() + " already has a periodic task at position " + position);
		}
		this.periodicTasks[position - 1] = periodicTask;
	}

	public AttackStatus getAttackStatus() {
		return attackStatus;
	}

	public void setAttackStatus(AttackStatus attackStatus) {
		this.attackStatus = attackStatus;
	}

	public List<EffectTemplate> getEffectTemplates() {
		return skillTemplate.getEffects().getEffects();
	}

	public boolean isToggle() {
		return skillTemplate.getActivationAttribute() == ActivationAttribute.TOGGLE;
	}

	public boolean isChant() {
		return skillTemplate.getTargetSlot() == SkillTargetSlot.CHANT;
	}

	public SkillTargetSlot getTargetSlot() {
		return skillTemplate.getTargetSlot();
	}

	public int getTargetSlotLevel() {
		return skillTemplate.getTargetSlotLevel();
	}

	public DispelCategoryType getDispelCategory() {
		return skillTemplate.getDispelCategory();
	}

	public int getReqDispelLevel() {
		return skillTemplate.getReqDispelLevel();
	}

	/**
	 * @param i
	 * @return attackStatusObserver for this effect template or null
	 */
	public AttackCalcObserver getAttackStatusObserver(int i) {
		return attackStatusObserver != null ? attackStatusObserver[i - 1] : null;
	}

	/**
	 * @param attackStatusObserver
	 *          the attackCalcObserver to set
	 */
	public void setAttackStatusObserver(AttackCalcObserver attackStatusObserver, int i) {
		if (this.attackStatusObserver == null)
			this.attackStatusObserver = new AttackCalcObserver[4];
		this.attackStatusObserver[i - 1] = attackStatusObserver;
	}

	/**
	 * @param i
	 * @return attackShieldObserver for this effect template or null
	 */
	public AttackCalcObserver getAttackShieldObserver(int i) {
		return attackShieldObserver != null ? attackShieldObserver[i - 1] : null;
	}

	/**
	 * @param attackShieldObserver
	 *          the attackShieldObserver to set
	 */
	public void setAttackShieldObserver(AttackCalcObserver attackShieldObserver, int i) {
		if (this.attackShieldObserver == null)
			this.attackShieldObserver = new AttackCalcObserver[4];
		this.attackShieldObserver[i - 1] = attackShieldObserver;
	}

	/**
	 * @return the effectedHp
	 */
	public int getEffectedHp() {
		return effectedHp;
	}

	public EffectReserved getReserveds(int i) {
		for (EffectReserved er : this.reserveds) {
			if (er.getPosition() == i)
				return er;
		}
		return new EffectReserved(0, 0, ResourceType.HP, true, false);
	}

	public void setReserveds(EffectReserved er, boolean overTimeEffect) {
		// set effected hp
		// TODO RI_ChargeAttack_G, RI_ChargingFlight_G
		boolean instantSkill = false;
		if (this.getSkill() != null && this.getSkill().isInstantSkill())
			instantSkill = true;
		if (er.getType() == ResourceType.HP && er.getValue() != 0 && !overTimeEffect && !instantSkill && !getEffected().isInvulnerable()) {
			Creature effected = getEffected();
			int value = (er.isDamage() ? -er.getValue() : er.getValue());
			value += effected.getLifeStats().getCurrentHp();
			if (value <= 0) {
				value = 0;
				// effected is about to die
				effected.getLifeStats().setIsAboutToDie(true);
				effected.getLifeStats().setKillingBlow(er.getValue());
			}
			effectedHp = (int) (100f * value / effected.getLifeStats().getMaxHp());
		}

		this.reserveds.add(er);
	}

	public TreeSet<EffectReserved> getReservedsToSend() {
		TreeSet<EffectReserved> toSend = new TreeSet<>();

		for (EffectReserved er : this.reserveds) {
			if (er.isSend() && er.getValue() != 0)
				toSend.add(er);
		}

		if (toSend.isEmpty())
			toSend.add(new EffectReserved(0, 0, ResourceType.HP, true));

		return toSend;
	}

	/**
	 * @return the launchSubEffect
	 */
	public boolean isLaunchSubEffect() {
		return launchSubEffect;
	}

	/**
	 * @param launchSubEffect
	 *          the launchSubEffect to set
	 */
	public void setLaunchSubEffect(boolean launchSubEffect) {
		this.launchSubEffect = launchSubEffect;
	}

	/**
	 * @return the shieldDefense
	 */
	public int getShieldDefense() {
		return shieldDefense;
	}

	/**
	 * @param shieldDefense
	 *          the shieldDefense to set
	 */
	public void setShieldDefense(int shieldDefense) {
		this.shieldDefense = shieldDefense;
	}

	/**
	 * @return True if the whole effect is reflected (through shield observer)
	 */
	public boolean isReflected() {
		return (shieldDefense & ShieldType.SKILL_REFLECTOR.getId()) != 0;
	}

	/**
	 * reflected damage
	 * 
	 * @return
	 */
	public int getReflectedDamage() {
		return this.reflectedDamage;
	}

	public void setReflectedDamage(int value) {
		this.reflectedDamage = value;
	}

	public int getReflectedSkillId() {
		return this.reflectedSkillId;
	}

	public void setReflectedSkillId(int value) {
		this.reflectedSkillId = value;
	}

	public int getProtectedSkillId() {
		return this.protectedSkillId;
	}

	public void setProtectedSkillId(int skillId) {
		this.protectedSkillId = skillId;
	}

	public int getProtectedDamage() {
		return this.protectedDamage;
	}

	public void setProtectedDamage(int protectedDamage) {
		this.protectedDamage = protectedDamage;
	}

	public int getProtectorId() {
		return this.protectorId;
	}

	public void setProtectorId(int protectorId) {
		this.protectorId = protectorId;
	}

	/**
	 * @return the spellStatus
	 */
	public SpellStatus getSpellStatus() {
		return spellStatus;
	}

	/**
	 * @param spellStatus
	 *          the spellStatus to set
	 */
	public void setSpellStatus(SpellStatus spellStatus) {
		this.spellStatus = spellStatus;
	}

	/**
	 * @return the dashStatus
	 */
	public DashStatus getDashStatus() {
		return dashStatus;
	}

	/**
	 * @param dashStatus
	 *          the dashStatus to set
	 */
	public void setDashStatus(DashStatus dashStatus) {
		this.dashStatus = dashStatus;
	}

	/**
	 * Number of signets carved on target
	 * 
	 * @return
	 */
	public int getCarvedSignet() {
		return this.carvedSignet;
	}

	public void setCarvedSignet(int value) {
		this.carvedSignet = value;
	}

	/**
	 * @return the subEffect
	 */
	public Effect getSubEffect() {
		return subEffect;
	}

	/**
	 * @param subEffect
	 *          the subEffect to set
	 */
	public void setSubEffect(Effect subEffect) {
		this.subEffect = subEffect;
	}

	/**
	 * @param effectId
	 * @return true or false
	 */
	public boolean containsEffectId(int effectId) {
		for (EffectTemplate template : successEffects.values()) {
			if (template.getEffectId() == effectId)
				return true;
		}
		return false;
	}

	public void setForceType(ForceType forceType) {
		this.forceType = forceType;
	}

	public ForceType getForceType() {
		return forceType;
	}

	public boolean isForcedEffect() {
		return forceType != null;
	}

	public boolean isPhysicalEffect() {
		EffectTemplate mainEffectTemplate = skillTemplate.getEffectTemplate(1);
		return mainEffectTemplate != null && mainEffectTemplate.getElement() == SkillElement.NONE;
	}

	/**
	 * Do initialization with proper calculations<br>
	 * Correct lifecycle of Effect: INITIALIZE - APPLY - START - END
	 */
	public void initialize() {
		if (skillTemplate.getEffects() == null)
			return;

		for (EffectTemplate template : getEffectTemplates()) {
			template.calculate(this);
		}

		if (!isInSuccessEffects(1)) {
			successEffects.clear();
		} else {
			if (effectHate == 0) // can be overridden from constructor with skill (from pet order)
				effectHate = calculateHateForSuccessEffects();
			if (isLaunchSubEffect()) {
				for (EffectTemplate template : successEffects.values()) {
					template.calculateSubEffect(this);
				}
			}
		}

		if (successEffects.isEmpty()) {
			if (isPhysicalEffect()) {
				if (getAttackStatus() == AttackStatus.CRITICAL)
					setAttackStatus(AttackStatus.CRITICAL_DODGE);
				else
					setAttackStatus(AttackStatus.DODGE);
				setSpellStatus(SpellStatus.DODGE2);
				skillMoveType = SkillMoveType.DODGE;
				effectResult = EffectResult.DODGE;
			} else {
				if (getAttackStatus() == AttackStatus.CRITICAL)
					setAttackStatus(AttackStatus.CRITICAL_RESIST);// TODO recheck
				else
					setAttackStatus(AttackStatus.RESIST);
				setSpellStatus(SpellStatus.NONE);
				skillMoveType = SkillMoveType.RESIST;
				effectResult = EffectResult.RESIST;
			}
		}

		// set spellstatus for sm_castspell_end packet
		switch (AttackStatus.getBaseStatus(getAttackStatus())) {
			case DODGE:
				setSpellStatus(SpellStatus.DODGE);
				break;
			case PARRY:
				if (getSpellStatus() == SpellStatus.NONE)
					setSpellStatus(SpellStatus.PARRY);
				break;
			case BLOCK:
				if (getSpellStatus() == SpellStatus.NONE)
					setSpellStatus(SpellStatus.BLOCK);
				break;
			case RESIST:
				setSpellStatus(SpellStatus.RESIST);
				break;
		}
	}

	private int calculateHateForSuccessEffects() {
		int effectHate = 0;
		for (EffectTemplate template : successEffects.values()) {
			effectHate += template.calculateHate(this);
		}
		return effectHate == 0 ? 0 : StatFunctions.calculateHate(getEffector(), effectHate);
	}

	/**
	 * Apply all effect templates
	 */
	public void applyEffect() {
		if (successEffects.isEmpty()) {
			broadcastHate();
			return;
		}

		Creature effected = getEffected();
		for (EffectTemplate template : successEffects.values()) {
			if (!shouldApplyFurtherEffects(effected))
				break;
			template.applyEffect(this);
			if (!shouldApplyFurtherEffects(effected))
				break;
			template.startSubEffect(this);
		}
		if (effected != null)
			effected.getAi().onEffectApplied(this);
	}

	public void broadcastHate() {
		if (effectHate != 0 && tauntHate >= 0) { // don't add hate if taunt hate is < 0!
			Creature effected = getEffected();
			effected.getAggroList().addHate(effector, effectHate);
			effected.getKnownList().forEachObject(visibleObject -> {
				if (visibleObject instanceof Creature) {
					AggroList al = ((Creature) visibleObject).getAggroList();
					if (al.isHating(effector))
						al.addHate(effector, effectHate);
				}
			});
			effectHate = 0; // set to 0 to avoid external second broadcast
		}
	}

	private boolean shouldApplyFurtherEffects(Creature effected) {
		if (effected != null) {
			if (!effected.isSpawned() && !skillTemplate.isPassive()) // only allow on despawned if it's a passive skill (players get them during enterWorld)
				return false;
			if (effected.isDead() && !skillTemplate.hasResurrectEffect())
				return false;
		}
		return true;
	}

	/**
	 * Start effect which includes: - start effect defined in template - start subeffect if possible - activate toggle skill if needed - schedule end of
	 * effect
	 */
	public void startEffect() {
		synchronized (this) {
			if (hasEnded.get()) // multiple concurrent skill casts can end each others effects by conflict
				return;
			if (successEffects.isEmpty())
				return;

			schedulePeriodicActions();

			for (EffectTemplate template : successEffects.values()) {
				template.startEffect(this);
				checkUseEquipmentConditions();
				checkCancelOnDmg();
			}

			broadcastHate();

			if (duration == null) {
				if (isToggle()) {
					if (effector instanceof Player)
						activateToggleSkill();
					duration = skillTemplate.getToggleTimer();
				} else {
					duration = calculateEffectsDuration();
				}
			}
			if (duration == 0)
				return;
			endTime = System.currentTimeMillis() + duration;

			endTask = ThreadPoolManager.getInstance().schedule(() -> {
				endedByTime = true;
				endEffect(true);
			}, duration);
		}
		effected.getPosition().getWorldMapInstance().getInstanceHandler().onStartEffect(this);
	}

	/**
	 * Will visually activate toggle skill
	 */
	private void activateToggleSkill() {
		PacketSendUtility.sendPacket((Player) effector, new SM_SKILL_ACTIVATION(getSkillId(), true));
	}

	/**
	 * Will visually deactivate toggle skill
	 */
	private void deactivateToggleSkill() {
		PacketSendUtility.sendPacket((Player) effector, new SM_SKILL_ACTIVATION(getSkillId(), false));
	}

	public void endEffect() {
		endEffect(true);
	}

	/**
	 * End effect and all effect actions
	 */
	public void endEffect(boolean broadcast) {
		synchronized (this) { // wait for startEffect before ending
			if (!hasEnded.compareAndSet(false, true))
				return;
		}
		stopTasks();

		endEffects();

		// if effect is a stance, remove stance from player
		if (effector instanceof Player) {
			Player player = (Player) effector;
			if (player.getController().getStanceSkillId() == getSkillId())
				player.getController().stopStance();
		}

		Creature effected = getEffected();
		// TODO better way to finish
		if (getSkillTemplate().getTargetSlot() == SkillTargetSlot.SPEC2) {
			effected.getLifeStats().increaseHp(TYPE.REGULAR, (int) (effected.getLifeStats().getMaxHp() * 0.2f), getEffector());
			effected.getLifeStats().increaseMp((int) (effected.getLifeStats().getMaxMp() * 0.2f));
		}

		if (isToggle() && effector instanceof Player) {
			deactivateToggleSkill();
		}
		effected.getEffectController().clearEffect(this, broadcast);

		if (effected instanceof Npc)
			effected.getAi().onEffectEnd(this);
		effected.getPosition().getWorldMapInstance().getInstanceHandler().onEndEffect(this);
	}

	/**
	 * Stop all scheduled tasks
	 */
	public void stopTasks() {
		if (endTask != null) {
			endTask.cancel(false);
			endTask = null;
		}

		if (periodicTasks != null) {
			for (Future<?> periodicTask : periodicTasks) {
				if (periodicTask != null)
					periodicTask.cancel(false);
			}
			periodicTasks = null;
		}

		stopPeriodicActions();
	}

	/**
	 * @return Time in milliseconds till the effect ends
	 */
	public long getRemainingTimeMillis() {
		return endTime - System.currentTimeMillis();
	}

	public int getRemainingTimeToDisplay() {
		if (getDuration() == 0) // permanent effect (or not yet started, should not happen)
			return -1;
		if (duration >= 86400000 && effected instanceof Npc) // >= 24h
			return -1;
		long remainingTimeMillis = getRemainingTimeMillis();
		return remainingTimeMillis > Integer.MAX_VALUE ? -1 : (int) remainingTimeMillis;
	}

	public boolean canSaveOnLogout() {
		if (skillTemplate.isNoSaveOnLogout())
			return false;
		if (getDuration() == 0) // permanent effect, such as toggle or passive skill (or not yet started, should not happen)
			return false;
		if (duration >= 86400000) // effects with duration >= 24h are event or instance related
			return false;
		return true;
	}

	/**
	 * @return the endTime
	 */
	public long getEndTime() {
		return endTime;
	}

	/**
	 * PVP damage ration
	 * 
	 * @return
	 */
	public int getPvpDamage() {
		return skillTemplate.getPvpDamage();
	}

	public ItemTemplate getItemTemplate() {
		return skill == null ? null : skill.getItemTemplate();
	}

	public boolean isGodstoneActivated() {
		return getItemTemplate() != null && getItemTemplate().getGodstoneInfo() != null;
	}

	/**
	 * Try to add this effect to effected controller
	 */
	public void addToEffectedController() {
		if (!addedToController) {
			Creature effected = getEffected();
			if (effected.getLifeStats() != null && !effected.isDead()) {
				effected.getEffectController().addEffect(this);
				addedToController = true;
			}
		}
	}

	public int getEffectHate() {
		return effectHate;
	}

	/**
	 * @return the tauntHate
	 */
	public int getTauntHate() {
		return tauntHate;
	}

	/**
	 * @param tauntHate
	 *          the tauntHate to set
	 */
	public void setTauntHate(int tauntHate) {
		this.tauntHate = tauntHate;
	}

	/**
	 * @param i
	 * @return actionObserver for this effect template
	 */
	public ActionObserver getActionObserver(int i) {
		return actionObserver == null ? null : actionObserver[i - 1];
	}

	/**
	 * @param observer
	 *          the observer to set
	 */
	public void setActionObserver(ActionObserver observer, int i) {
		if (actionObserver == null)
			actionObserver = new ActionObserver[4];
		actionObserver[i - 1] = observer;
	}

	public void addSuccessEffect(EffectTemplate effect) {
		successEffects.put(effect.getPosition(), effect);
	}

	public boolean isInSuccessEffects(int position) {
		return successEffects.get(position) != null;
	}

	public EffectTemplate effectInPos(int pos) {
		return successEffects.get(pos);
	}

	public Collection<EffectTemplate> getSuccessEffects() {
		return successEffects.values();
	}

	public void addAllEffectToSucess() {
		successEffects.clear();
		for (EffectTemplate template : getEffectTemplates()) {
			successEffects.put(template.getPosition(), template);
		}
	}

	private void schedulePeriodicActions() {
		if (skillTemplate.getPeriodicActions() == null)
			return;
		PeriodicActions periodicActions = skillTemplate.getPeriodicActions();
		if (periodicActions.getPeriodicActions() == null || periodicActions.getPeriodicActions().isEmpty())
			return;
		int checktime = periodicActions.getChecktime();
		periodicActionsTask = ThreadPoolManager.getInstance().scheduleAtFixedRate(() -> {
			for (PeriodicAction action : periodicActions.getPeriodicActions())
				action.act(Effect.this);
		}, checktime, checktime);
	}

	private void stopPeriodicActions() {
		if (periodicActionsTask != null) {
			periodicActionsTask.cancel(false);
			periodicActionsTask = null;
		}
	}

	private int calculateEffectsDuration() {
		long duration = calculateTemplateDuration();

		// adjust with pvp duration (not sure why some self target skills have pvp duration o.O idk how to handle that)
		if (getEffected() instanceof Player) {
			if (skillTemplate.getPvpDuration() != 0 && !effector.equals(effected))
				duration = duration * skillTemplate.getPvpDuration() / 100;
			if (getEffector().getMaster() instanceof Player) {
				for (EffectTemplate et : successEffects.values()) {
					if (et instanceof ParalyzeEffect && ((Player) getEffected()).validateLastParalyzeTime()) {
						duration = (long) (duration * getCumulativeResistDurationMultiplierFor(((Player) getEffected()).getParalyzeCount()) / 100f);
						break;
					} else if (et instanceof FearEffect && ((Player) getEffected()).validateLastFearTime()) {
						duration = (long) (duration * getCumulativeResistDurationMultiplierFor(((Player) getEffected()).getFearCount()) / 100f);
						break;
					} else if (et instanceof SleepEffect && ((Player) getEffected()).validateLastSleepTime()) {
						duration = (long) (duration * getCumulativeResistDurationMultiplierFor(((Player) getEffected()).getSleepCount()) / 100f);
						break;
					}
				}
			}
		}

		return (int) Math.min(Integer.MAX_VALUE, duration);
	}

	private long calculateTemplateDuration() {
		long longestTemplateDuration = 0;
		// iterate skill's effects until we can calculate a duration time, which is valid for all of them
		for (EffectTemplate et : successEffects.values()) {
			long effectDuration = et.getDuration2() + ((long) et.getDuration1()) * getSkillLevel(); // some event skills would produce an int overflow
			if (et.getRandomTime() > 0)
				effectDuration -= Rnd.get(0, et.getRandomTime());
			if (effectDuration > longestTemplateDuration)
				longestTemplateDuration = effectDuration;
		}
		return longestTemplateDuration;
	}

	private int getCumulativeResistDurationMultiplierFor(int resistCount) {
		switch (resistCount) {
			case 0:
			case 1:
				return 100;
			case 2:
				return 90;
			case 3:
				return 85;
			case 4:
				return 80;
			default:
				return 1;
		}
	}

	public boolean isDeityAvatar() {
		return skillTemplate.isDeityAvatar();
	}

	/**
	 * @return the x
	 */
	public float getX() {
		return x;
	}

	/**
	 * @return the y
	 */
	public float getY() {
		return y;
	}

	/**
	 * @return the z
	 */
	public float getZ() {
		return z;
	}

	public int getWorldId() {
		return worldId;
	}

	public int getInstanceId() {
		return instanceId;
	}

	/**
	 * @return the skillMoveType
	 */
	public SkillMoveType getSkillMoveType() {
		return skillMoveType;
	}

	/**
	 * @param skillMoveType
	 *          the skillMoveType to set
	 */
	public void setSkillMoveType(SkillMoveType skillMoveType) {
		this.skillMoveType = skillMoveType;
	}

	/**
	 * @return the targetX
	 */
	public float getTargetX() {
		return targetX;
	}

	/**
	 * @return the targetY
	 */
	public float getTargetY() {
		return targetY;
	}

	/**
	 * @return the targetZ
	 */
	public float getTargetZ() {
		return targetZ;
	}

	public void setTargetLoc(float x, float y, float z) {
		this.targetX = x;
		this.targetY = y;
		this.targetZ = z;
	}

	public void setSubEffectAborted(boolean value) {
		this.subEffectAbortedBySubConditions = value;
	}

	public boolean isSubEffectAbortedBySubConditions() {
		return this.subEffectAbortedBySubConditions;
	}

	/**
	 * Check all in use equipment conditions
	 * 
	 * @return true if all conditions have been satisfied
	 */
	private boolean useEquipmentConditionsCheck() {
		Conditions useEquipConditions = skillTemplate.getUseEquipmentconditions();
		return useEquipConditions == null || useEquipConditions.validate(this);
	}

	/**
	 * Check use equipment conditions by adding Unequip observer if needed
	 */
	private void checkUseEquipmentConditions() {
		// If skill has use equipment conditions, observe for unequip event and remove effect if event occurs
		if (getSkillTemplate().getUseEquipmentconditions() != null && !getSkillTemplate().getUseEquipmentconditions().getConditions().isEmpty()) {
			Creature effected = getEffected();
			ActionObserver observer = new ActionObserver(ObserverType.UNEQUIP) {

				@Override
				public void unequip(Item item, Player owner) {
					if (!useEquipmentConditionsCheck()) {
						endEffect();
						effected.getObserveController().removeObserver(this);
					}
				}
			};
			effected.getObserveController().addObserver(observer);
		}
	}

	/**
	 * Add Attacked/Dot_Attacked observers if this effect needs to be removed on damage received by effected
	 */
	private void checkCancelOnDmg() {
		if (isCancelOnDmg()) {
			Creature effected = getEffected();
			effected.getObserveController().attach(new ActionObserver(ObserverType.ATTACKED) {

				@Override
				public void attacked(Creature creature, int skillId) {
					effected.getEffectController().removeEffect(getSkillId());
				}
			});

			effected.getObserveController().attach(new ActionObserver(ObserverType.DOT_ATTACKED) {

				@Override
				public void dotattacked(Creature creature, Effect dotEffect) {
					effected.getEffectController().removeEffect(getSkillId());
				}
			});
		}
	}

	public void setCancelOnDmg(boolean value) {
		this.isCancelOnDmg = value;
	}

	public boolean isCancelOnDmg() {
		return isCancelOnDmg;
	}

	public void endEffects() {
		boolean hasStatModifiers = false;
		for (EffectTemplate template : successEffects.values()) {
			template.endEffect(this);
			if (!hasStatModifiers && (template.getChange() != null && !template.getChange().isEmpty() || template instanceof AbstractAbsoluteStatEffect))
				hasStatModifiers = true;
		}
		if (hasStatModifiers)
			getEffected().getGameStats().endEffect(this);
	}

	private int initializePower() {
		// tweak for pet order spirit substitution and bodyguard
		if (skillTemplate.getActivationAttribute().equals(ActivationAttribute.MAINTAIN)) {
			return 30;
		} else if (skillTemplate.getActivationAttribute().equals(ActivationAttribute.TOGGLE)) {
			return 100;
		}
		switch (skillTemplate.getName()) {
			case "Explosion of Wrath": // Tahabata fear
			case "Soul Petrify": // Tahabata paralyse
			case "Protective Shield":
				return 30;
			case "Surkana Aetheric Field":
				return 60;
			case "Sap Damage":
			case "Resistance":
			case "Weeping Curtain":
			case "Submissive Strike":
			case "Spinning Smash":
			case "Conqueror's Strike":
			case "Weaken":
			case "Canyonguard's Target":
			case "Relic Explosion":
			case "Ide Shielding":
				return 255;

		}
		if (skillTemplate.getGroup() != null) {
			switch (skillTemplate.getGroup()) {
				case "PR_GODSVOICE":// Word of Destruction I
				case "RA_SILENTARROW":
					return 20;
				case "WA_STEADINESS":// Unwavering Devotion
				case "KN_IRONBODY":// Iron Skin
				case "KN_INVINSIBLEPROTECT": // Empyrean Providence
				case "FI_CRUSADE": // Nezekans Blessing
				case "FI_BERSERK": // Zikels Threat
				case "KN_PURIFYWING":// Prayer of Freedom
				case "EL_ORDER_SACRIFICE":// Spirit Substitution
				case "EL_ETERNALSHIELD": // Spirit Preserve
				case "CH_IMPROVEDBODY": // Elemental Screen
				case "WI_GAINMANA": // Gain Mana
				case "RA_SHOCKARROW":// Shock Arrow
				case "RA_ROOTARROW":
				case "FI_ANKLEGRAB":// Ankle Snare
				case "WI_COUNTERMAGIC":// Curse Of Weakness
				case "PR_PAINLINKS":// Chain of Suffering
				case "CH_ETERNALSEAL": // Stilling Word
				case "EL_DECAYINGWING":
				case "AS_VENOMSTAB":
				case "CH_SOAREDROCK":
					return 30;
				case "EL_PLAGUESWAM":// Cursecloud
				case "BA_SONGOFBRAVE":
					return 40; // need 2 cleric dispels or potions
				case "RI_MAGNETICFIELD":
					return 255;
			}
		}
		return 10;
	}

	/**
	 * @return the power
	 */
	public int getPower() {
		return power;
	}

	/**
	 * @param power
	 *          the power to set
	 */
	public void setPower(int power) {
		this.power = power;
	}

	public int removePower(int power) {
		this.power -= power;

		return this.power;
	}

	public void setAccModBoost(int accModBoost) {
		this.accModBoost = accModBoost;
	}

	public int getAccModBoost() {
		return this.accModBoost;
	}

	/**
	 * functions that check for given effecttype
	 */
	public boolean isHideEffect() {
		return getSkillTemplate().hasAnyEffect(EffectType.HIDE);
	}

	public boolean isParalyzeEffect() {
		return getSkillTemplate().hasAnyEffect(EffectType.PARALYZE);
	}

	public boolean isStunEffect() {
		return getSkillTemplate().hasAnyEffect(EffectType.STUN);
	}

	public boolean isSanctuaryEffect() {
		return getSkillTemplate().hasAnyEffect(EffectType.SANCTUARY);
	}

	public boolean isDamageEffect() {
		return getSkillTemplate().hasAnyEffect(EffectType.BACKDASH, EffectType.CARVESIGNET, EffectType.DASH, EffectType.DEATHBLOW,
			EffectType.DELAYEDSPELLATTACKINSTANT, EffectType.DISPELBUFFCOUNTERATK, EffectType.MOVEBEHIND, EffectType.NOREDUCESPELLATKINSTANT,
			EffectType.PROCATKINSTANT, EffectType.SIGNETBURST, EffectType.SKILLATKDRAININSTANT, EffectType.SKILLATTACKINSTANT,
			EffectType.SPELLATKDRAININSTANT, EffectType.SPELLATTACKINSTANT);
	}

	public boolean isNoDeathPenalty() {
		return getSkillTemplate().hasAnyEffect(EffectType.NODEATHPENALTY);
	}

	public boolean isNoResurrectPenalty() {
		return getSkillTemplate().hasAnyEffect(EffectType.NORESURRECTPENALTY);
	}

	public boolean isHiPass() {
		return getSkillTemplate().hasAnyEffect(EffectType.HIPASS);
	}

	public boolean isDelayedDamage() {
		return getSkillTemplate().hasAnyEffect(EffectType.DELAYEDSPELLATTACKINSTANT);
	}

	public boolean isSummoning() {
		return getSkillTemplate().hasAnyEffect(EffectType.SUMMON, EffectType.SUMMONBINDINGGROUPGATE, EffectType.SUMMONFUNCTIONALNPC,
			EffectType.SUMMONGROUPGATE, EffectType.SUMMONHOMING, EffectType.SUMMONHOUSEGATE, EffectType.SUMMONSERVANT, EffectType.SUMMONSKILLAREA,
			EffectType.SUMMONTOTEM, EffectType.SUMMONTRAP);
	}

	public boolean isPetOrderUnSummonEffect() {
		return getSkillTemplate().hasAnyEffect(EffectType.PETORDERUNSUMMON);
	}

	public boolean canRemoveOnDie() {
		if (getSkillTemplate().isNoRemoveOnDie())
			return false;
		if (Event.isEventEffectForceType(forceType))
			return false;
		if (getSkillTemplate().hasAnyEffect(EffectType.XPBOOST))
			return false;
		return true;
	}

	/**
	 * @return the signetBurstedCount
	 */
	public int getSignetBurstedCount() {
		return signetBurstedCount;
	}

	/**
	 * @param signetBurstedCount
	 *          the signetBurstedCount to set
	 */
	public void setSignetBurstedCount(int signetBurstedCount) {
		this.signetBurstedCount = signetBurstedCount;
	}

	public final EffectResult getEffectResult() {
		return effectResult;
	}

	public final void setEffectResult(EffectResult effectResult) {
		this.effectResult = effectResult;
	}

	public boolean isEndedByTime() {
		return endedByTime;
	}

	public int getMpAbsorbed() {
		return mpAbsorbed;
	}

	public void setMpAbsorbed(int mpAbsorbed) {
		this.mpAbsorbed = mpAbsorbed;
	}

	/**
	 * @return the mpShieldSkillId
	 */
	public int getMpShieldSkillId() {
		return mpShieldSkillId;
	}

	/**
	 * @param mpShieldSkillId
	 *          the mpShieldSkillId to set
	 */
	public void setMpShieldSkillId(int mpShieldSkillId) {
		this.mpShieldSkillId = mpShieldSkillId;
	}

	public static class ForceType {

		private static final Map<String, ForceType> forceTypes = new ConcurrentHashMap<>();
		public static final ForceType DEFAULT = getInstance("");
		public static final ForceType MATERIAL_SKILL = getInstance("MATERIAL_SKILL");
		private final String name;

		private ForceType(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}

		public static ForceType getInstance(String name) {
			return forceTypes.computeIfAbsent(name, key -> new ForceType(name));
		}
	}

	public Effect getDesignatedDispelEffect() {
		return designatedDispelEffect;
	}

	public boolean setDesignatedDispelEffect(Effect effect) {
		if (designatedDispelEffect == null) {
			designatedDispelEffect = effect;
			return true;
		}
		return false;
	}

	public void resetDesignatedDispelEffect() {
		designatedDispelEffect = null;
	}
}
