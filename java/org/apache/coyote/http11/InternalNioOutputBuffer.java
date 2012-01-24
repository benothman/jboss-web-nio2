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
import java.nio.channels.CompletionHandler;
import java.nio.channels.InterruptedByTimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.coyote.ActionCode;
import org.apache.coyote.Response;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.NioChannel;
import org.apache.tomcat.util.net.NioEndpoint;

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
	protected NioEndpoint endpoint = null;

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
	public NioChannel geChannel() {
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
	private static void close(NioChannel channel) {
		System.out.println("--> Closing connecton : " + channel);
		try {
			channel.close();
		} catch (IOException e) {
			// NOTHING
		}
	}

	/**
	 * 
	 * @param buffer
	 * @return
	 */
	private int blockingWrite(ByteBuffer buffer, long timeout, TimeUnit unit) {
		try {
			if (timeout > 0) {
				return this.channel.write(buffer).get(timeout, unit);
			}
			return this.channel.write(buffer).get();
		} catch (TimeoutException te) {
			close(channel);
		} catch (Exception e) {
			// NOP
			log.warn("An error occurs when trying a blocking write");
			log.error(e.getMessage(), e);
		}
		return -1;
	}

	/**
	 * PErform a non-blocking write
	 * 
	 * @param buffer
	 */
	private void nonBlockingWrite(final ByteBuffer buffer, long timeout, TimeUnit unit) {

		/*
		 * int pos = 0; int end = bbuf.position(); while (pos < end) { res =
		 * Socket.sendibb(socket, pos, end - pos); if (res > 0) { pos += res; }
		 * else { break; } } if (pos < end) { if (response.getFlushLeftovers()
		 * && (Http11AprProcessor.containerThread.get() == Boolean.TRUE)) { //
		 * Switch to blocking mode and write the data Socket.timeoutSet(socket,
		 * endpoint.getSoTimeout() * 1000); res = Socket.sendbb(socket, 0, end);
		 * Socket.timeoutSet(socket, 0); } else { // Put any leftover bytes in
		 * the leftover byte chunk leftover.allocate(end - pos, -1);
		 * bbuf.position(pos); bbuf.limit(end); bbuf.get(leftover.getBuffer(),
		 * 0, end - pos); leftover.setEnd(end - pos); // Call for a write event
		 * because it is possible that no further write // operations are made
		 * if (!response.getFlushLeftovers()) {
		 * response.action(ActionCode.ACTION_EVENT_WRITE, null); } } }
		 */

		this.channel.write(buffer, timeout, unit, channel,
				new CompletionHandler<Integer, NioChannel>() {

					@Override
					public void completed(Integer nBytes, NioChannel attachment) {
						// Perform non blocking writes until all data is
						// written, or the
						// result
						// of the write is 0
						if (nBytes < 0) {
							close(attachment);
							return;
						}
						// TODO complete implementation
						if (buffer.hasRemaining()) {
							attachment.write(buffer, null, this);
						}
					}

					@Override
					public void failed(Throwable exc, NioChannel attachment) {
						if (exc instanceof InterruptedByTimeoutException) {
							close(attachment);
						}
					}
				});
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
			if (this.nonBlocking) {
				this.nonBlockingWrite(bbuf, writeTimeout, TimeUnit.MILLISECONDS);
			} else {
				if (this.blockingWrite(bbuf, writeTimeout, TimeUnit.MILLISECONDS) < 0) {
					throw new IOException(sm.getString("oob.failedwrite"));
				}
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
		if (leftover.getLength() > 0 && !(Http11AprProcessor.containerThread.get() == Boolean.TRUE)) {
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
			System.out.println("##### Step - 1 #####");
			if (Http11AprProcessor.containerThread.get() == Boolean.TRUE) {
				// Send leftover bytes
				System.out.println("##### Step - 2 #####");

				ByteBuffer bb = ByteBuffer.allocate(leftover.getLength());
				bb.put(leftover.getBuffer(), leftover.getOffset(), leftover.getEnd());
				bb.rewind();
				res = blockingWrite(bb, writeTimeout, TimeUnit.MILLISECONDS);
				leftover.recycle();
				// Send current buffer
				if (res > 0 && bbuf.position() > 0) {
					bbuf.rewind();
					while (bbuf.hasRemaining()) {
						res = blockingWrite(bbuf, writeTimeout, TimeUnit.MILLISECONDS);
					}
				}
				bbuf.clear();
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
					System.out.println("-----> written = " + res + ", remain = " + bbuf.remaining());
					if (res <= 0) {
						break;
					}
				}
			}
			response.setLastWrite(res);
			bbuf.clear();
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

		System.out.println("################ flushLeftover ###############");

		int len = leftover.getLength();
		int start = leftover.getStart();
		byte[] b = leftover.getBuffer();

		bbuf.clear();
		bbuf.put(b, leftover.getOffset(), leftover.getLength());
		bbuf.flip();

		if (nonBlocking) {
			nonBlockingWrite(bbuf, writeTimeout, TimeUnit.MILLISECONDS);
		} else {
			int res = 0;
			while ((res = blockingWrite(bbuf, writeTimeout, TimeUnit.MILLISECONDS)) > 0) {
				// Wait until all bytes are written, or the channel was closed
			}

			if (res < 0) { // The channel was closed
				throw new IOException(sm.getString("oob.failedwrite"));
			}

			response.setLastWrite(res);
			if (bbuf.position() < bbuf.limit()) {
				// Could not write all leftover data: put back to write
				leftover.setOffset(start);
				return false;
			} else {
				bbuf.clear();
				leftover.recycle();
				return true;
			}
		}

		return true;

		// -------------------
		/*
		 * while (len > 0) { int thisTime = len; if (bbuf.position() ==
		 * bbuf.capacity()) { int res = 0; bbuf.flip(); if (nonBlocking) {
		 * nonBlockingWrite(bbuf); } else { while((res = blockingWrite(bbuf)) >
		 * 0) { // Wait until all bytes are written, or the channel was closed }
		 * }
		 * 
		 * if (res < 0) { // The channel was closed throw new
		 * IOException(sm.getString("oob.failedwrite")); }
		 * 
		 * response.setLastWrite(res);
		 * 
		 * if (bbuf.position() < bbuf.limit()) { // Could not write all leftover
		 * data: put back to write leftover.setOffset(start); return false; }
		 * else { bbuf.clear(); } } if (thisTime > bbuf.capacity() -
		 * bbuf.position()) { thisTime = bbuf.capacity() - bbuf.position(); }
		 * 
		 * 
		 * 
		 * bbuf.put(b, start, thisTime); len = len - thisTime; start = start +
		 * thisTime; }
		 * 
		 * int pos = 0; int end = bbuf.position(); if (pos < end) { int res = 0;
		 * while (pos < end) { // res = Socket.sendibb(socket, pos, end - pos);
		 * // TODO update code to use channels if (res > 0) { pos += res; } else
		 * { break; } } if (res < 0) { throw new
		 * IOException(sm.getString("oob.failedwrite")); }
		 * response.setLastWrite(res); } if (pos < end) { leftover.allocate(end
		 * - pos, -1); bbuf.position(pos); bbuf.limit(end);
		 * bbuf.get(leftover.getBuffer(), 0, end - pos); leftover.setEnd(end -
		 * pos); bbuf.clear(); return false; } bbuf.clear(); leftover.recycle();
		 * 
		 * return true;
		 */
	}
}
