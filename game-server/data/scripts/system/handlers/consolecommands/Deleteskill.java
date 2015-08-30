package consolecommands;

import java.io.File;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlID;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

import com.aionemu.gameserver.model.gameobjects.VisibleObject;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.SkillLearnService;
import com.aionemu.gameserver.utils.chathandlers.ConsoleCommand;
import com.aionemu.gameserver.utils.PacketSendUtility;

/**
 * @author ginho1
 */
public class Deleteskill extends ConsoleCommand {

	public Deleteskill() {
		super("deleteskill");
	}

	@Override
	public void execute(Player admin, String... params) {
		if ((params.length < 0) || (params.length < 1)) {
			info(admin, null);
			return;
		}

		String skillName = params[0];
		int skillId = 0;


		final VisibleObject target = admin.getTarget();
		if (target == null) {
			PacketSendUtility.sendMessage(admin, "No target selected.");
			return;
		}

		if (!(target instanceof Player)) {
			PacketSendUtility.sendMessage(admin, "This command can only be used on a player!");
			return;
		}

		final Player player = (Player) target;


		try {

			JAXBContext jc = JAXBContext.newInstance(StaticData.class);
			Unmarshaller un = jc.createUnmarshaller();
			SkillData data = (SkillData) un.unmarshal(new File("./data/scripts/system/handlers/consolecommands/data/skills.xml"));

			SkillTemplate skillTemplate = data.getSkillTemplate(skillName);

			if(skillTemplate != null){
				skillId = skillTemplate.getTemplateId();
			}

		}
		catch (Exception e) {
			PacketSendUtility.sendMessage(admin, "Skill templates reload failed!" );
			System.out.println(e);
		}

		if (skillId > 0) {
			if (!check(admin, player, skillId))
				return;

			apply(admin, player, skillId);
		}
	}

	private static boolean check(Player admin, Player player, int skillId) {
		if (skillId != 0 && !player.getSkillList().isSkillPresent(skillId)) {
			PacketSendUtility.sendMessage(admin, "Player dont have this skill.");
			return false;
		}
		if (player.getSkillList().getSkillEntry(skillId).isStigma()) {
			PacketSendUtility.sendMessage(admin, "You can't remove stigma skill.");
			return false;
		}
		return true;
	}

	public void apply(Player admin, Player player, int skillId) {
		if (skillId != 0) {
			SkillLearnService.removeSkill(player, skillId);
			PacketSendUtility.sendMessage(admin, "You have successfully deleted the specified skill.");
		}
	}

	@Override
	public void info(Player admin, String message) {
		PacketSendUtility.sendMessage(admin, "syntax ///addcskill <skill name>");
	}

	@XmlRootElement(name = "ae_static_data")
	@XmlAccessorType(XmlAccessType.NONE)
	private static class StaticData {
		@XmlElement(name = "skills")
		public SkillData skillData;
	}

	@XmlAccessorType(XmlAccessType.NONE)
	@XmlType(namespace = "", name = "SkillTemplate")
	private static class SkillTemplate {

		@XmlAttribute(name = "id", required = true)
		@XmlID
		private String id;

		@XmlAttribute(name = "name")
		private String name;

		public String getName() {
			return name;
		}

		public int getTemplateId() {
			return skillId;
		}

		private int skillId;

		public void setSkillId(int skillId) {
			this.skillId = skillId;
		}

		void afterUnmarshal(Unmarshaller u, Object parent) {
			setSkillId(Integer.parseInt(id));
		}

	}

	@XmlRootElement(name = "skills")
	@XmlAccessorType(XmlAccessType.FIELD)
	private static class SkillData{

		@XmlElement(name = "skill")
		private List<SkillTemplate> its;

		public SkillTemplate getSkillTemplate(String skill) {

			for (SkillTemplate it : its) {
				if(it.getName().toLowerCase().equals(skill.toLowerCase()))
					return it;
			}
			return null;
		}

		protected List<SkillTemplate> getData() {
			return its;
		}
	}
}