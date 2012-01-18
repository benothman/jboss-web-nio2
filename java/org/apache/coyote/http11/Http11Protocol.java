/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.apache.coyote.http11;

import java.net.InetAddress;
import java.net.Socket;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.net.ssl.SSLContext;

import org.apache.coyote.Adapter;
import org.apache.coyote.ProtocolHandler;
import org.apache.coyote.RequestGroupInfo;
import org.apache.coyote.RequestInfo;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.net.JIoEndpoint;
import org.apache.tomcat.util.net.SSLImplementation;
import org.apache.tomcat.util.net.ServerSocketFactory;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.JIoEndpoint.Handler;
import org.apache.tomcat.util.res.StringManager;


/**
 * Abstract the protocol implementation, including threading, etc.
 * Processor is single threaded and specific to stream-based protocols,
 * will not fit Jk protocols like JNI.
 *
 * @author Remy Maucherat
 * @author Costin Manolache
 */
public class Http11Protocol 
    implements ProtocolHandler, MBeanRegistration {


    protected static org.jboss.logging.Logger log
        = org.jboss.logging.Logger.getLogger(Http11Protocol.class);

    /**
     * The string manager for this package.
     */
    protected static StringManager sm =
        StringManager.getManager(Constants.Package);


    // ------------------------------------------------------------ Constructor


    public Http11Protocol() {
        setSoLinger(Constants.DEFAULT_CONNECTION_LINGER);
        setSoTimeout(Constants.DEFAULT_CONNECTION_TIMEOUT);
        //setServerSoTimeout(Constants.DEFAULT_SERVER_SOCKET_TIMEOUT);
        setTcpNoDelay(Constants.DEFAULT_TCP_NO_DELAY);
    }

    
    // ----------------------------------------------------------------- Fields


    protected Http11ConnectionHandler cHandler = new Http11ConnectionHandler(this);
    protected JIoEndpoint endpoint = new JIoEndpoint();


    // *
    protected ObjectName tpOname = null;
    // *
    protected ObjectName rgOname = null;


    protected ServerSocketFactory socketFactory = null;
    protected SSLImplementation sslImplementation = null;


    // ----------------------------------------- ProtocolHandler Implementation
    // *


    protected HashMap<String, Object> attributes = new HashMap<String, Object>();

    
    /**
     * Pass config info
     */
    public void setAttribute(String name, Object value) {
        if (log.isTraceEnabled()) {
            log.trace(sm.getString("http11protocol.setattribute", name, value));
        }
        attributes.put(name, value);
    }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    public Iterator getAttributeNames() {
        return attributes.keySet().iterator();
    }

    /**
     * Set a property.
     */
    public void setProperty(String name, String value) {
        setAttribute(name, value);
    }

    /**
     * Get a property
     */
    public String getProperty(String name) {
        return (String)getAttribute(name);
    }

    /**
     * The adapter, used to call the connector.
     */
    protected Adapter adapter;
    public void setAdapter(Adapter adapter) { this.adapter = adapter; }
    public Adapter getAdapter() { return adapter; }


    public boolean hasIoEvents() {
        return false;
    }

    public RequestGroupInfo getRequestGroupInfo() {
        return cHandler.global;
    }

    public void init() throws Exception {
        endpoint.setName(getName());
        endpoint.setHandler(cHandler);

        // Verify the validity of the configured socket factory
        try {
            if (isSSLEnabled()) {
                sslImplementation =
                    SSLImplementation.getInstance(sslImplementationName);
                socketFactory = sslImplementation.getServerSocketFactory();
                endpoint.setServerSocketFactory(socketFactory);
            } else if (socketFactoryName != null) {
                socketFactory = (ServerSocketFactory) Class.forName(socketFactoryName).newInstance();
                endpoint.setServerSocketFactory(socketFactory);
            }
        } catch (Exception ex) {
            log.error(sm.getString("http11protocol.socketfactory.initerror"),
                      ex);
            throw ex;
        }

        if (socketFactory!=null) {
            Iterator<String> attE = attributes.keySet().iterator();
            while( attE.hasNext() ) {
                String key = attE.next();
                Object v=attributes.get(key);
                socketFactory.setAttribute(key, v);
            }
        }
        
        try {
            endpoint.init();
        } catch (Exception ex) {
            log.error(sm.getString("http11protocol.endpoint.initerror"), ex);
            throw ex;
        }
        if (log.isDebugEnabled())
            log.debug(sm.getString("http11protocol.init", getName()));

    }

    public void start() throws Exception {
        if (org.apache.tomcat.util.Constants.ENABLE_MODELER) {
            if (this.domain != null) {
                try {
                    tpOname = new ObjectName
                    (domain + ":" + "type=ThreadPool,name=" + getName());
                    Registry.getRegistry(null, null)
                    .registerComponent(endpoint, tpOname, null );
                } catch (Exception e) {
                    log.error("Can't register endpoint");
                }
                rgOname=new ObjectName
                (domain + ":type=GlobalRequestProcessor,name=" + getName());
                Registry.getRegistry(null, null).registerComponent
                ( cHandler.global, rgOname, null );
            }
        }
        try {
            endpoint.start();
        } catch (Exception ex) {
            log.error(sm.getString("http11protocol.endpoint.starterror"), ex);
            throw ex;
        }
        if (log.isInfoEnabled())
            log.info(sm.getString("http11protocol.start", getName()));
    }

    public void pause() throws Exception {
        try {
            endpoint.pause();
        } catch (Exception ex) {
            log.error(sm.getString("http11protocol.endpoint.pauseerror"), ex);
            throw ex;
        }
        // Wait for a while until all the processors are no longer processing requests
        RequestInfo[] states = cHandler.global.getRequestProcessors();
        int retry = 0;
        boolean done = false;
        while (!done && retry < org.apache.coyote.Constants.MAX_PAUSE_WAIT) {
            retry++;
            done = true;
            for (int i = 0; i < states.length; i++) {
                if (states[i].getStage() == org.apache.coyote.Constants.STAGE_SERVICE) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        ;
                    }
                    done = false;
                    break;
                }
            }
        }
        if (log.isInfoEnabled())
            log.info(sm.getString("http11protocol.pause", getName()));
    }

    public void resume() throws Exception {
        try {
            endpoint.resume();
        } catch (Exception ex) {
            log.error(sm.getString("http11protocol.endpoint.resumeerror"), ex);
            throw ex;
        }
        if (log.isInfoEnabled())
            log.info(sm.getString("http11protocol.resume", getName()));
    }

    public void destroy() throws Exception {
        if (log.isInfoEnabled())
            log.info(sm.getString("http11protocol.stop", getName()));
        endpoint.destroy();
        if (org.apache.tomcat.util.Constants.ENABLE_MODELER) {
            if (tpOname!=null)
                Registry.getRegistry(null, null).unregisterComponent(tpOname);
            if (rgOname != null)
                Registry.getRegistry(null, null).unregisterComponent(rgOname);
        }
    }

    public String getName() {
        String encodedAddr = "";
        if (getAddress() != null) {
            encodedAddr = "" + getAddress();
            encodedAddr = URLEncoder.encode(encodedAddr.replace('/', '-')) + "-";
        }
        return ("http-" + encodedAddr + endpoint.getPort());
    }

    // ------------------------------------------------------------- Properties

    
    /**
     * Processor cache.
     */
    protected int processorCache = -1;
    public int getProcessorCache() { return this.processorCache; }
    public void setProcessorCache(int processorCache) { this.processorCache = processorCache; }

    protected int socketBuffer = 9000;
    public int getSocketBuffer() { return socketBuffer; }
    public void setSocketBuffer(int socketBuffer) { this.socketBuffer = socketBuffer; }

    /**
     * This field indicates if the protocol is secure from the perspective of
     * the client (= https is used).
     */
    protected boolean secure;
    public boolean getSecure() { return secure; }
    public void setSecure(boolean b) { secure = b; }

    protected boolean SSLEnabled = false;
    public boolean isSSLEnabled() { return SSLEnabled;}
    public void setSSLEnabled(boolean SSLEnabled) {this.SSLEnabled = SSLEnabled;}    
    
    /**
     * Name of the socket factory.
     */
    protected String socketFactoryName = null;
    public String getSocketFactory() { return socketFactoryName; }
    public void setSocketFactory(String valueS) { socketFactoryName = valueS; }
    
    /**
     * Name of the SSL implementation.
     */
    protected String sslImplementationName=null;
    public String getSSLImplementation() { return sslImplementationName; }
    public void setSSLImplementation( String valueS) {
        sslImplementationName = valueS;
        setSecure(true);
    }
    
    
    // HTTP
    /**
     * Maximum number of requests which can be performed over a keepalive 
     * connection. The default is the same as for Apache HTTP Server.
     */
    protected int maxKeepAliveRequests = (org.apache.tomcat.util.Constants.LOW_MEMORY) ? 1 : 
        Integer.valueOf(System.getProperty("org.apache.coyote.http11.Http11Protocol.MAX_KEEP_ALIVE_REQUESTS", "100")).intValue();
    public int getMaxKeepAliveRequests() { return maxKeepAliveRequests; }
    public void setMaxKeepAliveRequests(int mkar) { maxKeepAliveRequests = mkar; }

    // HTTP
    /**
     * The number of seconds Tomcat will wait for a subsequent request
     * before closing the connection. The default is the same as for
     * Apache HTTP Server (15 000 milliseconds).
     */
    protected int keepAliveTimeout = -1;
    public int getKeepAliveTimeout() { return keepAliveTimeout; }
    public void setKeepAliveTimeout(int timeout) { keepAliveTimeout = timeout; }

    // HTTP
    /**
     * This timeout represents the socket timeout which will be used while
     * the adapter execution is in progress, unless disableUploadTimeout
     * is set to true. The default is the same as for Apache HTTP Server
     * (300 000 milliseconds).
     */
    protected int timeout = 300000;
    public int getTimeout() { return timeout; }
    public void setTimeout(int timeout) { this.timeout = timeout; }


    // *
    /**
     * Maximum size of the post which will be saved when processing certain
     * requests, such as a POST.
     */
    protected int maxSavePostSize = 4 * 1024;
    public int getMaxSavePostSize() { return maxSavePostSize; }
    public void setMaxSavePostSize(int valueI) { maxSavePostSize = valueI; }


    // HTTP
    /**
     * Maximum size of the HTTP message header.
     */
    protected int maxHttpHeaderSize = 8 * 1024;
    public int getMaxHttpHeaderSize() { return maxHttpHeaderSize; }
    public void setMaxHttpHeaderSize(int valueI) { maxHttpHeaderSize = valueI; }


    // HTTP
    /**
     * If true, the regular socket timeout will be used for the full duration
     * of the connection.
     */
    protected boolean disableUploadTimeout = true;
    public boolean getDisableUploadTimeout() { return disableUploadTimeout; }
    public void setDisableUploadTimeout(boolean isDisabled) { disableUploadTimeout = isDisabled; }


    // HTTP
    /**
     * Integrated compression support.
     */
    protected String compression = "off";
    public String getCompression() { return compression; }
    public void setCompression(String valueS) { compression = valueS; }
    
    
    // HTTP
    protected String noCompressionUserAgents = null;
    public String getNoCompressionUserAgents() { return noCompressionUserAgents; }
    public void setNoCompressionUserAgents(String valueS) { noCompressionUserAgents = valueS; }

    
    // HTTP
    protected String compressableMimeTypes = "text/html,text/xml,text/plain";
    public String getCompressableMimeType() { return compressableMimeTypes; }
    public void setCompressableMimeType(String valueS) { compressableMimeTypes = valueS; }
    
    
    // HTTP
    protected int compressionMinSize = 2048;
    public int getCompressionMinSize() { return compressionMinSize; }
    public void setCompressionMinSize(int valueI) { compressionMinSize = valueI; }


    // HTTP
    /**
     * User agents regular expressions which should be restricted to HTTP/1.0 support.
     */
    protected String restrictedUserAgents = null;
    public String getRestrictedUserAgents() { return restrictedUserAgents; }
    public void setRestrictedUserAgents(String valueS) { restrictedUserAgents = valueS; }
    
    // HTTP
    /**
     * Server header.
     */
    protected String server;
    public void setServer( String server ) { this.server = server; }
    public String getServer() { return server; }

    public Executor getExecutor() { return endpoint.getExecutor(); }
    public void setExecutor(Executor executor) { endpoint.setExecutor(executor); }
    
    public int getMaxThreads() { return endpoint.getMaxThreads(); }
    public void setMaxThreads(int maxThreads) { endpoint.setMaxThreads(maxThreads); }

    public int getThreadPriority() { return endpoint.getThreadPriority(); }
    public void setThreadPriority(int threadPriority) { endpoint.setThreadPriority(threadPriority); }

    public int getBacklog() { return endpoint.getBacklog(); }
    public void setBacklog(int backlog) { endpoint.setBacklog(backlog); }

    public int getPort() { return endpoint.getPort(); }
    public void setPort(int port) { endpoint.setPort(port); }

    public InetAddress getAddress() { return endpoint.getAddress(); }
    public void setAddress(InetAddress ia) { endpoint.setAddress(ia); }

    public boolean getTcpNoDelay() { return endpoint.getTcpNoDelay(); }
    public void setTcpNoDelay(boolean tcpNoDelay) { endpoint.setTcpNoDelay(tcpNoDelay); }

    public int getSoLinger() { return endpoint.getSoLinger(); }
    public void setSoLinger(int soLinger) { endpoint.setSoLinger(soLinger); }

    public int getSoTimeout() { return endpoint.getSoTimeout(); }
    public void setSoTimeout(int soTimeout) { endpoint.setSoTimeout(soTimeout); }

    public void setPollerSize(int pollerSize) { endpoint.setPollerSize(pollerSize); }
    public int getPollerSize() { return endpoint.getPollerSize(); }

    // HTTP
    /**
     * Return the Keep-Alive policy for the connection.
     */
    public boolean getKeepAlive() {
        return ((maxKeepAliveRequests != 0) && (maxKeepAliveRequests != 1));
    }

    // HTTP
    /**
     * Set the keep-alive policy for this connection.
     */
    public void setKeepAlive(boolean keepAlive) {
        if (!keepAlive) {
            setMaxKeepAliveRequests(1);
        }
    }

    /*
     * Note: All the following are JSSE/java.io specific attributes.
     */
    
    public String getKeystore() {
        return (String) getAttribute("keystore");
    }

    public void setKeystore( String k ) {
        setAttribute("keystore", k);
    }

    public String getKeypass() {
        return (String) getAttribute("keypass");
    }

    public void setKeypass( String k ) {
        attributes.put("keypass", k);
        //setAttribute("keypass", k);
    }

    public String getKeytype() {
        return (String) getAttribute("keystoreType");
    }

    public void setKeytype( String k ) {
        setAttribute("keystoreType", k);
    }

    public String getClientauth() {
        return (String) getAttribute("clientauth");
    }

    public void setClientauth( String k ) {
        setAttribute("clientauth", k);
    }

    public String getProtocols() {
        return (String) getAttribute("protocols");
    }

    public void setProtocols(String k) {
        setAttribute("protocols", k);
    }

    public String getAlgorithm() {
        return (String) getAttribute("algorithm");
    }

    public void setAlgorithm( String k ) {
        setAttribute("algorithm", k);
    }

    public String getCiphers() {
        return (String) getAttribute("ciphers");
    }

    public void setCiphers(String ciphers) {
        setAttribute("ciphers", ciphers);
    }

    public String getKeyAlias() {
        return (String) getAttribute("keyAlias");
    }

    public void setKeyAlias(String keyAlias) {
        setAttribute("keyAlias", keyAlias);
    }

    public SSLContext getSSLContext() {
        return (SSLContext) getAttribute("SSLContext");
    }

    public void setSSLContext(SSLContext sslContext) {
        setAttribute("SSLContext", sslContext);
    }

    // -----------------------------------  Http11ConnectionHandler Inner Class

    protected static class Http11ConnectionHandler implements Handler {

        protected Http11Protocol proto;
        protected AtomicLong registerCount = new AtomicLong(0);
        protected RequestGroupInfo global = new RequestGroupInfo();

        protected ConcurrentHashMap<Socket, Http11Processor> connections =
            new ConcurrentHashMap<Socket, Http11Processor>();
        protected ConcurrentLinkedQueue<Http11Processor> recycledProcessors = 
            new ConcurrentLinkedQueue<Http11Processor>() {
            protected AtomicInteger size = new AtomicInteger(0);
            public boolean offer(Http11Processor processor) {
                boolean offer = (proto.processorCache == -1) ? true : (size.get() < proto.processorCache);
                //avoid over growing our cache or add after we have stopped
                boolean result = false;
                if ( offer ) {
                    result = super.offer(processor);
                    if ( result ) {
                        size.incrementAndGet();
                    }
                }
                if (!result) unregister(processor);
                return result;
            }
            
            public Http11Processor poll() {
                Http11Processor result = super.poll();
                if ( result != null ) {
                    size.decrementAndGet();
                }
                return result;
            }
            
            public void clear() {
                Http11Processor next = poll();
                while ( next != null ) {
                    unregister(next);
                    next = poll();
                }
                super.clear();
                size.set(0);
            }
        };

        Http11ConnectionHandler(Http11Protocol proto) {
            this.proto = proto;
        }

        public SocketState event(Socket socket, SocketStatus status) {
            Http11Processor result = connections.get(socket);
            SocketState state = SocketState.CLOSED; 
            if (result != null) {
                result.startProcessing();
                // Call the appropriate event
                try {
                    state = result.event(status);
                } catch (java.net.SocketException e) {
                    // SocketExceptions are normal
                    Http11Protocol.log.debug
                        (sm.getString
                            ("http11protocol.proto.socketexception.debug"), e);
                } catch (java.io.IOException e) {
                    // IOExceptions are normal
                    Http11Protocol.log.debug
                        (sm.getString
                            ("http11protocol.proto.ioexception.debug"), e);
                }
                // Future developers: if you discover any other
                // rare-but-nonfatal exceptions, catch them here, and log as
                // above.
                catch (Throwable e) {
                    // any other exception or error is odd. Here we log it
                    // with "ERROR" level, so it will show up even on
                    // less-than-verbose logs.
                    Http11Protocol.log.error
                        (sm.getString("http11protocol.proto.error"), e);
                } finally {
                    if (state != SocketState.LONG) {
                        connections.remove(socket);
                        recycledProcessors.offer(result);
                    } else {
                        if (proto.endpoint.isRunning()) {
                            proto.endpoint.getEventPoller().add(socket, result.getTimeout(), 
                                    result.getResumeNotification(), false);
                        }
                    }
                    result.endProcessing();
                }
            }
            return state;
        }
        
        public SocketState process(Socket socket) {
            Http11Processor processor = recycledProcessors.poll();
            try {

                if (processor == null) {
                    processor = createProcessor();
                }

                if (proto.secure && (proto.sslImplementation != null)) {
                    processor.setSSLSupport
                        (proto.sslImplementation.getSSLSupport(socket));
                } else {
                    processor.setSSLSupport(null);
                }
                
                SocketState state = processor.process(socket);
                if (state == SocketState.LONG) {
                    // Associate the connection with the processor. The next request 
                    // processed by this thread will use either a new or a recycled
                    // processor.
                    connections.put(socket, processor);
                    proto.endpoint.getEventPoller().add(socket, processor.getTimeout(), 
                            processor.getResumeNotification(), false);
                } else {
                    recycledProcessors.offer(processor);
                }
                return state;

            } catch(java.net.SocketException e) {
                // SocketExceptions are normal
                Http11Protocol.log.debug
                    (sm.getString
                     ("http11protocol.proto.socketexception.debug"), e);
            } catch (java.io.IOException e) {
                // IOExceptions are normal
                Http11Protocol.log.debug
                    (sm.getString
                     ("http11protocol.proto.ioexception.debug"), e);
            }
            // Future developers: if you discover any other
            // rare-but-nonfatal exceptions, catch them here, and log as
            // above.
            catch (Throwable e) {
                // any other exception or error is odd. Here we log it
                // with "ERROR" level, so it will show up even on
                // less-than-verbose logs.
                Http11Protocol.log.error
                    (sm.getString("http11protocol.proto.error"), e);
            }
            recycledProcessors.offer(processor);
            return SocketState.CLOSED;
        }
        
        protected Http11Processor createProcessor() {
            Http11Processor processor =
                new Http11Processor(proto.maxHttpHeaderSize, proto.endpoint);
            processor.setAdapter(proto.adapter);
            processor.setMaxKeepAliveRequests(proto.maxKeepAliveRequests);
            processor.setKeepAliveTimeout(proto.keepAliveTimeout);
            processor.setTimeout(proto.timeout);
            processor.setDisableUploadTimeout(proto.disableUploadTimeout);
            processor.setCompressionMinSize(proto.compressionMinSize);
            processor.setCompression(proto.compression);
            processor.setNoCompressionUserAgents(proto.noCompressionUserAgents);
            processor.setCompressableMimeTypes(proto.compressableMimeTypes);
            processor.setRestrictedUserAgents(proto.restrictedUserAgents);
            processor.setSocketBuffer(proto.socketBuffer);
            processor.setMaxSavePostSize(proto.maxSavePostSize);
            processor.setServer(proto.server);
            register(processor);
            return processor;
        }
        
        protected void register(Http11Processor processor) {
            RequestInfo rp = processor.getRequest().getRequestProcessor();
            rp.setGlobalProcessor(global);
            if (org.apache.tomcat.util.Constants.ENABLE_MODELER && proto.getDomain() != null) {
                synchronized (this) {
                    try {
                        long count = registerCount.incrementAndGet();
                        ObjectName rpName = new ObjectName
                            (proto.getDomain() + ":type=RequestProcessor,worker="
                                + proto.getName() + ",name=HttpRequest" + count);
                        if (log.isDebugEnabled()) {
                            log.debug("Register " + rpName);
                        }
                        Registry.getRegistry(null, null).registerComponent(rp, rpName, null);
                        rp.setRpName(rpName);
                    } catch (Exception e) {
                        log.warn("Error registering request");
                    }
                }
            }
        }

        protected void unregister(Http11Processor processor) {
            RequestInfo rp = processor.getRequest().getRequestProcessor();
            rp.setGlobalProcessor(null);
            if (org.apache.tomcat.util.Constants.ENABLE_MODELER && proto.getDomain() != null) {
                synchronized (this) {
                    try {
                        ObjectName rpName = rp.getRpName();
                        if (log.isDebugEnabled()) {
                            log.debug("Unregister " + rpName);
                        }
                        Registry.getRegistry(null, null).unregisterComponent(rpName);
                        rp.setRpName(null);
                    } catch (Exception e) {
                        log.warn("Error unregistering request", e);
                    }
                }
            }
        }

    }


    // -------------------- JMX related methods --------------------

    // *
    protected String domain;
    protected ObjectName oname;
    protected MBeanServer mserver;

    public ObjectName getObjectName() {
        return oname;
    }

    public String getDomain() {
        return domain;
    }

    public ObjectName preRegister(MBeanServer server,
                                  ObjectName name) throws Exception {
        oname=name;
        mserver=server;
        domain=name.getDomain();
        return name;
    }

    public void postRegister(Boolean registrationDone) {
    }

    public void preDeregister() throws Exception {
    }

    public void postDeregister() {
    }
}
