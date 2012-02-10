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
package org.apache.coyote.http11;

import java.util.HashMap;
import java.util.Iterator;

import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.net.ssl.SSLContext;

import org.apache.coyote.Adapter;
import org.apache.coyote.ProtocolHandler;
import org.apache.tomcat.util.net.SSLImplementation;
import org.apache.tomcat.util.res.StringManager;

/**
 * {@code Http11AbstractProtocol}
 * <p>
 * Abstract the protocol implementation, including threading, etc. Processor is
 * single threaded and specific to stream-based protocols, will not fit Jk
 * protocols like JNI.
 * </p>
 * Created on Dec 19, 2011 at 11:58:19 AM
 * 
 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
 */
public abstract class Http11AbstractProtocol implements ProtocolHandler, MBeanRegistration {

	protected static org.jboss.logging.Logger log = org.jboss.logging.Logger
			.getLogger(Http11NioProtocol.class);

	/**
	 * The string manager for this package.
	 */
	protected static StringManager sm = StringManager.getManager(Constants.Package);

	protected HashMap<String, Object> attributes = new HashMap<String, Object>();
	// *
	protected ObjectName tpOname = null;
	// *
	protected ObjectName rgOname = null;

	protected SSLImplementation sslImplementation = null;

	/**
	 * The adapter, used to call the connector.
	 */
	protected Adapter adapter;

	/**
	 * Processor cache.
	 */
	protected int processorCache = -1;
	protected boolean canDestroy = false;
	protected int socketBuffer = 9000;
	/**
	 * Maximum size of the post which will be saved when processing certain
	 * requests, such as a POST.
	 */
	protected int maxSavePostSize = 4 * 1024;
	// --------------- HTTP ---------------
	/**
	 * Maximum size of the HTTP message header.
	 */
	protected int maxHttpHeaderSize = 8 * 1024;
	/**
	 * If true, the regular socket timeout will be used for the full duration of
	 * the connection.
	 */
	protected boolean disableUploadTimeout = true;
	/**
	 * Integrated compression support.
	 */
	protected String compression = "off";
	protected String noCompressionUserAgents = null;
	protected String compressableMimeTypes = "text/html,text/xml,text/plain";
	protected int compressionMinSize = 2048;
	protected String protocol = null;
	/**
	 * User agents regular expressions which should be restricted to HTTP/1.0
	 * support.
	 */
	protected String restrictedUserAgents = null;
	/**
	 * Maximum number of requests which can be performed over a keepalive
	 * connection. The default is the same as for Apache HTTP Server.
	 */
	protected int maxKeepAliveRequests = Integer.valueOf(
			System.getProperty("org.apache.coyote.http11.Http11Protocol.MAX_KEEP_ALIVE_REQUESTS",
					"100")).intValue();

	protected String domain;
	protected ObjectName oname;
	protected MBeanServer mserver;

	/**
	 * Server header.
	 */
	protected String server;
	/**
	 * This timeout represents the socket timeout which will be used while the
	 * adapter execution is in progress, unless disableUploadTimeout is set to
	 * true. The default is the same as for Apache HTTP Server (300 000
	 * milliseconds).
	 */
	protected int timeout = 300000;
	/**
	 * This field indicates if the protocol is secure from the perspective of
	 * the client (= https is used).
	 */
	protected boolean secure;

	/**
	 * Create a new instance of {@code Http11AbstractProtocol}
	 */
	public Http11AbstractProtocol() {
		super();
	}

