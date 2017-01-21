package com.aionemu.gameserver.model.team.alliance;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.callbacks.metadata.GlobalCallback;
import com.aionemu.gameserver.configs.main.GroupConfig;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.team.TeamType;
import com.aionemu.gameserver.model.team.alliance.callback.AddPlayerToAllianceCallback;
import com.aionemu.gameserver.model.team.alliance.callback.PlayerAllianceCreateCallback;
import com.aionemu.gameserver.model.team.alliance.callback.PlayerAllianceDisbandCallback;
import com.aionemu.gameserver.model.team.alliance.events.AllianceDisbandEvent;
import com.aionemu.gameserver.model.team.alliance.events.AssignViceCaptainEvent;
import com.aionemu.gameserver.model.team.alliance.events.ChangeAllianceLeaderEvent;
import com.aionemu.gameserver.model.team.alliance.events.ChangeAllianceLootRulesEvent;
import com.aionemu.gameserver.model.team.alliance.events.ChangeMemberGroupEvent;
import com.aionemu.gameserver.model.team.alliance.events.CheckAllianceReadyEvent;
import com.aionemu.gameserver.model.team.alliance.events.PlayerAllianceInvite;
import com.aionemu.gameserver.model.team.alliance.events.PlayerAllianceLeavedEvent;
import com.aionemu.gameserver.model.team.alliance.events.PlayerAllianceUpdateEvent;
import com.aionemu.gameserver.model.team.alliance.events.PlayerConnectedEvent;
import com.aionemu.gameserver.model.team.alliance.events.PlayerDisconnectedEvent;
import com.aionemu.gameserver.model.team.alliance.events.PlayerEnteredEvent;
import com.aionemu.gameserver.model.team.alliance.events.AssignViceCaptainEvent.AssignType;
import com.aionemu.gameserver.model.team.common.events.ShowBrandEvent;
import com.aionemu.gameserver.model.team.common.events.TeamCommand;
import com.aionemu.gameserver.model.team.common.events.TeamKinahDistributionEvent;
import com.aionemu.gameserver.model.team.common.events.PlayerLeavedEvent.LeaveReson;
import com.aionemu.gameserver.model.team.common.legacy.LootGroupRules;
import com.aionemu.gameserver.model.team.common.legacy.PlayerAllianceEvent;
import com.aionemu.gameserver.model.team.group.PlayerGroup;
import com.aionemu.gameserver.network.aion.serverpackets.SM_QUESTION_WINDOW;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.restrictions.RestrictionsManager;
import com.aionemu.gameserver.services.VortexService;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.utils.TimeUtil;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;

/**
 * @author ATracer
 */
public class PlayerAllianceService {

	private static final Logger log = LoggerFactory.getLogger(PlayerAllianceService.class);
	private static final Map<Integer, PlayerAlliance> alliances = new ConcurrentHashMap<>();
	private static final AtomicBoolean offlineCheckStarted = new AtomicBoolean();

	public static final void inviteToAlliance(final Player inviter, Player invited) {
		if (RestrictionsManager.canInviteToAlliance(inviter, invited)) {
			PlayerGroup playerGroup = invited.getPlayerGroup();

			if (playerGroup != null) {
				Player leader = playerGroup.getLeaderObject();
				if (!leader.equals(invited)) {
					PacketSendUtility.sendPacket(inviter, SM_SYSTEM_MESSAGE.STR_FORCE_INVITE_PARTY_HIM(invited.getName(), leader.getName()));
					PacketSendUtility.sendPacket(inviter, SM_SYSTEM_MESSAGE.STR_FORCE_INVITE_PARTY(leader.getName(), playerGroup.getMembers().size()));
					invited = leader;
				} else {
					PacketSendUtility.sendPacket(inviter, SM_SYSTEM_MESSAGE.STR_PARTY_ALLIANCE_INVITED_HIS_PARTY(invited.getName()));
				}
			} else {
				PacketSendUtility.sendPacket(inviter, SM_SYSTEM_MESSAGE.STR_FORCE_INVITED_HIM(invited.getName()));
			}

			PlayerAllianceInvite invite = new PlayerAllianceInvite(inviter);
			if (invited.getResponseRequester().putRequest(SM_QUESTION_WINDOW.STR_PARTY_ALLIANCE_DO_YOU_ACCEPT_HIS_INVITATION, invite)) {
				PacketSendUtility.sendPacket(invited, new SM_QUESTION_WINDOW(SM_QUESTION_WINDOW.STR_PARTY_ALLIANCE_DO_YOU_ACCEPT_HIS_INVITATION, 0, 0,
					inviter.getName()));
			}
		}
	}

