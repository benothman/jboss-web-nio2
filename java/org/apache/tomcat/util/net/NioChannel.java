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
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.AlreadyBoundException;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.Channels;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.CompletionHandler;
import java.nio.channels.InterruptedByTimeoutException;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.ReadPendingException;
import java.nio.channels.ShutdownChannelGroupException;
import java.nio.channels.UnsupportedAddressTypeException;
import java.nio.channels.WritePendingException;
import java.nio.channels.spi.AsynchronousChannelProvider;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@code NioChannel}
 * 
 * An asynchronous channel that can read and write bytes.
 * 
 * <p>
 * Some channels may not allow more than one read or write to be outstanding at
 * any given time. If a thread invokes a read method before a previous read
 * operation has completed then a {@link ReadPendingException} will be thrown.
 * Similarly, if a write method is invoked before a previous write has completed
 * then {@link WritePendingException} is thrown. Whether or not other kinds of
 * I/O operations may proceed concurrently with a read operation depends upon
 * the type of the channel.
 * </p>
 * <p>
 * Note that {@link java.nio.ByteBuffer ByteBuffers} are not safe for use by
 * multiple concurrent threads. When a read or write operation is initiated then
 * care must be taken to ensure that the buffer is not accessed until the
 * operation completes.
 * </p>
 * 
 * Created on Dec 19, 2011 at 11:40:18 AM
 * 
 * @see Channels#newInputStream(AsynchronousByteChannel)
 * @see Channels#newOutputStream(AsynchronousByteChannel)
 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
 */
public class NioChannel implements AsynchronousByteChannel {

	private static final AtomicLong counter = new AtomicLong();
	protected AsynchronousSocketChannel channel;
	private long id;
	private ByteBuffer buffer;
	private boolean flag;
	private String name;

	/**
	 * Create a new instance of {@code NioChannel}
	 * 
	 * @param channel
	 *            The {@link java.nio.channels.AsynchronousSocketChannel}
	 *            attached to this channel
	 * @throws NullPointerException
	 *             if the channel parameter is null
	 */
	protected NioChannel(AsynchronousSocketChannel channel) {
		if (channel == null) {
			throw new NullPointerException("null channel parameter");
		}
		this.channel = channel;
		this.id = counter.getAndIncrement();
		this.buffer = ByteBuffer.allocateDirect(1);
		this.name = getClass().getName() + "[" + getId() + "]";
	}

	/**
	 * Opens an asynchronous socket channel.
	 * 
	 * <p>
	 * The new channel is created by invoking the
	 * {@link AsynchronousChannelProvider#openAsynchronousSocketChannel
	 * openAsynchronousSocketChannel} method on the
	 * {@link AsynchronousChannelProvider} that created the group. If the group
	 * parameter is {@code null} then the resulting channel is created by the
	 * system-wide default provider, and bound to the <em>default group</em>.
	 * 
	 * @param group
	 *            The group to which the newly constructed channel should be
	 *            bound, or {@code null} for the default group
	 * 
	 * @return A new asynchronous socket channel
	 * 
	 * @throws ShutdownChannelGroupException
	 *             If the channel group is shutdown
	 * @throws IOException
	 *             If an I/O error occurs
	 */
	public static NioChannel open(AsynchronousChannelGroup group) throws IOException {
		AsynchronousSocketChannel channel = AsynchronousSocketChannel.open(group);
		return new NioChannel(channel);
	}

	/**
	 * Opens a {@code NioChannel}.
	 * 
	 * <p>
	 * This method returns an {@code NioChannel} that is bound to the
	 * <em>default group</em>.This method is equivalent to evaluating the
	 * expression: <blockquote>
	 * 
	 * <pre>
	 * open((AsynchronousChannelGroup) null);
	 * </pre>
	 * 
	 * </blockquote>
	 * 
	 * @return A new {@code NioChannel}
	 * 
	 * @throws IOException
	 *             If an I/O error occurs
	 */
	public static NioChannel open() throws IOException {
		return open(null);
	}

