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
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;

/**
 * {@code DefaultNioServerSocketChannelFactory}
 * 
 * Created on Jan 3, 2012 at 12:12:02 PM
 * 
 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
 */
public class DefaultNioServerSocketChannelFactory extends NioServerSocketChannelFactory {

	/**
	 * Create a new instance of {@code DefaultNioServerSocketChannelFactory}
	 */
	public DefaultNioServerSocketChannelFactory() {
		super();
	}

	/**
	 * Create a new instance of {@code DefaultNioServerSocketChannelFactory}
	 * 
	 * @param threadGroup
	 */
	public DefaultNioServerSocketChannelFactory(AsynchronousChannelGroup threadGroup) {
		super(threadGroup);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.tomcat.util.net.NioServerSocketChannelFactory#init()
	 */
	@Override
	public void init() throws IOException {
		// NOOP
	}

	/*
	 * (non-Javadoc)
	 * @see org.apache.tomcat.util.net.NioServerSocketChannelFactory#destroy()
	 */
	public void destroy() throws IOException {
		this.threadGroup = null;
		this.attributes.clear();
		this.attributes = null;
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.tomcat.util.net.NioServerSocketChannelFactory#initChannel(
	 * org.apache.tomcat.util.net.NioChannel)
	 */
	public void initChannel(NioChannel channel) throws Exception {
		// NOOP
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.tomcat.util.net.NioServerSocketChannelFactory#handshake(org
	 * .apache.tomcat.util.net.NioChannel)
	 */
	@Override
	public void handshake(NioChannel channel) throws IOException {
		// NOOP
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.tomcat.util.net.NioServerSocketChannelFactory#createServerChannel
	 * (int)
	 */
	@Override
	public AsynchronousServerSocketChannel createServerChannel(int port) throws IOException {
		return open().bind(new InetSocketAddress(port));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.tomcat.util.net.NioServerSocketChannelFactory#createServerChannel
	 * (int, int)
	 */
	@Override
	public AsynchronousServerSocketChannel createServerChannel(int port, int backlog)
			throws IOException {
		return open().bind(new InetSocketAddress(port), backlog);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.tomcat.util.net.NioServerSocketChannelFactory#createServerChannel
	 * (int, int, java.net.InetAddress)
	 */
	@Override
	public AsynchronousServerSocketChannel createServerChannel(int port, int backlog,
			InetAddress ifAddress) throws IOException {
		return open().bind(new InetSocketAddress(ifAddress, port), backlog);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.tomcat.util.net.NioServerSocketChannelFactory#acceptChannel
	 * (java.nio.channels.AsynchronousServerSocketChannel)
	 */
	@Override
	public NioChannel acceptChannel(AsynchronousServerSocketChannel listener) throws IOException {
		try {
			return new NioChannel(listener.accept().get());
		} catch (Exception e) {
			throw new IOException(e);
		}
	}
}
