package com.aionemu.gameserver.network.aion.serverpackets;

import com.aionemu.gameserver.model.team.legion.Legion;
import com.aionemu.gameserver.network.aion.AionConnection;
import com.aionemu.gameserver.network.aion.AionServerPacket;
import com.aionemu.gameserver.services.abyss.AbyssRankingCache;

/**
 * @author Simple
 */
public class SM_LEGION_INFO extends AionServerPacket {

	private final Legion legion;

	public SM_LEGION_INFO(Legion legion) {
		this.legion = legion;
	}

	@Override
	protected void writeImpl(AionConnection con) {
		writeS(legion.getName());
		writeC(legion.getLegionLevel());
		writeD(AbyssRankingCache.getInstance().getRankingListPosition(legion));
		writeH(legion.getDeputyPermission());
		writeH(legion.getCenturionPermission());
		writeH(legion.getLegionaryPermission());
		writeH(legion.getVolunteerPermission());
		writeQ(legion.getContributionPoints());
		writeD(0x00); // unk
		writeD(0x00); // unk
		writeD(legion.getDisbandTime());
		writeD(legion.getOccupiedLegionDominion());
		writeD(legion.getLastLegionDominion());
		writeD(legion.getCurrentLegionDominion());
		Legion.Announcement announcement = legion.getAnnouncement();
		writeS(announcement == null ? null : announcement.message());
		writeD(announcement == null ? 0 : (int) (announcement.time().getTime() / 1000));
	}
}
