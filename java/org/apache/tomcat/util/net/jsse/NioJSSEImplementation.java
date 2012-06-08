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

import java.net.Socket;

import javax.net.ssl.SSLSession;

import org.apache.tomcat.util.net.NioChannel;
import org.apache.tomcat.util.net.SSLImplementation;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.ServerSocketFactory;

/**
 * {@code NioJSSEImplementation}
 * 
 * Created on Feb 22, 2012 at 12:41:08 PM
 * 
 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
 */
public class NioJSSEImplementation extends SSLImplementation {

	static final String SSLClass = "javax.net.ssl.SSLEngine";

	static org.jboss.logging.Logger logger = org.jboss.logging.Logger
			.getLogger(JSSEImplementation.class);

	private NioJSSEFactory factory = null;

	/**
	 * Create a new instance of {@code NioJSSEImplementation}
	 * 
	 * @throws ClassNotFoundException
	 */
	public NioJSSEImplementation() throws ClassNotFoundException {
		// Check to see if JSSE is floating around somewhere
		Class.forName(SSLClass);
		factory = new NioJSSEFactory();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.tomcat.util.net.SSLImplementation#getImplementationName()
	 */
	public String getImplementationName() {
		return "JSSE";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.tomcat.util.net.SSLImplementation#getServerSocketChannelFactory
	 * ()
	 */
	public NioJSSESocketChannelFactory getServerSocketChannelFactory() {
		NioJSSESocketChannelFactory ssf = factory.getSocketChannelFactory();
		return ssf;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.tomcat.util.net.SSLImplementation#getSSLSupport(org.apache
	 * .tomcat.util.net.NioChannel)
	 */
	public SSLSupport getSSLSupport(NioChannel channel) {
		return factory.getSSLSupport(channel);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.tomcat.util.net.SSLImplementation#getSSLSupport(javax.net.
	 * ssl.SSLSession)
	 */
	public SSLSupport getSSLSupport(SSLSession session) {
		return factory.getSSLSupport(session);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.tomcat.util.net.SSLImplementation#getServerSocketFactory()
	 */
	@Override
	public ServerSocketFactory getServerSocketFactory() {
		throw new RuntimeException("Not supported for class " + ServerSocketFactory.class.getName());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.tomcat.util.net.SSLImplementation#getSSLSupport(java.net.Socket
	 * )
	 */
	@Override
	public SSLSupport getSSLSupport(Socket sock) {
		throw new RuntimeException("Not supported for class " + Socket.class.getName());
	}

}
