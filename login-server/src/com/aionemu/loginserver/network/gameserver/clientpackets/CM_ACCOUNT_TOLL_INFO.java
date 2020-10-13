package com.aionemu.loginserver.network.gameserver.clientpackets;

import com.aionemu.commons.database.dao.DAOManager;
import com.aionemu.loginserver.dao.PremiumDAO;
import com.aionemu.loginserver.network.gameserver.GsClientPacket;

/**
 * @author xTz
 */
public class CM_ACCOUNT_TOLL_INFO extends GsClientPacket {

	private int accountId;
	private long toll;

	@Override
	protected void readImpl() {
		accountId = readD();
		toll = readQ();
	}

	@Override
	protected void runImpl() {
		DAOManager.getDAO(PremiumDAO.class).updatePoints(accountId, toll, 0);
	}
}
