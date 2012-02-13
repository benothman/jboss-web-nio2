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
public class SSLNioChannel extends NioChannel {

	private static final int HANDSHAKE_MIN_BUFFER_SIZE = 16 * 1024;

	protected SSLEngine sslEngine;

	/**
	 * Create a new instance of {@code SSLNioChannel}
	 * 
	 * @param channel
	 *            the {@link java.nio.channels.AsynchronousSocketChannel}
	 * @param sslEngine
	 */
	protected SSLNioChannel(AsynchronousSocketChannel channel, SSLEngine sslEngine) {
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
	 * 
	 * /* (non-Javadoc)
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

		try {
			ByteBuffer tmp = ByteBuffer.allocateDirect(dst.capacity());
			int x = super.readBytes(tmp, timeout, unit);
			if (x < 0) {
				return x;
			}

			SSLEngineResult sslEngineResult = sslEngine.unwrap(tmp, dst);
			if (sslEngineResult.getStatus() == SSLEngineResult.Status.OK) {
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
	 * @see org.apache.tomcat.util.net.NioChannel#write(java.nio.ByteBuffer)
	 * 
	 * @Override public Future<Integer> write(ByteBuffer src) { throw new
	 * RuntimeException("Operation not supported for class " +
	 * getClass().getName() + ". Use method writeBytes(java.nio.ByteBuffer) or "
	 * +
	 * "readBytes(java.nio.ByteBuffer, long, java.util.concurrent.TimeUnit) instead"
	 * ); }
	 * 
	 * /* (non-Javadoc)
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
		try {
			SSLEngineResult sslEngineResult = null;
			ByteBuffer tmp = null;
			int length = getSSLSession().getPacketBufferSize();
			int i = 1;
			do {
				src.flip();
				tmp = ByteBuffer.allocateDirect((i++) * length);
				sslEngineResult = sslEngine.wrap(src, tmp);
			} while (sslEngineResult.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW);

			if (sslEngineResult.getStatus() == SSLEngineResult.Status.OK) {
				while (tmp.hasRemaining()) {
					int x = super.writeBytes(tmp, timeout, unit);
					if (x == -1) {
						return -1;
					}
				}

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
	 * @see org.apache.tomcat.util.net.NioChannel#close()
	 */
	@Override
	public void close() throws IOException {
		super.close();
		//getSSLSession().invalidate();
		// The closeOutbound method will be called automatically
		//this.sslEngine.closeInbound();
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
		ByteBuffer serverNetData = ByteBuffer.allocateDirect(packetBufferSize);
		ByteBuffer clientNetData = ByteBuffer.allocateDirect(packetBufferSize);

		// Begin handshake
		sslEngine.beginHandshake();
		boolean ok = true;

		int step = 0;
		// Process handshaking message
		while (sslEngine.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.FINISHED
				&& sslEngine.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING
				&& ok) {

			System.out.println("STEP : " + (++step) + ", Handshake status : "
					+ sslEngine.getHandshakeStatus());

			switch (sslEngine.getHandshakeStatus()) {
			case NEED_UNWRAP:
				int nBytes = this.channel.read(clientNetData).get();
				if (nBytes < 0) {
					System.out.println("NEED_UNWRAP ---> closing channel");
					ok = false;
					//this.close();
				} else {
					clientNetData.flip();
					clientAppData.clear();
					SSLEngineResult res = sslEngine.unwrap(clientNetData, clientAppData);
					
					switch (res.getStatus()) {
					case BUFFER_UNDERFLOW:
						// Loop until the status changes
						while (res.getStatus() == Status.BUFFER_UNDERFLOW) {
							//
							System.out
									.println("NEED_UNWRAP ----> res.getStatus() == Status.BUFFER_UNDERFLOW");
							ByteBuffer tmpClientNetData = ByteBuffer.allocateDirect(clientNetData
									.capacity() * 2);
							clientNetData.flip();
							tmpClientNetData.put(clientNetData);
							clientNetData = tmpClientNetData;
							this.channel.read(clientNetData).get();
							res = sslEngine.unwrap(clientNetData, clientAppData);
						}

						break;
					case BUFFER_OVERFLOW:
						while (res.getStatus() == Status.BUFFER_OVERFLOW) {
							System.out
									.println("NEED_UNWRAP ----> res.getStatus() == Status.BUFFER_OVERFLOW");
							clientAppData = ByteBuffer.allocateDirect(clientAppData.capacity() * 2);
							clientNetData.flip();
							res = sslEngine.unwrap(clientNetData, clientAppData);
						}

						break;
					case CLOSED:
						System.out.println("NEED_UNWRAP ---> CLOSED");
						ok = false;
					case OK:
						// NOP
						break;
					}
					// compact the buffer
					clientNetData.compact();
				}

				break;
			case NEED_WRAP:
				serverNetData.clear();
				SSLEngineResult res = sslEngine.wrap(serverAppData, serverNetData);
				switch (res.getStatus()) {
				case BUFFER_OVERFLOW:
					while (res.getStatus() == Status.BUFFER_OVERFLOW) {
						System.out
								.println("NEED_WRAP ----> res.getStatus() == Status.BUFFER_OVERFLOW");
						serverNetData = ByteBuffer.allocateDirect(serverNetData.capacity() * 2);
						serverAppData.flip();
						res = sslEngine.wrap(serverAppData, serverNetData);
					}

					break;
				case BUFFER_UNDERFLOW:
					// Should not happens in this case
					break;
				case CLOSED:
					System.out.println("NEED_WRAP ---> CLOSED");
					ok = false;
				case OK:
					break;
				}

				if (res.getStatus() == Status.OK) {
					// Send the handshaking data to client
					serverNetData.flip();
					while (serverNetData.hasRemaining()) {
						if (this.channel.write(serverNetData).get() < 0) {
							// Handle closed channel
							System.out.println("NEED_WRAP ---> closing channel");
							ok = false;
							//this.close();
							break;
						}
					}
				}

				break;
			case NEED_TASK:
				Runnable task = null;
				while ((task = sslEngine.getDelegatedTask()) != null) {
					// Run the task in non-blocking mode
					new Thread(task).start();
				}

				break;
			case NOT_HANDSHAKING:
				ok = false;
			case FINISHED:
				break;
			}
		}
		
		System.out.println("END OF HANDSHAKE PROCESS -> ok : " + ok +", HANDSHAKE STATUS : " + sslEngine.getHandshakeStatus());
		
		if (!ok) {
			throw new Exception("Handshake fails");
		}
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
