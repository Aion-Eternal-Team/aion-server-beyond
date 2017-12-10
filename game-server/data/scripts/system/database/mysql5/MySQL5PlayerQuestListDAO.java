package mysql5;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.commons.utils.GenericValidator;
import com.aionemu.gameserver.dao.MySQL5DAOUtils;
import com.aionemu.gameserver.dao.PlayerQuestListDAO;
import com.aionemu.gameserver.model.gameobjects.Persistable;
import com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.QuestStateList;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;

/**
 * @author MrPoke
 * @modified vlog, Rolandas
 */
public class MySQL5PlayerQuestListDAO extends PlayerQuestListDAO {

	private static final Logger log = LoggerFactory.getLogger(MySQL5PlayerQuestListDAO.class);
	public static final String SELECT_QUERY = "SELECT `quest_id`, `status`, `quest_vars`, `flags`, `complete_count`, `next_repeat_time`, `reward`, `complete_time` FROM `player_quests` WHERE `player_id`=?";
	public static final String UPDATE_QUERY = "UPDATE `player_quests` SET `status`=?, `quest_vars`=?, `flags`=?, `complete_count`=?, `next_repeat_time`=?, `reward`=?, `complete_time`=? WHERE `player_id`=? AND `quest_id`=?";
	public static final String DELETE_QUERY = "DELETE FROM `player_quests` WHERE `player_id`=? AND `quest_id`=?";
	public static final String INSERT_QUERY = "INSERT INTO `player_quests` (`player_id`, `quest_id`, `status`, `quest_vars`, `flags`, `complete_count`, `next_repeat_time`, `reward`, `complete_time`) VALUES (?,?,?,?,?,?,?,?,?)";

	@Override
	public QuestStateList load(int playerObjId) {
		QuestStateList questStateList = new QuestStateList();
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement stmt = con.prepareStatement(SELECT_QUERY)) {
			stmt.setInt(1, playerObjId);
			try (ResultSet rset = stmt.executeQuery()) {
				while (rset.next()) {
					int questId = rset.getInt("quest_id");
					int questVars = rset.getInt("quest_vars");
					int flags = rset.getInt("flags");
					int completeCount = rset.getInt("complete_count");
					Timestamp nextRepeatTime = rset.getTimestamp("next_repeat_time");
					Integer reward = rset.getInt("reward");
					if (rset.wasNull())
						reward = null;
					Timestamp completeTime = rset.getTimestamp("complete_time");
					QuestStatus status = QuestStatus.valueOf(rset.getString("status"));
					QuestState questState = new QuestState(questId, status, questVars, flags, completeCount, nextRepeatTime, reward, completeTime);
					questState.setPersistentState(PersistentState.UPDATED);
					questStateList.addQuest(questId, questState);
				}
			}
		} catch (Exception e) {
			log.error("Could not restore QuestStateList data for player: " + playerObjId + " from DB: " + e.getMessage(), e);
		}
		return questStateList;
	}

	@Override
	public void store(Player player) {
		Collection<QuestState> qsList = player.getQuestStateList().getAllQuestState();
		Set<Integer> delQsList = player.getQuestStateList().getDeletedQuestIds();
		if (qsList.isEmpty() && delQsList.isEmpty())
			return;

		try (Connection con = DatabaseFactory.getConnection()) {
			con.setAutoCommit(false);

			deleteQuest(con, player.getObjectId(), qsList, delQsList);
			addQuests(con, player.getObjectId(), qsList);
			updateQuests(con, player.getObjectId(), qsList);
		} catch (SQLException e) {
			log.error("Can't save quests for player " + player.getObjectId(), e);
		}

		for (QuestState qs : qsList) {
			qs.setPersistentState(PersistentState.UPDATED);
		}
	}

	private void addQuests(Connection con, int playerId, Collection<QuestState> states) {
		states = states.stream().filter(Persistable.NEW).collect(Collectors.toList());

		if (GenericValidator.isBlankOrNull(states))
			return;

		try (PreparedStatement ps = con.prepareStatement(INSERT_QUERY)) {
			for (QuestState qs : states) {
				ps.setInt(1, playerId);
				ps.setInt(2, qs.getQuestId());
				ps.setString(3, qs.getStatus().toString());
				ps.setInt(4, qs.getQuestVars().getQuestVars());
				ps.setInt(5, qs.getFlags());
				ps.setInt(6, qs.getCompleteCount());
				ps.setObject(7, qs.getNextRepeatTime(), Types.TIMESTAMP); // supports inserting null value
				ps.setObject(8, qs.getRewardGroup(), Types.SMALLINT); // supports inserting null value
				ps.setObject(9, qs.getLastCompleteTime(), Types.TIMESTAMP); // supports inserting null value
				ps.addBatch();
			}

			ps.executeBatch();
			con.commit();
		} catch (SQLException e) {
			log.error("Failed to insert new quests for player " + playerId);
		}
	}

	private void updateQuests(Connection con, int playerId, Collection<QuestState> states) {
		states = states.stream().filter(Persistable.CHANGED).collect(Collectors.toList());

		if (GenericValidator.isBlankOrNull(states))
			return;

		try (PreparedStatement ps = con.prepareStatement(UPDATE_QUERY)) {
			for (QuestState qs : states) {
				ps.setString(1, qs.getStatus().toString());
				ps.setInt(2, qs.getQuestVars().getQuestVars());
				ps.setInt(3, qs.getFlags());
				ps.setInt(4, qs.getCompleteCount());
				ps.setObject(5, qs.getNextRepeatTime(), Types.TIMESTAMP); // supports inserting null value
				ps.setObject(6, qs.getRewardGroup(), Types.SMALLINT); // supports inserting null value
				ps.setObject(7, qs.getLastCompleteTime(), Types.TIMESTAMP); // supports inserting null value
				ps.setInt(8, playerId);
				ps.setInt(9, qs.getQuestId());
				ps.addBatch();
			}

			ps.executeBatch();
			con.commit();
		} catch (SQLException e) {
			log.error("Failed to update existing quests for player " + playerId);
		}
	}

	private void deleteQuest(Connection con, int playerId, Collection<QuestState> states, Set<Integer> questIds) {
		states = states.stream().filter(Persistable.DELETED).collect(Collectors.toList());

		if (GenericValidator.isBlankOrNull(states) && questIds.isEmpty())
			return;

		try (PreparedStatement ps = con.prepareStatement(DELETE_QUERY)) {
			for (QuestState qs : states) {
				ps.setInt(1, playerId);
				ps.setInt(2, qs.getQuestId());
				ps.addBatch();
			}

			for (Integer questId : questIds) {
				ps.setInt(1, playerId);
				ps.setInt(2, questId);
				ps.addBatch();
			}

			ps.executeBatch();
			con.commit();
		} catch (SQLException e) {
			log.error("Failed to delete existing quests for player " + playerId);
		}
		questIds.clear();
	}

	@Override
	public boolean supports(String s, int i, int i1) {
		return MySQL5DAOUtils.supports(s, i, i1);
	}
}
