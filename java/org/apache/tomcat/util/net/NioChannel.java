/**
 * JBoss, Home of Professional Open Source. Copyright 2011, Red Hat, Inc., and
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
package org.apache.tomcat.util.net;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.CompletionHandler;
import java.nio.channels.InterruptedByTimeoutException;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.ReadPendingException;
import java.nio.channels.ShutdownChannelGroupException;
import java.nio.channels.WritePendingException;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@code NioChannel}
 * 
 * Created on Dec 19, 2011 at 11:40:18 AM
 * 
 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
 */
public class NioChannel implements AsynchronousByteChannel {

	private static final AtomicLong counter = new AtomicLong();
	private AsynchronousSocketChannel channel;
	private long id;
	private ByteBuffer buffer;
	private boolean flag;

	/**
	 * Create a new instance of {@code NioChannel}
	 * 
	 * @param channel
	 */
	protected NioChannel(AsynchronousSocketChannel channel) {
		if (channel == null) {
			throw new NullPointerException("null channel parameter");
		}
		this.channel = channel;
		this.id = counter.getAndIncrement();
		this.buffer = ByteBuffer.allocateDirect(1);
	}

	/**
	 * Reset the flag and the internal buffer
	 */
	public void reset() {
		this.flag = false;
		this.buffer.clear();
	}

	/**
	 * Set the flag to true
	 */
	public void setFlag() {
		this.flag = true;
	}

	/**
	 * @return the value of the <tt>flag</tt>
	 */
	public boolean getFlag() {
		return this.flag;
	}

	/**
	 * @return the internal buffer
	 */
	public ByteBuffer getBuffer() {
		return this.buffer;
	}