	/**
	 * Binds the channel's socket to a local address.
	 * 
	 * <p>
	 * This method is used to establish an association between the socket and a
	 * local address. Once an association is established then the socket remains
	 * bound until the channel is closed. If the {@code local} parameter has the
	 * value {@code null} then the socket will be bound to an address that is
	 * assigned automatically.
	 * 
	 * @param local
	 *            The address to bind the socket, or {@code null} to bind the
	 *            socket to an automatically assigned socket address
	 * 
	 * @return This channel
	 * 
	 * @throws AlreadyBoundException
	 *             If the socket is already bound
	 * @throws UnsupportedAddressTypeException
	 *             If the type of the given address is not supported
	 * @throws ClosedChannelException
	 *             If the channel is closed
	 * @throws IOException
	 *             If some other I/O error occurs
	 * @throws SecurityException
	 *             If a security manager is installed and it denies an
	 *             unspecified permission. An implementation of this interface
	 *             should specify any required permissions.
	 * 
	 * @see #getLocalAddress
	 */
	public NioChannel bind(SocketAddress local) throws IOException {
		this.channel.bind(local);
		return this;
	}

	/**
	 * Binds the channel's socket to a local address.
	 * 
	 * <p>
	 * This method is used to establish an association between the socket and a
	 * local address. Once an association is established then the socket remains
	 * bound until the channel is closed.
	 * 
	 * @param hostname
	 *            the Host name
	 * @param port
	 *            The port number
	 * 
	 * @return This channel
	 * @throws AlreadyBoundException
	 *             If the socket is already bound
	 * @throws UnsupportedAddressTypeException
	 *             If the type of the given address is not supported
	 * @throws ClosedChannelException
	 *             If the channel is closed
	 * @throws IOException
	 *             If some other I/O error occurs
	 * @throws IllegalArgumentException
	 *             if the port parameter is outside the range of valid port
	 *             values, or if the hostname parameter is <TT>null</TT>.
	 * @throws SecurityException
	 *             If a security manager is installed and it denies an
	 *             unspecified permission. An implementation of this interface
	 *             should specify any required permissions.
	 * 
	 * @see #bind(SocketAddress)
	 */
	public NioChannel bind(String hostname, int port) throws IOException {
		return bind(new InetSocketAddress(hostname, port));
	}

	/**
	 * Reset the flag and the internal buffer
	 */
	public void reset() {
		this.flag = false;
		this.buffer.clear();
	}

	/**
	 * @return <tt>TRUE</tt> if the channel is secure (i.e., use SSL), else
	 *         <tt>FALSE</tt>
	 */
	public boolean isSecure() {
		return false;
	}

	/**
	 * Set the flag to true
	 */
	protected void setFlag() {
		this.flag = true;
	}

	/**
	 * @return the value of the <tt>flag</tt>
	 */
	public boolean flag() {
		return this.flag;
	}

	/**
	 * @return the internal buffer
	 */
	public ByteBuffer getBuffer() {
		return this.buffer;
	}

	/**
	 * @param buffer
	 */
	protected void setBuffer(ByteBuffer buffer) {
		this.buffer = buffer;
	}

	/**
	 * @return the channel id
	 */
	public long getId() {
		return this.id;
	}

	/**
	 * Getter for name
	 * 
	 * @return the name
	 */
	public String getName() {
		return this.name;
	}

