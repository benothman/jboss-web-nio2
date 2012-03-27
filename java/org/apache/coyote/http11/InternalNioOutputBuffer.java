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
package org.apache.coyote.http11;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.CompletionHandler;
import java.nio.channels.InterruptedByTimeoutException;
import java.util.concurrent.TimeUnit;

import org.apache.coyote.ActionCode;
import org.apache.coyote.Response;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.CompletionHandlerAdapter;
import org.apache.tomcat.util.net.NioChannel;
import org.apache.tomcat.util.net.NioEndpoint;
import org.apache.tomcat.util.net.SocketStatus;

/**
 * {@code InternalNioOutputBuffer}
 * 
 * Created on Dec 16, 2011 at 9:15:05 AM
 * 
 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
 */
public class InternalNioOutputBuffer extends AbstractInternalOutputBuffer {

	/**
	 * Underlying channel.
	 */
	protected NioChannel channel;

	/**
	 * NIO endpoint.
	 */
	protected NioEndpoint endpoint;

	/**
	 * Create a new instance of {@code InternalNioOutputBuffer}
	 * 
	 * @param response
	 * @param headerBufferSize
	 * @param endpoint
	 */
	public InternalNioOutputBuffer(Response response, int headerBufferSize, NioEndpoint endpoint) {
		super(response, headerBufferSize);
		this.endpoint = endpoint;
		this.writeTimeout = endpoint.getSoTimeout();
	}

	/**
	 * Set the underlying socket.
	 * 
	 * @param channel
	 */
	public void setChannel(NioChannel channel) {
		this.channel = channel;
	}

	/**
	 * Get the underlying socket input stream.
	 * 
	 * @return the channel
	 */
	public NioChannel getChannel() {
		return channel;
	}

	/**
	 * Recycle the output buffer. This should be called when closing the
	 * connection.
	 */
	public void recycle() {
		super.recycle();
		channel = null;
	}

	/**
	 * Close the channel
	 * 
	 * @param channel
	 */
	private void close(NioChannel channel) {
		endpoint.closeChannel(channel);
	}

	/**
	 * Perform a blocking write operation
	 * 
	 * @param buffer
	 *            the buffer containing the data to write
	 * @param timeout
	 *            a timeout for the operation
	 * @param unit
	 *            The time unit
	 * 
	 * @return the number of bytes written, -1 in case of errors
	 */
	private int blockingWrite(ByteBuffer buffer, long timeout, TimeUnit unit) {
		int nw = 0;
		try {
			long wrTimeout = timeout > 0 ? timeout : Integer.MAX_VALUE;
			nw = this.channel.writeBytes(buffer, wrTimeout, unit);
			if (nw < 0) {
				close(channel);
			}
		} catch (Exception e) {
			log.warn("An error occurs when trying a blocking write");
			if (log.isDebugEnabled()) {
				log.debug(e.getMessage(), e);
			}
		}

		return nw;
	}

	/**
	 * Perform a non-blocking write operation
	 * 
	 * @param buffer
	 *            the buffer containing the data to write
	 * @param timeout
	 *            a timeout for the operation
	 * @param unit
	 *            The time unit
	 */
	private void nonBlockingWrite(final ByteBuffer buffer, final long timeout, final TimeUnit unit) {

		final NioChannel ch = this.channel;
		final long writeTimeout = timeout > 0 ? timeout : Integer.MAX_VALUE;

		ch.write(buffer, writeTimeout, unit, ch,
				new CompletionHandlerAdapter<Integer, NioChannel>() {

					@Override
					public void completed(Integer nBytes, NioChannel attachment) {
						if (nBytes < 0) {
							failed(new ClosedChannelException(), attachment);
							return;
						}

						if (buffer.hasRemaining()) {
							attachment.write(buffer, writeTimeout, unit, attachment, this);
						} else {
							// Clear the buffer when all bytes are written
							buffer.clear();
						}
					}

					@Override
					public void failed(Throwable exc, NioChannel attachment) {
						endpoint.removeEventChannel(attachment);
						if (exc instanceof InterruptedByTimeoutException) {
							endpoint.processChannel(attachment, SocketStatus.TIMEOUT);
							close(attachment);
						} else if (exc instanceof ClosedChannelException) {
							endpoint.processChannel(attachment, SocketStatus.DISCONNECT);
						} else {
							endpoint.processChannel(attachment, SocketStatus.ERROR);
						}
					}
				});
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.coyote.http11.AbstractInternalOutputBuffer#write(java.nio.
	 * ByteBuffer, long, java.util.concurrent.TimeUnit)
	 */
	@Override
	protected int write(final ByteBuffer buffer, final long timeout, final TimeUnit unit) {
		if (nonBlocking) {
			nonBlockingWrite(buffer, timeout, unit);
			return 0;
		}

		return blockingWrite(buffer, timeout, unit);
	}

