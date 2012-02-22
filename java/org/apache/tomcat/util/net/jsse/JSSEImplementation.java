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

package org.apache.tomcat.util.net.jsse;

import java.net.Socket;

import org.apache.tomcat.util.net.NioChannel;
import org.apache.tomcat.util.net.SSLImplementation;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.ServerSocketFactory;
import javax.net.ssl.SSLSession;

/**
 * {@code JSSEImplementation}
 * <p>
 * Concrete implementation class for JSSE
 * </p>
 * 
 * 
 * Created on Feb 22, 2012 at 12:53:14 PM
 * 
 * @author EKR & <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
 */
public class JSSEImplementation extends SSLImplementation {
	static final String SSLSocketClass = "javax.net.ssl.SSLSocket";

	static org.jboss.logging.Logger logger = org.jboss.logging.Logger
			.getLogger(JSSEImplementation.class);

	private JSSEFactory factory = null;

	/**
	 * Create a new instance of {@code JSSEImplementation}
	 * 
	 * @throws ClassNotFoundException
	 */
	public JSSEImplementation() throws ClassNotFoundException {
		// Check to see if JSSE is floating around somewhere
		Class.forName(SSLSocketClass);
		factory = new JSSEFactory();
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
	 * org.apache.tomcat.util.net.SSLImplementation#getServerSocketFactory()
	 */
	public ServerSocketFactory getServerSocketFactory() {
		ServerSocketFactory ssf = factory.getSocketFactory();
		return ssf;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.tomcat.util.net.SSLImplementation#getSSLSupport(java.net.Socket
	 * )
	 */
	public SSLSupport getSSLSupport(Socket s) {
		SSLSupport ssls = factory.getSSLSupport(s);
		return ssls;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.tomcat.util.net.SSLImplementation#getSSLSupport(javax.net.
	 * ssl.SSLSession)
	 */
	public SSLSupport getSSLSupport(SSLSession session) {
		SSLSupport ssls = factory.getSSLSupport(session);
		return ssls;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.tomcat.util.net.SSLImplementation#getServerSocketChannelFactory
	 * ()
	 */
	@Override
	public NioJSSESocketChannelFactory getServerSocketChannelFactory() {
		throw new RuntimeException("Not supported for class " + getClass().getName());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.tomcat.util.net.SSLImplementation#getSSLSupport(org.apache
	 * .tomcat.util.net.NioChannel)
	 */
	@Override
	public SSLSupport getSSLSupport(NioChannel channel) {
		throw new RuntimeException("Not supported for class " + getClass().getName());
	}

}
