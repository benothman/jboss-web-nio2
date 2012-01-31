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

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.Future;

import javax.net.ssl.SSLEngine;
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

		// TODO something seems to be wrong here

		ByteBuffer tmp = ByteBuffer.allocateDirect(getSSLSession().getPacketBufferSize());
		Future<Integer> future = super.read(tmp);

		// wait until the future is done
		while (!future.isDone()) {
			try {
				// Sleep 1 MS to wait for the future
				Thread.sleep(1);
			} catch (InterruptedException e) {
				break;
			}
		}

		try {
			if (future.isDone()) {
				dst.flip();
				tmp.flip();
				this.sslEngine.unwrap(tmp, dst);
			}
		} catch (SSLException e) {
			// TODO
		}
		return future;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.tomcat.util.net.NioChannel#write(java.nio.ByteBuffer)
	 */
	@Override
	public Future<Integer> write(ByteBuffer src) {
		
		
		
		
		
		
		
		
		
		
		return write(src, 1);
	}
	
	/**
	 * 
	 * @param src
	 * @param n
	 * @return
	 */
	protected Future<Integer> write(ByteBuffer src, int n) {
		// TODO something seems to be wrong here
		SSLSession session = this.sslEngine.getSession();
		ByteBuffer tmp = ByteBuffer.allocateDirect(n * session.getApplicationBufferSize());
		try {
			this.sslEngine.wrap(src, tmp);
		} catch (SSLException e) {
			// TODO Auto-generated catch block
		} catch (BufferOverflowException bofe) {
			return write(src, 2 * n);
		}
		tmp.flip();
		return super.write(tmp);
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
