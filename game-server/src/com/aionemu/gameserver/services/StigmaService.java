package com.aionemu.gameserver.services;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.utils.Rnd;
import com.aionemu.gameserver.configs.main.MembershipConfig;
import com.aionemu.gameserver.controllers.observer.ItemUseObserver;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.DescriptionId;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.TaskId;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.items.ItemSlot;
import com.aionemu.gameserver.model.skill.PlayerSkillEntry;
import com.aionemu.gameserver.model.templates.item.ItemQuality;
import com.aionemu.gameserver.model.templates.item.Stigma;
import com.aionemu.gameserver.network.aion.serverpackets.SM_INVENTORY_UPDATE_ITEM;
import com.aionemu.gameserver.network.aion.serverpackets.SM_ITEM_USAGE_ANIMATION;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.services.item.ItemPacketService;
import com.aionemu.gameserver.services.trade.PricesService;
import com.aionemu.gameserver.skillengine.model.SkillLearnTemplate;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.utils.audit.AuditLogger;

import javolution.util.FastTable;

/**
 * @author ATracer
 * @modified cura, Neon
 */
public class StigmaService {

	private static final Logger log = LoggerFactory.getLogger(StigmaService.class);
	private static final int MAX_STIGMA_ENCHANT_LVL = 255;

	private static String clearName(String itemName) {
		String[] splits = itemName.split(" ");
		String newName = "";
		for (int i = 0; i < (splits.length - 1); i++) {
			newName += splits[i];
		}
		return newName;
	}