	@GlobalCallback(PlayerAllianceCreateCallback.class)
	public static final PlayerAlliance createAlliance(Player leader, Player invited, TeamType type) {
		PlayerAlliance newAlliance = new PlayerAlliance(new PlayerAllianceMember(leader), type);
		alliances.put(newAlliance.getTeamId(), newAlliance);
		addPlayer(newAlliance, leader);
		addPlayer(newAlliance, invited);
		if (offlineCheckStarted.compareAndSet(false, true)) {
			initializeOfflineCheck();
		}
		return newAlliance;
	}

	private static void initializeOfflineCheck() {
		ThreadPoolManager.getInstance().scheduleAtFixedRate(new OfflinePlayerAllianceChecker(), 1000, 30 * 1000);
	}

	@GlobalCallback(AddPlayerToAllianceCallback.class)
	public static final void addPlayerToAlliance(PlayerAlliance alliance, Player invited) {
		// TODO leader member is already set
		alliance.addMember(new PlayerAllianceMember(invited));
	}

	/**
	 * Change alliance's loot rules and notify team members
	 */
	public static final void changeGroupRules(PlayerAlliance alliance, LootGroupRules lootRules) {
		alliance.onEvent(new ChangeAllianceLootRulesEvent(alliance, lootRules));
	}

	/**
	 * Player entered world - search for non expired alliance
	 */
	public static final void onPlayerLogin(Player player) {
		for (PlayerAlliance alliance : alliances.values()) {
			PlayerAllianceMember member = alliance.getMember(player.getObjectId());
			if (member != null) {
				alliance.onEvent(new PlayerConnectedEvent(alliance, player));
			}
		}
	}

	/**
	 * Player leaved world - set last online on member
	 */
	public static final void onPlayerLogout(Player player) {
		PlayerAlliance alliance = player.getPlayerAlliance();
		if (alliance != null) {
			PlayerAllianceMember member = alliance.getMember(player.getObjectId());
			member.updateLastOnlineTime();
			alliance.onEvent(new PlayerDisconnectedEvent(alliance, player));
		}
	}

	/**
	 * Update alliance members to some event of player
	 */
	public static final void updateAlliance(Player player, PlayerAllianceEvent allianceEvent) {
		PlayerAlliance alliance = player.getPlayerAlliance();
		if (alliance != null) {
			alliance.onEvent(new PlayerAllianceUpdateEvent(alliance, player, allianceEvent));
		}
	}

	public static final void updateAllianceEffects(Player player, int slot) {
		PlayerAlliance alliance = player.getPlayerAlliance();
		if (alliance != null) {
			alliance.onEvent(new PlayerAllianceUpdateEvent(alliance, player, PlayerAllianceEvent.UPDATE_EFFECTS, slot));
		}
	}

	/**
	 * Add player to alliance
	 */
	public static final void addPlayer(PlayerAlliance alliance, Player player) {
		Objects.requireNonNull(alliance, "Alliance should not be null");
		alliance.onEvent(new PlayerEnteredEvent(alliance, player));
	}

	/**
	 * Remove player from alliance (normal leave, or kick offline player)
	 */
	public static final void removePlayer(Player player) {
		PlayerAlliance alliance = player.getPlayerAlliance();
		if (alliance != null) {
			if (alliance.getTeamType().isDefence()) {
				VortexService.getInstance().removeDefenderPlayer(player);
			}
			alliance.onEvent(new PlayerAllianceLeavedEvent(alliance, player));
		}
	}

	/**
	 * Remove player from alliance (ban)
	 */
	public static final void banPlayer(Player bannedPlayer, Player banGiver) {
		Objects.requireNonNull(bannedPlayer, "Banned player should not be null");
		Objects.requireNonNull(banGiver, "Bangiver player should not be null");
		PlayerAlliance alliance = banGiver.getPlayerAlliance();
		if (alliance != null) {
			if (!alliance.isLeader(banGiver)) {
				PacketSendUtility.sendPacket(banGiver, SM_SYSTEM_MESSAGE.STR_FORCE_ONLY_LEADER_CAN_BANISH());
				return;
			}
			if (alliance.getTeamType().isDefence()) {
				VortexService.getInstance().removeDefenderPlayer(bannedPlayer);
			}
			PlayerAllianceMember bannedMember = alliance.getMember(bannedPlayer.getObjectId());
			if (bannedMember != null) {
				alliance.onEvent(new PlayerAllianceLeavedEvent(alliance, bannedMember.getObject(), LeaveReson.BAN, banGiver.getName()));
			} else {
				log.warn("TEAM: banning player not in alliance {}", alliance.onlineMembers());
			}
		}
	}

