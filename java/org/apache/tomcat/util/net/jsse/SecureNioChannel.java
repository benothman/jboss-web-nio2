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
import java.nio.channels.CompletionHandler;
import java.util.Formatter;
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
 * {@code SecureNioChannel}
 * 
 * Created on Jan 3, 2012 at 3:43:44 PM
 * 
 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
 */
public class SecureNioChannel extends NioChannel {

	private static final int MIN_BUFFER_SIZE = 16 * 1024;

	protected SSLEngine sslEngine;
	private ByteBuffer netInBuffer;
	private ByteBuffer netOutBuffer;
	protected boolean handshakeComplete = false;
	protected HandshakeStatus handshakeStatus; // gets set by handshake

	/**
	 * Create a new instance of {@code SecureNioChannel}
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
		System.out.println(this + " ---> readBytes()");
		// Prepare the internal buffer for reading
		// this.netInBuffer.compact();
		int x = super.readBytes(this.netInBuffer, timeout, unit);
		System.out.println("*** x = " + x + " ***");
		if (x < 0) {
			return -1;
		}

		// the data read
		int read = 0;
		// the SSL engine result
		SSLEngineResult result;
		do {
			// prepare the buffer
			this.netInBuffer.flip();
			// unwrap the data
			result = sslEngine.unwrap(this.netInBuffer, dst);
			// compact the buffer
			this.netInBuffer.compact();

			if (result.getStatus() == Status.OK || result.getStatus() == Status.BUFFER_UNDERFLOW) {
				// we did receive some data, add it to our total
				read += result.bytesProduced();
				// perform any tasks if needed
				if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
					tasks();
				}
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
				// buffer
				// for now, throw an exception, as we initialized the buffers
				// in the constructor
				throw new IOException("Unable to unwrap data, invalid status: "
						+ result.getStatus());
			}
			// continue to unwrapping as long as the input buffer has stuff
		} while (this.netInBuffer.position() != 0);

		System.out.println(" ------------>> read = " + read);

		return read;
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
		System.out.println(this + " ---> writeBytes()");

		// the number of bytes written
		int written = 0;
		// Compact the output buffer
		this.netOutBuffer.clear();
		// Wrap the source data into the internal buffer
		SSLEngineResult result = sslEngine.wrap(src, this.netOutBuffer);
		written = result.bytesConsumed();
		this.netOutBuffer.flip();

		if (result.getStatus() == Status.OK) {
			if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
				tasks();
			}
		} else {
			throw new IOException("Unable to wrap data, invalid engine state: "
					+ result.getStatus());
		}

		// write bytes to the channel
		while (this.netOutBuffer.hasRemaining()) {
			int x = super.writeBytes(this.netOutBuffer, timeout, unit);
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
		this.sslEngine.closeInbound();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.tomcat.util.net.NioChannel#awaitRead(long,
	 * java.util.concurrent.TimeUnit, java.lang.Object,
	 * java.nio.channels.CompletionHandler)
	 */
	@Override
	public <A> void awaitRead(long timeout, TimeUnit unit, final A attachment,
			final CompletionHandler<Integer, ? super A> handler) {
		System.out.println("**** " + this + ".awaitRead(...) ****");

		// reset the flag and the buffer
		reset();
		if (handler == null) {
			throw new NullPointerException("null handler parameter");
		}
		// Perform an asynchronous read operation using the internal buffer
		this.read(this.getBuffer(), timeout, unit, attachment, new CompletionHandler<Integer, A>() {

			@Override
			public void completed(Integer nBytes, A attach) {
				// Set the flag to true
				setFlag();
				handler.completed(nBytes, attach);
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
		super.read(this.netInBuffer, timeout, unit, attachment,
				new CompletionHandler<Integer, A>() {

					@Override
					public void completed(Integer nBytes, A attachment) {
						try {
							int read = 0;
							// the SSL engine result
							SSLEngineResult result;
							do {
								// prepare the input buffer
								netInBuffer.flip();
								// unwrap the data
								result = sslEngine.unwrap(netInBuffer, dst);
								// compact the buffer
								netInBuffer.compact();

								if (result.getStatus() == Status.OK
										|| result.getStatus() == Status.BUFFER_UNDERFLOW) {
									// we did receive some data, add it to our
									// total
									read += result.bytesProduced();
									// perform any tasks if needed
									if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
										tasks();
									}
									// if we need more network data, then bail
									// out for now.
									if (result.getStatus() == Status.BUFFER_UNDERFLOW) {
										break;
									}
								} else if (result.getStatus() == Status.BUFFER_OVERFLOW && read > 0) {
									// buffer overflow can happen, if we have
									// read data, then
									// empty out the dst buffer before we do
									// another read
									break;
								} else {
									// here we should trap BUFFER_OVERFLOW and
									// call expand on the
									// buffer
									// for now, throw an exception, as we
									// initialized the buffers
									// in the constructor
									throw new IOException("Unable to unwrap data, invalid status: "
											+ result.getStatus());
								}
								// continue to unwrapping as long as the input
								// buffer has stuff
							} while (netInBuffer.position() != 0);

							// If everything is OK, so complete
							handler.completed(read, attachment);
						} catch (Exception e) {
							// The operation must fails
							handler.failed(e, attachment);
						}
					}

					@Override
					public void failed(Throwable exc, A attachment) {
						handler.failed(exc, attachment);
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
	public <A> void read(final ByteBuffer[] dsts, int offset, int length, long timeout,
			TimeUnit unit, A attachment, CompletionHandler<Long, ? super A> handler) {

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
	public <A> void write(final ByteBuffer src, long timeout, TimeUnit unit, A attachment,
			final CompletionHandler<Integer, ? super A> handler) {

		try {
			// Prepare the output buffer
			this.netOutBuffer.clear();
			// Wrap the source data into the internal buffer
			SSLEngineResult result = sslEngine.wrap(src, this.netOutBuffer);
			// the number of bytes written
			final int written = result.bytesConsumed();
			this.netOutBuffer.flip();

			if (result.getStatus() == Status.OK) {
				if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
					tasks();
				}
			} else {
				throw new Exception("Unable to wrap data, invalid engine state: "
						+ result.getStatus());
			}

			super.write(this.netOutBuffer, timeout, unit, attachment,
					new CompletionHandler<Integer, A>() {

						@Override
						public void completed(Integer nBytes, A attachment) {
							// Call the handler completed method with the
							// consumed bytes number
							handler.completed(written, attachment);
						}

						@Override
						public void failed(Throwable exc, A attachment) {
							handler.failed(exc, attachment);
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
	public <A> void write(ByteBuffer[] srcs, int offset, int length, long timeout, TimeUnit unit,
			A attachment, CompletionHandler<Long, ? super A> handler) {
		// TODO
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
		int packetBufferSize = Math.max(session.getPacketBufferSize(), MIN_BUFFER_SIZE);
		// Create byte buffers to use for holding application data
		this.netInBuffer = ByteBuffer.allocateDirect(packetBufferSize);
		this.netOutBuffer = ByteBuffer.allocateDirect(packetBufferSize);
		setBuffer(ByteBuffer.allocateDirect(packetBufferSize));
		ByteBuffer serverNetData = ByteBuffer.allocateDirect(packetBufferSize);
		ByteBuffer serverAppData = ByteBuffer.allocateDirect(packetBufferSize);
		ByteBuffer clientNetData = ByteBuffer.allocateDirect(packetBufferSize);
		ByteBuffer clientAppData = ByteBuffer.allocateDirect(packetBufferSize);

		// Begin handshake
		sslEngine.beginHandshake();
		handshakeStatus = sslEngine.getHandshakeStatus();

		int step = 0;
		// Process handshaking message
		while (handshakeStatus != SSLEngineResult.HandshakeStatus.FINISHED
				&& handshakeStatus != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {

			System.out.println("STEP : " + (++step) + ", Handshake status : "
					+ sslEngine.getHandshakeStatus());

			switch (handshakeStatus) {
			case NEED_UNWRAP:
				if (!clientNetData.hasRemaining()) {
					clientNetData.clear();
				}

				int nBytes = this.channel.read(clientNetData).get();
				if (nBytes < 0) {
					throw new IOException(this +" NEED_UNWRAP : EOF encountered during handshake.");
				} else {

					boolean cont = false;
					// loop while we can perform pure SSLEngine data
					do {
						// prepare the buffer with the incoming data
						clientNetData.flip();
						// call unwrap
						SSLEngineResult res = sslEngine.unwrap(clientNetData, clientAppData);
						// compact the buffer, this is an optional method,
						// wonder what would happen if we didn't
						clientNetData.compact();
						// read in the status
						handshakeStatus = res.getHandshakeStatus();
						System.out.println("NEED_UNWRAP ---> handshakeStatus = " + handshakeStatus);
						if (res.getStatus() == SSLEngineResult.Status.OK
								&& handshakeStatus == HandshakeStatus.NEED_TASK) {
							// execute tasks if we need to
							handshakeStatus = tasks();
						}
						// perform another unwrap?
						cont = res.getStatus() == SSLEngineResult.Status.OK
								&& handshakeStatus == HandshakeStatus.NEED_UNWRAP;
					} while (cont);
				}

				break;
			case NEED_WRAP:
				serverAppData.clear();
				serverNetData.clear();
				SSLEngineResult res = sslEngine.wrap(serverAppData, serverNetData);
				serverNetData.flip();
				System.out.println(this + " NEED_WRAP ----> res.getStatus() = " + res.getStatus());

				handshakeStatus = res.getHandshakeStatus();
				System.out.println("----> NEED_WRAP-1 : HandshakeStatus = " + handshakeStatus);
				if (res.getStatus() == Status.OK) {
					// Send the handshaking data to client
					if (handshakeStatus == HandshakeStatus.NEED_TASK) {
						handshakeStatus = tasks();
					}
					while (serverNetData.hasRemaining()) {
						if (this.channel.write(serverNetData).get() < 0) {
							// Handle closed channel
							throw new IOException(this + " NEED_WRAP : EOF encountered during handshake.");
						}
					}
				} else {
					// wrap should always work with our buffers
					throw new IOException("Unexpected status:" + res.getStatus()
							+ " during handshake WRAP.");
				}

				System.out.println("----> NEED_WRAP-2 : HandshakeStatus = " + handshakeStatus);

				break;
			case NEED_TASK:
				handshakeStatus = tasks();

				break;
			case NOT_HANDSHAKING:
				throw new IOException("NOT_HANDSHAKING during handshake");
			case FINISHED:
				break;
			}
		}

		System.out
				.println(this + " END OF HANDSHAKE PROCESS, HANDSHAKE STATUS : " + handshakeStatus);
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

		System.out.println("----->> task() : handshakeStatus = " + sslEngine.getHandshakeStatus());

		return sslEngine.getHandshakeStatus();
	}

	/**
	 * Try to run tasks if any.
	 */
	private void tryTasks() {
		if (sslEngine.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
			handshakeStatus = tasks();
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
