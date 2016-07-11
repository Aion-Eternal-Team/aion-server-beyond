package quest.aturam_sky_fortress;

import com.aionemu.gameserver.model.DialogAction;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.handlers.QuestHandler;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;

/**
 * @author zhkchi
 * @modified Pad
 */
public class _18301MyPrecHious extends QuestHandler {

	private static final int sphereId = 702656;

	public _18301MyPrecHious() {
		super(18301);
	}

	@Override
	public void register() {
		qe.registerQuestNpc(799530).addOnQuestStart(questId);
		qe.registerQuestNpc(799530).addOnTalkEvent(questId);
		qe.registerQuestNpc(730374).addOnTalkEvent(questId);
		qe.registerQuestNpc(sphereId).addOnKillEvent(questId);
	}

	@Override
	public boolean onDialogEvent(QuestEnv env) {
		Player player = env.getPlayer();
		QuestState qs = player.getQuestStateList().getQuestState(questId);
		DialogAction dialog = env.getDialog();
		int targetId = env.getTargetId();

		if (qs == null || qs.getStatus() == QuestStatus.NONE) {
			if (targetId == 799530) {
				if (dialog == DialogAction.QUEST_SELECT) {
					return sendQuestDialog(env, 4762);
				} else if (dialog == DialogAction.QUEST_ACCEPT_1) {
					playQuestMovie(env, 468);
					return sendQuestStartDialog(env);
				} else {
					return sendQuestStartDialog(env);
				}
			}
		} else if (qs.getStatus() == QuestStatus.START) {
			if (targetId == 730374 && qs.getQuestVarById(0) == 7) {
				switch (dialog) {
					case USE_OBJECT:
						return sendQuestDialog(env, 1352);
					case SETPRO2:
						giveQuestItem(env, 182212100, 1);
						qs.setStatus(QuestStatus.REWARD);
						updateQuestStatus(env);
						return closeDialogWindow(env);
					default:
						return sendQuestEndDialog(env);
				}
			}
		} else if (qs.getStatus() == QuestStatus.REWARD) {
			if (targetId == 799530) {
				switch (dialog) {
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

	@Override
	public boolean onKillEvent(QuestEnv env) {
		Player player = env.getPlayer();
		QuestState qs = player.getQuestStateList().getQuestState(questId);
		int targetId = env.getTargetId();
		
		if (qs != null && qs.getStatus() == QuestStatus.START) {
			int var0 = qs.getQuestVarById(0);
			if (var0 <= 6 && targetId == sphereId) {
				qs.setQuestVarById(0, var0 + 1);
				updateQuestStatus(env);
				return true;
			}
		}
		return false;
	}

}