	/**
	 * Disband alliance after minimum of members has been reached
	 */
	@GlobalCallback(PlayerAllianceDisbandCallback.class)
	public static void disband(PlayerAlliance alliance, boolean onBefore) {
		Preconditions.checkState(alliance.onlineMembers() <= 1, "Can't disband alliance with more than one online member");
		alliance.onEvent(new AllianceDisbandEvent(alliance));
		alliances.remove(alliance.getTeamId());
	}

	public static void changeLeader(Player player) {
		PlayerAlliance alliance = player.getPlayerAlliance();
		if (alliance != null) {
			alliance.onEvent(new ChangeAllianceLeaderEvent(alliance, player));
		}
	}

	/**
	 * Change vice captain position of player (promote, demote)
	 */
	public static void changeViceCaptain(Player player, AssignType assignType) {
		PlayerAlliance alliance = player.getPlayerAlliance();
		if (alliance != null) {
			alliance.onEvent(new AssignViceCaptainEvent(alliance, player, assignType));
		}
	}

	public static final PlayerAlliance searchAlliance(Integer playerObjId) {
		for (PlayerAlliance alliance : alliances.values()) {
			if (alliance.hasMember(playerObjId)) {
				return alliance;
			}
		}
		return null;
	}

	/**
	 * Move members between alliance groups
	 */
	public static void changeMemberGroup(Player player, int firstPlayer, int secondPlayer, int allianceGroupId) {
		PlayerAlliance alliance = player.getPlayerAlliance();
		Objects.requireNonNull(alliance, "Alliance should not be null for group change");
		if (alliance.isLeader(player) || alliance.isViceCaptain(player)) {
			alliance.onEvent(new ChangeMemberGroupEvent(alliance, firstPlayer, secondPlayer, allianceGroupId));
		} else {
			PacketSendUtility.sendMessage(player, "You do not have the authority for that.");
		}
	}

	/**
	 * Check that alliance is ready
	 */
	public static void checkReady(Player player, TeamCommand eventCode) {
		PlayerAlliance alliance = player.getPlayerAlliance();
		if (alliance != null) {
			alliance.onEvent(new CheckAllianceReadyEvent(alliance, player, eventCode));
		}
	}

	/**
	 * Share specific amount of kinah between alliance members
	 */
	public static void distributeKinah(Player player, long amount) {
		PlayerAlliance alliance = player.getPlayerAlliance();
		if (alliance != null) {
			alliance.onEvent(new TeamKinahDistributionEvent<>(alliance, player, amount));
		}
	}

	public static void distributeKinahInGroup(Player player, long amount) {
		PlayerAllianceGroup allianceGroup = player.getPlayerAllianceGroup();
		if (allianceGroup != null) {
			allianceGroup.onEvent(new TeamKinahDistributionEvent<>(allianceGroup, player, amount));
		}
	}

	/**
	 * Show specific mark on top of player
	 */
	public static void showBrand(Player player, int targetObjId, int brandId) {
		PlayerAlliance alliance = player.getPlayerAlliance();
		if (alliance != null) {
			alliance.onEvent(new ShowBrandEvent<>(alliance, targetObjId, brandId));
		}
	}

	public static final String getServiceStatus() {
		return "Number of alliances: " + alliances.size();
	}

	public static class OfflinePlayerAllianceChecker implements Runnable, Predicate<PlayerAllianceMember> {

		private PlayerAlliance currentAlliance;

		@Override
		public void run() {
			for (PlayerAlliance alliance : alliances.values()) {
				currentAlliance = alliance;
				alliance.apply(this);
			}
			currentAlliance = null;
		}

		@Override
		public boolean apply(PlayerAllianceMember member) {
			int kickDelay = currentAlliance.getTeamType().isAutoTeam() ? 60 : GroupConfig.ALLIANCE_REMOVE_TIME;
			if (!member.isOnline() && TimeUtil.isExpired(member.getLastOnlineTime() + kickDelay * 1000)) {
				if (currentAlliance.getTeamType().isOffence()) {
					VortexService.getInstance().removeInvaderPlayer(member.getObject());
				}
				currentAlliance.onEvent(new PlayerAllianceLeavedEvent(currentAlliance, member.getObject(), LeaveReson.LEAVE_TIMEOUT));
			}
			return true;
		}

	}

}
