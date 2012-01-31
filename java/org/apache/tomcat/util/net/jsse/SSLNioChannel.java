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

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
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

	protected SSLEngine sslEngine;

	/**
	 * Create a new instance of {@code SSLNioChannel}
	 * 
	 * @param channel
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
	 */
	@Override
	public Future<Integer> read(ByteBuffer dst) {
		throw new RuntimeException("Operation not supported for class " + getClass().getName()
				+ ". Use method readBytes(java.nio.ByteBuffer) or "
				+ "readBytes(java.nio.ByteBuffer, long, java.util.concurrent.TimeUnit) instead");
	}

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

		try {
			ByteBuffer tmp = ByteBuffer.allocateDirect(dst.capacity());
			int x = super.readBytes(tmp, timeout, unit);
			if (x < 0) {
				return x;
			}

			SSLEngineResult sslEngineResult = sslEngine.unwrap(tmp, dst);

			return sslEngineResult.bytesConsumed();
		} catch (SSLException e) {
			e.printStackTrace();
		}

		return -1;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.tomcat.util.net.NioChannel#write(java.nio.ByteBuffer)
	 */
	@Override
	public Future<Integer> write(ByteBuffer src) {
		throw new RuntimeException("Operation not supported for class " + getClass().getName()
				+ ". Use method writeBytes(java.nio.ByteBuffer) or "
				+ "readBytes(java.nio.ByteBuffer, long, java.util.concurrent.TimeUnit) instead");
	}

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
	 * Note that the peer may also request a session renegotiation with this
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

		this.sslEngine.beginHandshake();
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
