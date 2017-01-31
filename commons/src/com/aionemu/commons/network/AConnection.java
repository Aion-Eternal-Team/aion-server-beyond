package com.aionemu.commons.network;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Queue;

import com.aionemu.commons.network.packet.BaseServerPacket;
import com.aionemu.commons.network.util.ThreadPoolManager;
import com.aionemu.commons.options.Assertion;

/**
 * Class that represent Connection with server socket. Connection is created by <code>ConnectionFactory</code> and attached to
 * <code>SelectionKey</code> key. Selection key is registered to one of Dispatchers <code>Selector</code> to handle io read and write.
 * 
 * @author -Nemesiss-
 */
public abstract class AConnection<T extends BaseServerPacket> {

	/**
	 * SocketChannel representing this connection
	 */
	private final SocketChannel socketChannel;
	/**
	 * Dispatcher [AcceptReadWriteDispatcherImpl] to which this connection SelectionKey is registered.
	 */
	private final Dispatcher dispatcher;
	/**
	 * SelectionKey representing this connection.
	 */
	private SelectionKey key;
	/**
	 * True if this connection should be closed after sending last server packet.
	 */
	protected boolean pendingClose;
	/**
	 * True if this connection is already closed.
	 */
	protected boolean closed;
	/**
	 * Object on which some methods are synchronized
	 */
	protected final Object guard = new Object();
	/**
	 * ByteBuffer for io write.
	 */
	public final ByteBuffer writeBuffer;
	/**
	 * ByteBuffer for io read.
	 */
	public final ByteBuffer readBuffer;

	/**
	 * Caching ip address to make sure that {@link #getIP()} method works even after disconnection
	 */
	private final String ip;

	/**
	 * Used only for PacketProcessor synchronization purpose
	 */
	private boolean locked = false;

	/**
	 * Constructor
	 * 
	 * @param sc
	 * @param d
	 * @throws IOException
	 */
	public AConnection(SocketChannel sc, Dispatcher d, int rbSize, int wbSize) throws IOException {
		socketChannel = sc;
		dispatcher = d;
		writeBuffer = ByteBuffer.allocate(wbSize);
		writeBuffer.flip();
		writeBuffer.order(ByteOrder.LITTLE_ENDIAN);
		readBuffer = ByteBuffer.allocate(rbSize);
		readBuffer.order(ByteOrder.LITTLE_ENDIAN);

		this.ip = socketChannel.socket().getInetAddress().getHostAddress();
	}

	/**
	 * Set selection key - result of registration this AConnection socketChannel to one of dispatchers.
	 * 
	 * @param key
	 */
	final void setKey(SelectionKey key) {
		this.key = key;
	}

	/**
	 * Notify Dispatcher Selector that we want write some data here.
	 */
	protected final void enableWriteInterest() {
		if (key.isValid()) {
			key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
			key.selector().wakeup();
		}
	}

	/**
	 * @return SocketChannel representing this connection.
	 */
	public SocketChannel getSocketChannel() {
		return socketChannel;
	}

	/**
	 * Sends the ServerPacket to this client.
	 */
	public final void sendPacket(T serverPacket) {
		synchronized (guard) {
			if (pendingClose || closed)
				return;

			getSendMsgQueue().add(serverPacket);
			enableWriteInterest();
		}
	}

	/**
	 * Connection will be closed at some time [by Dispatcher Thread], after that onDisconnect() method will be called to clear all other things.
	 */
	public final void close() {
		close(null);
	}

	/**
	 * Its guaranteed that closePacket will be sent before closing connection, but all past and future packets wont. Connection will be closed [by
	 * Dispatcher Thread], and onDisconnect() method will be called to clear all other things.
	 * 
	 * @param closePacket
	 *          Packet that will be sent before closing. If closePacket is null, regular {@link #close()} will be called instead.
	 */
	public final void close(T closePacket) {
		synchronized (guard) {
			if (pendingClose || closed)
				return;

			pendingClose = true;
			if (closePacket != null) {
				getSendMsgQueue().clear();
				getSendMsgQueue().add(closePacket);
				dispatcher.closeConnection(this);
				enableWriteInterest();
			} else {
				dispatcher.closeConnection(this);
				if (key.isValid())
					key.selector().wakeup(); // notify dispatcher
			}
		}
	}

	protected abstract Queue<T> getSendMsgQueue();

	/**
	 * This will close the connection and call onDisconnect() on another thread. May be called only by Dispatcher Thread.
	 */
	final void disconnect() {
		/**
		 * Test if this build should use assertion. If NetworkAssertion == false javac will remove this code block
		 */
		if (Assertion.NetworkAssertion)
			assert Thread.currentThread() == dispatcher;

		synchronized (guard) {
			if (closed)
				return;
			closed = true;
		}

		try {
			socketChannel.close();
		} catch (IOException e) {
		}
		key.cancel();
		key.attach(null);

		ThreadPoolManager.getInstance().execute(() -> onDisconnect());
	}

	/**
	 * @return True if this connection is pendingClose and not closed yet.
	 */
	final boolean isPendingClose() {
		return pendingClose && !closed;
	}

	final boolean isClosed() {
		return closed;
	}

	/**
	 * @return IP address of this Connection.
	 */
	public final String getIP() {
		return ip;
	}

	/**
	 * Used only for PacketProcessor synchronization purpose. Return true if locked successful - if wasn't locked before.
	 * 
	 * @return locked
	 */
	boolean tryLockConnection() {
		if (locked)
			return false;
		return locked = true;
	}

	/**
	 * Used only for PacketProcessor synchronization purpose. Unlock this connection.
	 */
	void unlockConnection() {
		locked = false;
	}

	/**
	 * @param data
	 * @return True if data was processed correctly, False if some error occurred and connection should be closed NOW.
	 */
	protected abstract boolean processData(ByteBuffer data);

	/**
	 * This method will be called by Dispatcher, and will be repeated till return false.
	 * 
	 * @param data
	 * @return True if data was written to buffer, False indicating that there are not any more data to write.
	 */
	protected abstract boolean writeData(ByteBuffer data);

	/**
	 * Called when AConnection object is fully initialized and ready to process and send packets. It may be used as hook for sending first packet etc.
	 */
	protected abstract void initialized();

	/**
	 * This method is called to inform that this connection was closed and should be cleared. This method is called only once.
	 */
	protected abstract void onDisconnect();

	/**
	 * This method is called by NioServer to inform that NioServer is shouting down. This method is called only once.
	 */
	protected abstract void onServerClose();
}
