package quest.beshmundir;

import static com.aionemu.gameserver.model.DialogAction.*;

import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;

/**
 * @author Gigi
 */
public class _30310RankandBile extends AbstractQuestHandler {

	public _30310RankandBile() {
		super(30310);
	}

	@Override
	public void register() {
		qe.registerQuestNpc(204225).addOnQuestStart(questId);
		qe.registerQuestNpc(204225).addOnTalkEvent(questId);
		qe.registerQuestNpc(799322).addOnTalkEvent(questId);
	}

	@Override
	public boolean onDialogEvent(QuestEnv env) {
		Player player = env.getPlayer();
		QuestState qs = player.getQuestStateList().getQuestState(questId);
		int targetId = env.getTargetId();
		int dialogActionId = env.getDialogActionId();
		if (qs == null || qs.isStartable()) {
			if (targetId == 204225) {
				if (dialogActionId == QUEST_SELECT)
					return sendQuestDialog(env, 4762);
				else
					return sendQuestStartDialog(env);
			}
		} else if (qs.getStatus() == QuestStatus.START) {
			int var = qs.getQuestVarById(0);
			if (targetId == 204225) {
				if (dialogActionId == QUEST_SELECT)
					return sendQuestDialog(env, 1011);
				else if (dialogActionId == CHECK_USER_HAS_QUEST_ITEM) {
					if (var == 0 && player.getInventory().getItemCountByItemId(182209713) >= 40) {
						removeQuestItem(env, 182209713, 40);
						changeQuestStep(env, 0, 0, true);
						return sendQuestDialog(env, 10000);
					} else
						return sendQuestDialog(env, 10001);
				}
				return false;
			}
		} else if (qs.getStatus() == QuestStatus.REWARD) {
			if (targetId == 799322) {
				switch (dialogActionId) {
					case USE_OBJECT:
						return sendQuestDialog(env, 10002);
					case SELECT_QUEST_REWARD:
						return sendQuestDialog(env, 5);
					default:
						return sendQuestEndDialog(env);
				}
			}
		}
		return false;
	}
}
