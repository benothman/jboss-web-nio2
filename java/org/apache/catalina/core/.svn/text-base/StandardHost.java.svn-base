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


import java.util.Locale;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Valve;
import org.apache.catalina.startup.HostConfig;
import org.apache.catalina.valves.ValveBase;
import org.apache.tomcat.util.modeler.Registry;


/**
 * Standard implementation of the <b>Host</b> interface.  Each
 * child container must be a Context implementation to process the
 * requests directed to a particular web application.
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 * @version $Revision$ $Date$
 */

public class StandardHost
    extends ContainerBase
    implements Host  
 {
    /* Why do we implement deployer and delegate to deployer ??? */

    private static org.jboss.logging.Logger log=
        org.jboss.logging.Logger.getLogger( StandardHost.class );
    
    // ----------------------------------------------------------- Constructors


    /**
     * Create a new StandardHost component with the default basic Valve.
     */
    public StandardHost() {

        super();
        pipeline.setBasic(new StandardHostValve());
        startChildren = 
            Boolean.valueOf(System.getProperty("org.apache.catalina.core.StandardHost.startChildren", "false")).booleanValue();

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * The set of aliases for this Host.
     */
    private String[] aliases = new String[0];


    /**
     * The application root for this Host.
     */
    private String appBase = ".";


    /**
     * The default webapp name.
     */
    private String defaultWebapp = "ROOT";


    /**
     * The Java class name of the default context configuration class
     * for deployed web applications.
     */
    private String configClass =
        System.getProperty("org.apache.catalina.core.StandardHost.configClass", null);


    /**
     * The Java class name of the default Context implementation class for
     * deployed web applications.
     */
    private String contextClass =
        "org.apache.catalina.core.StandardContext";


    /**
     * The Java class name of the default error reporter implementation class 
     * for deployed web applications.
     */
    private String errorReportValveClass =
        "org.apache.catalina.valves.ErrorReportValve";

    /**
     * The object name for the errorReportValve.
     */
    private ObjectName errorReportValveObjectName = null;

    /**
     * The descriptive information string for this implementation.
     */
    private static final String info =
        "org.apache.catalina.core.StandardHost/1.0";


    /**
     * Work Directory base for applications.
     */
    private String workDir = null;


    // ------------------------------------------------------------- Properties


    /**
     * Return the application root for this Host.  This can be an absolute
     * pathname, a relative pathname, or a URL.
     */
    public String getAppBase() {

        return (this.appBase);

    }


    /**
     * Set the application root for this Host.  This can be an absolute
     * pathname, a relative pathname, or a URL.
     *
     * @param appBase The new application root
     */
    public void setAppBase(String appBase) {

        String oldAppBase = this.appBase;
        this.appBase = appBase;
        support.firePropertyChange("appBase", oldAppBase, this.appBase);

    }


    public String getDefaultWebapp() {

        return (this.defaultWebapp);

    }


    public void setDefaultWebapp(String defaultWebapp) {

        String oldDefaultWebapp = this.defaultWebapp;
        this.defaultWebapp = defaultWebapp;
        support.firePropertyChange("defaultWebapp", oldDefaultWebapp, this.defaultWebapp);

    }


    /**
     * Return the Java class name of the context configuration class
     * for new web applications.
     */
    public String getConfigClass() {

        return (this.configClass);

    }


    /**
     * Set the Java class name of the context configuration class
     * for new web applications.
     *
     * @param configClass The new context configuration class
     */
    public void setConfigClass(String configClass) {

        String oldConfigClass = this.configClass;
        this.configClass = configClass;
        support.firePropertyChange("configClass",
                                   oldConfigClass, this.configClass);

    }


    /**
     * Return the Java class name of the Context implementation class
     * for new web applications.
     */
    public String getContextClass() {

        return (this.contextClass);

    }


    /**
     * Set the Java class name of the Context implementation class
     * for new web applications.
     *
     * @param contextClass The new context implementation class
     */
    public void setContextClass(String contextClass) {

        String oldContextClass = this.contextClass;
        this.contextClass = contextClass;
        support.firePropertyChange("contextClass",
                                   oldContextClass, this.contextClass);

    }


    /**
     * Return the Java class name of the error report valve class
     * for new web applications.
     */
    public String getErrorReportValveClass() {

        return (this.errorReportValveClass);

    }


    /**
     * Set the Java class name of the error report valve class
     * for new web applications.
     *
     * @param errorReportValveClass The new error report valve class
     */
    public void setErrorReportValveClass(String errorReportValveClass) {

        String oldErrorReportValveClassClass = this.errorReportValveClass;
        this.errorReportValveClass = errorReportValveClass;
        support.firePropertyChange("errorReportValveClass",
                                   oldErrorReportValveClassClass, 
                                   this.errorReportValveClass);

    }


    /**
     * Return the canonical, fully qualified, name of the virtual host
     * this Container represents.
     */
    public String getName() {

        return (name);

    }


    /**
     * Set the canonical, fully qualified, name of the virtual host
     * this Container represents.
     *
     * @param name Virtual host name
     *
     * @exception IllegalArgumentException if name is null
     */
    public void setName(String name) {

        if (name == null)
            throw new IllegalArgumentException
                (sm.getString("standardHost.nullName"));

        name = name.toLowerCase(Locale.ENGLISH);      // Internally all names are lower case

        String oldName = this.name;
        this.name = name;
        support.firePropertyChange("name", oldName, this.name);

    }


    /**
     * Host work directory base.
     */
    public String getWorkDir() {

        return (workDir);
    }


    /**
     * Host work directory base.
     */
    public void setWorkDir(String workDir) {

        this.workDir = workDir;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Add an alias name that should be mapped to this same Host.
     *
     * @param alias The alias to be added
     */
    public void addAlias(String alias) {

        alias = alias.toLowerCase(Locale.ENGLISH);

        // Skip duplicate aliases
        for (int i = 0; i < aliases.length; i++) {
            if (aliases[i].equals(alias))
                return;
        }

        // Add this alias to the list
        String newAliases[] = new String[aliases.length + 1];
        for (int i = 0; i < aliases.length; i++)
            newAliases[i] = aliases[i];
        newAliases[aliases.length] = alias;

        aliases = newAliases;

        // Inform interested listeners
        fireContainerEvent(ADD_ALIAS_EVENT, alias);

    }


    /**
     * Add a child Container, only if the proposed child is an implementation
     * of Context.
     *
     * @param child Child container to be added
     */
    public void addChild(Container child) {

        if (!(child instanceof Context))
            throw new IllegalArgumentException
                (sm.getString("standardHost.notContext"));
        super.addChild(child);

    }


    /**
     * Return the set of alias names for this Host.  If none are defined,
     * a zero length array is returned.
     */
    public String[] findAliases() {

        return (this.aliases);

    }


    /**
     * Return descriptive information about this Container implementation and
     * the corresponding version number, in the format
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    public String getInfo() {

        return (info);

    }


    /**
     * Remove the specified alias name from the aliases for this Host.
     *
     * @param alias Alias name to be removed
     */
    public void removeAlias(String alias) {

        alias = alias.toLowerCase(Locale.ENGLISH);

        // Make sure this alias is currently present
        int n = -1;
        for (int i = 0; i < aliases.length; i++) {
            if (aliases[i].equals(alias)) {
                n = i;
                break;
            }
        }
        if (n < 0)
            return;

        // Remove the specified alias
        int j = 0;
        String results[] = new String[aliases.length - 1];
        for (int i = 0; i < aliases.length; i++) {
            if (i != n)
                results[j++] = aliases[i];
        }
        aliases = results;

        // Inform interested listeners
        fireContainerEvent(REMOVE_ALIAS_EVENT, alias);

    }


    /**
     * Return a String representation of this component.
     */
    public String toString() {

        StringBuilder sb = new StringBuilder();
        if (getParent() != null) {
            sb.append(getParent().toString());
            sb.append(".");
        }
        sb.append("StandardHost[");
        sb.append(getName());
        sb.append("]");
        return (sb.toString());

    }


    /**
     * Start this host.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents it from being started
     */
    public synchronized void start() throws LifecycleException {
        if( started ) {
            return;
        }
        if( ! initialized )
            init();

        // Look for a realm - that may have been configured earlier. 
        // If the realm is added after context - it'll set itself.
        if( realm == null ) {
            ObjectName realmName=null;
            try {
                realmName=new ObjectName( domain + ":type=Realm,host=" + getName());
                if( mserver.isRegistered(realmName ) ) {
                    mserver.invoke(realmName, "init", 
                            new Object[] {},
                            new String[] {}
                    );            
                }
            } catch( Throwable t ) {
                log.debug("No realm for this host " + realmName);
            }
        }
            
        // Set error report valve
        if ((errorReportValveClass != null)
            && (!errorReportValveClass.equals(""))) {
            try {
                boolean found = false;
                if(errorReportValveObjectName != null) {
                    ObjectName[] names = 
                        ((StandardPipeline)pipeline).getValveObjectNames();
                    for (int i=0; !found && i<names.length; i++)
                        if(errorReportValveObjectName.equals(names[i]))
                            found = true ;
                    }
                    if(!found) {          	
                        Valve valve = (Valve) Class.forName(errorReportValveClass)
                        .newInstance();
                        addValve(valve);
                        errorReportValveObjectName = ((ValveBase)valve).getObjectName() ;
                    }
            } catch (Throwable t) {
                log.error(sm.getString
                    ("standardHost.invalidErrorReportValveClass", 
                     errorReportValveClass), t);
            }
        }

        super.start();

    }


    // -------------------- JMX  --------------------
    /**
      * Return the MBean Names of the Valves assoicated with this Host
      *
      * @exception Exception if an MBean cannot be created or registered
      */
     public String [] getValveNames()
         throws Exception
    {
         Valve [] valves = this.getValves();
         String [] mbeanNames = new String[valves.length];
         for (int i = 0; i < valves.length; i++) {
             if( valves[i] == null ) continue;
             if( ((ValveBase)valves[i]).getObjectName() == null ) continue;
             mbeanNames[i] = ((ValveBase)valves[i]).getObjectName().toString();
         }

         return mbeanNames;

     }

    public String[] getAliases() {
        return aliases;
    }

    private boolean initialized=false;
    
    public synchronized void init() {
        if( initialized ) return;
        initialized=true;
        
        // already registered.
        if( getParent() == null ) {
            try {
                // Register with the Engine
                ObjectName serviceName=new ObjectName(domain + 
                                        ":type=Engine");

                HostConfig deployer = new HostConfig();
                addLifecycleListener(deployer);                
                if( mserver.isRegistered( serviceName )) {
                    if(log.isDebugEnabled())
                        log.debug("Registering "+ serviceName +" with the Engine");
                    mserver.invoke( serviceName, "addChild",
                            new Object[] { this },
                            new String[] { "org.apache.catalina.Container" } );
                }
            } catch( Exception ex ) {
                log.error("Host registering failed!",ex);
            }
        }
        
        if (org.apache.tomcat.util.Constants.ENABLE_MODELER) {
            if( oname==null ) {
                // not registered in JMX yet - standalone mode
                try {
                    StandardEngine engine=(StandardEngine)parent;
                    domain=engine.getName();
                    if(log.isDebugEnabled())
                        log.debug( "Register host " + getName() + " with domain "+ domain );
                    oname=new ObjectName(domain + ":type=Host,host=" +
                            this.getName());
                    controller = oname;
                    Registry.getRegistry(null, null)
                    .registerComponent(this, oname, null);
                } catch( Throwable t ) {
                    log.error("Host registering failed!", t );
                }
            }
        }
    }

    public synchronized void destroy() throws Exception {
        // destroy our child containers, if any
        Container children[] = findChildren();
        super.destroy();
        for (int i = 0; i < children.length; i++) {
            if(children[i] instanceof StandardContext)
                ((StandardContext)children[i]).destroy();
        }
      
    }
    
    public ObjectName preRegister(MBeanServer server, ObjectName oname ) 
        throws Exception
    {
        ObjectName res=super.preRegister(server, oname);
        String name=oname.getKeyProperty("host");
        if( name != null )
            setName( name );
        return res;        
    }
    
    public ObjectName createObjectName(String domain, ObjectName parent)
        throws Exception
    {
        if( log.isDebugEnabled())
            log.debug("Create ObjectName " + domain + " " + parent );
        return new ObjectName( domain + ":type=Host,host=" + getName());
    }

}
