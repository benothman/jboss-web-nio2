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
package org.apache.tomcat.util.net;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.util.Hashtable;

import org.apache.tomcat.util.net.jsse.NioJSSESocketChannelFactory;

/**
 * {@code NioServerSocketChannelFactory}
 * 
 * Created on Jan 3, 2012 at 12:08:30 PM
 * 
 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
 */
public abstract class NioServerSocketChannelFactory implements Cloneable {

	protected static org.jboss.logging.Logger log = org.jboss.logging.Logger
			.getLogger(NioServerSocketChannelFactory.class);

	private static NioServerSocketChannelFactory theFactory;
	protected Hashtable<String, Object> attributes = new Hashtable<String, Object>();

	protected AsynchronousChannelGroup threadGroup;

	/**
	 * Create a new instance of {@code NioServerSocketChannelFactory}
	 */
	protected NioServerSocketChannelFactory() {
		/* NOTHING */
	}

	/**
	 * Create a new instance of {@code NioServerSocketChannelFactory}
	 * 
	 * @param threadGroup
	 */
	protected NioServerSocketChannelFactory(AsynchronousChannelGroup threadGroup) {
		this.threadGroup = threadGroup;
	}

	/**
	 * Initialize the factory
	 * 
	 * @throws IOException
	 */
	public abstract void init() throws IOException;

	/**
	 * Destroy the factory
	 * 
	 * @throws IOException
	 */
	public abstract void destroy() throws IOException;

	/**
	 * General mechanism to pass attributes from the ServerConnector to the
	 * socket factory.
	 * 
	 * Note that the "preferred" mechanism is to use bean setters and explicit
	 * methods, but this allows easy configuration via server.xml or simple
	 * Properties
	 * 
	 * @param name
	 * @param value
	 */
	public void setAttribute(String name, Object value) {
		if (name != null && value != null) {
			attributes.put(name, value);
		}
	}

	/**
	 * Returns a copy of the environment's default socket factory.
	 * 
	 * @return the default factory
	 */
	public static synchronized NioServerSocketChannelFactory getDefault() {
		return getDefault(null);
	}

	/**
	 * Returns a copy of the environment's default socket factory.
	 * 
	 * @param threadGroup
	 * @return the default factory
	 */
	public static synchronized NioServerSocketChannelFactory getDefault(
			AsynchronousChannelGroup threadGroup) {
		//
		// optimize typical case: no synch needed
		//

		if (theFactory == null) {
			//
			// Different implementations of this method could
			// work rather differently. For example, driving
			// this from a system property, or using a different
			// implementation than JavaSoft's.
			//

			theFactory = new DefaultNioServerSocketChannelFactory(threadGroup);
		}

		try {
			return (NioServerSocketChannelFactory) theFactory.clone();
		} catch (CloneNotSupportedException e) {
			throw new RuntimeException(e.getMessage());
		}
	}

	/**
	 * Create and configure a new secure {@code NioServerSocketChannelFactory}
	 * instance
	 * 
	 * @param threadGroup
	 *            the thread group that will be used with the server sockets
	 * @return a new secure {@code NioServerSocketChannelFactory} instance
	 */
	public static synchronized NioServerSocketChannelFactory createSecureFactory(
			AsynchronousChannelGroup threadGroup) {
		return new NioJSSESocketChannelFactory(threadGroup);
	}

	/**
	 * Create a new {@code NioServerSocketChannelFactory} instance
	 * 
	 * @param threadGroup
	 *            the thread group that will be used with the server sockets
	 * @param secure
	 * @return a new secure {@code NioServerSocketChannelFactory} instance
	 */
	public static synchronized NioServerSocketChannelFactory createServerSocketChannelFactory(
			AsynchronousChannelGroup threadGroup, boolean secure) {
		return secure ? createSecureFactory(threadGroup) : getDefault(threadGroup);
	}

	/**
	 * Returns a server socket channel which uses all network interfaces on the
	 * host, and is bound to a the specified port. The socket is configured with
	 * the socket options (such as accept timeout) given to this factory.
	 * 
	 * @param port
	 *            the port to listen to
	 * @return an instance of
	 *         {@link java.nio.channels.AsynchronousServerSocketChannel}
	 * @exception IOException
	 *                for networking errors
	 */
	public AsynchronousServerSocketChannel createServerChannel(int port) throws IOException {
		return createServerChannel(port, -1);
	}

	/**
	 * Returns a server socket which uses all network interfaces on the host, is
	 * bound to a the specified port, and uses the specified connection backlog.
	 * The socket is configured with the socket options (such as accept timeout)
	 * given to this factory.
	 * 
	 * @param port
	 *            the port to listen to
	 * @param backlog
	 *            how many connections are queued
	 * @return an instance of
	 *         {@link java.nio.channels.AsynchronousServerSocketChannel}
	 * @exception IOException
	 *                for networking errors
	 */
	public AsynchronousServerSocketChannel createServerChannel(int port, int backlog)
			throws IOException {
		return createServerChannel(port, backlog, null, false);
	}

	/**
	 * Returns a server socket channel which uses only the specified network
	 * interface on the local host, is bound to a the specified port, and uses
	 * the specified connection backlog. The socket is configured with the
	 * socket options (such as accept timeout) given to this factory.
	 * 
	 * @param port
	 *            the port to listen to
	 * @param backlog
	 *            how many connections are queued
	 * @param ifAddress
	 *            the network interface address to use
	 * @param reuseAddress
	 * @return an instance of
	 *         {@link java.nio.channels.AsynchronousServerSocketChannel}
	 * @exception IOException
	 *                for networking errors
	 */
	public abstract AsynchronousServerSocketChannel createServerChannel(int port, int backlog,
			InetAddress ifAddress, boolean reuseAddress) throws IOException;

	/**
	 * Initialize the specified {@code NioChannel}
	 * 
	 * @param channel
	 *            The channel to be initialized
	 * @throws Exception
	 */
	public abstract void initChannel(NioChannel channel) throws Exception;

	/**
	 * Wrapper function for accept(). This allows us to trap and translate
	 * exceptions if necessary
	 * 
	 * @param listener
	 *            The Asynchronous Server Socket channel that will accept a new
	 *            connection
	 * @return an instance of {@link NioChannel} representing the new connection
	 * 
	 * @exception IOException
	 */
	public abstract NioChannel acceptChannel(AsynchronousServerSocketChannel listener)
			throws IOException;

	/**
	 * Extra function to initiate the handshake. Sometimes necessary for SSL
	 * 
	 * @param channel
	 * 
	 * @exception IOException
	 */
	public abstract void handshake(NioChannel channel) throws IOException;

	/**
	 * Open an {@link java.nio.channels.AsynchronousServerSocketChannel}
	 * 
	 * @return an instance of
	 *         {@link java.nio.channels.AsynchronousServerSocketChannel}
	 * @throws IOException
	 */
	protected AsynchronousServerSocketChannel open() throws IOException {
		return AsynchronousServerSocketChannel.open(threadGroup);
	}
}
