package com.aionemu.gameserver.network.aion;

import java.nio.ByteBuffer;

import com.aionemu.commons.network.packet.BaseServerPacket;
import com.aionemu.gameserver.network.Crypt;

/**
 * Base class for every GS -> Aion Server Packet.
 * 
 * @author -Nemesiss-
 */
public abstract class AionServerPacket extends BaseServerPacket {

	/**
	 * Constructs new server packet
	 */
	protected AionServerPacket() {
		super();
		setOpCode(ServerPacketsOpcodes.getOpcode(getClass()));
	}

	protected AionServerPacket(int opCode) {
		super(opCode);
	}

	/**
	 * Write packet opCode and two additional bytes
	 */
	private void writeOP() {
		// obfuscate packet id
		int op = Crypt.encodeOpcodec(getOpCode());
		buf.putShort((short) (op));
		// put static server packet code
		buf.put(Crypt.staticServerPacketCode);
		// for checksum?
		buf.putShort((short) (~op));
	}

	/**
	 * Write and encrypt this packet data for given connection, to given buffer.
	 * 
	 * @param con
	 * @param buf
	 */
	public final void write(AionConnection con, ByteBuffer buffer) {
		setBuf(buffer);
		buf.putShort((short) 0);
		writeOP();
		writeImpl(con);
		buf.flip();
		buf.putShort((short) buf.limit());
		ByteBuffer b = buf.slice();
		buf.position(0);
		con.encrypt(b);
	}

	/**
	 * Write data that this packet represents to given byte buffer.
	 * 
	 * @param con
	 * @param buf
	 */
	protected void writeImpl(AionConnection con) {

	}

	public final ByteBuffer getBuf() {
		return this.buf;
	}

	/**
	 * Write string to buffer with a fixed length. Characters exceeding fixedLength will be truncated. Missing ones will be zero-padded.
	 * The number of written bytes to the buffer is fixedLength * 2 + 2. The additional two bytes are the terminating (zero) char which the client
	 * always requires. One could actually populate that last char normally and it would be displayed on client side, but then following data may get
	 * corrupted.
	 */
	protected final void writeS(String text, int fixedLength) {
		if (text == null || text.isEmpty()) {
			buf.put(new byte[(fixedLength + 1) * 2]);
		} else {
			for (int i = 0; i < fixedLength; i++)
				buf.putChar(i < text.length() ? text.charAt(i) : '\000');
			buf.putChar('\000');
		}
	}

	/**
	 * Writes dye information (dye status + 3 byte RGB value) to the buffer.
	 * 
	 * @param rgb
	 *          - may be null
	 */
	protected final void writeDyeInfo(Integer rgb) {
		if (rgb == null) {
			writeB(new byte[4]);
		} else {
			writeC(1); // dye status (1 = dyed, 0 = not dyed)
			writeC((rgb & 0xFF0000) >> 16); // r
			writeC((rgb & 0xFF00) >> 8); // g
			writeC(rgb & 0xFF); // b
		}
	}

	@Override
	protected int getOpCodeZeroPadding() {
		return 3;
	}
}
