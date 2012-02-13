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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CRL;
import java.security.cert.CRLException;
import java.security.cert.CertPathParameters;
import java.security.cert.CertStore;
import java.security.cert.CertStoreParameters;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.X509CertSelector;
import java.util.Collection;
import java.util.Locale;
import java.util.Vector;

import javax.net.ssl.CertPathTrustManagerParameters;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509KeyManager;

import org.apache.tomcat.util.net.DefaultNioServerSocketChannelFactory;
import org.apache.tomcat.util.net.NioChannel;
import org.apache.tomcat.util.res.StringManager;

/**
 * {@code NioJSSESocketChannelFactory}
 * 
 * <p>
 * SSL server socket factory. It _requires_ a valid RSA key and JSSE.
 * </p>
 * <p>
 * 1. Make the JSSE's jars available, either as an installed extension (copy
 * them into jre/lib/ext) or by adding them to the Tomcat classpath. 2. keytool
 * -genkey -alias tomcat -keyalg RSA Use "changeit" as password ( this is the
 * default we use )
 * </p>
 * 
 * Created on Jan 3, 2012 at 2:07:00 PM
 * 
 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
 */
public class NioJSSESocketChannelFactory extends DefaultNioServerSocketChannelFactory {

	private static StringManager sm = StringManager
			.getManager("org.apache.tomcat.util.net.jsse.res");
	private static final boolean RFC_5746_SUPPORTED;
	// defaults
	private static final String defaultProtocol = "TLS";
	static boolean defaultClientAuth = false;
	static String defaultKeystoreType = "JKS";
	private static final String defaultKeystoreFile = System.getProperty("user.home")
			+ "/.keystore";
	private static final String defaultKeyPass = "changeit";
	private static final int defaultSessionCacheSize = 0;
	private static final int defaultSessionTimeout = 86400;

	static org.jboss.logging.Logger log = org.jboss.logging.Logger
			.getLogger(NioJSSESocketChannelFactory.class);

	// private static SSLContext context;
	static {
		boolean result = false;
		try {
			SSLContext context = SSLContext.getInstance(defaultProtocol);
			context.init(null, null, new SecureRandom());
			SSLServerSocketFactory ssf = context.getServerSocketFactory();
			String ciphers[] = ssf.getSupportedCipherSuites();
			for (String cipher : ciphers) {
				if ("TLS_EMPTY_RENEGOTIATION_INFO_SCSV".equals(cipher)) {
					result = true;
					break;
				}
			}
		} catch (NoSuchAlgorithmException e) {
			// Assume no RFC 5746 support
		} catch (KeyManagementException e) {
			// Assume no RFC 5746 support
		}
		RFC_5746_SUPPORTED = result;
	}

	protected boolean initialized;
	private SSLContext sslContext;
	protected String clientAuth = "false";
	protected String[] enabledCiphers;
	protected boolean allowUnsafeLegacyRenegotiation = false;

	/**
	 * Flag to state that we require client authentication.
	 */
	protected boolean requireClientAuth = false;

	/**
	 * Flag to state that we would like client authentication.
	 */
	protected boolean wantClientAuth = false;

	/**
	 * Create a new instance of {@code NioJSSESocketChannelFactory}
	 */
	public NioJSSESocketChannelFactory() {
		super();
	}

