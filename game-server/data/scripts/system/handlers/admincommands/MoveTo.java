package admincommands;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.math.NumberUtils;

import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.npc.NpcTemplate;
import com.aionemu.gameserver.services.teleport.TeleportService;
import com.aionemu.gameserver.utils.ChatUtil;
import com.aionemu.gameserver.utils.Util;
import com.aionemu.gameserver.utils.chathandlers.AdminCommand;
import com.aionemu.gameserver.world.World;
import com.aionemu.gameserver.world.WorldMapType;
import com.aionemu.gameserver.world.WorldPosition;

/**
 * @author Neon
 */
public class MoveTo extends AdminCommand {

	public MoveTo() {
		super("moveto", "Moves you to any location.");

		// @formatter:off
		setSyntaxInfo(
			"<x> <y> [z] - Moves you to the specified coordinates on the current map (also supports pasted xml attributes like x=\"1422.7744\" y=\"1250.0612\" z=\"569.47\").",
			"<map name|ID> <x> <y> [z] - Moves you to the specified position (map names need underscores instead of spaces).",
			"<position link> - Moves you to the position of the chat link.",
			"<player name> - Moves you to the position of the player.",
			"<npc name|ID> - Moves you to the position of the npc."
		);
		// @formatter:on
	}

	@Override
	public void execute(Player admin, String... params) {
		String errorMsg = null;

		if (params.length >= 1) {
			WorldPosition pos;
			if (params.length == 1) {
				pos = ChatUtil.getPosition(params[0]);
			} else {
				pos = parseWorldPosition(admin, params);
			}
			if (pos != null && pos.getZ() != 0) {
				pos.setH(admin.getHeading());
				moveTo(admin, pos, "Teleported to " + WorldMapType.getWorld(pos.getMapId()) + "\nX:" + pos.getX() + " Y:" + pos.getY() + " Z:"
					+ pos.getZ());
				return;
			} else if (pos != null || params.length > 1 || params[0].startsWith("[pos:"))
				errorMsg = "Invalid map position or missing/deactivated geo.";
		}

		if (params.length == 1 && !NumberUtils.isDigits(params[0])) {
			Player player = World.getInstance().findPlayer(Util.convertName(params[0]));
			if (player != null && !player.equals(admin)) {
				moveTo(admin, player.getPosition(), "Teleported to " + ChatUtil.name(player) + ".");
				return;
			} else if (errorMsg == null || player != null)
				errorMsg = "Invalid player name or player is offline.";
		}

		if (params.length >= 1) {
			int npcId = getNpcId(params);
			if (npcId > 0) {
				sendInfo(admin, "Teleported to " + ChatUtil.path(npcId, true) + ".");
				TeleportService.teleportToNpc(admin, npcId);
				return;
			} else if (errorMsg == null)
				errorMsg = "Could not find the specified npc.";
		}

		sendInfo(admin, errorMsg);
	}

	private WorldPosition parseWorldPosition(Player admin, String[] params) {
		int coordIndex = 0;
		int mapId = 0;
		boolean isMapNameOrId = params[0].matches("^([a-zA-Z_]+|[1-9][0-9]{8,})$");
		if (isMapNameOrId) {
			mapId = NumberUtils.toInt(params[0]);
			if (mapId == 0)
				mapId = WorldMapType.getMapId(params[0]);
			coordIndex = 1;
		} else {
			mapId = admin.getPosition().getMapId();
		}
		Float x = null, y = null, z = null;
		Pattern p = Pattern.compile("^((?<type>x|y|z)(=|:)\"?)?(?<coord>[1-9][0-9]*(\\.[0-9]+)?f?)\"?,?$");
		int maxIndex = Math.min(params.length, 3);
		for (int i = coordIndex; i < maxIndex; i++) {
			Matcher m = p.matcher(params[i]);
			if (m.find()) {
				float coord = NumberUtils.toFloat(m.group("coord"));
				String type = m.group("type");
				if ("x".equals(type) || x == null && type == null)
					x = coord;
				else if ("y".equals(type) || y == null && type == null)
					y = coord;
				else if ("z".equals(type) || z == null && type == null)
					z = coord;
			} else {
				return null;
			}
		}
		return x == null || y == null ? null : ChatUtil.parsedCoordsToWorldPosition(mapId, x, y, z, null);
	}

	private void moveTo(Player admin, WorldPosition pos, String message) {
		sendInfo(admin, message); // msg before teleport, otherwise client could ignore it
		TeleportService.teleportTo(admin, pos);
	}

	private int getNpcId(String... params) {
		if (NumberUtils.isDigits(params[0])) {
			int npcId = NumberUtils.toInt(params[0]);
			if (npcId > 0 && DataManager.SPAWNS_DATA.getFirstSpawnByNpcId(0, npcId) != null)
				return npcId;
		} else {
			String npcName = String.join(" ", params).toLowerCase();
			for (NpcTemplate template : DataManager.NPC_DATA.getNpcData().valueCollection()) {
				if (template.getName().toLowerCase().equals(npcName)) {
					if (DataManager.SPAWNS_DATA.getFirstSpawnByNpcId(0, template.getTemplateId()) != null)
						return template.getTemplateId();
				}
			}
		}
		return 0;
	}
}
