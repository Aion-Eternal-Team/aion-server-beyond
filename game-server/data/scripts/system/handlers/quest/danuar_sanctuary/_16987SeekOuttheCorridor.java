package quest.danuar_sanctuary;

import com.aionemu.gameserver.model.DialogAction;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.handlers.QuestHandler;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.world.zone.ZoneName;

/**
 * @author Pad
 */

public class _16987SeekOuttheCorridor extends QuestHandler {

	private static final int questId = 16987;
	private static final int npcId = 804865;

	public _16987SeekOuttheCorridor() {
		super(questId);
	}

	@Override
	public void register() {
		qe.registerQuestNpc(npcId).addOnQuestStart(questId);
		qe.registerQuestNpc(npcId).addOnTalkEvent(questId);
		qe.registerOnEnterZone(ZoneName.get("DANUAR_SANCTUARY_INVESTIGATION_AREA_220080000"), questId);
	}

	@Override
	public boolean onDialogEvent(QuestEnv env) {
		Player player = env.getPlayer();
		QuestState qs = player.getQuestStateList().getQuestState(questId);
		DialogAction dialog = env.getDialog();
		int targetId = env.getTargetId();

		if (qs == null || qs.getStatus() == QuestStatus.NONE) {
			if (targetId == npcId) {
				if (dialog == DialogAction.QUEST_SELECT)
					return sendQuestDialog(env, 4762);
				else
					return sendQuestStartDialog(env);
			}
		} else if (qs.getStatus() == QuestStatus.REWARD) {
			if (targetId == npcId)
				return sendQuestEndDialog(env);
		}
		return false;
	}

	@Override
	public boolean onEnterZoneEvent(QuestEnv env, ZoneName zoneName) {
		QuestState qs = env.getPlayer().getQuestStateList().getQuestState(questId);
		qs.setQuestVarById(0, 1);
		qs.setStatus(QuestStatus.REWARD);
		updateQuestStatus(env);
		return true;
	}
}