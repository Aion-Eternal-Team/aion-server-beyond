package com.aionemu.gameserver.model.instance.instancereward;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.aionemu.gameserver.model.autogroup.AGPlayer;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.instance.playerreward.HarmonyGroupReward;
import com.aionemu.gameserver.model.instance.playerreward.InstancePlayerReward;
import com.aionemu.gameserver.network.aion.instanceinfo.HarmonyScoreInfo;
import com.aionemu.gameserver.network.aion.serverpackets.SM_INSTANCE_SCORE;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.world.WorldMapInstance;

/**
 * @author xTz
 */
public class HarmonyArenaReward extends PvPArenaReward {

	private List<HarmonyGroupReward> groups = new ArrayList<>();

	public HarmonyArenaReward(int mapId, int instanceId, WorldMapInstance instance) {
		super(mapId, instanceId, instance);
	}

	public HarmonyGroupReward getHarmonyGroupReward(int objectId) {
		for (InstancePlayerReward reward : groups) {
			HarmonyGroupReward harmonyReward = (HarmonyGroupReward) reward;
			if (harmonyReward.containPlayer(objectId)) {
				return harmonyReward;
			}
		}
		return null;
	}

	public List<HarmonyGroupReward> getHarmonyGroupInside() {
		List<HarmonyGroupReward> harmonyGroups = new ArrayList<>();
		for (HarmonyGroupReward group : groups) {
			for (AGPlayer agp : group.getAGPlayers()) {
				if (agp.isInInstance()) {
					harmonyGroups.add(group);
					break;
				}
			}
		}
		return harmonyGroups;
	}

	public List<Player> getPlayersInside(HarmonyGroupReward group) {
		List<Player> players = new ArrayList<>();
		for (Player playerInside : instance.getPlayersInside()) {
			if (group.containPlayer(playerInside.getObjectId())) {
				players.add(playerInside);
			}
		}
		return players;
	}

	public void addHarmonyGroup(HarmonyGroupReward reward) {
		groups.add(reward);
	}

	public List<HarmonyGroupReward> getGroups() {
		return groups;
	}

	public void sendPacket(int type, Player owner) {
		int time = getTime();
		instance.forEachPlayer(player -> {
			PacketSendUtility.sendPacket(player, new SM_INSTANCE_SCORE(new HarmonyScoreInfo(this, type, owner == null ? player : owner), this, time));
		});
	}

	@Override
	public int getRank(int points) {
		List<HarmonyGroupReward> sortedByPoints = groups.stream().sorted((r1, r2) -> Integer.compare(r2.getPoints(), r1.getPoints()))
			.collect(Collectors.toList());
		int rank = -1;
		for (HarmonyGroupReward reward : sortedByPoints) {
			if (reward.getPoints() >= points) {
				rank++;
			}
		}
		return rank;
	}

	@Override
	public int getTotalPoints() {
		return groups.stream().mapToInt(r -> r.getPoints()).sum();
	}

	@Override
	public void clear() {
		groups.clear();
		super.clear();
	}

}