	/**
	 * Create a new instance of {@code NioJSSESocketChannelFactory}
	 * 
	 * @param threadGroup
	 */
	public NioJSSESocketChannelFactory(AsynchronousChannelGroup threadGroup) {
		super(threadGroup);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.tomcat.util.net.DefaultNioServerSocketChannelFactory#acceptChannel
	 * (java.nio.channels.AsynchronousServerSocketChannel)
	 */
	@Override
	public NioChannel acceptChannel(AsynchronousServerSocketChannel listener) throws IOException {
		try {
			AsynchronousSocketChannel asyncChannel = listener.accept().get();
			InetSocketAddress addr = (InetSocketAddress) asyncChannel.getRemoteAddress();
			SSLEngine engine = sslContext.createSSLEngine(addr.getHostString(), addr.getPort());
			NioChannel channel = new SecureNioChannel(asyncChannel, engine);
			
			return channel;
		} catch (Exception e) {
			throw new IOException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.tomcat.util.net.NioServerSocketChannelFactory#initChannel(
	 * org.apache.tomcat.util.net.NioChannel)
	 */
	public void initChannel(NioChannel channel) throws Exception {
		SecureNioChannel sslChannel = (SecureNioChannel) channel;
		initSSLEngine(sslChannel.getSslEngine());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.tomcat.util.net.NioServerSocketChannelFactory#handshake(java
	 * .nio.channels.AsynchronousSocketChannel)
	 */
	@Override
	public void handshake(NioChannel channel) throws IOException {
		// We do getSession instead of startHandshake() so we can call this
		// multiple times
		SecureNioChannel sslChannel = (SecureNioChannel) channel;

		if (sslChannel.handshakeDone()) {
			// The handshake was already done
			return;
		}

		SSLEngine engine = sslChannel.getSslEngine();
		SSLSession session = sslChannel.getSSLSession();

		if (!allowUnsafeLegacyRenegotiation && !RFC_5746_SUPPORTED) {
			// Prevent further handshakes by removing all cipher suites
			engine.setEnabledCipherSuites(new String[0]);
		}
		sslChannel.handshake();

		if (session.getCipherSuite().equals("SSL_NULL_WITH_NULL_NULL"))
			throw new IOException(
					"SSL handshake failed. Ciper suite in SSL Session is SSL_NULL_WITH_NULL_NULL");
	}

	/**
	 * 
	 * @param ctx
	 */
	public void setSslContext(SSLContext ctx) {
		sslContext = ctx;
	}

	/**
	 * @return the SSLContext
	 */
	public SSLContext getSslContext() {
		return sslContext;
	}

	/**
	 * Reads the keystore and initializes the SSL socket factory.
	 * 
	 * @throws IOException
	 */
	@Override
	public void init() throws IOException {
		try {
			String clientAuthStr = (String) attributes.get("clientauth");
			if ("true".equalsIgnoreCase(clientAuthStr) || "yes".equalsIgnoreCase(clientAuthStr)) {
				requireClientAuth = true;
			} else if ("want".equalsIgnoreCase(clientAuthStr)) {
				wantClientAuth = true;
			}

			// SSL protocol variant (e.g., TLS, SSL v3, etc.)
			String protocol = (String) attributes.get("protocol");
			if (protocol == null) {
				protocol = defaultProtocol;
			}

			// Certificate encoding algorithm (e.g., SunX509)
			String algorithm = (String) attributes.get("algorithm");
			if (algorithm == null) {
				algorithm = KeyManagerFactory.getDefaultAlgorithm();
			}

			String keystoreType = (String) attributes.get("keystoreType");
			if (keystoreType == null) {
				keystoreType = defaultKeystoreType;
			}

			String keystoreProvider = (String) attributes.get("keystoreProvider");
			String trustAlgorithm = (String) attributes.get("truststoreAlgorithm");

			if (trustAlgorithm == null) {
				trustAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
			}

			/*
			 * log.info("SSL Parameters [Protocol :" + protocol +
			 * ", algorithm : " + algorithm + ", keystoreType : " + keystoreType
			 * + ", keystoreProvider : " + keystoreProvider +
			 * ", trustAlgorithm: " + trustAlgorithm + "]");
			 * 
			 * log.info("Key Managers --> " + getKeyManagers(keystoreType,
			 * keystoreProvider, algorithm, (String)
			 * attributes.get("keyAlias")));
			 * 
			 * log.info("Trust Managers --> " + getTrustManagers(keystoreType,
			 * keystoreProvider, trustAlgorithm));
			 */

			// Create and init SSLContext
			sslContext = (SSLContext) attributes.get("SSLContext");
			if (sslContext == null) {
				sslContext = SSLContext.getInstance(protocol);
				sslContext.init(
						getKeyManagers(keystoreType, keystoreProvider, algorithm,
								(String) attributes.get("keyAlias")),
						getTrustManagers(keystoreType, keystoreProvider, trustAlgorithm),
						new SecureRandom());
			}

			// Configure SSL session cache
			int sessionCacheSize;
			if (attributes.get("sessionCacheSize") != null) {
				sessionCacheSize = Integer.parseInt((String) attributes.get("sessionCacheSize"));
			} else {
				sessionCacheSize = defaultSessionCacheSize;
			}
			int sessionCacheTimeout;
			if (attributes.get("sessionCacheTimeout") != null) {
				sessionCacheTimeout = Integer.parseInt((String) attributes
						.get("sessionCacheTimeout"));
			} else {
				sessionCacheTimeout = defaultSessionTimeout;
			}
			SSLSessionContext sessionContext = sslContext.getServerSessionContext();
			if (sessionContext != null) {
				sessionContext.setSessionCacheSize(sessionCacheSize);
				sessionContext.setSessionTimeout(sessionCacheTimeout);
			}

			// create proxy
			SSLServerSocketFactory sslProxy = sslContext.getServerSocketFactory();

			// Determine which cipher suites to enable
			String requestedCiphers = (String) attributes.get("ciphers");
			// log.info("Requested Ciphers --> " + requestedCiphers);
			enabledCiphers = getEnabledCiphers(requestedCiphers,
					sslProxy.getSupportedCipherSuites());

			/*
			 * String tmp = ""; for (String s : enabledCiphers) { tmp += s +
			 * ", "; }
			 * 
			 * log.info("Enabled Ciphers --> " + tmp);
			 */
			allowUnsafeLegacyRenegotiation = "true".equals(attributes
					.get("allowUnsafeLegacyRenegotiation"));

			// Check that the SSL configuration is OK
			checkConfig();

		} catch (Exception e) {
			throw new IOException(e.getMessage(), e);
		}
	}

	/**
	 * Determines the SSL cipher suites to be enabled.
	 * 
	 * @param requestedCiphers
	 *            Comma-separated list of requested ciphers
	 * 
	 * @param supportedCiphers
	 *            Array of supported ciphers
	 * 
	 * @return Array of SSL cipher suites to be enabled, or null if none of the
	 *         requested ciphers are supported
	 */
	protected String[] getEnabledCiphers(String requestedCiphers, String[] supportedCiphers) {

		String[] enabledCiphers = null;
		SSLServerSocketFactory sslProxy = sslContext.getServerSocketFactory();
		if (requestedCiphers != null) {
			Vector<Object> vec = null;
			String cipher = requestedCiphers;
			int index = requestedCiphers.indexOf(',');
			if (index != -1) {
				int fromIndex = 0;
				while (index != -1) {
					cipher = requestedCiphers.substring(fromIndex, index).trim();
					if (cipher.length() > 0) {
						/*
						 * Check to see if the requested cipher is among the
						 * supported ciphers, i.e., may be enabled
						 */
						for (int i = 0; supportedCiphers != null && i < supportedCiphers.length; i++) {
							if (supportedCiphers[i].equals(cipher)) {
								if (vec == null) {
									vec = new Vector<Object>();
								}
								vec.addElement(cipher);
								break;
							}
						}
					}
					fromIndex = index + 1;
					index = requestedCiphers.indexOf(',', fromIndex);
				} // while
				cipher = requestedCiphers.substring(fromIndex);
			}

			if (cipher != null) {
				cipher = cipher.trim();
				if (cipher.length() > 0) {
					/*
					 * Check to see if the requested cipher is among the
					 * supported ciphers, i.e., may be enabled
					 */
					for (int i = 0; supportedCiphers != null && i < supportedCiphers.length; i++) {
						if (supportedCiphers[i].equals(cipher)) {
							if (vec == null) {
								vec = new Vector<Object>();
							}
							vec.addElement(cipher);
							break;
						}
					}
				}
			}

			if (vec != null) {
				enabledCiphers = new String[vec.size()];
				vec.copyInto(enabledCiphers);
			}
		} else {
			enabledCiphers = sslProxy.getDefaultCipherSuites();
		}

		return enabledCiphers;
	}

	/**
	 * Gets the SSL server's keystore.
	 * 
	 * @param type
	 *            the type of the keystore
	 * @param provider
	 *            the keystore provider
	 * @param pass
	 *            the keystore password
	 * @return the SSL server's keystore
	 * @throws IOException
	 */
	protected KeyStore getKeystore(String type, String provider, String pass) throws IOException {

		String keystoreFile = (String) attributes.get("keystore");
		if (keystoreFile == null)
			keystoreFile = defaultKeystoreFile;

		return getStore(type, provider, keystoreFile, pass);
	}

	/**
	 * Gets the SSL server's keystore password.
	 * 
	 * @return the keystore password
	 */
	protected String getKeystorePassword() {
		String keyPass = (String) attributes.get("keypass");
		if (keyPass == null) {
			keyPass = defaultKeyPass;
		}
		String keystorePass = (String) attributes.get("keystorePass");
		if (keystorePass == null) {
			keystorePass = keyPass;
		}
		return keystorePass;
	}

	/**
	 * Gets the key- or truststore with the specified type, path, and password.
	 * 
	 * @param type
	 * @param provider
	 * @param path
	 * @param pass
	 * @return
	 * @throws IOException
	 */
	private KeyStore getStore(String type, String provider, String path, String pass)
			throws IOException {

		KeyStore ks = null;
		InputStream istream = null;
		try {
			if (provider == null) {
				ks = KeyStore.getInstance(type);
			} else {
				ks = KeyStore.getInstance(type, provider);
			}
			if (!("PKCS11".equalsIgnoreCase(type) || "".equalsIgnoreCase(path))) {
				File keyStoreFile = new File(path);
				if (!keyStoreFile.isAbsolute()) {
					keyStoreFile = new File(System.getProperty("catalina.base"), path);
				}
				istream = new FileInputStream(keyStoreFile);
			}

			char[] storePass = null;
			if (pass != null) {
				storePass = pass.toCharArray();
			}
			ks.load(istream, storePass);
		} catch (FileNotFoundException fnfe) {
			log.error(sm.getString("jsse.keystore_load_failed", type, path, fnfe.getMessage()),
					fnfe);
			throw fnfe;
		} catch (IOException ioe) {
			log.error(sm.getString("jsse.keystore_load_failed", type, path, ioe.getMessage()), ioe);
			throw ioe;
		} catch (Exception ex) {
			String msg = sm.getString("jsse.keystore_load_failed", type, path, ex.getMessage());
			log.error(msg, ex);
			throw new IOException(msg);
		} finally {
			if (istream != null) {
				try {
					istream.close();
				} catch (IOException ioe) {
					// Do nothing
				}
			}
		}

		return ks;
	}

	/**
	 * Gets the initialized key managers.
	 * 
	 * @param keystoreType
	 * @param keystoreProvider
	 * @param algorithm
	 * @param keyAlias
	 * @return
	 * @throws Exception
	 */
	protected KeyManager[] getKeyManagers(String keystoreType, String keystoreProvider,
			String algorithm, String keyAlias) throws Exception {

		KeyManager[] kms = null;

		String keystorePass = getKeystorePassword();

		KeyStore ks = getKeystore(keystoreType, keystoreProvider, keystorePass);
		if (keyAlias != null && !ks.isKeyEntry(keyAlias)) {
			throw new IOException(sm.getString("jsse.alias_no_key_entry", keyAlias));
		}

		KeyManagerFactory kmf = KeyManagerFactory.getInstance(algorithm);
		kmf.init(ks, keystorePass.toCharArray());

		kms = kmf.getKeyManagers();
		if (keyAlias != null) {
			if (JSSESocketFactory.defaultKeystoreType.equals(keystoreType)) {
				keyAlias = keyAlias.toLowerCase(Locale.ENGLISH);
			}
			for (int i = 0; i < kms.length; i++) {
				kms[i] = new JSSEKeyManager((X509KeyManager) kms[i], keyAlias);
			}
		}

		return kms;
	}

	/**
	 * Gets the initialized trust managers.
	 * 
	 * @param keystoreType
	 * @param keystoreProvider
	 * @param algorithm
	 * @return
	 * @throws Exception
	 */
	protected TrustManager[] getTrustManagers(String keystoreType, String keystoreProvider,
			String algorithm) throws Exception {
		String crlf = (String) attributes.get("crlFile");

		TrustManager[] tms = null;

		KeyStore trustStore = getTrustStore(keystoreType, keystoreProvider);
		if (trustStore != null) {
			if (crlf == null) {
				TrustManagerFactory tmf = TrustManagerFactory.getInstance(algorithm);
				tmf.init(trustStore);
				tms = tmf.getTrustManagers();
			} else {
				TrustManagerFactory tmf = TrustManagerFactory.getInstance(algorithm);
				CertPathParameters params = getParameters(algorithm, crlf, trustStore);
				ManagerFactoryParameters mfp = new CertPathTrustManagerParameters(params);
				tmf.init(mfp);
				tms = tmf.getTrustManagers();
			}
		}

		return tms;
	}

	/**
	 * Gets the SSL server's truststore.
	 * 
	 * @param keystoreType
	 * @param keystoreProvider
	 * @return the SSL server's truststore.
	 * @throws IOException
	 */
	protected KeyStore getTrustStore(String keystoreType, String keystoreProvider)
			throws IOException {
		KeyStore trustStore = null;

		String truststoreFile = (String) attributes.get("truststoreFile");
		if (truststoreFile == null) {
			truststoreFile = System.getProperty("javax.net.ssl.trustStore");
		}
		if (log.isDebugEnabled()) {
			log.debug("Truststore = " + truststoreFile);
		}
		String truststorePassword = (String) attributes.get("truststorePass");
		if (truststorePassword == null) {
			truststorePassword = System.getProperty("javax.net.ssl.trustStorePassword");
		}
		if (log.isDebugEnabled()) {
			log.debug("TrustPass = " + truststorePassword);
		}
		String truststoreType = (String) attributes.get("truststoreType");
		if (truststoreType == null) {
			truststoreType = System.getProperty("javax.net.ssl.trustStoreType");
		}
		if (truststoreType == null) {
			truststoreType = keystoreType;
		}
		if (log.isDebugEnabled()) {
			log.debug("trustType = " + truststoreType);
		}
		String truststoreProvider = (String) attributes.get("truststoreProvider");
		if (truststoreProvider == null) {
			truststoreProvider = System.getProperty("javax.net.ssl.trustStoreProvider");
		}
		if (truststoreProvider == null) {
			truststoreProvider = keystoreProvider;
		}
		if (log.isDebugEnabled()) {
			log.debug("trustProvider = " + truststoreProvider);
		}

		if (truststoreFile != null) {
			trustStore = getStore(truststoreType, truststoreProvider, truststoreFile,
					truststorePassword);
		}

		return trustStore;
	}

	/**
	 * Return the initialization parameters for the TrustManager. Currently,
	 * only the default <code>PKIX</code> is supported.
	 * 
	 * @param algorithm
	 *            The algorithm to get parameters for.
	 * @param crlf
	 *            The path to the CRL file.
	 * @param trustStore
	 *            The configured TrustStore.
	 * @return The parameters including the CRLs and TrustStore.
	 */
	protected CertPathParameters getParameters(String algorithm, String crlf, KeyStore trustStore)
			throws Exception {
		CertPathParameters params = null;
		if ("PKIX".equalsIgnoreCase(algorithm)) {
			PKIXBuilderParameters xparams = new PKIXBuilderParameters(trustStore,
					new X509CertSelector());
			Collection<?> crls = getCRLs(crlf);
			CertStoreParameters csp = new CollectionCertStoreParameters(crls);
			CertStore store = CertStore.getInstance("Collection", csp);
			xparams.addCertStore(store);
			xparams.setRevocationEnabled(true);
			String trustLength = (String) attributes.get("trustMaxCertLength");
			if (trustLength != null) {
				try {
					xparams.setMaxPathLength(Integer.parseInt(trustLength));
				} catch (Exception ex) {
					log.warn("Bad maxCertLength: " + trustLength);
				}
			}

			params = xparams;
		} else {
			throw new CRLException("CRLs not supported for type: " + algorithm);
		}
		return params;
	}

	/**
	 * Load the collection of CRLs.
	 * 
	 * @param crlf
	 * @return a collection of {@code java.security.cert.CRL}
	 * @throws IOException
	 * @throws CRLException
	 * @throws CertificateException
	 */
	protected Collection<? extends CRL> getCRLs(String crlf) throws IOException, CRLException,
			CertificateException {

		File crlFile = new File(crlf);
		if (!crlFile.isAbsolute()) {
			crlFile = new File(System.getProperty("catalina.base"), crlf);
		}
		Collection<? extends CRL> crls = null;
		InputStream is = null;
		try {
			CertificateFactory cf = CertificateFactory.getInstance("X.509");
			is = new FileInputStream(crlFile);
			crls = cf.generateCRLs(is);
		} catch (IOException iex) {
			throw iex;
		} catch (CRLException crle) {
			throw crle;
		} catch (CertificateException ce) {
			throw ce;
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (Exception ex) {
				}
			}
		}
		return crls;
	}

	/**
	 * Set the SSL protocol variants to be enabled.
	 * 
	 * @param engine
	 *            the SSLEngine.
	 * @param protocols
	 *            the protocols to use.
	 */
	protected void setEnabledProtocols(SSLEngine engine, String[] protocols) {
		if (protocols != null) {
			engine.setEnabledProtocols(protocols);
		}
	}

	/**
	 * Determines the SSL protocol variants to be enabled.
	 * 
	 * @param engine
	 *            The SSLEngine to get supported list from.
	 * @param requestedProtocols
	 *            Comma-separated list of requested SSL protocol variants
	 * 
	 * @return Array of SSL protocol variants to be enabled, or null if none of
	 *         the requested protocol variants are supported
	 */
	protected String[] getEnabledProtocols(SSLEngine engine, String requestedProtocols) {
		String[] supportedProtocols = engine.getSupportedProtocols();

		String[] enabledProtocols = null;

		if (requestedProtocols != null) {
			Vector<Object> vec = null;
			String tab[] = requestedProtocols.trim().split("\\s*,\\s*");
			if (tab.length > 0) {
				vec = new Vector<Object>(tab.length);
			}
			for (String s : tab) {
				if (s.length() > 0) {
					/*
					 * Check to see if the requested protocol is among the
					 * supported protocols, i.e., may be already enabled
					 */
					for (int i = 0; supportedProtocols != null && i < supportedProtocols.length; i++) {
						if (supportedProtocols[i].equals(s)) {
							vec.addElement(s);
							break;
						}
					}
				}
			}

			if (vec != null && !vec.isEmpty()) {
				enabledProtocols = new String[vec.size()];
				vec.copyInto(enabledProtocols);
			}
		}

		return enabledProtocols;
	}

	/**
	 * Configure the given SSL server socket with the requested cipher suites,
	 * protocol versions, and need for client authentication
	 * 
	 * @param engine
	 */
	private void initSSLEngine(SSLEngine engine) {
		if (enabledCiphers != null) {
			engine.setEnabledCipherSuites(enabledCiphers);
		}

		engine.setUseClientMode(false);
		String requestedProtocols = (String) attributes.get("protocols");

		setEnabledProtocols(engine, getEnabledProtocols(engine, requestedProtocols));

		// we don't know if client authentication is needed -
		// after parsing the request we may re-handshake
		engine.setWantClientAuth(wantClientAuth);
		engine.setNeedClientAuth(requireClientAuth);
	}

	/**
	 * Checks that the certificate is compatible with the enabled cipher suites.
	 * If we don't check now, the JIoEndpoint can enter a nasty logging loop.
	 * See bug 45528.
	 */
	private void checkConfig() throws IOException {
		// Create an unbound server socket
		SSLServerSocketFactory sslProxy = sslContext.getServerSocketFactory();
		ServerSocket socket = sslProxy.createServerSocket();
		SSLEngine engine = sslContext.createSSLEngine();
		initSSLEngine(engine);

		try {
			// Set the timeout to 1ms as all we care about is if it throws an
			// SSLException on accept.
			socket.setSoTimeout(1);
			socket.accept();
			// Will never get here - no client can connect to an unbound port
		} catch (SSLException ssle) {
			// SSL configuration is invalid. Possibly cert doesn't match ciphers
			IOException ioe = new IOException(sm.getString("jsse.invalid_ssl_conf",
					ssle.getMessage()));
			ioe.initCause(ssle);
			throw ioe;
		} catch (Exception e) {
			/*
			 * Possible ways of getting here socket.accept() throws a
			 * SecurityException socket.setSoTimeout() throws a SocketException
			 * socket.accept() throws some other exception (after a JDK change)
			 * In these cases the test won't work so carry on - essentially the
			 * behaviour before this patch socket.accept() throws a
			 * SocketTimeoutException In this case all is well so carry on
			 */
		} finally {
			// Should be open here but just in case
			if (!socket.isClosed()) {
				socket.close();
			}
		}

	}
}
