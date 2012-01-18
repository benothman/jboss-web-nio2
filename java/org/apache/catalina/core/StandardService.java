/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.apache.catalina.core;


import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.lang.reflect.Method;
import java.security.SecureRandom;
import java.util.ArrayList;

import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.catalina.Container;
import org.apache.catalina.Engine;
import org.apache.catalina.Executor;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.util.Base64;
import org.apache.catalina.util.LifecycleSupport;
import org.apache.catalina.util.StringManager;
import org.apache.tomcat.util.http.mapper.Mapper;
import org.apache.tomcat.util.modeler.Registry;
import org.jboss.logging.Logger;


/**
 * Standard implementation of the <code>Service</code> interface.  The
 * associated Container is generally an instance of Engine, but this is
 * not required.
 *
 * @author Craig R. McClanahan
 */

public class StandardService
        implements Lifecycle, Service, MBeanRegistration 
 {
    private static Logger log = Logger.getLogger(StandardService.class);
   

    /**
     * Alternate flag to enable delaying startup of connectors in embedded mode.
     */
    public static final boolean DELAY_CONNECTOR_STARTUP =
        Boolean.valueOf(System.getProperty("org.apache.catalina.core.StandardService.DELAY_CONNECTOR_STARTUP", "true")).booleanValue();


    // ----------------------------------------------------- Instance Variables


    /**
     * Descriptive information about this component implementation.
     */
    private static final String info =
        "org.apache.catalina.core.StandardService/1.0";


    /**
     * The name of this service.
     */
    private String name = null;


    /**
     * The lifecycle event support for this component.
     */
    private LifecycleSupport lifecycle = new LifecycleSupport(this);


    /**
     * The string manager for this package.
     */
    private static final StringManager sm =
        StringManager.getManager(Constants.Package);

    /**
     * The <code>Server</code> that owns this Service, if any.
     */
    private Server server = null;

    /**
     * Has this component been started?
     */
    private boolean started = false;


    /**
     * The property change support for this component.
     */
    protected PropertyChangeSupport support = new PropertyChangeSupport(this);


    /**
     * The set of Connectors associated with this Service.
     */
    protected Connector connectors[] = new Connector[0];
    
    /**
     * 
     */
    protected ArrayList<Executor> executors = new ArrayList<Executor>();

    /**
     * The Container associated with this Service. (In the case of the
     * org.apache.catalina.startup.Embedded subclass, this holds the most
     * recently added Engine.)
     */
    protected Container container = null;

    
    /**
     * Mapper.
     */
    protected Mapper mapper = new Mapper();


    /**
     * The associated mapper.
     */
    protected ServiceMapperListener mapperListener = new ServiceMapperListener(mapper);
    

    /**
     * Has this component been initialized?
     */
    protected boolean initialized = false;


    /**
     * A String initialization parameter used to increase the entropy of
     * the initialization of our random number generator.
     */
    protected String entropy = null;

    
    /**
     * The random associated with this service.
     */
    protected SecureRandom random = null;


    // ------------------------------------------------------------- Properties


    /**
     * Return the <code>Container</code> that handles requests for all
     * <code>Connectors</code> associated with this Service.
     */
    public Container getContainer() {

        return (this.container);

    }


    /**
     * Set the <code>Container</code> that handles requests for all
     * <code>Connectors</code> associated with this Service.
     *
     * @param container The new Container
     */
    public void setContainer(Container container) {

        Container oldContainer = this.container;
        if ((oldContainer != null) && (oldContainer instanceof Engine))
            ((Engine) oldContainer).setService(null);
        this.container = container;
        if ((this.container != null) && (this.container instanceof Engine))
            ((Engine) this.container).setService(this);
        if (started && (this.container != null) &&
            (this.container instanceof Lifecycle)) {
            try {
                ((Lifecycle) this.container).start();
            } catch (LifecycleException e) {
                ;
            }
        }
        synchronized (connectors) {
            for (int i = 0; i < connectors.length; i++)
                connectors[i].setContainer(this.container);
        }
        if (started && (oldContainer != null) &&
            (oldContainer instanceof Lifecycle)) {
            try {
                ((Lifecycle) oldContainer).stop();
            } catch (LifecycleException e) {
                ;
            }
        }

        // Report this property change to interested listeners
        support.firePropertyChange("container", oldContainer, this.container);

    }


    public Mapper getMapper() {
        return mapper;
    }


    public ObjectName getContainerName() {
        if( container instanceof ContainerBase ) {
            return ((ContainerBase)container).getJmxName();
        }
        return null;
    }


    /**
     * Return descriptive information about this Service implementation and
     * the corresponding version number, in the format
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    public String getInfo() {

        return (info);

    }


    /**
     * Return the name of this Service.
     */
    public String getName() {

        return (this.name);

    }


    /**
     * Set the name of this Service.
     *
     * @param name The new service name
     */
    public void setName(String name) {

        this.name = name;

    }


    /**
     * Return the <code>Server</code> with which we are associated (if any).
     */
    public Server getServer() {

        return (this.server);

    }


    /**
     * Set the <code>Server</code> with which we are associated (if any).
     *
     * @param server The server that owns this Service
     */
    public void setServer(Server server) {

        this.server = server;

    }


    @Override
    public String getEntropy() {
        // Calculate a semi-useful value if this has not been set
        if (this.entropy == null) {
            // Use APR to get a crypto secure entropy value
            byte[] result = new byte[32];
            boolean apr = false;
            try {
                String methodName = "random";
                Class paramTypes[] = new Class[2];
                paramTypes[0] = result.getClass();
                paramTypes[1] = int.class;
                Object paramValues[] = new Object[2];
                paramValues[0] = result;
                paramValues[1] = new Integer(32);
                Method method = Class.forName("org.apache.tomcat.jni.OS")
                    .getMethod(methodName, paramTypes);
                method.invoke(null, paramValues);
                apr = true;
            } catch (Throwable t) {
                // Ignore
            }
            if (apr) {
                setEntropy(new String(Base64.encode(result)));
            }
        }
        return (this.entropy);
    }


    @Override
    public void setEntropy(String entropy) {
        this.entropy = entropy;
    }


    // --------------------------------------------------------- Public Methods


    @Override
    public SecureRandom getRandom() {
        return random;
    }


    /**
     * Add a new Connector to the set of defined Connectors, and associate it
     * with this Service's Container.
     *
     * @param connector The Connector to be added
     */
    public void addConnector(Connector connector) {

        synchronized (connectors) {
            connector.setContainer(this.container);
            connector.setService(this);
            Connector results[] = new Connector[connectors.length + 1];
            System.arraycopy(connectors, 0, results, 0, connectors.length);
            results[connectors.length] = connector;
            connectors = results;

            if (!DELAY_CONNECTOR_STARTUP) {
                if (initialized) {
                    try {
                        connector.init();
                    } catch (LifecycleException e) {
                        log.error("Connector.initialize", e);
                    }
                }

                if (started && (connector instanceof Lifecycle)) {
                    try {
                        ((Lifecycle) connector).start();
                    } catch (LifecycleException e) {
                        log.error("Connector.start", e);
                    }
                }
            }

            // Report this property change to interested listeners
            support.firePropertyChange("connector", null, connector);
        }

    }

    public ObjectName[] getConnectorNames() {
        ObjectName results[] = new ObjectName[connectors.length];
        for (int i=0; i<results.length; i++) {
            results[i] = connectors[i].getObjectName();
        }
        return results;
    }


    /**
     * Add a property change listener to this component.
     *
     * @param listener The listener to add
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {

        support.addPropertyChangeListener(listener);

    }


    /**
     * Find and return the set of Connectors associated with this Service.
     */
    public Connector[] findConnectors() {

        return (connectors);

    }


    /**
     * Remove the specified Connector from the set associated from this
     * Service.  The removed Connector will also be disassociated from our
     * Container.
     *
     * @param connector The Connector to be removed
     */
    public void removeConnector(Connector connector) {

        synchronized (connectors) {
            int j = -1;
            for (int i = 0; i < connectors.length; i++) {
                if (connector == connectors[i]) {
                    j = i;
                    break;
                }
            }
            if (j < 0)
                return;
            if (!DELAY_CONNECTOR_STARTUP) {
                if (started && (connectors[j] instanceof Lifecycle)) {
                    try {
                        ((Lifecycle) connectors[j]).stop();
                    } catch (LifecycleException e) {
                        log.error("Connector.stop", e);
                    }
                }
            }
            connectors[j].setContainer(null);
            connector.setService(null);
            int k = 0;
            Connector results[] = new Connector[connectors.length - 1];
            for (int i = 0; i < connectors.length; i++) {
                if (i != j)
                    results[k++] = connectors[i];
            }
            connectors = results;

            // Report this property change to interested listeners
            support.firePropertyChange("connector", connector, null);
        }

    }


    /**
     * Remove a property change listener from this component.
     *
     * @param listener The listener to remove
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {

        support.removePropertyChangeListener(listener);

    }


    /**
     * Return a String representation of this component.
     */
    public String toString() {

        StringBuilder sb = new StringBuilder("StandardService[");
        sb.append(getName());
        sb.append("]");
        return (sb.toString());

    }


    // ------------------------------------------------------ Lifecycle Methods


    /**
     * Add a LifecycleEvent listener to this component.
     *
     * @param listener The listener to add
     */
    public void addLifecycleListener(LifecycleListener listener) {

        lifecycle.addLifecycleListener(listener);

    }


    /**
     * Get the lifecycle listeners associated with this lifecycle. If this 
     * Lifecycle has no listeners registered, a zero-length array is returned.
     */
    public LifecycleListener[] findLifecycleListeners() {

        return lifecycle.findLifecycleListeners();

    }


    /**
     * Remove a LifecycleEvent listener from this component.
     *
     * @param listener The listener to remove
     */
    public void removeLifecycleListener(LifecycleListener listener) {

        lifecycle.removeLifecycleListener(listener);

    }
    
    /**
     * Adds a named executor to the service
     * @param ex Executor
     */
    public void addExecutor(Executor ex) {
        synchronized (executors) {
            if (!executors.contains(ex)) {
                executors.add(ex);
                if (started)
                    try {
                        ex.start();
                    } catch (LifecycleException x) {
                        log.error("Executor.start", x);
                    }
            }
        }
    }

    /**
     * Retrieves all executors
     * @return Executor[]
     */
    public Executor[] findExecutors() {
        synchronized (executors) {
            Executor[] arr = new Executor[executors.size()];
            executors.toArray(arr);
            return arr;
        }
    }

    /**
     * Retrieves executor by name, null if not found
     * @param name String
     * @return Executor
     */
    public Executor getExecutor(String name) {
        synchronized (executors) {
            for (int i = 0; i < executors.size(); i++) {
                if (name.equals(executors.get(i).getName()))
                    return executors.get(i);
            }
        }
        return null;
    }

    /**
     * Removes an executor from the service
     * @param ex Executor
     */
    public void removeExecutor(Executor ex) {
        synchronized (executors) {
            if ( executors.remove(ex) && started ) {
                try {
                    ex.stop();
                } catch (LifecycleException e) {
                    log.error("Executor.stop", e);
                }
            }
        }
    }



    /**
     * Prepare for the beginning of active use of the public methods of this
     * component.  This method should be called before any of the public
     * methods of this component are utilized.  It should also send a
     * LifecycleEvent of type START_EVENT to any registered listeners.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    public void start() throws LifecycleException {

        // Validate and update our current component state
        if (log.isInfoEnabled() && started) {
            log.info(sm.getString("standardService.start.started"));
        }
        
        if( ! initialized )
            init(); 

        // Notify our interested LifecycleListeners
        lifecycle.fireLifecycleEvent(BEFORE_START_EVENT, null);
        if(log.isDebugEnabled())
            log.debug(sm.getString("standardService.start.name", this.name));
        lifecycle.fireLifecycleEvent(START_EVENT, null);
        started = true;

        // Start our defined Container first
        if (container != null) {
            synchronized (container) {
                if (container instanceof Lifecycle) {
                    ((Lifecycle) container).start();
                }
            }
        }

        synchronized (executors) {
            for ( int i=0; i<executors.size(); i++ ) {
                executors.get(i).start();
            }
        }

        // Start our defined Connectors second
        if (!DELAY_CONNECTOR_STARTUP) {
        	synchronized (connectors) {
        		for (int i = 0; i < connectors.length; i++) {
        			if (connectors[i] instanceof Lifecycle)
        				((Lifecycle) connectors[i]).start();
        		}
        	}
        }
        
        // Notify our interested LifecycleListeners
        lifecycle.fireLifecycleEvent(AFTER_START_EVENT, null);

    }


    /**
     * Gracefully terminate the active use of the public methods of this
     * component.  This method should be the last one called on a given
     * instance of this component.  It should also send a LifecycleEvent
     * of type STOP_EVENT to any registered listeners.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that needs to be reported
     */
    public void stop() throws LifecycleException {

        // Validate and update our current component state
        if (!started) {
            return;
        }

        // Notify our interested LifecycleListeners
        lifecycle.fireLifecycleEvent(BEFORE_STOP_EVENT, null);

        // Stop our defined Connectors first
        if (!DELAY_CONNECTOR_STARTUP) {
            synchronized (connectors) {
                for (int i = 0; i < connectors.length; i++) {
                    connectors[i].pause();
                }
            }
        }

        lifecycle.fireLifecycleEvent(STOP_EVENT, null);
        if(log.isDebugEnabled())
            log.debug(sm.getString("standardService.stop.name", this.name));
        started = false;

        // Stop our defined Container second
        if (container != null) {
            synchronized (container) {
                if (container instanceof Lifecycle) {
                    ((Lifecycle) container).stop();
                }
            }
        }

        // Stop our defined Connectors first
        if (!DELAY_CONNECTOR_STARTUP) {
            synchronized (connectors) {
                for (int i = 0; i < connectors.length; i++) {
                    if (connectors[i] instanceof Lifecycle)
                        ((Lifecycle) connectors[i]).stop();
                }
            }
        }

        synchronized (executors) {
            for ( int i=0; i<executors.size(); i++ ) {
                executors.get(i).stop();
            }
        }

        if (org.apache.tomcat.util.Constants.ENABLE_MODELER) {
            if( oname==controller ) {
                // we registered ourself on init().
                // That should be the typical case - this object is just for
                // backward compat, nobody should bother to load it explicitely
                Registry.getRegistry(null, null).unregisterComponent(oname);
                Executor[] executors = findExecutors();
                for (int i = 0; i < executors.length; i++) {
                    try {
                        ObjectName executorObjectName = 
                            new ObjectName(domain + ":type=Executor,name=" + executors[i].getName());
                        Registry.getRegistry(null, null).unregisterComponent(executorObjectName);
                    } catch (Exception e) {
                        // Ignore (invalid ON, which cannot happen)
                    }
                }
            }
        }        

        // Notify our interested LifecycleListeners
        lifecycle.fireLifecycleEvent(AFTER_STOP_EVENT, null);

    }


    /**
     * Invoke a pre-startup initialization. This is used to allow connectors
     * to bind to restricted ports under Unix operating environments.
     */
    public void initialize()
            throws LifecycleException
    {
        // Service shouldn't be used with embeded, so it doesn't matter
        if (initialized) {
            if(log.isInfoEnabled())
                log.info(sm.getString("standardService.initialize.initialized"));
            return;
        }
        initialized = true;

        if (org.apache.tomcat.util.Constants.ENABLE_MODELER) {
            if( oname==null ) {
                try {
                    // Hack - Server should be deprecated...
                    Container engine=this.getContainer();
                    domain=engine.getName();
                    oname=new ObjectName(domain + ":type=Service,serviceName="+name);
                    this.controller=oname;
                    Registry.getRegistry(null, null)
                    .registerComponent(this, oname, null);

                    Executor[] executors = findExecutors();
                    for (int i = 0; i < executors.length; i++) {
                        ObjectName executorObjectName = 
                            new ObjectName(domain + ":type=Executor,name=" + executors[i].getName());
                        Registry.getRegistry(null, null)
                        .registerComponent(executors[i], executorObjectName, null);
                    }

                } catch (Exception e) {
                    log.error(sm.getString("standardService.register.failed",domain),e);
                }
            }
        }
        if( server==null ) {
            // If no server was defined - create one
            server = new StandardServer();
            server.addService(this);
        }
               
        addLifecycleListener(mapperListener);
        
        // Initialize our defined Connectors
        if (!DELAY_CONNECTOR_STARTUP) {
            synchronized (connectors) {
                for (int i = 0; i < connectors.length; i++) {
                    connectors[i].init();
                }
            }
        }

        // Construct and seed a new random number generator
        String entropy = getEntropy();
        if (entropy != null) {
            random = new SecureRandom(getEntropy().getBytes());
        } else {
            random = new SecureRandom();
        }
    }
    
    public void destroy() throws LifecycleException {
        if( started ) stop();
        // FIXME unregister should be here probably -- stop doing that ?
        removeLifecycleListener(mapperListener);
    }

    public void init() {
        try {
            initialize();
        } catch( Throwable t ) {
            log.error(sm.getString("standardService.initialize.failed",domain),t);
        }
    }

    protected String type;
    protected String domain;
    protected String suffix;
    protected ObjectName oname;
    protected ObjectName controller;
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