	/**
	 * Returns the provider that created this channel.
	 * 
	 * @return the channel provider
	 */
	public final AsynchronousChannelProvider provider() {
		return this.channel.provider();
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

	/**
	 * Closes this channel.
	 * 
	 * <p>
	 * Any outstanding asynchronous operations upon this channel will complete
	 * with the exception {@link AsynchronousCloseException}. After a channel is
	 * closed, further attempts to initiate asynchronous I/O operations complete
	 * immediately with cause {@link ClosedChannelException}.
	 * </p>
	 * <p>
	 * This method otherwise behaves exactly as specified by the
	 * {@link java.nio.channels.Channel} interface.
	 * </p>
	 * 
	 * @throws IOException
	 *             If an I/O error occurs
	 */
	@Override
	public void close() throws IOException {
		this.channel.close();
	}

	/**
	 * Try to close this channel. If the channel is already closed or the
	 * {@code force} parameter is false, nothing will happen. This method have
	 * an impact only and only if the channel is open and the {@code force}
	 * parameter is <tt>true</tt>
	 * 
	 * @param force
	 *            a boolean value indicating if we need to force closing the
	 *            channel
	 * @throws IOException
	 */
	public void close(boolean force) throws IOException {
		if (isOpen() && force) {
			this.close();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.nio.channels.AsynchronousByteChannel#read(java.nio.ByteBuffer)
	 */
	@Deprecated
	@Override
	public Future<Integer> read(ByteBuffer dst) {
		return this.channel.read(dst);
	}

	/**
	 * Read a sequence of bytes in blocking mode
	 * 
	 * @param dst
	 *            the buffer containing the read bytes
	 * @return the number of bytes read
	 * @throws Exception
	 * @throws ExecutionException
	 * @throws InterruptedException
	 */
	public int readBytes(ByteBuffer dst) throws Exception {
		return read(dst).get();
	}

	/**
	 * Read a sequence of bytes in blocking mode
	 * 
	 * @param dst
	 *            the buffer containing the read bytes
	 * @param timeout
	 *            the read timeout
	 * @param unit
	 *            the timeout unit
	 * @return The number of bytes read
	 * @throws Exception
	 * @throws TimeoutException
	 *             if the read operation is timed out
	 * @throws ExecutionException
	 * @throws InterruptedException
	 */
	public int readBytes(ByteBuffer dst, long timeout, TimeUnit unit) throws Exception {
		return read(dst).get(timeout, unit);
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
	 * {@link NioChannel#read(ByteBuffer,Object,CompletionHandler)} method.
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

	/**
	 * <p>
	 * Wait for incoming data in a non-blocking mode. The received data will be
	 * stored by default in the internal buffer (By default, only one byte). The
	 * user should retrieve this byte first and complete the read operation.
	 * This method works like a listener for the incoming data on this channel.
	 * </p>
	 * <p>
	 * Note: The channel is reset (flag, buffer) before the read operation
	 * </p>
	 * 
	 * @param timeout
	 *            The maximum time for the I/O operation to complete
	 * @param unit
	 *            The time unit of the {@code timeout} argument
	 * @param attachment
	 *            The object to attach to the I/O operation; can be {@code null}
	 * @param handler
	 *            The handler for consuming the result
	 * @throws ReadPendingException
	 *             If a read operation is already in progress on this channel
	 * @throws NotYetConnectedException
	 *             If this channel is not yet connected
	 * @throws ShutdownChannelGroupException
	 *             If the channel group has terminated
	 */
	public <A> void awaitRead(long timeout, TimeUnit unit, final A attachment,
			final CompletionHandler<Integer, ? super A> handler) {

		System.out.println("**** " + this + ".awaitRead(...) ****");

		// reset the flag and the buffer
		reset();
		if (handler == null) {
			throw new NullPointerException("null handler parameter");
		}

		// Perform an asynchronous read operation using the internal buffer
		read(buffer, timeout, unit, attachment, new CompletionHandler<Integer, A>() {

			@Override
			public void completed(Integer result, A attach) {
				// Set the flag to true
				setFlag();
				handler.completed(result, attach);
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
	 * @see java.nio.channels.AsynchronousByteChannel#write(java.nio.ByteBuffer)
	 */
	@Deprecated
	@Override
	public Future<Integer> write(ByteBuffer src) {
		return this.channel.write(src);
	}

	/**
	 * Write a sequence of bytes in blocking mode
	 * 
	 * @param dst
	 *            the buffer containing the bytes to write
	 * @return the number of bytes written
	 * @throws Exception
	 * @throws ExecutionException
	 * @throws InterruptedException
	 */
	public int writeBytes(ByteBuffer dst) throws Exception {
		return write(dst).get();
	}

	/**
	 * Write a sequence of bytes in blocking mode
	 * 
	 * @param dst
	 *            the buffer containing the bytes to write
	 * @param timeout
	 *            the read timeout
	 * @param unit
	 *            the timeout unit
	 * @return The number of bytes written
	 * @throws Exception
	 * @throws TimeoutException
	 * @throws ExecutionException
	 * @throws InterruptedException
	 */
	public int writeBytes(ByteBuffer dst, long timeout, TimeUnit unit) throws Exception {
		return write(dst).get(timeout, unit);
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
	 * {@link NioChannel#write(ByteBuffer,Object,CompletionHandler)} method.
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
	 * @return This channel
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
	 * @return This channel
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
		return getName();
	}
}