	/**
	 * Send an acknowledgment.
	 * 
	 * @throws Exception
	 */
	public void sendAck() throws Exception {

		if (!committed) {
			this.bbuf.clear();
			this.bbuf.put(Constants.ACK_BYTES).flip();
			if (this.write(bbuf, writeTimeout, TimeUnit.MILLISECONDS) < 0) {
				throw new IOException(sm.getString("oob.failedwrite"));
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.coyote.http11.AbstractInternalOutputBuffer#doWrite(org.apache
	 * .tomcat.util.buf.ByteChunk, org.apache.coyote.Response)
	 */
	public int doWrite(ByteChunk chunk, Response res) throws IOException {

		if (!committed) {
			// Send the connector a request for commit. The connector should
			// then validate the headers, send them (using sendHeaders) and
			// set the filters accordingly.
			response.action(ActionCode.ACTION_COMMIT, null);
		}

		// If non blocking (event) and there are leftover bytes,
		// and lastWrite was 0 -> error
		if (leftover.getLength() > 0 && !(Http11NioProcessor.containerThread.get() == Boolean.TRUE)) {
			throw new IOException(sm.getString("oob.backlog"));
		}

		if (lastActiveFilter == -1) {
			return outputBuffer.doWrite(chunk, res);
		} else {
			return activeFilters[lastActiveFilter].doWrite(chunk, res);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.coyote.http11.AbstractInternalOutputBuffer#flushBuffer()
	 */
	protected void flushBuffer() throws IOException {
		int res = 0;

		// If there are still leftover bytes here, this means the user did a
		// direct flush:
		// - If the call is asynchronous, throw an exception
		// - If the call is synchronous, make regular blocking writes to flush
		// the data
		if (leftover.getLength() > 0) {
			if (Http11NioProcessor.containerThread.get() == Boolean.TRUE) {
				// Send leftover bytes
				while (leftover.getLength() > 0) {
					// Calculate the maximum number of bytes that can fit in the
					// buffer
					int n = Math.min(bbuf.remaining(), leftover.getLength());
					int off = leftover.getOffset();
					// Put bytes into the buffer
					bbuf.put(leftover.getBuffer(), off, n).flip();
					// Update the offset of the leftover ByteChunck
					leftover.setOffset(off + n);
					while (bbuf.hasRemaining()) {
						res = blockingWrite(bbuf, writeTimeout, TimeUnit.MILLISECONDS);
						if (res < 0) {
							break;
						}
					}
					bbuf.clear();
					if (res < 0) {
						throw new IOException(sm.getString("oob.failedwrite"));
					}
				}
				leftover.recycle();
			} else {
				throw new IOException(sm.getString("oob.backlog"));
			}
		}

		if (bbuf.position() > 0) {
			bbuf.flip();

			if (nonBlocking) {
				// Perform non blocking writes until all data is written, or the
				// result of the write is 0
				nonBlockingWrite(bbuf, writeTimeout, TimeUnit.MILLISECONDS);
			} else {
				while (bbuf.hasRemaining()) {
					res = blockingWrite(bbuf, writeTimeout, TimeUnit.MILLISECONDS);
					if (res <= 0) {
						break;
					}
				}
				response.setLastWrite(res);
				bbuf.clear();
			}

			if (res < 0) {
				throw new IOException(sm.getString("oob.failedwrite"));
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.coyote.http11.AbstractInternalOutputBuffer#flushLeftover()
	 */
	@Override
	public boolean flushLeftover() throws IOException {
		// Calculate the number of bytes that fit in the buffer
		int n = Math.min(leftover.getLength(), bbuf.remaining());
		// put bytes in the buffer
		bbuf.put(leftover.getBuffer(), leftover.getOffset(), n).flip();
		// Update the offset
		leftover.setOffset(leftover.getOffset() + n);
		final NioChannel ch = channel;

		ch.write(bbuf, writeTimeout, TimeUnit.MILLISECONDS, null,
				new CompletionHandler<Integer, Void>() {

					@Override
					public void completed(Integer result, Void attachment) {
						if (result < 0) {
							failed(new IOException(sm.getString("oob.failedwrite")), attachment);
							return;
						}
						response.setLastWrite(result);
						if (!bbuf.hasRemaining()) {
							bbuf.clear();
							if (leftover.getLength() > 0) {
								int n = Math.min(leftover.getLength(), bbuf.remaining());
								bbuf.put(leftover.getBuffer(), leftover.getOffset(), n).flip();
								leftover.setOffset(leftover.getOffset() + n);
							} else {
								leftover.recycle();
								return;
							}
						}
						// Write the remaining bytes
						ch.write(bbuf, writeTimeout, TimeUnit.MILLISECONDS, null, this);
					}

					@Override
					public void failed(Throwable exc, Void attachment) {
						close(ch);
					}
				});

		return true;
	}
}
