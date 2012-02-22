/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.apache.tomcat.util.net;

import java.net.Socket;
import javax.net.ssl.SSLSession;

import org.apache.tomcat.util.net.jsse.NioJSSESocketChannelFactory;

/**
 * {@code SSLImplementation}
 * <p>
 * Abstract factory and base class for all SSL implementations.
 * </p>
 * 
 * 
 * Created on Feb 22, 2012 at 12:55:17 PM
 * 
 * @author EKR & <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
 */
abstract public class SSLImplementation {
	private static org.jboss.logging.Logger logger = org.jboss.logging.Logger
			.getLogger(SSLImplementation.class);

	// The default implementations in our search path
	private static final String JSSEImplementationClass = "org.apache.tomcat.util.net.jsse.JSSEImplementation";

	private static final String[] implementations = { JSSEImplementationClass,
			"org.apache.tomcat.util.net.jsse.NioJSSEImplementation" };

	/**
	 * @return the default implementation of {@code SSLImplementation}
	 * @throws ClassNotFoundException
	 */
	public static SSLImplementation getInstance() throws ClassNotFoundException {
		for (int i = 0; i < implementations.length; i++) {
			try {
				SSLImplementation impl = getInstance(implementations[i]);
				return impl;
			} catch (Exception e) {
				if (logger.isTraceEnabled())
					logger.trace("Error creating " + implementations[i], e);
			}
		}

		// If we can't instantiate any of these
		throw new ClassNotFoundException("Can't find any SSL implementation");
	}

	/**
	 * Returns the {@code SSLImplementation} specified by the name of it's class
	 * 
	 * @param className
	 * @return a new instance of the {@code SSLImplementation} given by it's name
	 * @throws ClassNotFoundException
	 */
	public static SSLImplementation getInstance(String className) throws ClassNotFoundException {
		if (className == null)
			return getInstance();

		try {
			// Workaround for the J2SE 1.4.x classloading problem (under
			// Solaris).
			// Class.forName(..) fails without creating class using new.
			// This is an ugly workaround.
			if (JSSEImplementationClass.equals(className)) {
				return new org.apache.tomcat.util.net.jsse.JSSEImplementation();
			}
			Class<?> clazz = Class.forName(className);
			return (SSLImplementation) clazz.newInstance();
		} catch (Exception e) {
			if (logger.isDebugEnabled())
				logger.debug("Error loading SSL Implementation " + className, e);
			throw new ClassNotFoundException("Error loading SSL Implementation " + className + " :"
					+ e.toString());
		}
	}

	/**
	 * @return the implementation name
	 */
	abstract public String getImplementationName();

	/**
	 * @return a new instance of {@link ServerSocketFactory} 
	 */
	abstract public ServerSocketFactory getServerSocketFactory();

	/**
	 * 
	 * @return a new instance of {@link NioJSSESocketChannelFactory}
	 */
	public abstract NioJSSESocketChannelFactory getServerSocketChannelFactory();

	/**
	 * Return a {@link SSLSupport} attached to the socket
	 * @param sock
	 * @return a {@link SSLSupport} attached to the socket
	 */
	abstract public SSLSupport getSSLSupport(Socket sock);

	/**
	 * @param channel
	 * @return the {@link SSLSupport} attached to this channel
	 */
	public abstract SSLSupport getSSLSupport(NioChannel channel);
	
	/**
	 * @param session
	 * @return the {@link SSLSupport} attached to this session
	 */
	abstract public SSLSupport getSSLSupport(SSLSession session);
}
