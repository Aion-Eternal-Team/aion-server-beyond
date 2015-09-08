package mysql5;

import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.dao.AccountPassportsDAO;
import com.aionemu.gameserver.dao.MySQL5DAOUtils;
import com.aionemu.gameserver.model.account.Account;
import com.aionemu.gameserver.model.gameobjects.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.passport.Passport;
import com.aionemu.gameserver.model.gameobjects.player.passport.PassportsList;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author ViAl
 * @rework Luzien
 */
public class MySQL5AccountPassportsDAO extends AccountPassportsDAO {

	private static final Logger log = LoggerFactory.getLogger(MySQL5AccountPassportsDAO.class);
	public static final String SELECT_QUERY = "SELECT `passport_id`, `rewarded`, `arrive_date` FROM `account_passports` WHERE `account_id`=?";
	public static final String UPDATE_QUERY = "UPDATE `account_passports` SET `rewarded`=? WHERE `account_id`=? AND `passport_id`=?";
	public static final String RESET_LASTSTAMPS_QUERY = "UPDATE `account_stamps` SET `last_stamp`=NULL";
	public static final String RESET_STAMPS_QUERY = "UPDATE `account_stamps` SET `stamps`=0";
	public static final String INSERT_QUERY = "INSERT INTO `account_passports` (`account_id`, `passport_id`, `rewarded`, `arrive_date`) VALUES (?,?,?,?)";
	public static final String DELETE_QUERY = "DELETE FROM `account_passports` WHERE account_id = ? AND passport_id = ? and arrive_date = ?";
	public static final String INSERT_STAMPS_QUERY = "INSERT INTO `account_stamps` (`account_id`, `stamps`, `last_stamp`) VALUES (?,?,?)";
	public static final String UPDATE_STAMPS_QUERY = "UPDATE `account_stamps` SET `stamps`= ?, `last_stamp`  = ? WHERE `account_id` = ?";
	public static final String SELECT_STAMPS_QUERY = "SELECT `stamps`, `last_stamp` FROM `account_stamps` WHERE `account_id`=?";

	@Override
	public void loadPassport(Account account) {
		PassportsList passportList = new PassportsList();
		try {
			try (Connection con = DatabaseFactory.getConnection(); PreparedStatement stmt = con.prepareStatement(SELECT_QUERY);) {
				stmt.setInt(1, account.getId());
				try (ResultSet rset = stmt.executeQuery();) {
					while (rset.next()) {
						int passport_id = rset.getInt("passport_id");
						boolean rewarded = rset.getInt("rewarded") != 0;
						Timestamp arriveDate = rset.getTimestamp("arrive_date");
						Passport pp = new Passport(passport_id, rewarded, arriveDate);
						pp.setState(PersistentState.UPDATED);
						passportList.addPassport(pp);
					}
					account.setPassportsList(passportList);
				}
			}
			try (Connection con = DatabaseFactory.getConnection(); PreparedStatement stmt = con.prepareStatement(SELECT_STAMPS_QUERY);) {
				stmt.setInt(1, account.getId());
				try (ResultSet rset = stmt.executeQuery();) {
					int stamps = 0;
					Timestamp last_stamp = null;
					if (rset.next()) {
						stamps = rset.getInt("stamps");
						last_stamp = rset.getTimestamp("last_stamp");
					} else {
						insertStamps(account.getId());
					}
					account.setPassportStamps(stamps);
					account.setLastStamp(last_stamp);
				}
			}
		} catch (Exception e) {
			log.error("Could not restore completed passport data for account: " + account.getId() + " from DB: " + e.getMessage(), e);
		}
	}

	@Override
	public void storePassportList(int accountId, List<Passport> pList) {
		for (Passport passport : pList) {
			switch (passport.getState()) {
				case NEW:
					addPassports(accountId, passport);
					break;
				case UPDATE_REQUIRED:
					updatePassport(accountId, passport);
					break;
				case DELETED:
					deletePassport(accountId, passport);
					break;
			}
			passport.setState(PersistentState.UPDATED);
		}
	}

	@Override
	public void storePassport(Account account) {
		storePassportList(account.getId(), account.getPassportsList().getAllPassports());
		updateStamps(account);
	}

	private void addPassports(int accountId, Passport passport) {
		try {
			try (Connection conn = DatabaseFactory.getConnection(); PreparedStatement ps = conn.prepareStatement(INSERT_QUERY);) {
				ps.setInt(1, accountId);
				ps.setInt(2, passport.getId());
				ps.setInt(3, passport.isRewarded() ? 1 : 0);
				ps.setTimestamp(4, passport.getArriveDate());
				ps.executeUpdate();
			}
		} catch (SQLException e) {
			log.error("Error while adding passports for account " + accountId, e);
		}
	}

	private void updatePassport(int accountId, Passport passport) {
		try {
			try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement(UPDATE_QUERY);) {
				ps.setInt(1, passport.isRewarded() ? 1 : 0);
				ps.setInt(2, accountId);
				ps.setInt(3, passport.getId());
				ps.executeUpdate();
			}
		} catch (SQLException e) {
			log.error("Failed to update existing passports for account " + accountId, e);
		}
	}

	private void deletePassport(int accountId, Passport passport) {
		try {
			try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement(DELETE_QUERY);) {
				ps.setInt(1, accountId);
				ps.setInt(2, passport.getId());
				ps.setTimestamp(3, passport.getArriveDate());
				ps.executeUpdate();
			}
		} catch (SQLException e) {
			log.error("Failed to delete passports for account " + accountId, e);
		}
	}

	private void insertStamps(int accountId) {
		try {
			try (Connection conn = DatabaseFactory.getConnection(); PreparedStatement ps = conn.prepareStatement(INSERT_STAMPS_QUERY);) {
				ps.setInt(1, accountId);
				ps.setInt(2, 0);
				ps.setTimestamp(3, null);
				ps.executeUpdate();
			}
		} catch (SQLException e) {
			log.error("Error while adding stamos for account " + accountId, e);
		}
	}

	private void updateStamps(Account account) {
		try {
			try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement(UPDATE_STAMPS_QUERY);) {
				ps.setInt(1, account.getPassportStamps());
				ps.setTimestamp(2, account.getLastStamp());
				ps.setInt(3, account.getId());
				ps.executeUpdate();
			}
		} catch (SQLException e) {
			log.error("Failed to update existing passports for account " + account.getId(), e);
		}
	}

	@Override
	public boolean supports(String s, int i, int i1) {
		return MySQL5DAOUtils.supports(s, i, i1);
	}

	@Override
	public void resetAllPassports() {
		try {
			try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement(RESET_LASTSTAMPS_QUERY);) {
				ps.executeUpdate();
			}
		} catch (SQLException e) {
			log.error("Failed to reset all passports", e);
		}
	}

	@Override
	public void resetAllStamps() {
		try {
			try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement(RESET_STAMPS_QUERY);) {
				ps.executeUpdate();
			}
		} catch (SQLException e) {
			log.error("Failed to reset all stamps", e);
		}
	}
}
