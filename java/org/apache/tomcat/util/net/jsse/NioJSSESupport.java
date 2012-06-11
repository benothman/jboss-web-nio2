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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

import javax.net.ssl.SSLSession;
import javax.security.cert.X509Certificate;

import org.apache.tomcat.util.net.SSLSupport;

/**
 * {@code NioJSSESupport}
 * 
 * Created on Jan 5, 2012 at 1:28:34 PM
 * 
 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
 */
class NioJSSESupport implements SSLSupport {

	private static org.jboss.logging.Logger log = org.jboss.logging.Logger
			.getLogger(NioJSSESupport.class);

	protected SecureNioChannel channel;
	protected SSLSession session;

	/**
	 * Create a new instance of {@code NioJSSESupport}
	 * 
	 * @param channel
	 */
	NioJSSESupport(SecureNioChannel channel) {
		this.channel = channel;
		this.session = channel.getSSLSession();
	}

	/**
	 * Create a new instance of {@code NioJSSESupport}
	 * 
	 * @param session
	 */
	NioJSSESupport(SSLSession session) {
		this.session = session;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.tomcat.util.net.SSLSupport#getCipherSuite()
	 */
	@Override
	public String getCipherSuite() throws IOException {
		// Look up the current SSLSession
		return this.session == null ? null : this.session.getCipherSuite();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.tomcat.util.net.SSLSupport#getPeerCertificateChain()
	 */
	@Override
	public Object[] getPeerCertificateChain() throws IOException {
		return getPeerCertificateChain(false);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.tomcat.util.net.SSLSupport#getPeerCertificateChain(boolean)
	 */
	@Override
	public Object[] getPeerCertificateChain(boolean force) throws IOException {
		// Look up the current SSLSession
		if (session == null) {
			return null;
		}

		// Convert JSSE's certificate format to the ones we need
		X509Certificate[] jsseCerts = null;
		try {
			jsseCerts = session.getPeerCertificateChain();
		} catch (Exception bex) {
			// ignore.
		}
		if (jsseCerts == null)
			jsseCerts = new X509Certificate[0];
		if (jsseCerts.length <= 0 && force) {
			session.invalidate();
			handShake();
			session = channel.getSSLSession();
		}
		return getX509Certificates(session);
	}

	/**
	 * 
	 * @param session
	 * @return
	 * @throws IOException
	 */
	protected java.security.cert.X509Certificate[] getX509Certificates(SSLSession session)
			throws IOException {
		Certificate[] certs = null;
		try {
			certs = session.getPeerCertificates();
		} catch (Throwable t) {
			log.debug("Error getting client certs", t);
			return null;
		}
		if (certs == null)
			return null;

		java.security.cert.X509Certificate[] x509Certs = new java.security.cert.X509Certificate[certs.length];
		for (int i = 0; i < certs.length; i++) {
			if (certs[i] instanceof java.security.cert.X509Certificate) {
				// always currently true with the JSSE 1.1.x
				x509Certs[i] = (java.security.cert.X509Certificate) certs[i];
			} else {
				try {
					byte[] buffer = certs[i].getEncoded();
					CertificateFactory cf = CertificateFactory.getInstance("X.509");
					ByteArrayInputStream stream = new ByteArrayInputStream(buffer);
					x509Certs[i] = (java.security.cert.X509Certificate) cf
							.generateCertificate(stream);
				} catch (Exception ex) {
					log.info("Error translating cert " + certs[i], ex);
					return null;
				}
			}
			if (log.isTraceEnabled())
				log.trace("Cert #" + i + " = " + x509Certs[i]);
		}
		if (x509Certs.length < 1)
			return null;
		return x509Certs;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.tomcat.util.net.SSLSupport#getKeySize()
	 */
	@Override
	public Integer getKeySize() throws IOException {
		// Look up the current SSLSession
		SSLSupport.CipherData c_aux[] = ciphers;
		if (session == null)
			return null;
		Integer keySize = (Integer) session.getValue(KEY_SIZE_KEY);
		if (keySize == null) {
			int size = 0;
			String cipherSuite = session.getCipherSuite();
			for (int i = 0; i < c_aux.length; i++) {
				if (cipherSuite.indexOf(c_aux[i].phrase) >= 0) {
					size = c_aux[i].keySize;
					break;
				}
			}
			keySize = new Integer(size);
			session.putValue(KEY_SIZE_KEY, keySize);
		}
		return keySize;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.tomcat.util.net.SSLSupport#getSessionId()
	 */
	@Override
	public String getSessionId() throws IOException {
		// Look up the current SSLSession
		if (session == null) {
			return null;
		}
		// Expose ssl_session (getId)
		byte[] ssl_session = session.getId();
		if (ssl_session == null) {
			return null;
		}
		StringBuilder buf = new StringBuilder("");
		for (int x = 0; x < ssl_session.length; x++) {
			String digit = Integer.toHexString((int) ssl_session[x]);
			if (digit.length() < 2) {
				buf.append('0');
			}
			if (digit.length() > 2) {
				digit = digit.substring(digit.length() - 2);
			}
			buf.append(digit);
		}
		return buf.toString();
	}

	/**
	 * 
	 * @throws IOException
	 */
	protected void handShake() throws IOException {
		if (channel != null && channel.handshakeComplete) {
			return;
		}

		if (channel != null) {
			channel.handshake();
		}
	}
}
