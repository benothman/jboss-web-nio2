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
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.CompletionHandler;
import java.util.Formatter;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSession;

import org.apache.tomcat.util.net.NioChannel;

/**
 * {@code SecureNioChannel}
 * <p>
 * This class is an extension of the class {@link NioChannel} to allow using
 * secure communication channels.
 * </p>
 * Created on Jan 3, 2012 at 3:43:44 PM
 * 
 * @see javax.net.ssl.SSLEngine
 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
 */
public class SecureNioChannel extends NioChannel {

	private static final int MIN_BUFFER_SIZE = 16 * 1024;

	protected SSLEngine sslEngine;
	private ByteBuffer netInBuffer;
	private ByteBuffer netOutBuffer;
	protected boolean handshakeComplete = false;
	// To save the handshake status for each operation
	protected HandshakeStatus handshakeStatus;

	/**
	 * Create a new instance of {@code SecureNioChannel}
	 * 
	 * @param channel
	 *            the {@link java.nio.channels.AsynchronousSocketChannel}
	 * @param engine
	 *            The {@link javax.net.ssl.SSLEngine} linked to this channel
	 * @throws NullPointerException
	 *             if the one at least one of the parameters is null
	 */
	protected SecureNioChannel(AsynchronousSocketChannel channel, SSLEngine engine) {
		super(channel);
		if (engine == null) {
			throw new NullPointerException("null SSLEngine parameter");
		}

		this.sslEngine = engine;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.tomcat.util.net.NioChannel#isSecure()
	 */
	public boolean isSecure() {
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.tomcat.util.net.NioChannel#read(java.nio.ByteBuffer)
	 * 
	 * @deprecated (use readBytes(...) instead)
	 */
	@Deprecated
	@Override
	public Future<Integer> read(ByteBuffer dst) {
		throw new RuntimeException("Operation not supported for class " + getClass().getName()
				+ ". Use method readBytes(...) instead");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.tomcat.util.net.NioChannel#readBytes(java.nio.ByteBuffer)
	 */
	public int readBytes(ByteBuffer dst) throws Exception {
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
	public int readBytes(ByteBuffer dst, long timeout, TimeUnit unit) throws Exception {
		System.out.println(this + " ---> START readBytes()");
		int x = super.readBytes(this.netInBuffer, timeout, unit);
		System.out.println("*** x = " + x + " ***");
		if (x < 0) {
			return -1;
		}

		// Unwrap the data read
		int read = this.unwrap(this.netInBuffer, dst);
		System.out.println(" ------------>> read = " + read + ", this.netInBuffer.position() = "
				+ this.netInBuffer.position());
		System.out.println(this + " ---> END readBytes()");
		return read;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.tomcat.util.net.NioChannel#read(java.nio.ByteBuffer,
	 * java.lang.Object, java.nio.channels.CompletionHandler)
	 */
	@Override
	public <A> void read(final ByteBuffer dst, A attachment,
			CompletionHandler<Integer, ? super A> handler) {
		this.read(dst, Integer.MAX_VALUE, TimeUnit.MILLISECONDS, attachment, handler);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.tomcat.util.net.NioChannel#read(java.nio.ByteBuffer,
	 * long, java.util.concurrent.TimeUnit, java.lang.Object,
	 * java.nio.channels.CompletionHandler)
	 */
	@Override
	public <A> void read(final ByteBuffer dst, long timeout, TimeUnit unit, A attachment,
			final CompletionHandler<Integer, ? super A> handler) {

		this.reset(this.netInBuffer);

		this.channel.read(this.netInBuffer, timeout, unit, attachment,
				new CompletionHandler<Integer, A>() {

					@Override
					public void completed(Integer nBytes, A attach) {
						if (nBytes < 0) {
							handler.failed(new ClosedChannelException(), attach);
							return;
						}

						try {
							// Unwrap the data
							int read = unwrap(netInBuffer, dst);
							// If everything is OK, so complete
							handler.completed(read, attach);
						} catch (Exception e) {
							// The operation must fails
							handler.failed(e, attach);
						}
					}

					@Override
					public void failed(Throwable exc, A attach) {
						handler.failed(exc, attach);
					}
				});
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.tomcat.util.net.NioChannel#read(java.nio.ByteBuffer[],
	 * int, int, long, java.util.concurrent.TimeUnit, java.lang.Object,
	 * java.nio.channels.CompletionHandler)
	 */
	@Override
	public <A> void read(final ByteBuffer[] dsts, final int offset, final int length, long timeout,
			TimeUnit unit, A attachment, final CompletionHandler<Long, ? super A> handler) {

		if (!handshakeComplete) {
			throw new IllegalStateException(
					"Handshake incomplete, you must complete handshake before writing data.");
		}

		if (handler == null) {
			throw new NullPointerException("'handler' parameter is null");
		}
		if ((offset < 0) || (length < 0) || (offset > dsts.length - length)) {
			throw new IndexOutOfBoundsException();
		}

		final ByteBuffer netInBuffers[] = new ByteBuffer[length];
		for (int i = 0; i < length; i++) {
			netInBuffers[i] = ByteBuffer.allocateDirect(getSSLSession().getPacketBufferSize());
		}

		this.reset(netInBuffers[0]);
		super.read(netInBuffers, 0, length, timeout, unit, attachment,
				new CompletionHandler<Long, A>() {

					@Override
					public void completed(Long nBytes, A attach) {
						if (nBytes < 0) {
							handler.failed(new ClosedChannelException(), attach);
							return;
						}

						long read = 0;
						for (int i = 0; i < length; i++) {
							try {
								read += unwrap(netInBuffers[i], dsts[offset + i]);
							} catch (Exception e) {
								handler.failed(e, attach);
								return;
							}
						}

						handler.completed(read, attach);
					}

					@Override
					public void failed(Throwable exc, A attach) {
						handler.failed(exc, attach);
					}
				});

	}

	/**
	 * 
	 * @param bytes
	 * @return a String representation of the hexadecimal value
	 */
	public static String bytesToHexString(byte[] bytes) {
		StringBuilder sb = new StringBuilder(bytes.length * 2);
		Formatter formatter = new Formatter(sb);
		for (byte b : bytes) {
			formatter.format("%02x", b);
		}

		return sb.toString();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.tomcat.util.net.NioChannel#write(java.nio.ByteBuffer)
	 * 
	 * @deprecated (use writeBytes(...) instead)
	 */
	@Deprecated
	@Override
	public Future<Integer> write(ByteBuffer src) {
		throw new RuntimeException("Operation not supported for class " + getClass().getName()
				+ ". Use method writeBytes(...) instead");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.tomcat.util.net.NioChannel#writeBytes(java.nio.ByteBuffer)
	 */
	public int writeBytes(ByteBuffer src) throws Exception {
		try {
			return writeBytes(src, Integer.MAX_VALUE, TimeUnit.MILLISECONDS);
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
	public int writeBytes(ByteBuffer src, long timeout, TimeUnit unit) throws Exception {
		// Clear the output buffer
		this.netOutBuffer.compact();
		// the number of bytes written
		int written = wrap(src, this.netOutBuffer);
		this.netOutBuffer.flip();

		// write bytes to the channel
		while (this.netOutBuffer.hasRemaining()) {
			int x = this.channel.write(this.netOutBuffer).get(timeout, unit);
			if (x < 0) {
				return -1;
			}
		}

		return written;
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.tomcat.util.net.NioChannel#write(java.nio.ByteBuffer,
	 * java.lang.Object, java.nio.channels.CompletionHandler)
	 */
	@Override
	public <A> void write(ByteBuffer src, A attachment,
			CompletionHandler<Integer, ? super A> handler) {
		this.write(src, Integer.MAX_VALUE, TimeUnit.MILLISECONDS, attachment, handler);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.tomcat.util.net.NioChannel#write(java.nio.ByteBuffer,
	 * long, java.util.concurrent.TimeUnit, java.lang.Object,
	 * java.nio.channels.CompletionHandler)
	 */
	@Override
	public <A> void write(final ByteBuffer src, long timeout, TimeUnit unit, final A attachment,
			final CompletionHandler<Integer, ? super A> handler) {

		try {
			// Prepare the output buffer
			this.netOutBuffer.clear();
			// Wrap the source data into the internal buffer
			final int written = wrap(src, this.netOutBuffer);
			this.netOutBuffer.flip();

			// Write data to the channel
			this.channel.write(this.netOutBuffer, timeout, unit, attachment,
					new CompletionHandler<Integer, A>() {

						@Override
						public void completed(Integer nBytes, A attach) {
							if (nBytes < 0) {
								handler.failed(new ClosedChannelException(), attach);
								return;
							}

							// Call the handler completed method with the
							// consumed bytes number
							handler.completed(written, attach);
						}

						@Override
						public void failed(Throwable exc, A attach) {
							handler.failed(exc, attach);
						}
					});

		} catch (Throwable exp) {
			handler.failed(exp, attachment);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.tomcat.util.net.NioChannel#write(java.nio.ByteBuffer[],
	 * int, int, long, java.util.concurrent.TimeUnit, java.lang.Object,
	 * java.nio.channels.CompletionHandler)
	 */
	@Override
	public <A> void write(final ByteBuffer[] srcs, int offset, int length, long timeout,
			TimeUnit unit, A attachment, final CompletionHandler<Long, ? super A> handler) {

		if (!handshakeComplete()) {
			throw new IllegalStateException(
					"Handshake incomplete, you must complete handshake before writing data.");
		}

		if (handler == null) {
			throw new NullPointerException("'handler' parameter is null");
		}
		if ((offset < 0) || (length < 0) || (offset > srcs.length - length)) {
			throw new IndexOutOfBoundsException();
		}

		ByteBuffer[] netOutBuffers = new ByteBuffer[length];
		int size = getSSLSession().getPacketBufferSize();
		long written = 0;
		for (int i = 0; i < length; i++) {
			try {
				// Prepare the output buffer
				netOutBuffers[i] = ByteBuffer.allocateDirect(size);
				// Wrap the source data into the internal buffer
				written += wrap(srcs[offset + i], netOutBuffers[i]);
				netOutBuffers[i].flip();
			} catch (Throwable exp) {
				handler.failed(exp, attachment);
				return;
			}
		}

		final long res = written;

		this.channel.write(netOutBuffers, 0, length, timeout, unit, attachment,
				new CompletionHandler<Long, A>() {

					@Override
					public void completed(Long nBytes, A attach) {
						if (nBytes < 0) {
							handler.failed(new ClosedChannelException(), attach);
							return;
						}
						// If everything is OK, so complete
						handler.completed(res, attach);
					}

					@Override
					public void failed(Throwable exc, A attach) {
						handler.failed(exc, attach);
					}
				});
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
	 * Attempts to encode a buffer of plaintext application data into SSL/TLS
	 * network data.
	 * 
	 * @param src
	 *            a ByteBuffer containing outbound application data
	 * @param dst
	 *            a ByteBuffer to hold outbound network data
	 * @return the number of bytes consumed
	 * @throws Exception
	 *             if the wrap status is not <tt>OK</tt>
	 */
	private int wrap(ByteBuffer src, ByteBuffer dst) throws Exception {
		// Wrap the source data into the destination buffer
		SSLEngineResult result = sslEngine.wrap(src, dst);
		// the number of bytes written
		int written = result.bytesConsumed();
		this.handshakeStatus = result.getHandshakeStatus();
		if (result.getStatus() == Status.OK) {
			tryTasks();
		} else {
			throw new Exception("Unable to wrap data, invalid engine state: " + result.getStatus());
		}

		return written;
	}

	/**
	 * Attempts to decode SSL/TLS network data into a plaintext application data
	 * buffer.
	 * 
	 * @param src
	 *            a ByteBuffer containing inbound network data.
	 * @param dst
	 *            a ByteBuffer to hold inbound application data.
	 * @return the number of bytes produced
	 * @throws Exception
	 */
	private int unwrap(ByteBuffer src, ByteBuffer dst) throws Exception {
		SSLEngineResult result;
		int read = 0;
		do {
			// prepare the input buffer
			src.flip();
			// unwrap the data
			result = sslEngine.unwrap(src, dst);
			// compact the buffer
			src.compact();

			if (result.getStatus() == Status.OK || result.getStatus() == Status.BUFFER_UNDERFLOW) {
				// we did receive some data, add it to our total
				read += result.bytesProduced();

				handshakeStatus = result.getHandshakeStatus();
				// perform any tasks if needed
				tryTasks();
				// if we need more network data, then bail out for now.
				if (result.getStatus() == Status.BUFFER_UNDERFLOW) {
					break;
				}
			} else if (result.getStatus() == Status.BUFFER_OVERFLOW && read > 0) {
				// buffer overflow can happen, if we have read data, then
				// empty out the dst buffer before we do another read
				break;
			} else {
				// here we should trap BUFFER_OVERFLOW and call expand on the
				// buffer for now, throw an exception, as we initialized the
				// buffers in the constructor
				throw new IOException(this + " Unable to unwrap data, invalid status: "
						+ result.getStatus());
			}
			// continue to unwrapping as long as the input buffer has stuff
		} while (src.position() != 0);

		return read;
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
		if (handshakeComplete) {
			return;
		}

		try {
			doHandshake();
		} catch (Exception e) {
			throw new SSLException(e);
		}
	}

	/**
	 * Start a new handshake operation for this channel.
	 * 
	 * @see #handshake()
	 * @throws SSLException
	 */
	protected void reHandshake() throws SSLException {
		handshakeComplete = false;
		handshakeStatus = sslEngine.getHandshakeStatus();
		try {
			doHandshake();
		} catch (Exception e) {
			throw new SSLException(e);
		}
	}

	/**
	 * Execute a handshake with the client socket channel
	 * 
	 * @throws Exception
	 */
	private void doHandshake() throws Exception {

		SSLSession session = getSSLSession();
		int packetBufferSize = Math.max(session.getPacketBufferSize(), MIN_BUFFER_SIZE);
		// Create byte buffers to use for holding application data
		initBuffers(packetBufferSize);

		ByteBuffer clientNetData = ByteBuffer.allocateDirect(packetBufferSize);
		ByteBuffer clientAppData = ByteBuffer.allocateDirect(packetBufferSize);

		// Begin handshake
		sslEngine.beginHandshake();
		handshakeStatus = sslEngine.getHandshakeStatus();
		int i = 1;
		int step = 1;
		boolean read = true;
		// Process handshaking message
		while (!handshakeComplete) {

			switch (handshakeStatus) {
			case NEED_UNWRAP:
				int nBytes = 0;
				if (read) {
					clientAppData.clear();
					nBytes = this.channel.read(this.netInBuffer).get();
					System.out.println("NEED_UNWRAP --> " + this + " : " + nBytes);
				}
				if (nBytes < 0) {
					throw new IOException(this + " : EOF encountered during handshake UNWRAP.");
				} else {
					boolean cont = false;
					// Loop while we can perform pure SSLEngine data
					do {
						// Prepare the buffer with the incoming data
						this.netInBuffer.flip();
						// Call unwrap
						SSLEngineResult res = sslEngine.unwrap(this.netInBuffer, clientAppData);
						System.out.println(this + " --> UNWRAP STEP " + (step++));

						// Compact the buffer, this is an optional method,
						// wonder what would happen if we didn't
						this.netInBuffer.compact();
						// Read in the status
						handshakeStatus = res.getHandshakeStatus();
						System.out.println(" HANDSHAKE UNWRAP --------> res.getStatus() = "
								+ res.getStatus() + ",  handshakeStatus = " + handshakeStatus);
						if (res.getStatus() == SSLEngineResult.Status.OK) {

							System.out
									.println("######## NEED_UNWRAP ----->>>> res.bytesConsumed() = "
											+ res.bytesConsumed());

							System.out
									.println("######## NEED_UNWRAP ----->>>> ["+getId()+"] - 1) clientAppData.position() = "
											+ clientAppData.position()
											+ ", clientAppData.limit() = " + clientAppData.limit());

							// --------------------------
							if (clientAppData.position() > 0) {
								clientAppData.flip();
							}
							byte b[] = new byte[clientAppData.limit()];
							clientAppData.get(b);
							System.out.println("*** clientAppData content : <" + new String(b)
									+ "> ***");
							// --------------------------

							// Execute tasks if we need to
							tryTasks();
							read = true;
						} else if (res.getStatus() == Status.BUFFER_UNDERFLOW) {
							read = true;
						} else if (res.getStatus() == Status.BUFFER_OVERFLOW) {
							ByteBuffer tmp = ByteBuffer.allocateDirect(packetBufferSize * (++i));

							if (clientAppData.position() > 0) {
								clientAppData.flip();
							}
							tmp.put(clientAppData);
							clientAppData = tmp;
							read = false;
						}
						// Perform another unwrap?
						cont = res.getStatus() == SSLEngineResult.Status.OK
								&& handshakeStatus == HandshakeStatus.NEED_UNWRAP;
					} while (cont);
				}

				break;
			case NEED_WRAP:
				clientNetData.compact();
				this.netOutBuffer.clear();
				SSLEngineResult res = sslEngine.wrap(clientNetData, this.netOutBuffer);
				handshakeStatus = res.getHandshakeStatus();
				this.netOutBuffer.flip();

				System.out.println(this + " NEED_WRAP : this.netOutBuffer.limit() = "
						+ this.netOutBuffer.limit());

				if (res.getStatus() == Status.OK) {
					// Execute tasks if we need to
					tryTasks();
					// Send the handshaking data to client
					while (this.netOutBuffer.hasRemaining()) {
						if (this.channel.write(this.netOutBuffer).get() < 0) {
							// Handle closed channel
							throw new IOException(this
									+ " : EOF encountered during handshake WRAP.");
						}
					}
				} else {
					// Wrap should always work with our buffers
					throw new IOException("Unexpected status:" + res.getStatus()
							+ " during handshake WRAP.");
				}

				break;
			case NEED_TASK:
				handshakeStatus = tasks();

				break;
			case NOT_HANDSHAKING:
				throw new SSLHandshakeException("NOT_HANDSHAKING during handshake");
			case FINISHED:
				handshakeComplete = true;
				break;
			}
		}

		System.out.println("######## ----->>>> netInBuffer.position() = "
				+ this.netInBuffer.position() + ", netInBuffer.limit() = "
				+ this.netInBuffer.limit());

		System.out.println("######## NEED_UNWRAP ----->>>> ["+getId()+"] - 2.1) clientAppData.position() = "
				+ clientAppData.position() + ", clientAppData.limit() = " + clientAppData.limit());
		clientAppData.flip();
		System.out.println("######## NEED_UNWRAP ----->>>> ["+getId()+"] - 2.2) clientAppData.position() = "
				+ clientAppData.position() + ", clientAppData.limit() = " + clientAppData.limit());

		byte bbb[] = new byte[clientAppData.limit()];
		clientAppData.get(bbb);
		System.out.println("*** FINISHED - b.length --> " + bbb.length);
		System.out.println("*** FINISHED - clientAppData content : <" + new String(bbb)+">");
		
		
		this.handshakeComplete = (handshakeStatus == HandshakeStatus.FINISHED);
	}

	/**
	 * Perform tasks, if any, during the handshake phase
	 * 
	 * @return The handshake status (
	 *         {@link javax.net.ssl.SSLEngineResult.HandshakeStatus})
	 */
	private SSLEngineResult.HandshakeStatus tasks() {
		Runnable task = null;
		while ((task = sslEngine.getDelegatedTask()) != null) {
			// Run the task in blocking mode
			task.run();
		}

		System.out.println("----->> task() : handshakeStatus = " + sslEngine.getHandshakeStatus());

		return sslEngine.getHandshakeStatus();
	}

	/**
	 * Try to run tasks if any.
	 */
	private void tryTasks() {
		if (handshakeStatus == HandshakeStatus.NEED_TASK) {
			handshakeStatus = tasks();
		}
	}

	/**
	 * 
	 * @param capacity
	 */
	private void initBuffers(int capacity) {
		if (this.netInBuffer == null) {
			this.netInBuffer = ByteBuffer.allocateDirect(capacity);
		} else {
			this.netInBuffer.clear();
		}
		if (this.netOutBuffer == null) {
			this.netOutBuffer = ByteBuffer.allocateDirect(capacity);
		} else {
			this.netOutBuffer.clear();
		}
	}

	/**
	 * Check if the handshake was done or not yet
	 * 
	 * @return <tt>true</tt> if the handshake was already done, else
	 *         <tt>false</tt>
	 */
	protected boolean handshakeComplete() {
		return this.handshakeComplete;
	}

	/**
	 * @return The SSL Session of the channel
	 */
	public SSLSession getSSLSession() {
		return this.sslEngine.getSession();
	}
}
