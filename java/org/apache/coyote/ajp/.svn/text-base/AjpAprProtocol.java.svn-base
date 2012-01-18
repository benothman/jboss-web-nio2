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

package org.apache.coyote.ajp;

import java.net.InetAddress;
import java.net.URLEncoder;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.coyote.Adapter;
import org.apache.coyote.ProtocolHandler;
import org.apache.coyote.RequestGroupInfo;
import org.apache.coyote.RequestInfo;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.net.AprEndpoint;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.AprEndpoint.Handler;
import org.apache.tomcat.util.res.StringManager;


/**
 * Abstract the protocol implementation, including threading, etc.
 * Processor is single threaded and specific to stream-based protocols,
 * will not fit Jk protocols like JNI.
 *
 * @author Remy Maucherat
 * @author Costin Manolache
 */
public class AjpAprProtocol 
    implements ProtocolHandler, MBeanRegistration {
    
    
    protected static org.jboss.logging.Logger log =
        org.jboss.logging.Logger.getLogger(AjpAprProtocol.class);

    /**
     * The string manager for this package.
     */
    protected static StringManager sm =
        StringManager.getManager(Constants.Package);


    // ------------------------------------------------------------ Constructor


    public AjpAprProtocol() {
        cHandler = new AjpConnectionHandler(this);
        setSoLinger(Constants.DEFAULT_CONNECTION_LINGER);
        setSoTimeout(Constants.DEFAULT_CONNECTION_TIMEOUT);
        //setServerSoTimeout(Constants.DEFAULT_SERVER_SOCKET_TIMEOUT);
        setTcpNoDelay(Constants.DEFAULT_TCP_NO_DELAY);
    }

    
    // ----------------------------------------------------- Instance Variables


    protected ObjectName tpOname;
    
    
    protected ObjectName rgOname;


    /**
     * Associated APR endpoint.
     */
    protected AprEndpoint endpoint = new AprEndpoint();


    /**
     * Configuration attributes.
     */
    protected Hashtable attributes = new Hashtable();


    /**
     * Adapter which will process the requests recieved by this endpoint.
     */
    private Adapter adapter;
    
    
    /**
     * Connection handler for AJP.
     */
    private AjpConnectionHandler cHandler;


    private boolean canDestroy = false;


    // --------------------------------------------------------- Public Methods


    /** 
     * Pass config info
     */
    public void setAttribute(String name, Object value) {
        if (log.isTraceEnabled()) {
            log.trace(sm.getString("ajpprotocol.setattribute", name, value));
        }
        attributes.put(name, value);
    }

    public Object getAttribute(String key) {
        if (log.isTraceEnabled()) {
            log.trace(sm.getString("ajpprotocol.getattribute", key));
        }
        return attributes.get(key);
    }


    public Iterator getAttributeNames() {
        return attributes.keySet().iterator();
    }


    /**
     * The adapter, used to call the connector
     */
    public void setAdapter(Adapter adapter) {
        this.adapter = adapter;
    }


    public Adapter getAdapter() {
        return adapter;
    }


    public boolean hasIoEvents() {
        return false;
    }

    public RequestGroupInfo getRequestGroupInfo() {
        return cHandler.global;
    }


    /** Start the protocol
     */
    public void init() throws Exception {
        endpoint.setName(getName());
        endpoint.setHandler(cHandler);
        endpoint.setUseSendfile(false);

        try {
            endpoint.init();
        } catch (Exception ex) {
            log.error(sm.getString("ajpprotocol.endpoint.initerror"), ex);
            throw ex;
        }
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("ajpprotocol.init", getName()));
        }
    }


    public void start() throws Exception {
        if (org.apache.tomcat.util.Constants.ENABLE_MODELER) {
            if (this.domain != null ) {
                try {
                    tpOname = new ObjectName
                    (domain + ":" + "type=ThreadPool,name=" + getName());
                    Registry.getRegistry(null, null)
                    .registerComponent(endpoint, tpOname, null );
                } catch (Exception e) {
                    log.error("Can't register threadpool" );
                }
                rgOname = new ObjectName
                (domain + ":type=GlobalRequestProcessor,name=" + getName());
                Registry.getRegistry(null, null).registerComponent
                (cHandler.global, rgOname, null);
            }
        }

        try {
            endpoint.start();
        } catch (Exception ex) {
            log.error(sm.getString("ajpprotocol.endpoint.starterror"), ex);
            throw ex;
        }
        if (log.isInfoEnabled())
            log.info(sm.getString("ajpprotocol.start", getName()));
    }

    public void pause() throws Exception {
        try {
            endpoint.pause();
        } catch (Exception ex) {
            log.error(sm.getString("ajpprotocol.endpoint.pauseerror"), ex);
            throw ex;
        }
        canDestroy = false;
        // Wait for a while until all the processors are idle
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
            if (done) {
                canDestroy = true;
            }
        }
        if (log.isInfoEnabled())
            log.info(sm.getString("ajpprotocol.pause", getName()));
    }

    public void resume() throws Exception {
        try {
            endpoint.resume();
        } catch (Exception ex) {
            log.error(sm.getString("ajpprotocol.endpoint.resumeerror"), ex);
            throw ex;
        }
        if (log.isInfoEnabled())
            log.info(sm.getString("ajpprotocol.resume", getName()));
    }

    public void destroy() throws Exception {
        if (log.isInfoEnabled())
            log.info(sm.getString("ajpprotocol.stop", getName()));
        if (canDestroy) {
            endpoint.destroy();
        } else {
            log.warn(sm.getString("ajpprotocol.cannotDestroy", getName()));
            try {
                RequestInfo[] states = cHandler.global.getRequestProcessors();
                for (int i = 0; i < states.length; i++) {
                    if (states[i].getStage() == org.apache.coyote.Constants.STAGE_SERVICE) {
                        // FIXME: Log RequestInfo content
                    }
                }
            } catch (Exception ex) {
                log.error(sm.getString("ajpprotocol.cannotDestroy", getName()), ex);
                throw ex;
            }
        }
        if (org.apache.tomcat.util.Constants.ENABLE_MODELER) {
            if (tpOname!=null)
                Registry.getRegistry(null, null).unregisterComponent(tpOname);
            if (rgOname != null)
                Registry.getRegistry(null, null).unregisterComponent(rgOname);
        }
    }

    // *
    public String getName() {
        String encodedAddr = "";
        if (getAddress() != null) {
            encodedAddr = "" + getAddress();
            encodedAddr = URLEncoder.encode(encodedAddr.replace('/', '-')) + "-";
        }
        return ("ajp-" + encodedAddr + endpoint.getPort());
    }

    /**
     * Processor cache.
     */
    protected int processorCache = -1;
    public int getProcessorCache() { return this.processorCache; }
    public void setProcessorCache(int processorCache) { this.processorCache = processorCache; }

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

    public boolean getReverseConnection() { return endpoint.isReverseConnection(); }
    public void setReverseConnection(boolean reverseConnection) { endpoint.setReverseConnection(reverseConnection); }

    public boolean getDeferAccept() { return endpoint.getDeferAccept(); }
    public void setDeferAccept(boolean deferAccept) { endpoint.setDeferAccept(deferAccept); }

    /**
     * Should authentication be done in the native webserver layer, 
     * or in the Servlet container ?
     */
    protected boolean tomcatAuthentication = true;
    public boolean getTomcatAuthentication() { return tomcatAuthentication; }
    public void setTomcatAuthentication(boolean tomcatAuthentication) { this.tomcatAuthentication = tomcatAuthentication; }

    /**
     * Required secret.
     */
    protected String requiredSecret = null;
    public void setRequiredSecret(String requiredSecret) { this.requiredSecret = requiredSecret; }
    
    /**
     * AJP packet size.
     */
    protected int packetSize = Constants.MAX_PACKET_SIZE;
    public int getPacketSize() { return packetSize; }
    public void setPacketSize(int packetSize) { this.packetSize = packetSize; }

    /**
     * The number of seconds Tomcat will wait for a subsequent request
     * before closing the connection.
     */
    public int getKeepAliveTimeout() { return endpoint.getKeepAliveTimeout(); }
    public void setKeepAliveTimeout(int timeout) { endpoint.setKeepAliveTimeout(timeout); }

    public boolean getUseSendfile() { return endpoint.getUseSendfile(); }
    public void setUseSendfile(boolean useSendfile) { /* No sendfile for AJP */ }

    public int getPollTime() { return endpoint.getPollTime(); }
    public void setPollTime(int pollTime) { endpoint.setPollTime(pollTime); }

    public void setPollerSize(int pollerSize) { endpoint.setPollerSize(pollerSize); }
    public int getPollerSize() { return endpoint.getPollerSize(); }

    // --------------------------------------  AjpConnectionHandler Inner Class


    protected static class AjpConnectionHandler implements Handler {

        protected AjpAprProtocol proto;
        protected AtomicLong registerCount = new AtomicLong(0);
        protected RequestGroupInfo global = new RequestGroupInfo();

        protected ConcurrentHashMap<Long, AjpAprProcessor> connections =
            new ConcurrentHashMap<Long, AjpAprProcessor>();
        protected ConcurrentLinkedQueue<AjpAprProcessor> recycledProcessors = 
            new ConcurrentLinkedQueue<AjpAprProcessor>() {
            protected AtomicInteger size = new AtomicInteger(0);
            public boolean offer(AjpAprProcessor processor) {
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
            
            public AjpAprProcessor poll() {
                AjpAprProcessor result = super.poll();
                if ( result != null ) {
                    size.decrementAndGet();
                }
                return result;
            }
            
            public void clear() {
                AjpAprProcessor next = poll();
                while ( next != null ) {
                    unregister(next);
                    next = poll();
                }
                super.clear();
                size.set(0);
            }
        };

        public AjpConnectionHandler(AjpAprProtocol proto) {
            this.proto = proto;
        }

        public SocketState event(long socket, SocketStatus status) {
            AjpAprProcessor result = connections.get(socket);
            SocketState state = SocketState.CLOSED; 
            if (result != null) {
                result.startProcessing();
                // Call the appropriate event
                try {
                    state = result.event(status);
                } catch (java.net.SocketException e) {
                    // SocketExceptions are normal
                    AjpAprProcessor.log.debug
                        (sm.getString
                            ("ajpprotocol.proto.socketexception.debug"), e);
                } catch (java.io.IOException e) {
                    // IOExceptions are normal
                    AjpAprProcessor.log.debug
                        (sm.getString
                            ("ajpprotocol.proto.ioexception.debug"), e);
                }
                // Future developers: if you discover any other
                // rare-but-nonfatal exceptions, catch them here, and log as
                // above.
                catch (Throwable e) {
                    // any other exception or error is odd. Here we log it
                    // with "ERROR" level, so it will show up even on
                    // less-than-verbose logs.
                    AjpAprProcessor.log.error
                        (sm.getString("ajpprotocol.proto.error"), e);
                } finally {
                    if (state != SocketState.LONG) {
                        connections.remove(socket);
                        recycledProcessors.offer(result);
                        if (proto.endpoint.isRunning() && state == SocketState.OPEN) {
                            proto.endpoint.getPoller().add(socket);
                        }
                    } else {
                        if (proto.endpoint.isRunning()) {
                            proto.endpoint.getEventPoller().add(socket, result.getTimeout(), 
                                    false, false, result.getResumeNotification(), false);
                        }
                    }
                    result.endProcessing();
                }
            }
            return state;
        }
        
        public SocketState process(long socket) {
            AjpAprProcessor processor = recycledProcessors.poll();
            try {

                if (processor == null) {
                    processor = createProcessor();
                }

                SocketState state = processor.process(socket);
                if (state == SocketState.LONG) {
                    // Associate the connection with the processor. The next request 
                    // processed by this thread will use either a new or a recycled
                    // processor.
                    connections.put(socket, processor);
                    proto.endpoint.getEventPoller().add(socket, processor.getTimeout(), false, 
                            false, processor.getResumeNotification(), false);
                } else {
                    recycledProcessors.offer(processor);
                }
                return state;

            } catch(java.net.SocketException e) {
                // SocketExceptions are normal
                AjpAprProtocol.log.debug
                    (sm.getString
                     ("ajpprotocol.proto.socketexception.debug"), e);
            } catch (java.io.IOException e) {
                // IOExceptions are normal
                AjpAprProtocol.log.debug
                    (sm.getString
                     ("ajpprotocol.proto.ioexception.debug"), e);
            }
            // Future developers: if you discover any other
            // rare-but-nonfatal exceptions, catch them here, and log as
            // above.
            catch (Throwable e) {
                // any other exception or error is odd. Here we log it
                // with "ERROR" level, so it will show up even on
                // less-than-verbose logs.
                AjpAprProtocol.log.error
                    (sm.getString("ajpprotocol.proto.error"), e);
            }
            recycledProcessors.offer(processor);
            return SocketState.CLOSED;
        }

        protected AjpAprProcessor createProcessor() {
            AjpAprProcessor processor = new AjpAprProcessor(proto.packetSize, proto.endpoint);
            processor.setAdapter(proto.adapter);
            processor.setTomcatAuthentication(proto.tomcatAuthentication);
            processor.setRequiredSecret(proto.requiredSecret);
            register(processor);
            return processor;
        }
        
        protected void register(AjpAprProcessor processor) {
            RequestInfo rp = processor.getRequest().getRequestProcessor();
            rp.setGlobalProcessor(global);
            if (org.apache.tomcat.util.Constants.ENABLE_MODELER && proto.getDomain() != null) {
                synchronized (this) {
                    try {
                        long count = registerCount.incrementAndGet();
                        ObjectName rpName = new ObjectName
                        (proto.getDomain() + ":type=RequestProcessor,worker="
                                + proto.getName() + ",name=AjpRequest" + count);
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

        protected void unregister(AjpAprProcessor processor) {
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


    // -------------------- Various implementation classes --------------------


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