	/**
	 * @return the channel id
	 */
	public long getId() {
		return this.id;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.nio.channels.Channel#isOpen()
	 */
	@Override
	public boolean isOpen() {
		return this.channel.isOpen();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.nio.channels.Channel#close()
	 */
	@Override
	public void close() throws IOException {
		this.channel.close();
	}

	/**
	 * @param force
	 * @throws IOException
	 */
	public void close(boolean force) throws IOException {
		if (isOpen() && force) {
			this.channel.close();
		}
	}

	/**
	 * Getter for channel
	 * 
	 * @return the channel
	 */
	public AsynchronousSocketChannel getChannel() {
		return this.channel;
	}

	/**
	 * Setter for the channel
	 * 
	 * @param channel
	 *            the channel to set
	 */
	public void setChannel(AsynchronousSocketChannel channel) {
		this.channel = channel;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.nio.channels.ReadableByteChannel#read(java.nio.ByteBuffer)
	 */
	@Override
	public Future<Integer> read(ByteBuffer dst) {
		return this.channel.read(dst);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.nio.channels.AsynchronousByteChannel#read(java.nio.ByteBuffer,
	 * java.lang.Object, java.nio.channels.CompletionHandler)
	 */
	@Override
	public <A> void read(ByteBuffer dst, A attachment, CompletionHandler<Integer, ? super A> handler) {
		this.channel.read(dst, attachment, handler);
	}

	/**
	 * Reads a sequence of bytes from this channel into the given buffer.
	 * 
	 * <p>
	 * This method initiates an asynchronous read operation to read a sequence
	 * of bytes from this channel into the given buffer. The {@code handler}
	 * parameter is a completion handler that is invoked when the read operation
	 * completes (or fails). The result passed to the completion handler is the
	 * number of bytes read or {@code -1} if no bytes could be read because the
	 * channel has reached end-of-stream.
	 * 
	 * <p>
	 * If a timeout is specified and the timeout elapses before the operation
	 * completes then the operation completes with the exception
	 * {@link InterruptedByTimeoutException}. Where a timeout occurs, and the
	 * implementation cannot guarantee that bytes have not been read, or will
	 * not be read from the channel into the given buffer, then further attempts
	 * to read from the channel will cause an unspecific runtime exception to be
	 * thrown.
	 * 
	 * <p>
	 * Otherwise this method works in the same manner as the
	 * {@link AsynchronousByteChannel#read(ByteBuffer,Object,CompletionHandler)}
	 * method.
	 * 
	 * @param dst
	 *            The buffer into which bytes are to be transferred
	 * @param timeout
	 *            The maximum time for the I/O operation to complete
	 * @param unit
	 *            The time unit of the {@code timeout} argument
	 * @param attachment
	 *            The object to attach to the I/O operation; can be {@code null}
	 * @param handler
	 *            The handler for consuming the result
	 * 
	 * @throws IllegalArgumentException
	 *             If the buffer is read-only
	 * @throws ReadPendingException
	 *             If a read operation is already in progress on this channel
	 * @throws NotYetConnectedException
	 *             If this channel is not yet connected
	 * @throws ShutdownChannelGroupException
	 *             If the channel group has terminated
	 */
	public <A> void read(ByteBuffer dst, long timeout, TimeUnit unit, A attachment,
			CompletionHandler<Integer, ? super A> handler) {
		this.channel.read(dst, timeout, unit, attachment, handler);
	}

	/**
	 * Reads a sequence of bytes from this channel into a subsequence of the
	 * given buffers. This operation, sometimes called a
	 * <em>scattering read</em>, is often useful when implementing network
	 * protocols that group data into segments consisting of one or more
	 * fixed-length headers followed by a variable-length body. The
	 * {@code handler} parameter is a completion handler that is invoked when
	 * the read operation completes (or fails). The result passed to the
	 * completion handler is the number of bytes read or {@code -1} if no bytes
	 * could be read because the channel has reached end-of-stream.
	 * 
	 * <p>
	 * This method initiates a read of up to <i>r</i> bytes from this channel,
	 * where <i>r</i> is the total number of bytes remaining in the specified
	 * subsequence of the given buffer array, that is,
	 * 
	 * <blockquote>
	 * 
	 * <pre>
	 * dsts[offset].remaining()
	 *     + dsts[offset+1].remaining()
	 *     + ... + dsts[offset+length-1].remaining()
	 * </pre>
	 * 
	 * </blockquote>
	 * 
	 * at the moment that the read is attempted.
	 * 
	 * <p>
	 * Suppose that a byte sequence of length <i>n</i> is read, where <tt>0</tt>
	 * &nbsp;<tt>&lt;</tt>&nbsp;<i>n</i>&nbsp;<tt>&lt;=</tt>&nbsp;<i>r</i>. Up
	 * to the first <tt>dsts[offset].remaining()</tt> bytes of this sequence are
	 * transferred into buffer <tt>dsts[offset]</tt>, up to the next
	 * <tt>dsts[offset+1].remaining()</tt> bytes are transferred into buffer
	 * <tt>dsts[offset+1]</tt>, and so forth, until the entire byte sequence is
	 * transferred into the given buffers. As many bytes as possible are
	 * transferred into each buffer, hence the final position of each updated
	 * buffer, except the last updated buffer, is guaranteed to be equal to that
	 * buffer's limit. The underlying operating system may impose a limit on the
	 * number of buffers that may be used in an I/O operation. Where the number
	 * of buffers (with bytes remaining), exceeds this limit, then the I/O
	 * operation is performed with the maximum number of buffers allowed by the
	 * operating system.
	 * 
	 * <p>
	 * If a timeout is specified and the timeout elapses before the operation
	 * completes then it completes with the exception
	 * {@link InterruptedByTimeoutException}. Where a timeout occurs, and the
	 * implementation cannot guarantee that bytes have not been read, or will
	 * not be read from the channel into the given buffers, then further
	 * attempts to read from the channel will cause an unspecific runtime
	 * exception to be thrown.
	 * 
	 * @param dsts
	 *            The buffers into which bytes are to be transferred
	 * @param offset
	 *            The offset within the buffer array of the first buffer into
	 *            which bytes are to be transferred; must be non-negative and no
	 *            larger than {@code dsts.length}
	 * @param length
	 *            The maximum number of buffers to be accessed; must be
	 *            non-negative and no larger than {@code dsts.length - offset}
	 * @param timeout
	 *            The maximum time for the I/O operation to complete
	 * @param unit
	 *            The time unit of the {@code timeout} argument
	 * @param attachment
	 *            The object to attach to the I/O operation; can be {@code null}
	 * @param handler
	 *            The handler for consuming the result
	 * 
	 * @throws IndexOutOfBoundsException
	 *             If the pre-conditions for the {@code offset} and
	 *             {@code length} parameter aren't met
	 * @throws IllegalArgumentException
	 *             If the buffer is read-only
	 * @throws ReadPendingException
	 *             If a read operation is already in progress on this channel
	 * @throws NotYetConnectedException
	 *             If this channel is not yet connected
	 * @throws ShutdownChannelGroupException
	 *             If the channel group has terminated
	 */
	public <A> void read(ByteBuffer[] dsts, int offset, int length, long timeout, TimeUnit unit,
			A attachment, CompletionHandler<Long, ? super A> handler) {
		this.channel.read(dsts, offset, length, timeout, unit, attachment, handler);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.nio.channels.WritableByteChannel#write(java.nio.ByteBuffer)
	 */
	@Override
	public Future<Integer> write(ByteBuffer src) {
		return this.channel.write(src);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.nio.channels.AsynchronousByteChannel#write(java.nio.ByteBuffer,
	 * java.lang.Object, java.nio.channels.CompletionHandler)
	 */
	@Override
	public <A> void write(ByteBuffer src, A attachment,
			CompletionHandler<Integer, ? super A> handler) {
		this.channel.write(src, attachment, handler);
	}

	/**
	 * Writes a sequence of bytes to this channel from the given buffer.
	 * 
	 * <p>
	 * This method initiates an asynchronous write operation to write a sequence
	 * of bytes to this channel from the given buffer. The {@code handler}
	 * parameter is a completion handler that is invoked when the write
	 * operation completes (or fails). The result passed to the completion
	 * handler is the number of bytes written.
	 * 
	 * <p>
	 * If a timeout is specified and the timeout elapses before the operation
	 * completes then it completes with the exception
	 * {@link InterruptedByTimeoutException}. Where a timeout occurs, and the
	 * implementation cannot guarantee that bytes have not been written, or will
	 * not be written to the channel from the given buffer, then further
	 * attempts to write to the channel will cause an unspecific runtime
	 * exception to be thrown.
	 * 
	 * <p>
	 * Otherwise this method works in the same manner as the
	 * {@link AsynchronousByteChannel#write(ByteBuffer,Object,CompletionHandler)}
	 * method.
	 * 
	 * @param src
	 *            The buffer from which bytes are to be retrieved
	 * @param timeout
	 *            The maximum time for the I/O operation to complete
	 * @param unit
	 *            The time unit of the {@code timeout} argument
	 * @param attachment
	 *            The object to attach to the I/O operation; can be {@code null}
	 * @param handler
	 *            The handler for consuming the result
	 * 
	 * @throws WritePendingException
	 *             If a write operation is already in progress on this channel
	 * @throws NotYetConnectedException
	 *             If this channel is not yet connected
	 * @throws ShutdownChannelGroupException
	 *             If the channel group has terminated
	 */
	public <A> void write(ByteBuffer src, long timeout, TimeUnit unit, A attachment,
			CompletionHandler<Integer, ? super A> handler) {
		this.channel.write(src, timeout, unit, attachment, handler);
	}

	/**
	 * Writes a sequence of bytes to this channel from a subsequence of the
	 * given buffers. This operation, sometimes called a
	 * <em>gathering write</em>, is often useful when implementing network
	 * protocols that group data into segments consisting of one or more
	 * fixed-length headers followed by a variable-length body. The
	 * {@code handler} parameter is a completion handler that is invoked when
	 * the write operation completes (or fails). The result passed to the
	 * completion handler is the number of bytes written.
	 * 
	 * <p>
	 * This method initiates a write of up to <i>r</i> bytes to this channel,
	 * where <i>r</i> is the total number of bytes remaining in the specified
	 * subsequence of the given buffer array, that is,
	 * 
	 * <blockquote>
	 * 
	 * <pre>
	 * srcs[offset].remaining()
	 *     + srcs[offset+1].remaining()
	 *     + ... + srcs[offset+length-1].remaining()
	 * </pre>
	 * 
	 * </blockquote>
	 * 
	 * at the moment that the write is attempted.
	 * 
	 * <p>
	 * Suppose that a byte sequence of length <i>n</i> is written, where
	 * <tt>0</tt>&nbsp;<tt>&lt;</tt>&nbsp;<i>n</i>&nbsp;<tt>&lt;=</tt>
	 * &nbsp;<i>r</i>. Up to the first <tt>srcs[offset].remaining()</tt> bytes
	 * of this sequence are written from buffer <tt>srcs[offset]</tt>, up to the
	 * next <tt>srcs[offset+1].remaining()</tt> bytes are written from buffer
	 * <tt>srcs[offset+1]</tt>, and so forth, until the entire byte sequence is
	 * written. As many bytes as possible are written from each buffer, hence
	 * the final position of each updated buffer, except the last updated
	 * buffer, is guaranteed to be equal to that buffer's limit. The underlying
	 * operating system may impose a limit on the number of buffers that may be
	 * used in an I/O operation. Where the number of buffers (with bytes
	 * remaining), exceeds this limit, then the I/O operation is performed with
	 * the maximum number of buffers allowed by the operating system.
	 * 
	 * <p>
	 * If a timeout is specified and the timeout elapses before the operation
	 * completes then it completes with the exception
	 * {@link InterruptedByTimeoutException}. Where a timeout occurs, and the
	 * implementation cannot guarantee that bytes have not been written, or will
	 * not be written to the channel from the given buffers, then further
	 * attempts to write to the channel will cause an unspecific runtime
	 * exception to be thrown.
	 * 
	 * @param srcs
	 *            The buffers from which bytes are to be retrieved
	 * @param offset
	 *            The offset within the buffer array of the first buffer from
	 *            which bytes are to be retrieved; must be non-negative and no
	 *            larger than {@code srcs.length}
	 * @param length
	 *            The maximum number of buffers to be accessed; must be
	 *            non-negative and no larger than {@code srcs.length - offset}
	 * @param timeout
	 *            The maximum time for the I/O operation to complete
	 * @param unit
	 *            The time unit of the {@code timeout} argument
	 * @param attachment
	 *            The object to attach to the I/O operation; can be {@code null}
	 * @param handler
	 *            The handler for consuming the result
	 * 
	 * @throws IndexOutOfBoundsException
	 *             If the pre-conditions for the {@code offset} and
	 *             {@code length} parameter aren't met
	 * @throws WritePendingException
	 *             If a write operation is already in progress on this channel
	 * @throws NotYetConnectedException
	 *             If this channel is not yet connected
	 * @throws ShutdownChannelGroupException
	 *             If the channel group has terminated
	 */
	public <A> void write(ByteBuffer[] srcs, int offset, int length, long timeout, TimeUnit unit,
			A attachment, CompletionHandler<Long, ? super A> handler) {
		this.channel.write(srcs, offset, length, timeout, unit, attachment, handler);
	}

	/**
	 * @return the local address
	 * @throws IOException
	 */
	public SocketAddress getLocalAddress() throws IOException {
		return this.channel.getLocalAddress();
	}

	/**
	 * @return the remote address
	 * @throws IOException
	 */
	public SocketAddress getRemoteAddress() throws IOException {
		return this.channel.getRemoteAddress();
	}

	/**
	 * Shutdown the connection for reading without closing the channel.
	 * 
	 * <p>
	 * Once shutdown for reading then further reads on the channel will return
	 * {@code -1}, the end-of-stream indication. If the input side of the
	 * connection is already shutdown then invoking this method has no effect.
	 * The effect on an outstanding read operation is system dependent and
	 * therefore not specified. The effect, if any, when there is data in the
	 * socket receive buffer that has not been read, or data arrives
	 * subsequently, is also system dependent.
	 * 
	 * @return The channel
	 * 
	 * @throws NotYetConnectedException
	 *             If this channel is not yet connected
	 * @throws ClosedChannelException
	 *             If this channel is closed
	 * @throws IOException
	 *             If some other I/O error occurs
	 */
	public final NioChannel shutdownInput() throws IOException {
		this.channel.shutdownInput();
		return this;
	}

	/**
	 * Shutdown the connection for writing without closing the channel.
	 * 
	 * <p>
	 * Once shutdown for writing then further attempts to write to the channel
	 * will throw {@link ClosedChannelException}. If the output side of the
	 * connection is already shutdown then invoking this method has no effect.
	 * The effect on an outstanding write operation is system dependent and
	 * therefore not specified.
	 * 
	 * @return The channel
	 * 
	 * @throws NotYetConnectedException
	 *             If this channel is not yet connected
	 * @throws ClosedChannelException
	 *             If this channel is closed
	 * @throws IOException
	 *             If some other I/O error occurs
	 */
	public final NioChannel shutdownOutput() throws IOException {
		this.channel.shutdownOutput();
		return this;
	}

	/**
	 * @param name
	 * @param value
	 * @throws IOException
	 */
	public <T> void setOption(SocketOption<T> name, T value) throws IOException {
		this.channel.setOption(name, value);
	}

	/**
	 * @param name
	 *            the option name
	 * @return the socket option, if any, having the specified name
	 * @throws IOException
	 */
	public <T> T getOption(SocketOption<T> name) throws IOException {
		return this.channel.getOption(name);
	}

	/**
	 * @return the set of supported options
	 */
	public Set<SocketOption<?>> supportedOptions() {
		return this.channel.supportedOptions();
	}

	@Override
	public String toString() {
		return getClass().getName() + "[" + getId() + "]";
	}
}