	/**
	 * @return the name of the protocol
	 */
	public abstract String getName();

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.coyote.ProtocolHandler#setAttribute(java.lang.String,
	 * java.lang.Object)
	 */
	@Override
	public void setAttribute(String name, Object value) {
		if (log.isTraceEnabled())
			log.trace(sm.getString("http11protocol.setattribute", name, value));

		attributes.put(name, value);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.coyote.ProtocolHandler#getAttribute(java.lang.String)
	 */
	@Override
	public Object getAttribute(String key) {
		if (log.isTraceEnabled())
			log.trace(sm.getString("http11protocol.getattribute", key));
		return attributes.get(key);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.coyote.ProtocolHandler#getAttributeNames()
	 */
	@Override
	public Iterator<String> getAttributeNames() {
		return attributes.keySet().iterator();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.coyote.ProtocolHandler#setAdapter(org.apache.coyote.Adapter)
	 */
	@Override
	public void setAdapter(Adapter adapter) {
		this.adapter = adapter;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.coyote.ProtocolHandler#getAdapter()
	 */
	@Override
	public Adapter getAdapter() {
		return adapter;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.coyote.ProtocolHandler#hasIoEvents()
	 */
	@Override
	public boolean hasIoEvents() {
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * javax.management.MBeanRegistration#preRegister(javax.management.MBeanServer
	 * , javax.management.ObjectName)
	 */
	@Override
	public ObjectName preRegister(MBeanServer server, ObjectName name) throws Exception {
		oname = name;
		mserver = server;
		domain = name.getDomain();
		return name;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.management.MBeanRegistration#postRegister(java.lang.Boolean)
	 */
	@Override
	public void postRegister(Boolean registrationDone) {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.management.MBeanRegistration#preDeregister()
	 */
	@Override
	public void preDeregister() throws Exception {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.management.MBeanRegistration#postDeregister()
	 */
	@Override
	public void postDeregister() {
	}

	/**
	 * Getter for attributes
	 * 
	 * @return the attributes
	 */
	public HashMap<String, Object> getAttributes() {
		return this.attributes;
	}

	/**
	 * Setter for the attributes
	 * 
	 * @param attributes
	 *            the attributes to set
	 */
	public void setAttributes(HashMap<String, Object> attributes) {
		this.attributes = attributes;
	}

	/**
	 * Getter for tpOname
	 * 
	 * @return the tpOname
	 */
	public ObjectName getTpOname() {
		return this.tpOname;
	}

	/**
	 * Setter for the tpOname
	 * 
	 * @param tpOname
	 *            the tpOname to set
	 */
	public void setTpOname(ObjectName tpOname) {
		this.tpOname = tpOname;
	}

	/**
	 * Getter for rgOname
	 * 
	 * @return the rgOname
	 */
	public ObjectName getRgOname() {
		return this.rgOname;
	}

	/**
	 * Setter for the rgOname
	 * 
	 * @param rgOname
	 *            the rgOname to set
	 */
	public void setRgOname(ObjectName rgOname) {
		this.rgOname = rgOname;
	}

	/**
	 * Getter for sslImplementation
	 * 
	 * @return the sslImplementation
	 */
	public SSLImplementation getSslImplementation() {
		return this.sslImplementation;
	}

	/**
	 * Setter for the sslImplementation
	 * 
	 * @param sslImplementation
	 *            the sslImplementation to set
	 */
	public void setSslImplementation(SSLImplementation sslImplementation) {
		this.sslImplementation = sslImplementation;
	}

	/**
	 * Getter for processorCache
	 * 
	 * @return the processorCache
	 */
	public int getProcessorCache() {
		return this.processorCache;
	}

	/**
	 * Setter for the processorCache
	 * 
	 * @param processorCache
	 *            the processorCache to set
	 */
	public void setProcessorCache(int processorCache) {
		this.processorCache = processorCache;
	}

	/**
	 * Getter for canDestroy
	 * 
	 * @return the canDestroy
	 */
	public boolean getCanDestroy() {
		return this.canDestroy;
	}

	/**
	 * Setter for the canDestroy
	 * 
	 * @param canDestroy
	 *            the canDestroy to set
	 */
	public void setCanDestroy(boolean canDestroy) {
		this.canDestroy = canDestroy;
	}

	/**
	 * Getter for socketBuffer
	 * 
	 * @return the socketBuffer
	 */
	public int getSocketBuffer() {
		return this.socketBuffer;
	}

	/**
	 * Setter for the socketBuffer
	 * 
	 * @param socketBuffer
	 *            the socketBuffer to set
	 */
	public void setSocketBuffer(int socketBuffer) {
		this.socketBuffer = socketBuffer;
	}

	/**
	 * Getter for maxSavePostSize
	 * 
	 * @return the maxSavePostSize
	 */
	public int getMaxSavePostSize() {
		return this.maxSavePostSize;
	}

	/**
	 * Setter for the maxSavePostSize
	 * 
	 * @param maxSavePostSize
	 *            the maxSavePostSize to set
	 */
	public void setMaxSavePostSize(int maxSavePostSize) {
		this.maxSavePostSize = maxSavePostSize;
	}

	/**
	 * Getter for maxHttpHeaderSize
	 * 
	 * @return the maxHttpHeaderSize
	 */
	public int getMaxHttpHeaderSize() {
		return this.maxHttpHeaderSize;
	}

	/**
	 * Setter for the maxHttpHeaderSize
	 * 
	 * @param maxHttpHeaderSize
	 *            the maxHttpHeaderSize to set
	 */
	public void setMaxHttpHeaderSize(int maxHttpHeaderSize) {
		this.maxHttpHeaderSize = maxHttpHeaderSize;
	}

	/**
	 * Getter for disableUploadTimeout
	 * 
	 * @return the disableUploadTimeout
	 */
	public boolean getDisableUploadTimeout() {
		return this.disableUploadTimeout;
	}

	/**
	 * Setter for the disableUploadTimeout
	 * 
	 * @param disableUploadTimeout
	 *            the disableUploadTimeout to set
	 */
	public void setDisableUploadTimeout(boolean disableUploadTimeout) {
		this.disableUploadTimeout = disableUploadTimeout;
	}

	/**
	 * Getter for compression
	 * 
	 * @return the compression
	 */
	public String getCompression() {
		return this.compression;
	}

	/**
	 * Setter for the compression
	 * 
	 * @param compression
	 *            the compression to set
	 */
	public void setCompression(String compression) {
		this.compression = compression;
	}

	/**
	 * Getter for noCompressionUserAgents
	 * 
	 * @return the noCompressionUserAgents
	 */
	public String getNoCompressionUserAgents() {
		return this.noCompressionUserAgents;
	}

	/**
	 * Setter for the noCompressionUserAgents
	 * 
	 * @param noCompressionUserAgents
	 *            the noCompressionUserAgents to set
	 */
	public void setNoCompressionUserAgents(String noCompressionUserAgents) {
		this.noCompressionUserAgents = noCompressionUserAgents;
	}

	/**
	 * Getter for compressableMimeTypes
	 * 
	 * @return the compressableMimeTypes
	 */
	public String getCompressableMimeType() {
		return this.compressableMimeTypes;
	}

	/**
	 * Setter for the compressableMimeTypes
	 * 
	 * @param compressableMimeTypes
	 *            the compressableMimeTypes to set
	 */
	public void setCompressableMimeType(String compressableMimeTypes) {
		this.compressableMimeTypes = compressableMimeTypes;
	}

	/**
	 * Getter for compressionMinSize
	 * 
	 * @return the compressionMinSize
	 */
	public int getCompressionMinSize() {
		return this.compressionMinSize;
	}

	/**
	 * Setter for the compressionMinSize
	 * 
	 * @param compressionMinSize
	 *            the compressionMinSize to set
	 */
	public void setCompressionMinSize(int compressionMinSize) {
		this.compressionMinSize = compressionMinSize;
	}

	/**
	 * Getter for protocol
	 * 
	 * @return the protocol
	 */
	public String getProtocol() {
		return this.protocol;
	}

	/**
	 * Setter for the protocol
	 * 
	 * @param protocol
	 *            the protocol to set
	 */
	public void setProtocol(String protocol) {
		this.protocol = protocol;
	}

	/**
	 * Getter for restrictedUserAgents
	 * 
	 * @return the restrictedUserAgents
	 */
	public String getRestrictedUserAgents() {
		return this.restrictedUserAgents;
	}

	/**
	 * Setter for the restrictedUserAgents
	 * 
	 * @param restrictedUserAgents
	 *            the restrictedUserAgents to set
	 */
	public void setRestrictedUserAgents(String restrictedUserAgents) {
		this.restrictedUserAgents = restrictedUserAgents;
	}

	/**
	 * Getter for maxKeepAliveRequests
	 * 
	 * @return the maxKeepAliveRequests
	 */
	public int getMaxKeepAliveRequests() {
		return this.maxKeepAliveRequests;
	}

	/**
	 * Setter for the maxKeepAliveRequests
	 * 
	 * @param maxKeepAliveRequests
	 *            the maxKeepAliveRequests to set
	 */
	public void setMaxKeepAliveRequests(int maxKeepAliveRequests) {
		this.maxKeepAliveRequests = maxKeepAliveRequests;
	}

	/**
	 * Getter for domain
	 * 
	 * @return the domain
	 */
	public String getDomain() {
		return this.domain;
	}

	/**
	 * Setter for the domain
	 * 
	 * @param domain
	 *            the domain to set
	 */
	public void setDomain(String domain) {
		this.domain = domain;
	}

	/**
	 * Getter for oname
	 * 
	 * @return the oname
	 */
	public ObjectName getObjectName() {
		return this.oname;
	}

	/**
	 * Setter for the oname
	 * 
	 * @param oname
	 *            the oname to set
	 */
	public void setObjectName(ObjectName oname) {
		this.oname = oname;
	}

	/**
	 * Getter for mserver
	 * 
	 * @return the mserver
	 */
	public MBeanServer getMserver() {
		return this.mserver;
	}

	/**
	 * Setter for the mserver
	 * 
	 * @param mserver
	 *            the mserver to set
	 */
	public void setMserver(MBeanServer mserver) {
		this.mserver = mserver;
	}

	/**
	 * Getter for server
	 * 
	 * @return the server
	 */
	public String getServer() {
		return this.server;
	}

	/**
	 * Setter for the server
	 * 
	 * @param server
	 *            the server to set
	 */
	public void setServer(String server) {
		this.server = server;
	}

	/**
	 * Getter for timeout
	 * 
	 * @return the timeout
	 */
	public int getTimeout() {
		return this.timeout;
	}

	/**
	 * Setter for the timeout
	 * 
	 * @param timeout
	 *            the timeout to set
	 */
	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	/**
	 * Getter for secure
	 * 
	 * @return the secure
	 */
	public boolean isSecure() {
		return this.secure;
	}

	/**
	 * Setter for the secure
	 * 
	 * @param secure
	 *            the secure to set
	 */
	public void setSecure(boolean secure) {
		this.secure = secure;
	}

	/**
	 * @return the key store
	 */
	public String getKeystore() {
		return (String) getAttribute("keystore");
	}

	/**
	 * @param keystore
	 */
	public void setKeystore(String keystore) {
		setAttribute("keystore", keystore);
	}

	/**
	 * @return the key pass
	 */
	public String getKeypass() {
		return (String) getAttribute("keypass");
	}

	/**
	 * @param keypass
	 */
	public void setKeypass(String keypass) {
		attributes.put("keypass", keypass);
	}

	/**
	 * @return the key store type
	 */
	public String getKeytype() {
		return (String) getAttribute("keystoreType");
	}

	/**
	 * @param keyType
	 */
	public void setKeytype(String keyType) {
		setAttribute("keystoreType", keyType);
	}

	/**
	 * @return the client authentication
	 */
	public String getClientauth() {
		return (String) getAttribute("clientauth");
	}

	/**
	 * @param k
	 */
	public void setClientauth(String k) {
		setAttribute("clientauth", k);
	}

	/**
	 * @return the protocols
	 */
	public String getProtocols() {
		return (String) getAttribute("protocols");
	}

	/**
	 * @param protocols the protocols to set
	 */
	public void setProtocols(String protocols) {
		System.out.println("************ "+getClass().getName() + "setProtocols =====> protocols : " + protocols);
		setAttribute("protocols", protocols);
	}

	/**
	 * @return the algorithm
	 */
	public String getAlgorithm() {
		return (String) getAttribute("algorithm");
	}

	/**
	 * @param k
	 */
	public void setAlgorithm(String k) {
		setAttribute("algorithm", k);
	}

	/**
	 * @return the ciphers
	 */
	public String getCiphers() {
		return (String) getAttribute("ciphers");
	}

	/**
	 * 
	 * @param ciphers
	 */
	public void setCiphers(String ciphers) {
		setAttribute("ciphers", ciphers);
	}

	/**
	 * @return the ke alias
	 */
	public String getKeyAlias() {
		return (String) getAttribute("keyAlias");
	}

	/**
	 * 
	 * @param keyAlias
	 */
	public void setKeyAlias(String keyAlias) {
		setAttribute("keyAlias", keyAlias);
	}

	/**
	 * @return the SSL context
	 */
	public SSLContext getSSLContext() {
		return (SSLContext) getAttribute("SSLContext");
	}

	/**
	 * @param sslContext
	 */
	public void setSSLContext(SSLContext sslContext) {
		setAttribute("SSLContext", sslContext);
	}
}