	/**
	 * @param player
	 * @param resultItem
	 * @param slot
	 * @return
	 */
	public static boolean notifyEquipAction(Player player, Item resultItem, long slot) {
		if (resultItem.getItemTemplate().isStigma()) {
			Stigma stigmaInfo = resultItem.getItemTemplate().getStigma();
			int stigmaLevel = resultItem.getEnchantLevel();
			String rsultItemName = clearName(resultItem.getItemName());
			boolean replace = false;
			for (Item i : player.getEquipment().getEquippedItemsAllStigma()) {
				if (i.getEquipmentSlot() == slot) {
					if (!clearName(i.getItemName()).equals(rsultItemName))
						return false;
					removeStigmaSkills(player, i.getItemTemplate().getStigma(), i.getEnchantLevel(), i.getEnchantLevel() > resultItem.getEnchantLevel());
					replace = true;
					break;
				}
			}
			if (!replace) {
				// check the number of stigma wearing
				if (ItemSlot.isRegularStigma(slot) && getPossibleStigmaCount(player) <= player.getEquipment().getEquippedItemsRegularStigma().size()) {
					AuditLogger.info(player, "Possible client hack stigma count big :O");
					return false;
				} else if (ItemSlot.isAdvancedStigma(slot)
					&& getPossibleAdvancedStigmaCount(player) <= player.getEquipment().getEquippedItemsAdvencedStigma().size()) {
					AuditLogger.info(player, "Possible client hack advanced stigma count big :O");
					return false;
				}
			}

			long kinahcount = 25000;
			// Sets the price for equipping stigma during mission in Space of Destiny [ID: 320070000] and Sliver of darkness [ID: 310070000]
			if ((player.getRace() == Race.ASMODIANS && player.getWorldId() == 320070000)
				|| (player.getRace() == Race.ELYOS && player.getWorldId() == 310070000))
				kinahcount = 1000;
			else if (resultItem.getItemTemplate().getItemQuality().equals(ItemQuality.LEGEND))
				kinahcount = 50000;
			else if (resultItem.getItemTemplate().getItemQuality().equals(ItemQuality.UNIQUE))
				kinahcount = 100000;

			if (!player.getInventory().tryDecreaseKinah(PricesService.getPriceForService(kinahcount, player.getRace()))) {
				PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_STIGMA_NOT_ENOUGH_MONEY);
				return false;
			}
			addStigmaSkills(player, stigmaInfo, stigmaLevel);
		}
		return true;
	}

	/**
	 * @param player
	 */
	public static void onPlayerLogin(Player player) {
		if (player.havePermission(MembershipConfig.STIGMA_AUTOLEARN)) {
			for (int level = 20; level <= player.getLevel(); level++) {
				for (SkillLearnTemplate template : DataManager.SKILL_TREE_DATA.getTemplatesFor(player.getPlayerClass(), level, player.getRace())) {
					if (template.isStigma())
						player.getSkillList().addTemporarySkill(player, template.getSkillId(), template.getSkillLevel());
				}
			}
			return;
		}

		mainLoop:
		for (Item item : player.getEquipment().getEquippedItemsAllStigma()) {
			if (!item.getItemTemplate().isStigma()) {
				player.getEquipment().unEquipItem(item.getObjectId(), false);
				log.warn("Unequipped stigma: " + item.getItemId() + ", stigma info missing for item (possibly pre-4.8 stigma)");
				continue;
			}

			if (!isPossibleEquippedStigma(player, item)) {
				player.getEquipment().unEquipItem(item.getObjectId(), false);
				AuditLogger.info(player, "Unequipped stigma: " + item.getItemId() + ", possible client hack (stigma count big)");
				continue;
			}

			if (!item.getItemTemplate().isClassSpecific(player.getPlayerClass())) {
				player.getEquipment().unEquipItem(item.getObjectId(), false);
				AuditLogger.info(player, "Unequipped stigma: " + item.getItemId() + ", possible client hack (not valid for class)");
				continue;
			}

			// check for double stigmas equipped into the same slot
			for (Item checkStigma : player.getEquipment().getEquippedItemsAllStigma()) {
				if (checkStigma.getEquipmentSlot() == item.getEquipmentSlot() && checkStigma.getItemId() != item.getItemId()) {
					player.getEquipment().unEquipItem(item.getObjectId(), false);
					AuditLogger.info(player, "Unequipped stigma: " + item.getItemId() + ", double stigma in the same slot");
					continue mainLoop;
				}
			}

			addStigmaSkills(player, item.getItemTemplate().getStigma(), item.getEnchantLevel());
		}

			addLinkedStigmaSkills(player);
	}

	public static void removeLinkedStigmaSkills(Player player) {
		List<PlayerSkillEntry> linkedStigmaSkill = new FastTable<>();
		while (true) { // remove all linked stigma skills (can be more than one if stigma auto learning is enabled)
			String stack = null;
			linkedStigmaSkill.clear();
			for (PlayerSkillEntry skill : player.getSkillList().getAllSkills()) {
				if (skill.isLinkedStigmaSkill()) {
					if (stack == null)
						stack = skill.getSkillTemplate().getStack();
					if (skill.getSkillTemplate().getStack().equalsIgnoreCase(stack))
						linkedStigmaSkill.add(skill);
					if (stack.equalsIgnoreCase("NONE"))
						break;
				}
			}
			if (linkedStigmaSkill.size() == 0)
				break;
			for (PlayerSkillEntry skillEntry : linkedStigmaSkill)
				SkillLearnService.removeSkill(player, skillEntry.getSkillId());
			int nameId = DataManager.SKILL_DATA.getSkillTemplate(linkedStigmaSkill.get(0).getSkillId()).getNameId();
			PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_MSG_STIGMA_DELETE_HIDDEN_SKILL(nameId, linkedStigmaSkill.size()));
		}
	}

	public static void addLinkedStigmaSkills(Player player) {
		List<Item> stigmas = player.getEquipment().getEquippedItemsAllStigma();
		if (stigmas.size() < 6)
			return;

		for (Item stigma : stigmas) {
			if (!stigma.isStigmaChargeable())
				return;
	}

		int skillId = getLinkedStigmaLearnSkill(player);
		if (skillId > 0) {
			// linked stigma level is the lowest enchant level of all equipped stigmas
			int linkedStigmaSkillLevel = stigmas.stream().min((i1, i2) -> i1.getEnchantLevel() - i2.getEnchantLevel()).get().getEnchantLevel() + 1;
			for (SkillLearnTemplate skill : DataManager.SKILL_TREE_DATA.getSkillsForSkill(skillId, player.getPlayerClass(), player.getRace(),
				player.getLevel()))
				player.getSkillList().addTemporarySkill(player, skill.getSkillId(), linkedStigmaSkillLevel);
		}
	}

	private static int getLinkedStigmaLearnSkill(Player player) {
		// references: http://aion.mouseclic.com/beta/stigma.php
		switch (player.getPlayerClass()) {
			case GLADIATOR:
				if (isEquipped(player, 140001119))
					if ((isEquipped(player, 140001106) && isEquipped(player, 140001108)) || (isEquipped(player, 140001106) && isEquipped(player, 140001107))
						|| (isEquipped(player, 140001108) && isEquipped(player, 140001107)))
						return 643; // Unraveling Assault
				if (isEquipped(player, 140001118))
					if ((isEquipped(player, 140001104) && isEquipped(player, 140001103)) || (isEquipped(player, 140001104) && isEquipped(player, 140001105))
						|| (isEquipped(player, 140001103) && isEquipped(player, 140001105)))
						return 731; // Wind Lance

				if (player.getRace().equals(Race.ASMODIANS))
					return 661; // Battle Banner
				else
					return 662; // Battle Banner
			case TEMPLAR:
				if (isEquipped(player, 140001134))
					if ((isEquipped(player, 140001122) && isEquipped(player, 140001120)) || (isEquipped(player, 140001122) && isEquipped(player, 140001125))
						|| (isEquipped(player, 140001120) && isEquipped(player, 140001125)))
						return 2921; // Invigorating Strike
				if (isEquipped(player, 140001135))
					if ((isEquipped(player, 140001123) && isEquipped(player, 140001124)) || (isEquipped(player, 140001123) && isEquipped(player, 140001121))
						|| (isEquipped(player, 140001124) && isEquipped(player, 140001121)))
						return 2918; // Shield of Vengeance

				return 2917; // Eternal Denial
			case ASSASSIN:
				if (isEquipped(player, 140001152))
					if ((isEquipped(player, 140001138) && isEquipped(player, 140001139)) || (isEquipped(player, 140001138) && isEquipped(player, 140001141))
						|| (isEquipped(player, 140001139) && isEquipped(player, 140001141)))
						return 3238; // Shimmerbomb
				if (isEquipped(player, 140001151))
					if ((isEquipped(player, 140001136) && isEquipped(player, 140001140)) || (isEquipped(player, 140001136) && isEquipped(player, 140001137))
						|| (isEquipped(player, 140001140) && isEquipped(player, 140001137)))
						return 3241; // Fangdrop Stab

				return 3244; // Explosive Rebranding
			case RANGER:
				if (isEquipped(player, 140001172))
					if ((isEquipped(player, 140001155) && isEquipped(player, 140001157)) || (isEquipped(player, 140001155) && isEquipped(player, 140001153))
						|| (isEquipped(player, 140001157) && isEquipped(player, 140001153)))
						return 1008; // Ripthread Shot
				if (isEquipped(player, 140001173))
					if ((isEquipped(player, 140001154) && isEquipped(player, 140001158)) || (isEquipped(player, 140001154) && isEquipped(player, 140001156))
						|| (isEquipped(player, 140001158) && isEquipped(player, 140001156)))
						return 938; // Night Haze

				return 1064; // Staggering Trap
			case SORCERER:
				if (isEquipped(player, 140001191))
					if ((isEquipped(player, 140001174) && isEquipped(player, 140001181)) || (isEquipped(player, 140001174) && isEquipped(player, 140001178))
						|| (isEquipped(player, 140001181) && isEquipped(player, 140001178)))
						return 1342; // Slumberswept Wind
				if (isEquipped(player, 140001192))
					if ((isEquipped(player, 140001176) && isEquipped(player, 140001177)) || (isEquipped(player, 140001176) && isEquipped(player, 140001184))
						|| (isEquipped(player, 140001176) && isEquipped(player, 140001185)) || (isEquipped(player, 140001177) && isEquipped(player, 140001184))
						|| (isEquipped(player, 140001177) && isEquipped(player, 140001185)))
						return 1542; // Aetherblaze

				return 1420; // Repulsion Field
			case SPIRIT_MASTER:
				if (isEquipped(player, 140001209))
					if ((isEquipped(player, 140001195) && isEquipped(player, 140001193)) || (isEquipped(player, 140001195) && isEquipped(player, 140001194))
						|| (isEquipped(player, 140001193) && isEquipped(player, 140001194)))
						return 3543; // Spirit's Empowerment
				if (isEquipped(player, 140001210))
					if ((isEquipped(player, 140001199) && isEquipped(player, 140001196)) || (isEquipped(player, 140001199) && isEquipped(player, 140001197))
						|| (isEquipped(player, 140001199) && isEquipped(player, 140001198)) || (isEquipped(player, 140001196) && isEquipped(player, 140001197))
						|| (isEquipped(player, 140001196) && isEquipped(player, 140001198)))
						return 3549; // Command: Absorb Wounds

				return 3851; // Blood Funnel
			case CLERIC:
				if (isEquipped(player, 140001246))
					if ((isEquipped(player, 140001234) && isEquipped(player, 140001232)) || (isEquipped(player, 140001234) && isEquipped(player, 140001233))
						|| (isEquipped(player, 140001235) && isEquipped(player, 140001232)) || (isEquipped(player, 140001235) && isEquipped(player, 140001233))
						|| (isEquipped(player, 140001232) && isEquipped(player, 140001233)))
						return 3934; // Restoration Relief
				if (isEquipped(player, 140001245))
					if ((isEquipped(player, 140001229) && isEquipped(player, 140001228)) || (isEquipped(player, 140001229) && isEquipped(player, 140001230))
						|| (isEquipped(player, 140001229) && isEquipped(player, 140001231)) || (isEquipped(player, 140001230) && isEquipped(player, 140001228))
						|| (isEquipped(player, 140001231) && isEquipped(player, 140001228)))
						return 4169; // Judge's Edict

				return 3911; // Summon Vexing Energy
			case CHANTER:
				if (isEquipped(player, 140001226))
					if ((isEquipped(player, 140001212) && isEquipped(player, 140001213)) || (isEquipped(player, 140001212) && isEquipped(player, 140001211))
						|| (isEquipped(player, 140001213) && isEquipped(player, 140001211)))
						return 1909; // Word of Instigation
				if (isEquipped(player, 140001227))
					if ((isEquipped(player, 140001214) && isEquipped(player, 140001216)) || (isEquipped(player, 140001214) && isEquipped(player, 140001215))
						|| (isEquipped(player, 140001216) && isEquipped(player, 140001215)))
						return 1903; // Resonant Strike

				return 1906; // Debilitating Incantation
			case RIDER:
				if (isEquipped(player, 140001279))
					if ((isEquipped(player, 140001264) && isEquipped(player, 140001269)) || (isEquipped(player, 140001264) && isEquipped(player, 140001265))
						|| (isEquipped(player, 140001269) && isEquipped(player, 140001265)))
						return 2858; // Explosive Exhaust
				if (isEquipped(player, 140001280))
					if ((isEquipped(player, 140001266) && isEquipped(player, 140001268)) || (isEquipped(player, 140001266) && isEquipped(player, 140001267))
						|| (isEquipped(player, 140001268) && isEquipped(player, 140001267)))
						return 2863; // Powerspike Trigger

				return 2851; // Nerve Pulse
			case GUNNER:
				if (isEquipped(player, 140001262))
					if ((isEquipped(player, 140001249) && isEquipped(player, 140001247)) || (isEquipped(player, 140001249) && isEquipped(player, 140001248))
						|| (isEquipped(player, 140001247) && isEquipped(player, 140001248)))
						return 2370; // Pursuit Stance
				if (isEquipped(player, 140001263))
					if ((isEquipped(player, 140001251) && isEquipped(player, 140001252)) || (isEquipped(player, 140001251) && isEquipped(player, 140001250))
						|| (isEquipped(player, 140001252) && isEquipped(player, 140001250)))
						return 2377; // Sequential Fire

				return 2382; // Pulverizer Cannon
			case BARD:
				if (isEquipped(player, 140001297))
					if ((isEquipped(player, 140001285) && isEquipped(player, 140001283)) || (isEquipped(player, 140001285) && isEquipped(player, 140001286))
						|| (isEquipped(player, 140001283) && isEquipped(player, 140001286)))
						return 4483; // Purging Paean
				if (isEquipped(player, 140001296))
					if ((isEquipped(player, 140001281) && isEquipped(player, 140001284)) || (isEquipped(player, 140001281) && isEquipped(player, 140001282))
						|| (isEquipped(player, 140001284) && isEquipped(player, 140001282)))
						return 4480; // Blazing Requiem

				return 4566; // Delusional Dirge
		}
		return 0;
	}

	public static boolean isEquipped(Player player, int itemId) {
		if (player.getEquipment().getEquippedItemsByItemId(itemId) != null)
			return player.getEquipment().getEquippedItemsByItemId(itemId).size() > 0;
		return false;
	}

	/**
	 * Get the number of available Stigma
	 *
	 * @param player
	 * @return
	 */
	private static int getPossibleStigmaCount(Player player) {
		if (player.havePermission(MembershipConfig.STIGMA_SLOT_QUEST))
			return 3;
		int playerLevel = player.getLevel();
		boolean isCompleteQuest = isCompleteQuest(player);
		if (isCompleteQuest) {
			if (playerLevel < 30)
				return 1;
			else if (playerLevel < 40)
				return 2;
			else
				return 3;
		}
		return 0;
	}

	private static boolean isCompleteQuest(Player player) {
		// Stigma Quest Elyos: 1929, Asmodians: 2900
		boolean isCompleteQuest = false;

		if (player.getRace() == Race.ELYOS) {
			QuestState qs = player.getQuestStateList().getQuestState(1929);
			if (qs != null)
				isCompleteQuest = player.isCompleteQuest(1929) || (qs.getStatus() == QuestStatus.START && qs.getQuestVars().getQuestVars() == 98);
			else
				isCompleteQuest = player.isCompleteQuest(1929);
		} else {
			QuestState qs = player.getQuestStateList().getQuestState(2900);
			if (qs != null)
				isCompleteQuest = player.isCompleteQuest(2900) || (qs.getStatus() == QuestStatus.START && qs.getQuestVars().getQuestVars() == 99);
			else
				isCompleteQuest = player.isCompleteQuest(2900);
		}
		return isCompleteQuest;
	}

	/**
	 * Get the number of available Advanced Stigma
	 *
	 * @param player
	 * @return
	 */
	private static int getPossibleAdvancedStigmaCount(Player player) {
		if (player.havePermission(MembershipConfig.STIGMA_SLOT_QUEST))
			return 3;
		int playerLevel = player.getLevel();
		boolean isCompleteQuest = isCompleteQuest(player);
		if (isCompleteQuest) {
			if (playerLevel >= 55)
				return 3;
			else if (playerLevel >= 50)
				return 2;
			else if (playerLevel >= 45)
				return 1;
		}
		return 0;
	}

	/**
	 * Stigma is a worn check available slots
	 *
	 * @param player
	 * @param item
	 * @return
	 */
	private static boolean isPossibleEquippedStigma(Player player, Item item) {
		if (!item.getItemTemplate().isStigma())
			return false;

		long itemSlotToEquip = item.getEquipmentSlot();

		// Stigma
		if (ItemSlot.isRegularStigma(itemSlotToEquip)) {
			int stigmaCount = getPossibleStigmaCount(player);
			if (stigmaCount > 0) {
				if (stigmaCount == 1) {
					if (itemSlotToEquip == ItemSlot.STIGMA1.getSlotIdMask())
						return true;
				} else if (stigmaCount == 2) {
					if (itemSlotToEquip == ItemSlot.STIGMA1.getSlotIdMask() || itemSlotToEquip == ItemSlot.STIGMA2.getSlotIdMask())
						return true;
				} else if (stigmaCount == 3)
					return true;
			}
		}
		// Advanced Stigma
		else if (ItemSlot.isAdvancedStigma(itemSlotToEquip)) {
			int advStigmaCount = getPossibleAdvancedStigmaCount(player);
			if (advStigmaCount > 0) {
				if (advStigmaCount == 1) {
					if (itemSlotToEquip == ItemSlot.ADV_STIGMA1.getSlotIdMask())
						return true;
				} else if (advStigmaCount == 2) {
					if (itemSlotToEquip == ItemSlot.ADV_STIGMA1.getSlotIdMask() || itemSlotToEquip == ItemSlot.ADV_STIGMA2.getSlotIdMask())
						return true;
				} else if (advStigmaCount == 3)
					return true;
			}
		}
		return false;
	}

	public static void chargeStigma(Player player, Item stigma, Item chargeStone) {
		Stigma stigmaInfo = stigma.getItemTemplate().getStigma();
		if (stigma.getItemId() != chargeStone.getItemId() || chargeStone.getEnchantLevel() > 0)
			return;
		if (!stigma.isStigmaChargeable())
			return;
		if (stigma.getEnchantLevel() >= MAX_STIGMA_ENCHANT_LVL) {
			PacketSendUtility.sendMessage(player, "Max stigma enchant level reached. Can't enchant more.");
			return;
		}

		float success = 100.0f;

		if (stigma.getEnchantLevel() < 10)
			success -= stigma.getEnchantLevel() * 10.0f;
		else
			success = 10.0f;
		final boolean isSuccess = Rnd.get() * 100 < success;

		final int parentItemId = stigma.getItemId();
		final int parntObjectId = stigma.getObjectId();
		final int parentNameId = stigma.getNameId();
		PacketSendUtility.broadcastPacket(player,
			new SM_ITEM_USAGE_ANIMATION(player.getObjectId(), parntObjectId, chargeStone.getObjectId(), parentItemId, 5000, 0, 0), true);
		final ItemUseObserver observer = new ItemUseObserver() {

			@Override
			public void abort() {
				player.getController().cancelTask(TaskId.ITEM_USE);
				player.removeItemCoolDown(stigma.getItemTemplate().getUseLimits().getDelayId());
				PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_ITEM_CANCELED(new DescriptionId(parentNameId)));
				PacketSendUtility.broadcastPacket(player,
					new SM_ITEM_USAGE_ANIMATION(player.getObjectId(), parntObjectId, chargeStone.getObjectId(), parentItemId, 0, 2, 0), true);
				player.getObserveController().removeObserver(this);
			}
		};
		player.getObserveController().attach(observer);
		player.getController().addTask(TaskId.ITEM_USE, ThreadPoolManager.getInstance().schedule(new Runnable() {

			@Override
			public void run() {
				player.getObserveController().removeObserver(observer);
				PacketSendUtility.broadcastPacket(player,
					new SM_ITEM_USAGE_ANIMATION(player.getObjectId(), parntObjectId, parentItemId, 0, isSuccess ? 1 : 2, 1), true);
				if (!player.getInventory().decreaseByObjectId(chargeStone.getObjectId(), 1, ItemPacketService.ItemUpdateType.DEC_STIGMA_USE))
					return;
				if (!isSuccess) {
					if (stigma.isEquipped())
						player.getEquipment().unEquipItem(stigma.getObjectId());
					player.getInventory().decreaseByObjectId(stigma.getObjectId(), 1, ItemPacketService.ItemUpdateType.DEC_STIGMA_USE);
					PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_MSG_STIGMA_ENCHANT_FAIL(stigma.getNameId()));
				} else {
					stigma.setEnchantLevel(stigma.getEnchantLevel() + 1);
					if (stigma.isEquipped()) {
						removeStigmaSkills(player, stigmaInfo, stigma.getEnchantLevel() - 1, false);
						addStigmaSkills(player, stigmaInfo, stigma.getEnchantLevel());
					}
					PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_MSG_STIGMA_ENCHANT_SUCCESS(stigma.getNameId()));
					PacketSendUtility.sendPacket(player, new SM_INVENTORY_UPDATE_ITEM(player, stigma));
					if (stigma.getPersistentState() != PersistentState.DELETED) {
						stigma.setPersistentState(PersistentState.UPDATE_REQUIRED);
						if (stigma.isEquipped())
							player.getEquipment().setPersistentState(PersistentState.UPDATE_REQUIRED);
						else
							player.getInventory().setPersistentState(PersistentState.UPDATE_REQUIRED);
					}
				}
			}

		}, 5000));
	}

	private static void addStigmaSkills(Player player, Stigma stigma, int stigmaLevel) {
		for (String skillGroup : stigma.getGainSkillGroups())
			for (SkillTemplate st : DataManager.SKILL_DATA.getSkillTemplatesByGroup(skillGroup))
				for (SkillLearnTemplate skill : DataManager.SKILL_TREE_DATA.getTemplatesForSkill(st.getSkillId(), player.getPlayerClass(), player.getRace()))
					if (player.getLevel() >= skill.getMinLevel())
						player.getSkillList().addTemporarySkill(player, skill.getSkillId(), stigmaLevel + 1);
	}

	public static void removeStigmaSkills(Player player, Stigma stigma, int stigmaLevel, boolean onUnequip) {
		for (String skillGroup : stigma.getGainSkillGroups()) {
		int nameId = 0;
			for (SkillTemplate st : DataManager.SKILL_DATA.getSkillTemplatesByGroup(skillGroup)) {
				if (onUnequip)
					nameId = st.getNameId();
				for (SkillLearnTemplate skill : DataManager.SKILL_TREE_DATA.getTemplatesForSkill(st.getSkillId(), player.getPlayerClass(), player.getRace()))
					SkillLearnService.removeSkill(player, skill.getSkillId());
		}
		if (nameId != 0)
			PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_STIGMA_YOU_CANNOT_USE_THIS_SKILL_AFTER_UNEQUIP_STIGMA_STONE(nameId));
		}
		removeLinkedStigmaSkills(player);
	}
}
