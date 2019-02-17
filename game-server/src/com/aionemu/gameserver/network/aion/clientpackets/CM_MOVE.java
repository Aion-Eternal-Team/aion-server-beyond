package com.aionemu.gameserver.network.aion.clientpackets;

import java.util.Set;

import com.aionemu.gameserver.controllers.movement.MovementMask;
import com.aionemu.gameserver.controllers.movement.PlayerMoveController;
import com.aionemu.gameserver.model.gameobjects.player.CustomPlayerState;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.network.aion.AionClientPacket;
import com.aionemu.gameserver.network.aion.AionConnection.State;
import com.aionemu.gameserver.network.aion.serverpackets.SM_MOVE;
import com.aionemu.gameserver.services.antihack.AntiHackService;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.world.World;

/**
 * Packet about player movement.
 *
 * @author -Nemesiss-
 */
public class CM_MOVE extends AionClientPacket {

	private byte type;
	private byte heading;
	private float x, y, z, x2, y2, z2, vehicleX, vehicleY, vehicleZ, vectorX, vectorY, vectorZ;
	private byte glideFlag;
	private int unk1, unk2;

	public CM_MOVE(int opcode, Set<State> validStates) {
		super(opcode, validStates);
	}

	@Override
	protected void readImpl() {
		x = readF();
		y = readF();
		z = readF();

		heading = readC();
		type = readC();

		if ((type & MovementMask.POSITION) == MovementMask.POSITION && (type & MovementMask.MANUAL) == MovementMask.MANUAL) {
			if ((type & MovementMask.ABSOLUTE) == MovementMask.ABSOLUTE) {
				x2 = readF();
				y2 = readF();
				z2 = readF();
			} else {
				vectorX = readF();
				vectorY = readF();
				vectorZ = readF();
				x2 = vectorX + x;
				y2 = vectorY + y;
				z2 = vectorZ + z;
			}
		}
		if ((type & MovementMask.GLIDE) == MovementMask.GLIDE) {
			glideFlag = readC();
		}
		if ((type & MovementMask.VEHICLE) == MovementMask.VEHICLE) {
			unk1 = readD();
			unk2 = readD();
			vehicleX = readF();
			vehicleY = readF();
			vehicleZ = readF();
		}
	}

	@Override
	protected void runImpl() {
		Player player = getConnection().getActivePlayer();
		if (player.isDead())
			return;
		if (player.getEffectController().isUnderFear())
			return;
		if (player.isInCustomState(CustomPlayerState.WATCHING_CUTSCENE)) // client sends crap when watching cutscenes in transform state
			return;

		PlayerMoveController m = player.getMoveController();
		byte oldMask = m.movementMask;
		m.movementMask = type;

		if (type == MovementMask.IMMEDIATE) { // stopping or turning
			if (oldMask == MovementMask.IMMEDIATE) { // turning
				player.getController().onMove();
			} else {
				m.setNewDirection(x, y, z, heading);
				if ((oldMask & MovementMask.ABSOLUTE) == MovementMask.ABSOLUTE) { // update target destination coordinates
					PacketSendUtility.broadcastToSightedPlayers(player,
						new SM_MOVE(player, (byte) (MovementMask.POSITION | MovementMask.MANUAL | MovementMask.ABSOLUTE)));
				}
			}
			// notify arrived
			player.getController().onStopMove();
			player.getFlyController().onStopGliding();
		} else {
			if ((type & MovementMask.GLIDE) == MovementMask.GLIDE) {
				m.glideFlag = glideFlag;
				player.getFlyController().switchToGliding();
			}

			if ((type & MovementMask.POSITION) == MovementMask.POSITION && (type & MovementMask.MANUAL) == MovementMask.MANUAL) { // start move or change direction
				m.setNewDirection(x2, y2, z2, heading);
				if ((type & MovementMask.ABSOLUTE) == MovementMask.ABSOLUTE) {
					if (player.isInCustomState(CustomPlayerState.TELEPORTATION_MODE)) {
						World.getInstance().updatePosition(player, x2, y2, z2, heading);
						m.updateLastMove();
						PacketSendUtility.broadcastToSightedPlayers(player, new SM_MOVE(player), true);
						return;
					}
				} else {
					m.vectorX = vectorX;
					m.vectorY = vectorY;
					m.vectorZ = vectorZ;
				}
				if (!m.isInMove())
					player.getController().onStartMove();
			} else {
				player.getController().onMove();
				if ((type & MovementMask.ABSOLUTE) == 0) {
					float speed = player.getGameStats().getMovementSpeedFloat();
					m.setNewDirection(x + m.vectorX * speed, y + m.vectorY * speed, player.isFlying() ? z + m.vectorZ * speed : z + m.vectorZ, heading);
				} else if (heading != player.getHeading())
					m.setNewDirection(m.getTargetX2(), m.getTargetY2(), m.getTargetZ2(), heading);
			}

			if ((type & MovementMask.VEHICLE) == MovementMask.VEHICLE) {
				m.unk1 = unk1;
				m.unk2 = unk2;
				m.vehicleX = vehicleX;
				m.vehicleY = vehicleY;
				m.vehicleZ = vehicleZ;
			}
		}

		if (!AntiHackService.canMove(player, x, y, z, type))
			return;

		if (!player.isSpawned()) // should be checked as late as possible, to prevent false warnings from World.updatePosition
			return;
		if (player.isProtectionActive() && (player.getX() != x || player.getY() != y || player.getZ() > z + 0.5f))
			player.getController().stopProtectionActiveTask();
		World.getInstance().updatePosition(player, x, y, z, heading);
		m.updateLastMove();

		if ((type & MovementMask.POSITION) == MovementMask.POSITION && (type & MovementMask.MANUAL) == MovementMask.MANUAL
			|| type == MovementMask.IMMEDIATE)
			PacketSendUtility.broadcastToSightedPlayers(player, new SM_MOVE(player));

		if ((type & MovementMask.FALL) == MovementMask.FALL) {
			player.getFlyController().onStopGliding();
			m.updateFalling(z);
		} else {
			m.stopFalling(z);
		}
	}

	@Override
	public String toString() {
		return "CM_MOVE [type=" + (type & 0xFF) + ", heading=" + heading + ", x=" + x + ", y=" + y + ", z=" + z + ", x2=" + x2 + ", y2=" + y2 + ", z2="
			+ z2 + ", vehicleX=" + vehicleX + ", vehicleY=" + vehicleY + ", vehicleZ=" + vehicleZ + ", vectorX=" + vectorX + ", vectorY=" + vectorY
			+ ", vectorZ=" + vectorZ + ", glideFlag=" + glideFlag + ", unk1=" + unk1 + ", unk2=" + unk2 + "]";
	}
}
