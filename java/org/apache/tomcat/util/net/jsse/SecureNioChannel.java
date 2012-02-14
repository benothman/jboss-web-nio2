/**
 * JBoss, Home of Professional Open Source. Copyright 2012, Red Hat, Inc., and
 * individual contributors as indicated by the @author tags. See the
 * copyright.txt file in the distribution for a full listing of individual
 * contributors.
 * 
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 * 
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 * site: http://www.fsf.org.
 */
package org.apache.tomcat.util.net.jsse;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import org.apache.tomcat.util.net.NioChannel;

/**
 * {@code SSLNioChannel}
 * 
 * Created on Jan 3, 2012 at 3:43:44 PM
 * 
 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
 */
public class SecureNioChannel extends NioChannel {

	private static final int HANDSHAKE_MIN_BUFFER_SIZE = 16 * 1024;

	protected SSLEngine sslEngine;
	private ByteBuffer internalByteBuffer;

	/**
	 * Create a new instance of {@code SSLNioChannel}
	 * 
	 * @param channel
	 *            the {@link java.nio.channels.AsynchronousSocketChannel}
	 * @param sslEngine
	 */
	protected SecureNioChannel(AsynchronousSocketChannel channel, SSLEngine sslEngine) {
		super(channel);
		this.sslEngine = sslEngine;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.tomcat.util.net.NioChannel#read(java.nio.ByteBuffer)
	 * 
	 * 
	 * @Override public Future<Integer> read(ByteBuffer dst) { throw new
	 * RuntimeException("Operation not supported for class " +
	 * getClass().getName() + ". Use method readBytes(java.nio.ByteBuffer) or "
	 * +
	 * "readBytes(java.nio.ByteBuffer, long, java.util.concurrent.TimeUnit) instead"
	 * ); }
	 */

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.tomcat.util.net.NioChannel#readBytes(java.nio.ByteBuffer)
	 */
	public int readBytes(ByteBuffer dst) throws InterruptedException, ExecutionException {
		try {
			return readBytes(dst, Integer.MAX_VALUE, TimeUnit.SECONDS);
		} catch (TimeoutException e) {
			e.printStackTrace();
			return -1;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.tomcat.util.net.NioChannel#readBytes(java.nio.ByteBuffer,
	 * long, java.util.concurrent.TimeUnit)
	 */
	public int readBytes(ByteBuffer dst, long timeout, TimeUnit unit) throws InterruptedException,
			ExecutionException, TimeoutException {
		System.out.println(this + " ---> readBytes()");
		try {

			this.internalByteBuffer.clear();

			int x = super.readBytes(this.internalByteBuffer, timeout, unit);
			System.out.println("*** x = " + x + " ***");
			if (x < 0) {
				return x;
			}
			this.internalByteBuffer.flip();
			SSLEngineResult sslEngineResult = sslEngine.unwrap(this.internalByteBuffer, dst);

			this.internalByteBuffer.flip();
			ByteBuffer tmp = ByteBuffer.allocateDirect(dst.capacity());
			SSLEngineResult sslEngineRslt = sslEngine.unwrap(this.internalByteBuffer, tmp);
			tmp.flip();
			System.out.println(this + " --> readBytes() --> status : "
					+ sslEngineResult.getStatus());

			byte bytes[] = new byte[tmp.limit()];
			System.out.println("Byte received from client -> " + new String(bytes));

			if (sslEngineResult.getStatus() == SSLEngineResult.Status.OK) {
				return sslEngineResult.bytesProduced();
			}

		} catch (SSLException e) {
			e.printStackTrace();
		}

		return -1;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.tomcat.util.net.NioChannel#write(java.nio.ByteBuffer)
	 * 
	 * @Override public Future<Integer> write(ByteBuffer src) { throw new
	 * RuntimeException("Operation not supported for class " +
	 * getClass().getName() + ". Use method writeBytes(java.nio.ByteBuffer) or "
	 * +
	 * "readBytes(java.nio.ByteBuffer, long, java.util.concurrent.TimeUnit) instead"
	 * ); }
	 */

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.tomcat.util.net.NioChannel#writeBytes(java.nio.ByteBuffer)
	 */
	public int writeBytes(ByteBuffer src) throws InterruptedException, ExecutionException {
		try {
			return writeBytes(src, Integer.MAX_VALUE, TimeUnit.SECONDS);
		} catch (TimeoutException e) {
			e.printStackTrace();
			return -1;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.tomcat.util.net.NioChannel#writeBytes(java.nio.ByteBuffer,
	 * long, java.util.concurrent.TimeUnit)
	 */
	public int writeBytes(ByteBuffer src, long timeout, TimeUnit unit) throws InterruptedException,
			ExecutionException, TimeoutException {
		System.out.println(this + " ---> writeBytes()");

		src.flip();
		byte bytes[] = new byte[src.limit()];
		src.get(bytes);
		System.out.println("Server response content ---->>" + new String(bytes));

		try {
			SSLEngineResult sslEngineResult = null;
			int length = getSSLSession().getPacketBufferSize();
			int i = 1;
			do {
				src.flip();
				this.internalByteBuffer = ByteBuffer.allocateDirect((i++) * length);
				sslEngineResult = sslEngine.wrap(src, this.internalByteBuffer);
			} while (sslEngineResult.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW);
			this.internalByteBuffer.flip();

			System.out.println("tmp.limit() ---> " + this.internalByteBuffer.limit()
					+ ", STATUS : " + sslEngineResult.getStatus());

			if (sslEngineResult.getStatus() == SSLEngineResult.Status.OK) {
				while (this.internalByteBuffer.hasRemaining()) {
					int x = super.writeBytes(this.internalByteBuffer, timeout, unit);
					if (x < 0) {
						return -1;
					}
				}

				return sslEngineResult.bytesConsumed();
			}
		} catch (SSLException e) {
			e.printStackTrace();
		}

		return -1;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.tomcat.util.net.NioChannel#close()
	 */
	@Override
	public void close() throws IOException {
		super.close();
		// getSSLSession().invalidate();
		// The closeOutbound method will be called automatically
		// this.sslEngine.closeInbound();
	}

	/**
	 * Getter for sslEngine
	 * 
	 * @return the sslEngine
	 */
	public SSLEngine getSslEngine() {
		return this.sslEngine;
	}

	/**
	 * Setter for the sslEngine
	 * 
	 * @param sslEngine
	 *            the sslEngine to set
	 */
	protected void setSslEngine(SSLEngine sslEngine) {
		this.sslEngine = sslEngine;
	}

	/**
	 * Initiates handshaking (initial or renegotiation) on this SSLEngine.
	 * <P>
	 * This method is not needed for the initial handshake, as the
	 * <code>wrap()</code> and <code>unwrap()</code> methods will implicitly
	 * call this method if handshaking has not already begun.
	 * <P>
	 * Note that the client may also request a session renegotiation with this
	 * <code>SSLEngine</code> by sending the appropriate session renegotiate
	 * handshake message.
	 * <P>
	 * Unlike the {@link SSLSocket#startHandshake() SSLSocket#startHandshake()}
	 * method, this method does not block until handshaking is completed.
	 * <P>
	 * To force a complete SSL/TLS session renegotiation, the current session
	 * should be invalidated prior to calling this method.
	 * <P>
	 * Some protocols may not support multiple handshakes on an existing engine
	 * and may throw an <code>SSLException</code>.
	 * 
	 * @throws SSLException
	 *             if a problem was encountered while signaling the
	 *             <code>SSLEngine</code> to begin a new handshake. See the
	 *             class description for more information on engine closure.
	 * @throws IllegalStateException
	 *             if the client/server mode has not yet been set.
	 * @see javax.net.ssl.SSLEngine#beginHandshake()
	 * @see javax.net.ssl.SSLSession#invalidate()
	 */
	protected void handshake() throws SSLException {
		if (handshakeDone()) {
			return;
		}

		try {
			doHandshake();
		} catch (Exception e) {
			throw new SSLException(e);
		}
	}

	/**
	 * 
	 * @throws Exception
	 */
	private void doHandshake() throws Exception {

		SSLSession session = getSSLSession();

		// Create byte buffers to use for holding application data
		int appBufferSize = Math.max(session.getApplicationBufferSize(), HANDSHAKE_MIN_BUFFER_SIZE);
		ByteBuffer serverAppData = ByteBuffer.allocateDirect(appBufferSize);
		ByteBuffer clientAppData = ByteBuffer.allocateDirect(appBufferSize);

		int packetBufferSize = Math.max(session.getPacketBufferSize(), HANDSHAKE_MIN_BUFFER_SIZE);
		this.internalByteBuffer = ByteBuffer.allocateDirect(packetBufferSize);
		ByteBuffer clientNetData = ByteBuffer.allocateDirect(packetBufferSize);

		// Begin handshake
		sslEngine.beginHandshake();

		int step = 0;
		// Process handshaking message
		while (sslEngine.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.FINISHED
				&& sslEngine.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {

			System.out.println("STEP : " + (++step) + ", Handshake status : "
					+ sslEngine.getHandshakeStatus());

			switch (sslEngine.getHandshakeStatus()) {
			case NEED_UNWRAP:
				if (clientNetData.remaining() == 0) {
					clientNetData.clear();
				}

				int nBytes = this.channel.read(clientNetData).get();
				if (nBytes < 0) {
					throw new IOException("NEED_UNWRAP : EOF encountered during handshake.");
				} else {

					boolean cont = false;
					// loop while we can perform pure SSLEngine data
					do {
						// prepare the buffer with the incoming data
						clientNetData.flip();
						// call unwrap
						SSLEngineResult result = sslEngine.unwrap(clientNetData, clientAppData);
						// compact the buffer, this is an optional method,
						// wonder what would happen if we didn't
						clientNetData.compact();
						// read in the status
						HandshakeStatus handshakeStatus = result.getHandshakeStatus();
						if (result.getStatus() == SSLEngineResult.Status.OK
								&& result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
							// execute tasks if we need to
							handshakeStatus = tasks();
						}
						// perform another unwrap?
						cont = result.getStatus() == SSLEngineResult.Status.OK
								&& handshakeStatus == HandshakeStatus.NEED_UNWRAP;
					} while (cont);
				}

				break;
			case NEED_WRAP:
				this.internalByteBuffer.clear();
				SSLEngineResult res = sslEngine.wrap(serverAppData, this.internalByteBuffer);
				this.internalByteBuffer.flip();
				System.out.println(this + " NEED_WRAP ----> res.getStatus() = " + res.getStatus());

				if (res.getStatus() == Status.OK) {
					// Send the handshaking data to client
					while (this.internalByteBuffer.hasRemaining()) {
						if (this.channel.write(this.internalByteBuffer).get() < 0) {
							// Handle closed channel
							throw new IOException("NEED_WRAP : EOF encountered during handshake.");
						}
					}
				} else {
					// wrap should always work with our buffers
					throw new IOException("Unexpected status:" + res.getStatus()
							+ " during handshake WRAP.");
				}

				break;
			case NEED_TASK:
				tasks();

				break;
			case NOT_HANDSHAKING:
				throw new IOException("NOT_HANDSHAKING during handshake");
			case FINISHED:
				break;
			}
		}

		System.out.println(this + "END OF HANDSHAKE PROCESS, HANDSHAKE STATUS : "
				+ sslEngine.getHandshakeStatus());
	}

	/**
	 * 
	 * @return
	 */
	private SSLEngineResult.HandshakeStatus tasks() {
		Runnable task = null;
		while ((task = sslEngine.getDelegatedTask()) != null) {
			// Run the task in non-blocking mode
			System.out.println("New Task started");
			task.run();
		}
		return sslEngine.getHandshakeStatus();
	}

	/**
	 * Check if the handshake was done or not yet
	 * 
	 * @return <tt>true</tt> if the handshake was already done, else
	 *         <tt>false</tt>
	 */
	protected boolean handshakeDone() {
		return this.sslEngine.getHandshakeStatus() == HandshakeStatus.FINISHED;
	}

	/**
	 * @return The SSL Session of the channel
	 */
	public SSLSession getSSLSession() {
		return this.sslEngine.getSession();
	}
}
