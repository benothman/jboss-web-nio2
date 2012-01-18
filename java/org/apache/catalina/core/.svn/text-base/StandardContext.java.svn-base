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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.EventListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.TreeMap;

import javax.management.AttributeNotFoundException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.naming.directory.DirContext;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextAttributeListener;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
import javax.servlet.ServletRequestAttributeListener;
import javax.servlet.ServletRequestListener;
import javax.servlet.SessionTrackingMode;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionListener;

import org.apache.catalina.Authenticator;
import org.apache.catalina.Container;
import org.apache.catalina.ContainerListener;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Globals;
import org.apache.catalina.Host;
import org.apache.catalina.InstanceListener;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Loader;
import org.apache.catalina.Manager;
import org.apache.catalina.ThreadBindingListener;
import org.apache.catalina.Wrapper;
import org.apache.catalina.deploy.ApplicationParameter;
import org.apache.catalina.deploy.ErrorPage;
import org.apache.catalina.deploy.FilterDef;
import org.apache.catalina.deploy.FilterMap;
import org.apache.catalina.deploy.JspPropertyGroup;
import org.apache.catalina.deploy.LoginConfig;
import org.apache.catalina.deploy.SecurityCollection;
import org.apache.catalina.deploy.SecurityConstraint;
import org.apache.catalina.deploy.SessionCookie;
import org.apache.catalina.deploy.jsp.TagLibraryInfo;
import org.apache.catalina.session.StandardManager;
import org.apache.catalina.util.CharsetMapper;
import org.apache.catalina.util.RequestUtil;
import org.apache.catalina.util.URLEncoder;
import org.apache.naming.resources.BaseDirContext;
import org.apache.naming.resources.DirContextURLStreamHandler;
import org.apache.naming.resources.FileDirContext;
import org.apache.naming.resources.ProxyDirContext;
import org.apache.naming.resources.WARDirContext;
import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.util.modeler.Registry;
import org.jboss.logging.Logger;

/**
 * Standard implementation of the <b>Context</b> interface.  Each
 * child container must be a Wrapper implementation to process the
 * requests directed to a particular servlet.
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 * @version $Revision$ $Date$
 */

public class StandardContext
    extends ContainerBase
    implements Context, NotificationEmitter
{
    protected static Logger log = Logger.getLogger(StandardContext.class);

    // ----------------------------------------------------------- Constructors


    /**
     * Create a new StandardContext component with the default basic Valve.
     */
    public StandardContext() {

        super();
        pipeline.setBasic(new StandardContextValve());
        broadcaster = new NotificationBroadcasterSupport();

    }


    // ----------------------------------------------------- Class Variables


    /**
     * The descriptive information string for this implementation.
     */
    protected static final String info =
        "org.apache.catalina.core.StandardContext/1.0";


    /**
     * Array containing the safe characters set.
     */
    protected static URLEncoder urlEncoder;


    /**
     * GMT timezone - all HTTP dates are on GMT
     */
    static {
        urlEncoder = new URLEncoder();
        urlEncoder.addSafeCharacter('~');
        urlEncoder.addSafeCharacter('-');
        urlEncoder.addSafeCharacter('_');
        urlEncoder.addSafeCharacter('.');
        urlEncoder.addSafeCharacter('*');
        urlEncoder.addSafeCharacter('/');
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * The alternate deployment descriptor name.
     */
    protected String altDDName = null;


    /**
     * Lifecycle provider.
     */
    protected InstanceManager instanceManager = null;


   /**
     * Associated host name.
     */
    protected String hostName;

    
    /**
     * The set of application listener class names configured for this
     * application, in the order they were encountered in the web.xml file.
     */
    protected String applicationListeners[] = new String[0];


    /**
     * The set of application listener class names configured for this
     * application that have been added from TLDs, and have a limited access to
     * the servlet context.
     */
    protected HashSet<String> restrictedApplicationListeners = new HashSet<String>();


    /**
     * The set of application listener class names configured for this
     * application, in the order they were encountered in the web.xml file.
     */
    protected EventListener applicationListenerInstances[] = new EventListener[0];


    /**
     * The set of instantiated application event listener objects</code>.
     */
    protected Object applicationEventListenersInstances[] = null;


    /**
     * The set of instantiated application lifecycle listener objects</code>.
     */
    protected Object applicationLifecycleListenersInstances[] = null;


    /**
     * The set of instantiated application session lifecycle listener objects</code>.
     */
    protected Object applicationSessionLifecycleListenersInstances[] = null;


    /**
     * The set of instantiated listener objects</code>.
     */
    protected Object listenersInstances[] = null;


    /**
     * The set of application parameters defined for this application.
     */
    protected ApplicationParameter applicationParameters[] =
        new ApplicationParameter[0];


    /**
     * The application authenticator for this Context. This is simply a reference
     * and the authenticator should still be set as a valve.
     */
    protected Authenticator authenticator = null;
    
    /**
     * The application available flag for this Context.
     */
    protected boolean available = false;
    
    /**
     * The application starting flag for this Context.
     */
    protected boolean starting = false;
    
    /**
     * The broadcaster that sends j2ee notifications. 
     */
    protected NotificationBroadcasterSupport broadcaster = null;
    
    /**
     * The Locale to character set mapper for this application.
     */
    protected CharsetMapper charsetMapper = null;


    /**
     * The Java class name of the CharsetMapper class to be created.
     */
    protected String charsetMapperClass =
      "org.apache.catalina.util.CharsetMapper";


    /**
     * The "correctly configured" flag for this Context.
     */
    protected boolean configured = false;


    /**
     * The security constraints for this web application.
     */
    protected SecurityConstraint constraints[] = new SecurityConstraint[0];


    /**
     * The ServletContext implementation associated with this Context.
     */
    protected ApplicationContext context = null;


    /**
    * The class name of the context configurator.
    */
    protected String configClass = null;


    /**
     * Session tracking modes.
     */
    protected Set<SessionTrackingMode> defaultSessionTrackingModes = 
        EnumSet.of(SessionTrackingMode.URL, SessionTrackingMode.COOKIE /*TODO:, SessionTrackingMode.SSL*/);
    

    /**
     * Session tracking modes.
     */
    protected Set<SessionTrackingMode> sessionTrackingModes = null;
    

   /**
     * Should we allow the <code>ServletContext.getContext()</code> method
     * to access the context of other web applications in this server?
     */
    protected boolean crossContext = 
        Boolean.valueOf(System.getProperty("org.apache.catalina.core.StandardContext.CROSS_CONTEXT", "false")).booleanValue();

    
    /**
     * Encoded path.
     */
    protected String encodedPath = null;
    

    /**
     * The display name of this web application.
     */
    protected String displayName = null;


    /**
     * The distributable flag for this web application.
     */
    protected boolean distributable = false;


    /**
     * The document root for this web application.
     */
    protected String docBase = null;


    /**
     * The exception pages for this web application, keyed by fully qualified
     * class name of the Java exception.
     */
    protected HashMap<String, ErrorPage> exceptionPages = new HashMap<String, ErrorPage>();


    /**
     * The set of filter configurations (and associated filter instances) we
     * have initialized, keyed by filter name.
     */
    protected HashMap<String, ApplicationFilterConfig> filterConfigs = new HashMap<String, ApplicationFilterConfig>();


    /**
     * The set of filter definitions for this application, keyed by
     * filter name.
     */
    protected HashMap<String, FilterDef> filterDefs = new HashMap<String, FilterDef>();


    /**
     * The set of filter mappings for this application, in the order
     * they were defined in the deployment descriptor with additional mappings
     * added via the {@link ServletContext} possibly both before and after those
     * defined in the deployment descriptor.
     */
    protected FilterMap filterMaps[] = new FilterMap[0];


    /**
     * Filter mappings added via {@link ServletContext} may have to be inserted
     * before the mappings in the deploymenmt descriptor but must be inserted in
     * the order the {@link ServletContext} methods are called. This isn't an
     * issue for the mappings added after the deployment descriptor - they are
     * just added to the end - but correctly the adding mappings before the
     * deployment descriptor mappings requires knowing where the last 'before'
     * mapping was added.
     */
    protected int filterMapInsertPoint = 0;


    /**
     * Ignore annotations.
     */
    protected boolean ignoreAnnotations = false;


    /**
     * The set of classnames of InstanceListeners that will be added
     * to each newly created Wrapper by <code>createWrapper()</code>.
     */
    protected String instanceListeners[] = new String[0];

    
    /**
     * The set of JSP property groups defined for the webapp, keyed by pattern.
     */
    protected LinkedHashMap<String, JspPropertyGroup> jspPropertyGroups = new LinkedHashMap<String, JspPropertyGroup>();
    

    /**
     * The set of taglibs defined for the webapp, keyed by uri.
     */
    protected HashMap<String, TagLibraryInfo> jspTagLibraries = new HashMap<String, TagLibraryInfo>();
    

    /**
     * The logical name of the webapp, if any which may be used in other descriptors.
     */
    protected String logicalName = null;


    /**
     * The login configuration descriptor for this web application.
     */
    protected LoginConfig loginConfig = null;


    /**
     * The mapper associated with this context.
     */
    protected org.apache.tomcat.util.http.mapper.Mapper mapper = 
        new org.apache.tomcat.util.http.mapper.Mapper();


    /**
     * The MIME mappings for this web application, keyed by extension.
     */
    protected HashMap<String, String> mimeMappings = new HashMap<String, String>();


     /**
      * Special case: error page for status 200.
      */
     protected ErrorPage okErrorPage = null;


    /**
     * The context initialization parameters for this web application,
     * keyed by name.
     */
    protected HashMap<String, String> parameters = new HashMap<String, String>();


    /**
     * The request processing pause flag (while reloading occurs)
     */
    protected boolean paused = false;


    /**
     * The public identifier of the DTD for the web application deployment
     * descriptor version we are currently parsing.  This is used to support
     * relaxed validation rules when processing version 2.2 web.xml files.
     */
    protected String publicId = null;

    
    /**
     * Version number.
     */
    protected String version = null;


    /**
     * Version number.
     */
    protected int versionMinor = 0;


    /**
     * Version number.
     */
    protected int versionMajor = 0;


    /**
     * The override flag for this web application.
     */
    protected boolean override = false;


    /**
     * The privileged flag for this web application.
     */
    protected boolean privileged = false;


    /**
     * Should the next call to <code>addWelcomeFile()</code> cause replacement
     * of any existing welcome files?  This will be set before processing the
     * web application's deployment descriptor, so that application specified
     * choices <strong>replace</strong>, rather than append to, those defined
     * in the global descriptor.
     */
    protected boolean replaceWelcomeFiles = false;


    /**
     * The security role mappings for this application, keyed by role
     * name (as used within the application).
     */
    protected HashMap<String, String> roleMappings = new HashMap<String, String>();


    /**
     * The security roles for this application, keyed by role name.
     */
    protected String securityRoles[] = new String[0];


    /**
     * The servlet mappings for this web application, keyed by
     * matching pattern.
     */
    protected HashMap<String, String> servletMappings = new HashMap<String, String>();


    /**
     * The session timeout (in minutes) for this web application.
     */
    protected int sessionTimeout = 30;

    /**
     * The notification sequence number.
     */
    protected long sequenceNumber = 0;

    /**
     * The session cookie.
     */
    protected SessionCookie sessionCookie = new SessionCookie();
    
    /**
     * The status code error pages for this web application, keyed by
     * HTTP status code (as an Integer).
     */
    protected HashMap<Integer, ErrorPage> statusPages = new HashMap<Integer, ErrorPage>();


    /**
     * The JSP tag libraries for this web application, keyed by URI
     */
    protected HashMap<String, String> taglibs = new HashMap<String, String>();


    /**
     * Amount of ms that the container will wait for servlets to unload.
     */
    protected long unloadDelay = 2000;


    /**
     * The welcome files for this application.
     */
    protected String welcomeFiles[] = new String[0];


    /**
     * The set of classnames of LifecycleListeners that will be added
     * to each newly created Wrapper by <code>createWrapper()</code>.
     */
    protected String wrapperLifecycles[] = new String[0];


    /**
     * The set of classnames of ContainerListeners that will be added
     * to each newly created Wrapper by <code>createWrapper()</code>.
     */
    protected String wrapperListeners[] = new String[0];


    /**
     * The pathname to the work directory for this context (relative to
     * the server's home if not absolute).
     */
    protected String workDir = null;


    /**
     * Java class name of the Wrapper class implementation we use.
     */
    protected String wrapperClassName = StandardWrapper.class.getName();
    protected Class<?> wrapperClass = null;


    /**
     * Filesystem based flag.
     */
    protected boolean filesystemBased = false;


    /**
     * Caching allowed flag.
     */
    protected boolean cachingAllowed = true;


    /**
     * Case sensitivity.
     */
    protected boolean caseSensitive = true;


    /**
     * Allow linking.
     */
    protected boolean allowLinking = false;


    /**
     * Cache max size in KB.
     */
    protected int cacheMaxSize = (org.apache.tomcat.util.Constants.LOW_MEMORY) ? 128 : 10240; // 10 MB


    /**
     * Cache object max size in KB.
     */
    protected int cacheObjectMaxSize = (org.apache.tomcat.util.Constants.LOW_MEMORY) ? 8 : 256; // 256K


    /**
     * Cache TTL in ms.
     */
    protected int cacheTTL = 5000;


    /**
     * Non proxied resources.
     */
    protected DirContext webappResources = null;

    protected long startupTime;
    protected long startTime;
    protected long tldScanTime;

    /** 
     * Name of the engine. If null, the domain is used.
     */ 
    protected String engineName = null;
    protected String j2EEApplication="none";
    protected String j2EEServer="none";


    protected static final ThreadBindingListener DEFAULT_NAMING_LISTENER = (new ThreadBindingListener() {
        public void bind() {}
        public void unbind() {}
    });
    protected ThreadBindingListener threadBindingListener = DEFAULT_NAMING_LISTENER;


    // ----------------------------------------------------- Context Properties


    public InstanceManager getInstanceManager() {
       return instanceManager;
    }


    public void setInstanceManager(InstanceManager instanceManager) {
       this.instanceManager = instanceManager;
    }

    
    public String getEncodedPath() {
        return encodedPath;
    }


    public void setName( String name ) {
        super.setName( name );
        encodedPath = urlEncoder.encode(name);
    }


    /**
     * Is caching allowed ?
     */
    public boolean isCachingAllowed() {
        return cachingAllowed;
    }


    /**
     * Set caching allowed flag.
     */
    public void setCachingAllowed(boolean cachingAllowed) {
        this.cachingAllowed = cachingAllowed;
    }


    /**
     * Set case sensitivity.
     */
    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }


    /**
     * Is case sensitive ?
     */
    public boolean isCaseSensitive() {
        return caseSensitive;
    }


    /**
     * Set allow linking.
     */
    public void setAllowLinking(boolean allowLinking) {
        this.allowLinking = allowLinking;
    }


    /**
     * Is linking allowed.
     */
    public boolean isAllowLinking() {
        return allowLinking;
    }


    /**
     * Set cache TTL.
     */
    public void setCacheTTL(int cacheTTL) {
        this.cacheTTL = cacheTTL;
    }


    /**
     * Get cache TTL.
     */
    public int getCacheTTL() {
        return cacheTTL;
    }


    /**
     * Return the maximum size of the cache in KB.
     */
    public int getCacheMaxSize() {
        return cacheMaxSize;
    }


    /**
     * Set the maximum size of the cache in KB.
     */
    public void setCacheMaxSize(int cacheMaxSize) {
        this.cacheMaxSize = cacheMaxSize;
    }


    /**
     * Return the maximum size of objects to be cached in KB.
     */
    public int getCacheObjectMaxSize() {
        return cacheObjectMaxSize;
    }


    /**
     * Set the maximum size of objects to be placed the cache in KB.
     */
    public void setCacheObjectMaxSize(int cacheObjectMaxSize) {
        this.cacheObjectMaxSize = cacheObjectMaxSize;
    }


    /**
     * Returns true if the resources associated with this context are
     * filesystem based.
     */
    public boolean isFilesystemBased() {
        return (filesystemBased);
    }


    /**
     * Force setting if filesystem based.
     */
    public void setFilesystemBased(boolean filesystemBased) {
        this.filesystemBased = filesystemBased;
    }


    /**
     * Return the set of initialized application event listener objects,
     * in the order they were specified in the web application deployment
     * descriptor, for this application.
     *
     * @exception IllegalStateException if this method is called before
     *  this application has started, or after it has been stopped
     */
    public Object[] getApplicationEventListeners() {
        return (applicationEventListenersInstances != null 
                ? applicationEventListenersInstances : listenersInstances);
    }


    /**
     * Store the set of initialized application event listener objects,
     * in the order they were specified in the web application deployment
     * descriptor, for this application.
     *
     * @param listeners The set of instantiated listener objects.
     */
    public void setApplicationEventListeners(Object listeners[]) {
        applicationEventListenersInstances = listeners;
    }


    /**
     * Return the set of initialized application lifecycle listener objects,
     * in the order they were specified in the web application deployment
     * descriptor, for this application.
     *
     * @exception IllegalStateException if this method is called before
     *  this application has started, or after it has been stopped
     */
    public Object[] getApplicationSessionLifecycleListeners() {
        return (applicationSessionLifecycleListenersInstances != null 
                ? applicationSessionLifecycleListenersInstances : listenersInstances);
    }


    /**
     * Store the set of initialized application lifecycle listener objects,
     * in the order they were specified in the web application deployment
     * descriptor, for this application.
     *
     * @param listeners The set of instantiated listener objects.
     */
    public void setApplicationSessionLifecycleListeners(Object listeners[]) {
        applicationSessionLifecycleListenersInstances = listeners;
    }


    /**
     * Return the set of initialized application lifecycle listener objects,
     * in the order they were specified in the web application deployment
     * descriptor, for this application.
     *
     * @exception IllegalStateException if this method is called before
     *  this application has started, or after it has been stopped
     */
    public Object[] getApplicationLifecycleListeners() {
        return (applicationLifecycleListenersInstances);
    }


    /**
     * Store the set of initialized application lifecycle listener objects,
     * in the order they were specified in the web application deployment
     * descriptor, for this application.
     *
     * @param listeners The set of instantiated listener objects.
     */
    public void setApplicationLifecycleListeners(Object listeners[]) {
        applicationLifecycleListenersInstances = listeners;
    }


    /**
     * Return the application authenticator for this Context.
     */
    public Authenticator getAuthenticator() {
        return (this.authenticator);
    }


    /**
     * Set the application authenticator for this Context.
     *
     * @param authenticator The new application authenticator
     */
    public void setAuthenticator(Authenticator authenticator) {
        Authenticator oldAuthenticator = this.authenticator;
        this.authenticator = authenticator;
        support.firePropertyChange("authenticator", oldAuthenticator, 
                this.authenticator);
    }


    /**
     * Return the application available flag for this Context.
     */
    public boolean getAvailable() {
        return (this.available);
    }


    /**
     * Set the application available flag for this Context.
     *
     * @param available The new application available flag
     */
    public void setAvailable(boolean available) {
        boolean oldAvailable = this.available;
        this.available = available;
        support.firePropertyChange("available", oldAvailable, this.available);
    }


    /**
     * Return the application starting flag for this Context.
     */
    public boolean isStarting() {
        return (this.starting);
    }


    /**
     * Set the application starting flag for this Context.
     *
     * @param starting The new application starting flag
     */
    public void setStarting(boolean starting) {
        this.starting = starting;
    }


   /**
     * Return the Locale to character set mapper for this Context.
     */
    public CharsetMapper getCharsetMapper() {

        // Create a mapper the first time it is requested
        if (this.charsetMapper == null) {
            try {
                Class<?> clazz = Class.forName(charsetMapperClass);
                this.charsetMapper = (CharsetMapper) clazz.newInstance();
            } catch (Throwable t) {
                this.charsetMapper = new CharsetMapper();
            }
        }

        return (this.charsetMapper);

    }


    /**
     * Set the Locale to character set mapper for this Context.
     *
     * @param mapper The new mapper
     */
    public void setCharsetMapper(CharsetMapper mapper) {
        CharsetMapper oldCharsetMapper = this.charsetMapper;
        this.charsetMapper = mapper;
        if( mapper != null )
            this.charsetMapperClass= mapper.getClass().getName();
        support.firePropertyChange("charsetMapper", oldCharsetMapper,
                                   this.charsetMapper);
    }

    /**
     * Return the class name of the context configurator.
     */
    public String getConfigClass() {
        return (this.configClass);
    }


    /**
     * Set the class name of the context configurator.
     *
     * @param configClass The class name of the listener.
     */
    public void setConfigClass(String configClass) {
        this.configClass = configClass;
    }
    
    
    /**
     * Return the "correctly configured" flag for this Context.
     */
    public boolean getConfigured() {
        return (this.configured);
    }


    /**
     * Set the "correctly configured" flag for this Context.  This can be
     * set to false by startup listeners that detect a fatal configuration
     * error to avoid the application from being made available.
     *
     * @param configured The new correctly configured flag
     */
    public void setConfigured(boolean configured) {
        boolean oldConfigured = this.configured;
        this.configured = configured;
        support.firePropertyChange("configured", oldConfigured, this.configured);
    }


    /**
     * Return the "use cookies for session ids" flag.
     */
    public boolean getCookies() {
        return (getSessionTrackingModes().contains(SessionTrackingMode.COOKIE));
    }


    /**
     * Set the "use cookies for session ids" flag.
     *
     * @param cookies The new flag
     */
    public void setCookies(boolean cookies) {
        boolean oldCookies = defaultSessionTrackingModes.contains(SessionTrackingMode.COOKIE);
        if (oldCookies && !cookies) {
            defaultSessionTrackingModes.remove(SessionTrackingMode.COOKIE);
        }
        if (!oldCookies && cookies) {
            defaultSessionTrackingModes.add(SessionTrackingMode.COOKIE);
        }
        if (oldCookies != cookies) {
            support.firePropertyChange("cookies", oldCookies, cookies);
        }
    }


    /**
     * Return the "allow crossing servlet contexts" flag.
     */
    public boolean getCrossContext() {
        return (this.crossContext);
    }


    /**
     * Set the "allow crossing servlet contexts" flag.
     *
     * @param crossContext The new cross contexts flag
     */
    public void setCrossContext(boolean crossContext) {
        boolean oldCrossContext = this.crossContext;
        this.crossContext = crossContext;
        support.firePropertyChange("crossContext", oldCrossContext, this.crossContext);
    }

    /**
     * Gets the time (in milliseconds) it took to start this context.
     *
     * @return Time (in milliseconds) it took to start this context.
     */
    public long getStartupTime() {
        return startupTime;
    }

    public void setStartupTime(long startupTime) {
        this.startupTime = startupTime;
    }

    public long getTldScanTime() {
        return tldScanTime;
    }

    public void setTldScanTime(long tldScanTime) {
        this.tldScanTime = tldScanTime;
    }

    /**
     * Return the display name of this web application.
     */
    public String getDisplayName() {
        return (this.displayName);
    }


    /**
     * Return the alternate Deployment Descriptor name.
     */
    public String getAltDDName(){
        return altDDName;
    }


    /**
     * Set an alternate Deployment Descriptor name.
     */
    public void setAltDDName(String altDDName) {
        this.altDDName = altDDName;
        if (context != null) {
            context.setAttribute(Globals.ALT_DD_ATTR,altDDName);
        }
    }


    /**
     * Set the display name of this web application.
     *
     * @param displayName The new display name
     */
    public void setDisplayName(String displayName) {
        String oldDisplayName = this.displayName;
        this.displayName = displayName;
        support.firePropertyChange("displayName", oldDisplayName, this.displayName);
    }


    /**
     * Return the distributable flag for this web application.
     */
    public boolean getDistributable() {
        return (this.distributable);
    }

    /**
     * Set the distributable flag for this web application.
     *
     * @param distributable The new distributable flag
     */
    public void setDistributable(boolean distributable) {
        boolean oldDistributable = this.distributable;
        this.distributable = distributable;
        support.firePropertyChange("distributable", oldDistributable, this.distributable);
        // Bugzilla 32866
        if( getManager() != null) {
            getManager().setDistributable(distributable);
        }
    }


    /**
     * Return the document root for this Context.  This can be an absolute
     * pathname, a relative pathname, or a URL.
     */
    public String getDocBase() {
        return (this.docBase);
    }

    /**
     * Set the document root for this Context.  This can be an absolute
     * pathname, a relative pathname, or a URL.
     *
     * @param docBase The new document root
     */
    public void setDocBase(String docBase) {
        this.docBase = docBase;
    }

    /**
     * Return descriptive information about this Container implementation and
     * the corresponding version number, in the format
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    public String getInfo() {

        return (info);

    }

    public String getEngineName() {
        if( engineName != null ) return engineName;
        return domain;
    }

    public void setEngineName(String engineName) {
        this.engineName = engineName;
    }

    public String getJ2EEApplication() {
        return j2EEApplication;
    }

    public void setJ2EEApplication(String j2EEApplication) {
        this.j2EEApplication = j2EEApplication;
    }

    public String getJ2EEServer() {
        return j2EEServer;
    }

    public void setJ2EEServer(String j2EEServer) {
        this.j2EEServer = j2EEServer;
    }


    /**
     * Set the Loader with which this Context is associated.
     *
     * @param loader The newly associated loader
     */
    public void setLoader(Loader loader) {
        super.setLoader(loader);
    }


    /**
     * Return the boolean on the annotations parsing.
     */
    public boolean getIgnoreAnnotations() {
        return this.ignoreAnnotations;
    }
    
    
    /**
     * Set the boolean on the annotations parsing for this web 
     * application.
     * 
     * @param ignoreAnnotations The boolean on the annotations parsing
     */
    public void setIgnoreAnnotations(boolean ignoreAnnotations) {
        boolean oldIgnoreAnnotations = this.ignoreAnnotations;
        this.ignoreAnnotations = ignoreAnnotations;
        support.firePropertyChange("ignoreAnnotations", oldIgnoreAnnotations, this.ignoreAnnotations);
    }
    
    
    /**
     * Set the session cookie configuration.
     *
     * @param sessionCookie The new value
     */
    public void setSessionCookie(SessionCookie sessionCookie) {
        SessionCookie oldSessionCookie = this.sessionCookie;
        this.sessionCookie = sessionCookie;
        support.firePropertyChange("sessionCookie", oldSessionCookie, sessionCookie);
    }


    /**
     * Return the session cookie configuration.
     */
    public SessionCookie getSessionCookie() {
        return this.sessionCookie;
    }


    /**
     * Return the logical name for this web application.
     */
    public String getLogicalName() {
        return logicalName;
    }


    /**
     * Set the logical name for this web application.
     *
     * @param logicalName The new logical name
     */
    public void setLogicalName(String logicalName) {
        String oldLogicalName = this.logicalName;
        this.logicalName = logicalName;
        support.firePropertyChange("logicalName", oldLogicalName, logicalName);
    }


    /**
     * Return the login configuration descriptor for this web application.
     */
    public LoginConfig getLoginConfig() {
        return (this.loginConfig);
    }


    /**
     * Set the login configuration descriptor for this web application.
     *
     * @param config The new login configuration
     */
    public void setLoginConfig(LoginConfig config) {

        // Validate the incoming property value
        if (config == null)
            throw new IllegalArgumentException
                (sm.getString("standardContext.loginConfig.required"));
        String loginPage = config.getLoginPage();
        if ((loginPage != null) && !loginPage.startsWith("/")) {
            if (isServlet22()) {
                if(log.isDebugEnabled())
                    log.debug(sm.getString("standardContext.loginConfig.loginWarning",
                                 loginPage));
                config.setLoginPage("/" + loginPage);
            } else {
                throw new IllegalArgumentException
                    (sm.getString("standardContext.loginConfig.loginPage",
                                  loginPage));
            }
        }
        String errorPage = config.getErrorPage();
        if ((errorPage != null) && !errorPage.startsWith("/")) {
            if (isServlet22()) {
                if(log.isDebugEnabled())
                    log.debug(sm.getString("standardContext.loginConfig.errorWarning",
                                 errorPage));
                config.setErrorPage("/" + errorPage);
            } else {
                throw new IllegalArgumentException
                    (sm.getString("standardContext.loginConfig.errorPage",
                                  errorPage));
            }
        }

        // Process the property setting change
        LoginConfig oldLoginConfig = this.loginConfig;
        this.loginConfig = config;
        support.firePropertyChange("loginConfig",
                                   oldLoginConfig, this.loginConfig);

    }


    /**
     * Get the mapper associated with the context.
     */
    public org.apache.tomcat.util.http.mapper.Mapper getMapper() {
        return (mapper);
    }


    /**
     * Return the context path for this Context.
     */
    public String getPath() {
        return (getName());
    }

    
    /**
     * Set the context path for this Context.
     * <p>
     * <b>IMPLEMENTATION NOTE</b>:  The context path is used as the "name" of
     * a Context, because it must be unique.
     *
     * @param path The new context path
     */
    public void setPath(String path) {
        setName(path);
    }


    /**
     * Return the public identifier of the deployment descriptor DTD that is
     * currently being parsed.
     */
    public String getPublicId() {
        return (this.publicId);
    }


    /**
     * Set the public identifier of the deployment descriptor DTD that is
     * currently being parsed.
     *
     * @param publicId The public identifier
     */
    public void setPublicId(String publicId) {
        String oldPublicId = this.publicId;
        this.publicId = publicId;
        support.firePropertyChange("publicId", oldPublicId, publicId);
    }


    /**
     * Return the Servlet API version defined for the webapp.
     */
    public String getVersion() {
        return this.version;
    }


    /**
     * Return the Servlet API version defined for the webapp.
     */
    public int getVersionMajor() {
        if (version != null) {
            int pos = version.indexOf('.');
            if (pos != -1) {
                versionMajor = Integer.parseInt(version.substring(0, pos));
            }
        }
        return versionMajor;
    }


    /**
     * Return the Servlet API version defined for the webapp.
     */
    public int getVersionMinor() {
        if (version != null) {
            int pos = version.indexOf('.');
            if (pos < version.length()) {
                versionMinor = Integer.parseInt(version.substring(pos + 1));
            }
        }
        return versionMinor;
    }


    /**
     * Set the Servlet API version defined for the webapp.
     *
     * @param version The version
     */
    public void setVersion(String version) {
        String oldVersion = this.version;
        this.version = version;
        getVersionMajor();
        getVersionMinor();
        support.firePropertyChange("version", oldVersion, version);
    }


    /**
     * Return the DefaultContext override flag for this web application.
     */
    public boolean getOverride() {
        return (this.override);
    }


    /**
     * Return the parent class loader (if any) for this web application.
     * This call is meaningful only <strong>after</strong> a Loader has
     * been configured.
     */
    public ClassLoader getParentClassLoader() {
        if (parentClassLoader != null)
            return (parentClassLoader);
        if (getPrivileged()) {
            return this.getClass().getClassLoader();
        } else if (parent != null) {
            return (parent.getParentClassLoader());
        }
        return (ClassLoader.getSystemClassLoader());
    }

    
    /**
     * Return the privileged flag for this web application.
     */
    public boolean getPrivileged() {
        return (this.privileged);
    }


    /**
     * Set the privileged flag for this web application.
     *
     * @param privileged The new privileged flag
     */
    public void setPrivileged(boolean privileged) {
        boolean oldPrivileged = this.privileged;
        this.privileged = privileged;
        support.firePropertyChange("privileged", oldPrivileged, this.privileged);
    }


    /**
     * Set the override flag for this web application.
     *
     * @param override The new override flag
     */
    public void setOverride(boolean override) {
        boolean oldOverride = this.override;
        this.override = override;
        support.firePropertyChange("override", oldOverride, this.override);
    }


    public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
        return defaultSessionTrackingModes;
    }


    public Set<SessionTrackingMode> getSessionTrackingModes() {
        if (sessionTrackingModes == null) {
            return defaultSessionTrackingModes;
        } else {
            return sessionTrackingModes;
        }
    }


    public void setSessionTrackingModes(
            Set<SessionTrackingMode> sessionTrackingModes) {
        this.sessionTrackingModes = sessionTrackingModes;
    }
    
    
    public void addSessionTrackingMode(String trackingMode) {
        SessionTrackingMode mode = SessionTrackingMode.valueOf(trackingMode);
        if (mode == null) {
            // FIXME: error message
            throw new IllegalArgumentException();
        }
        if (sessionTrackingModes == null) {
            sessionTrackingModes = new HashSet<SessionTrackingMode>();
        }
        sessionTrackingModes.add(mode);
    }
    
    
    /**
     * Return the "replace welcome files" property.
     */
    public boolean isReplaceWelcomeFiles() {
        return (this.replaceWelcomeFiles);
    }


    /**
     * Set the "replace welcome files" property.
     *
     * @param replaceWelcomeFiles The new property value
     */
    public void setReplaceWelcomeFiles(boolean replaceWelcomeFiles) {
        boolean oldReplaceWelcomeFiles = this.replaceWelcomeFiles;
        this.replaceWelcomeFiles = replaceWelcomeFiles;
        support.firePropertyChange("replaceWelcomeFiles", oldReplaceWelcomeFiles, this.replaceWelcomeFiles);
    }


    /**
     * Return the servlet context for which this Context is a facade.
     */
    public ServletContext getServletContext() {
        if (context == null) {
            context = new ApplicationContext(getBasePath(), this);
            if (altDDName != null)
                context.setAttribute(Globals.ALT_DD_ATTR,altDDName);
        }
        return (context.getFacade());
    }


    /**
     * Return the default session timeout (in minutes) for this
     * web application.
     */
    public int getSessionTimeout() {
        return (this.sessionTimeout);
    }


    /**
     * Set the default session timeout (in minutes) for this
     * web application.
     *
     * @param timeout The new default session timeout
     */
    public void setSessionTimeout(int timeout) {
        int oldSessionTimeout = this.sessionTimeout;
        /*
         * SRV.13.4 ("Deployment Descriptor"):
         * If the timeout is 0 or less, the container ensures the default
         * behaviour of sessions is never to time out.
         */
        this.sessionTimeout = (timeout == 0) ? -1 : timeout;
        support.firePropertyChange("sessionTimeout", oldSessionTimeout, this.sessionTimeout);
    }


    /**
     * Return the value of the unloadDelay flag.
     */
    public long getUnloadDelay() {
        return (this.unloadDelay);
    }

    
    /**
     * Set the value of the unloadDelay flag, which represents the amount
     * of ms that the container will wait when unloading servlets.
     * Setting this to a small value may cause more requests to fail 
     * to complete when stopping a web application.
     *
     * @param unloadDelay The new value
     */
    public void setUnloadDelay(long unloadDelay) {
        long oldUnloadDelay = this.unloadDelay;
        this.unloadDelay = unloadDelay;
        support.firePropertyChange("unloadDelay", oldUnloadDelay, this.unloadDelay);
    }


    /**
     * Return the Java class name of the Wrapper implementation used
     * for servlets registered in this Context.
     */
    public String getWrapperClass() {
        return (this.wrapperClassName);
    }


    /**
     * Set the Java class name of the Wrapper implementation used
     * for servlets registered in this Context.
     *
     * @param wrapperClassName The new wrapper class name
     *
     * @throws IllegalArgumentException if the specified wrapper class
     * cannot be found or is not a subclass of StandardWrapper
     */
    public void setWrapperClass(String wrapperClassName) {
        this.wrapperClassName = wrapperClassName;
        try {
            wrapperClass = Class.forName(wrapperClassName);         
            if (!StandardWrapper.class.isAssignableFrom(wrapperClass)) {
                throw new IllegalArgumentException(
                    sm.getString("standardContext.invalidWrapperClass",
                                 wrapperClassName));
            }
        } catch (ClassNotFoundException cnfe) {
            throw new IllegalArgumentException(cnfe.getMessage());
        }
    }


    /**
     * Set the resources DirContext object with which this Container is
     * associated.
     *
     * @param resources The newly associated DirContext
     */
    public void setResources(DirContext resources) {

        if (started) {
            throw new IllegalStateException
                (sm.getString("standardContext.resources.started"));
        }

        DirContext oldResources = this.webappResources;
        if (oldResources == resources)
            return;

        if (resources instanceof BaseDirContext) {
            ((BaseDirContext) resources).setCached(isCachingAllowed());
            ((BaseDirContext) resources).setCacheTTL(getCacheTTL());
            ((BaseDirContext) resources).setCacheMaxSize(getCacheMaxSize());
            ((BaseDirContext) resources).setCacheObjectMaxSize(getCacheObjectMaxSize());
        }
        if (resources instanceof FileDirContext) {
            filesystemBased = true;
            ((FileDirContext) resources).setCaseSensitive(isCaseSensitive());
            ((FileDirContext) resources).setAllowLinking(isAllowLinking());
        }
        this.webappResources = resources;

        // The proxied resources will be refreshed on start
        this.resources = null;

        support.firePropertyChange("resources", oldResources,
                                   this.webappResources);

    }


    /**
     * Get the associated ThreadBindingListener.
     */
    public ThreadBindingListener getThreadBindingListener() {
        return threadBindingListener;
    }


    /**
     * Get the associated ThreadBindingListener.
     */
    public void setThreadBindingListener(ThreadBindingListener threadBindingListener) {
        this.threadBindingListener = threadBindingListener;
    }


    // ------------------------------------------------------ Public Properties


    /**
     * Return the Locale to character set mapper class for this Context.
     */
    public String getCharsetMapperClass() {
        return (this.charsetMapperClass);
    }


    /**
     * Set the Locale to character set mapper class for this Context.
     *
     * @param mapper The new mapper class
     */
    public void setCharsetMapperClass(String mapper) {
        String oldCharsetMapperClass = this.charsetMapperClass;
        this.charsetMapperClass = mapper;
        support.firePropertyChange("charsetMapperClass",
                                   oldCharsetMapperClass,
                                   this.charsetMapperClass);
    }


    /** Get the absolute path to the work dir.
     *  To avoid duplication.
     * 
     * @return The work path
     */ 
    public String getWorkPath() {
        if (getWorkDir() == null) {
            return null;
        }
        File workDir = new File(getWorkDir());
        if (!workDir.isAbsolute()) {
            File catalinaHome = workBase();
            String catalinaHomePath = null;
            try {
                catalinaHomePath = catalinaHome.getCanonicalPath();
                workDir = new File(catalinaHomePath,
                        getWorkDir());
            } catch (IOException e) {
                log.warn("Exception obtaining work path for " + getPath());
            }
        }
        return workDir.getAbsolutePath();
    }
    
    /**
     * Return the work directory for this Context.
     */
    public String getWorkDir() {
        return (this.workDir);
    }


    /**
     * Set the work directory for this Context.
     *
     * @param workDir The new work directory
     */
    public void setWorkDir(String workDir) {
        this.workDir = workDir;
        if (started) {
            postWorkDirectory();
        }
    }


    // -------------------------------------------------------- Context Methods


    /**
     * Add a new Listener class name to the set of Listeners
     * configured for this application.
     *
     * @param listener Java class name of a listener class
     */
    public void addApplicationListener(String listener) {
        addApplicationListener(listener, false);
    }


    /**
     * Add a new Listener class name to the set of Listeners
     * configured for this application.
     *
     * @param listener Java class name of a listener class
     */
    protected void addApplicationListener(String listener, boolean restricted) {
        String results[] = new String[applicationListeners.length + 1];
        for (int i = 0; i < applicationListeners.length; i++) {
            if (listener.equals(applicationListeners[i])) {
                log.info(sm.getString("standardContext.duplicateListener", listener));
                if (!restricted && restrictedApplicationListeners.contains(listener)) {
                    restrictedApplicationListeners.remove(listener);
                }
                return;
            }
            results[i] = applicationListeners[i];
        }
        results[applicationListeners.length] = listener;
        if (restricted && !restrictedApplicationListeners.contains(listener)) {
            boolean found = false;
            for (String check : applicationListeners) {
                if (check.equals(listener)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                restrictedApplicationListeners.add(listener);
            }
        }
        applicationListeners = results;
        fireContainerEvent("addApplicationListener", listener);
    }


    /**
     * Add a new Listener instance to the set of Listeners
     * configured for this application.
     *
     * @param listener Java instance of a listener
     */
    public <T extends EventListener> void addApplicationListenerInstance(T listener) {
        EventListener results[] = new EventListener[applicationListenerInstances.length + 1];
        for (int i = 0; i < applicationListenerInstances.length; i++) {
            if (listener.equals(applicationListenerInstances[i])) {
                log.info(sm.getString("standardContext.duplicateListener", listener));
                return;
            }
            results[i] = applicationListenerInstances[i];
        }
        results[applicationListenerInstances.length] = listener;
        applicationListenerInstances = results;
        fireContainerEvent("addApplicationListenerInstance", listener);
    }


    /**
     * Add a new application parameter for this application.
     *
     * @param parameter The new application parameter
     */
    public void addApplicationParameter(ApplicationParameter parameter) {
        String newName = parameter.getName();
        for (int i = 0; i < applicationParameters.length; i++) {
            if (newName.equals(applicationParameters[i].getName()) &&
                    !applicationParameters[i].getOverride())
                return;
        }
        ApplicationParameter results[] =
            new ApplicationParameter[applicationParameters.length + 1];
        System.arraycopy(applicationParameters, 0, results, 0,
                applicationParameters.length);
        results[applicationParameters.length] = parameter;
        applicationParameters = results;
        fireContainerEvent("addApplicationParameter", parameter);
    }


    /**
     * Add a child Container, only if the proposed child is an implementation
     * of Wrapper.
     *
     * @param child Child container to be added
     *
     * @exception IllegalArgumentException if the proposed container is
     *  not an implementation of Wrapper
     */
    public void addChild(Container child) {

        // Global JspServlet
        Wrapper oldJspServlet = null;

        if (!(child instanceof Wrapper)) {
            throw new IllegalArgumentException
                (sm.getString("standardContext.notWrapper"));
        }

        Wrapper wrapper = (Wrapper) child;
        boolean isJspServlet = "jsp".equals(child.getName());

        // Allow webapp to override JspServlet inherited from global web.xml.
        if (isJspServlet) {
            oldJspServlet = (Wrapper) findChild("jsp");
            if (oldJspServlet != null) {
                removeChild(oldJspServlet);
            }
        }

        String jspFile = wrapper.getJspFile();
        if ((jspFile != null) && !jspFile.startsWith("/")) {
            if (isServlet22()) {
                if(log.isDebugEnabled())
                    log.debug(sm.getString("standardContext.wrapper.warning", 
                                       jspFile));
                wrapper.setJspFile("/" + jspFile);
            } else {
                throw new IllegalArgumentException
                    (sm.getString("standardContext.wrapper.error", jspFile));
            }
        }

        super.addChild(child);

        if (isJspServlet && oldJspServlet != null) {
            /*
             * The webapp-specific JspServlet inherits all the mappings
             * specified in the global web.xml, and may add additional ones.
             */
            String[] jspMappings = oldJspServlet.findMappings();
            for (int i=0; jspMappings!=null && i<jspMappings.length; i++) {
                addServletMapping(jspMappings[i], child.getName());
            }
        }
    }


    /**
     * Add a security constraint to the set for this web application.
     */
    public void addConstraint(SecurityConstraint constraint) {

        // Validate the proposed constraint
        SecurityCollection collections[] = constraint.findCollections();
        for (int i = 0; i < collections.length; i++) {
            String patterns[] = collections[i].findPatterns();
            for (int j = 0; j < patterns.length; j++) {
                patterns[j] = adjustURLPattern(patterns[j]);
                if (!validateURLPattern(patterns[j]))
                    throw new IllegalArgumentException
                        (sm.getString
                         ("standardContext.securityConstraint.pattern",
                          patterns[j]));
            }
        }

        // Add this constraint to the set for our web application
        SecurityConstraint results[] =
            new SecurityConstraint[constraints.length + 1];
        for (int i = 0; i < constraints.length; i++)
            results[i] = constraints[i];
        results[constraints.length] = constraint;
        constraints = results;

    }



    /**
     * Add an error page for the specified error or Java exception.
     *
     * @param errorPage The error page definition to be added
     */
    public void addErrorPage(ErrorPage errorPage) {
        // Validate the input parameters
        if (errorPage == null)
            throw new IllegalArgumentException
                (sm.getString("standardContext.errorPage.required"));
        String location = errorPage.getLocation();
        if ((location != null) && !location.startsWith("/")) {
            if (isServlet22()) {
                if(log.isDebugEnabled())
                    log.debug(sm.getString("standardContext.errorPage.warning",
                                 location));
                errorPage.setLocation("/" + location);
            } else {
                throw new IllegalArgumentException
                    (sm.getString("standardContext.errorPage.error",
                                  location));
            }
        }

        // Add the specified error page to our internal collections
        String exceptionType = errorPage.getExceptionType();
        if (exceptionType != null) {
            exceptionPages.put(exceptionType, errorPage);
        } else {
            if (errorPage.getErrorCode() == 200) {
                this.okErrorPage = errorPage;
            }
            statusPages.put(errorPage.getErrorCode(), errorPage);
        }
        fireContainerEvent("addErrorPage", errorPage);

    }


    /**
     * Add a filter definition to this Context.
     *
     * @param filterDef The filter definition to be added
     */
    public void addApplicationFilterConfig(ApplicationFilterConfig filterConfig) {
        filterConfigs.put(filterConfig.getFilterName(), filterConfig);
        fireContainerEvent("addApplicationFilterConfig", filterConfig);
    }


    /**
     * Add a filter definition to this Context.
     *
     * @param filterDef The filter definition to be added
     */
    public void addFilterDef(FilterDef filterDef) {
        filterDefs.put(filterDef.getFilterName(), filterDef);
        fireContainerEvent("addFilterDef", filterDef);
    }


    /**
     * Add a filter mapping to this Context at the end of the current set
     * of filter mappings.
     *
     * @param filterMap The filter mapping to be added
     *
     * @exception IllegalArgumentException if the specified filter name
     *  does not match an existing filter definition, or the filter mapping
     *  is malformed
     */
    public void addFilterMap(FilterMap filterMap) {
        validateFilterMap(filterMap);
        // Add this filter mapping to our registered set
        FilterMap results[] =new FilterMap[filterMaps.length + 1];
        System.arraycopy(filterMaps, 0, results, 0, filterMaps.length);
        results[filterMaps.length] = filterMap;
        filterMaps = results;
        fireContainerEvent("addFilterMap", filterMap);
    }

    
    /**
     * Add a filter mapping to this Context before the mappings defined in the
     * deployment descriptor but after any other mappings added via this method.
     *
     * @param filterMap The filter mapping to be added
     *
     * @exception IllegalArgumentException if the specified filter name
     *  does not match an existing filter definition, or the filter mapping
     *  is malformed
     */
    public void addFilterMapBefore(FilterMap filterMap) {
        validateFilterMap(filterMap);
        // Add this filter mapping to our registered set
        FilterMap results[] = new FilterMap[filterMaps.length + 1];
        System.arraycopy(filterMaps, 0, results, 0, filterMapInsertPoint);
        System.arraycopy(filterMaps, filterMapInsertPoint, results,
                filterMapInsertPoint + 1,
                filterMaps.length - filterMapInsertPoint);
        results[filterMapInsertPoint] = filterMap;
        filterMapInsertPoint++;
        filterMaps = results;
        fireContainerEvent("addFilterMap", filterMap);
    }


    /**
     * Validate the supplied FilterMap.
     */
    protected void validateFilterMap(FilterMap filterMap) {
        // Validate the proposed filter mapping
        String filterName = filterMap.getFilterName();
        String[] servletNames = filterMap.getServletNames();
        String[] urlPatterns = filterMap.getURLPatterns();
        if (findFilterDef(filterName) == null)
            throw new IllegalArgumentException
                (sm.getString("standardContext.filterMap.name", filterName));

        if (!filterMap.getMatchAllServletNames() && 
            !filterMap.getMatchAllUrlPatterns() && 
            (servletNames.length == 0) && (urlPatterns.length == 0))
            throw new IllegalArgumentException
                (sm.getString("standardContext.filterMap.either"));
        // FIXME: Older spec revisions may still check this
        /*
        if ((servletNames.length != 0) && (urlPatterns.length != 0))
            throw new IllegalArgumentException
                (sm.getString("standardContext.filterMap.either"));
        */
        for (int i = 0; i < urlPatterns.length; i++) {
            if (!validateURLPattern(urlPatterns[i])) {
                throw new IllegalArgumentException
                    (sm.getString("standardContext.filterMap.pattern",
                            urlPatterns[i]));
            }
        }
    }


    /**
     * Add the classname of an InstanceListener to be added to each
     * Wrapper appended to this Context.
     *
     * @param listener Java class name of an InstanceListener class
     */
    public void addInstanceListener(String listener) {
        String results[] =new String[instanceListeners.length + 1];
        for (int i = 0; i < instanceListeners.length; i++)
            results[i] = instanceListeners[i];
        results[instanceListeners.length] = listener;
        instanceListeners = results;
        fireContainerEvent("addInstanceListener", listener);
    }


    /**
     * Add the given URL pattern as a jsp-property-group.  This maps
     * resources that match the given pattern so they will be passed
     * to the JSP container.  Though there are other elements in the
     * property group, we only care about the URL pattern here.  The
     * JSP container will parse the rest.
     *
     * @param pattern URL pattern to be mapped
     */
    public void addJspMapping(String pattern) {
        String servletName = findServletMapping("*.jsp");
        if (servletName == null) {
            servletName = "jsp";
        }

        if( findChild(servletName) != null) {
            addServletMapping(pattern, servletName, true);
        } else {
            if(log.isDebugEnabled())
                log.debug("Skiping " + pattern + " , no servlet " + servletName);
        }
    }


    /**
     * Add the given jsp-property-group.
     *
     * @param pattern URL pattern to be mapped
     */
    public void addJspPropertyGroup(JspPropertyGroup propertyGroup) {
        // Add any JSP mapping specified, as it needs to be mapped to the JSP Servlet
        ArrayList<String> urlPatterns = propertyGroup.getUrlPatterns();
        for (int i = 0; i < urlPatterns.size(); i++) {
            addJspMapping(urlPatterns.get(i));
            // Split off the groups to individual mappings
            jspPropertyGroups.put(urlPatterns.get(i), propertyGroup);
        }
    }


    /**
     * Add the given JSP tag library metadata.
     *
     * @param tagLibrayInfo the tag library info that will be added
     */
    public void addJspTagLibrary(TagLibraryInfo tagLibraryInfo) {
        // Add listeners specified by the taglib
        String[] listeners = tagLibraryInfo.getListeners();
        for (int i = 0; i < listeners.length; i++) {
            addApplicationListener(listeners[i], true);
        }
        jspTagLibraries.put(tagLibraryInfo.getUri(), tagLibraryInfo);
    }

    
    /**
     * Add the given JSP tag library metadata with a specified mapping.
     *
     * @param uri the tag library URI
     * @param tagLibrayInfo the tag library info that will be added
     */
    public void addJspTagLibrary(String uri, TagLibraryInfo tagLibraryInfo) {
        jspTagLibraries.put(uri, tagLibraryInfo);
    }

    
    /**
     * Add a Locale Encoding Mapping (see Sec 5.4 of Servlet spec 2.4)
     *
     * @param locale locale to map an encoding for
     * @param encoding encoding to be used for a give locale
     */
    public void addLocaleEncodingMappingParameter(String locale, String encoding){
        getCharsetMapper().addCharsetMappingFromDeploymentDescriptor(locale, encoding);
    }


    /**
     * Add a new MIME mapping, replacing any existing mapping for
     * the specified extension.
     *
     * @param extension Filename extension being mapped
     * @param mimeType Corresponding MIME type
     */
    public void addMimeMapping(String extension, String mimeType) {
        mimeMappings.put(extension, mimeType);
        fireContainerEvent("addMimeMapping", extension);
    }


    /**
     * Add a new context initialization parameter.
     *
     * @param name Name of the new parameter
     * @param value Value of the new  parameter
     *
     * @exception IllegalArgumentException if the name or value is missing,
     *  or if this context initialization parameter has already been
     *  registered
     */
    public void addParameter(String name, String value) {
        // Validate the proposed context initialization parameter
        if ((name == null) || (value == null))
            throw new IllegalArgumentException
                (sm.getString("standardContext.parameter.required"));
        if (parameters.get(name) != null)
            throw new IllegalArgumentException
                (sm.getString("standardContext.parameter.duplicate", name));

        // Add this parameter to our defined set
        parameters.put(name, value);
        fireContainerEvent("addParameter", name);

    }


    /**
     * Add a security role reference for this web application.
     *
     * @param role Security role used in the application
     * @param link Actual security role to check for
     */
    public void addRoleMapping(String role, String link) {
        roleMappings.put(role, link);
        fireContainerEvent("addRoleMapping", role);
    }


    /**
     * Add a new security role for this web application.
     *
     * @param role New security role
     */
    public void addSecurityRole(String role) {
        String results[] =new String[securityRoles.length + 1];
        for (int i = 0; i < securityRoles.length; i++)
            results[i] = securityRoles[i];
        results[securityRoles.length] = role;
        securityRoles = results;
        fireContainerEvent("addSecurityRole", role);
    }


    /**
     * Add a new servlet mapping, replacing any existing mapping for
     * the specified pattern.
     *
     * @param pattern URL pattern to be mapped
     * @param name Name of the corresponding servlet to execute
     *
     * @exception IllegalArgumentException if the specified servlet name
     *  is not known to this Context
     */
    public void addServletMapping(String pattern, String name) {
        addServletMapping(pattern, name, false);
    }


    /**
     * Add a new servlet mapping, replacing any existing mapping for
     * the specified pattern.
     *
     * @param pattern URL pattern to be mapped
     * @param name Name of the corresponding servlet to execute
     * @param jspWildCard true if name identifies the JspServlet
     * and pattern contains a wildcard; false otherwise
     *
     * @exception IllegalArgumentException if the specified servlet name
     *  is not known to this Context
     */
    public void addServletMapping(String pattern, String name,
                                  boolean jspWildCard) {
        // Validate the proposed mapping
        Wrapper wrapper = (Wrapper) findChild(name);
        if (findChild(name) == null)
            throw new IllegalArgumentException
                (sm.getString("standardContext.servletMap.name", name));
        pattern = adjustURLPattern(RequestUtil.URLDecode(pattern));
        if (!validateURLPattern(pattern))
            throw new IllegalArgumentException
                (sm.getString("standardContext.servletMap.pattern", pattern));

        // Add this mapping to our registered set
        String name2 = (String) servletMappings.get(pattern);
        if (name2 != null) {
            // Don't allow more than one servlet on the same pattern
            Wrapper wrapper2 = (Wrapper) findChild(name2);
            wrapper2.removeMapping(pattern);
            mapper.removeWrapper(pattern);
        }
        servletMappings.put(pattern, name);
        wrapper.addMapping(pattern);

        // Update context mapper
        if (wrapper.getEnabled()) {
            mapper.addWrapper(pattern, wrapper, jspWildCard);
        }

        fireContainerEvent("addServletMapping", pattern);

    }


    /**
     * Add a JSP tag library for the specified URI.
     *
     * @param uri URI, relative to the web.xml file, of this tag library
     * @param location Location of the tag library descriptor
     */
    public void addTaglib(String uri, String location) {
        taglibs.put(uri, location);
        fireContainerEvent("addTaglib", uri);
    }


    /**
     * Add a new welcome file to the set recognized by this Context.
     *
     * @param name New welcome file name
     */
    public void addWelcomeFile(String name) {

        // Welcome files from the application deployment descriptor
        // completely replace those from the default conf/web.xml file
        if (replaceWelcomeFiles) {
            welcomeFiles = new String[0];
            setReplaceWelcomeFiles(false);
        }
        String results[] =new String[welcomeFiles.length + 1];
        for (int i = 0; i < welcomeFiles.length; i++)
            results[i] = welcomeFiles[i];
        results[welcomeFiles.length] = name;
        welcomeFiles = results;

        if (started)
            postContextAttributes();
        fireContainerEvent("addWelcomeFile", name);

    }


    /**
     * Add the classname of a LifecycleListener to be added to each
     * Wrapper appended to this Context.
     *
     * @param listener Java class name of a LifecycleListener class
     */
    public void addWrapperLifecycle(String listener) {
        String results[] =new String[wrapperLifecycles.length + 1];
        for (int i = 0; i < wrapperLifecycles.length; i++)
            results[i] = wrapperLifecycles[i];
        results[wrapperLifecycles.length] = listener;
        wrapperLifecycles = results;
        fireContainerEvent("addWrapperLifecycle", listener);
    }


    /**
     * Add the classname of a ContainerListener to be added to each
     * Wrapper appended to this Context.
     *
     * @param listener Java class name of a ContainerListener class
     */
    public void addWrapperListener(String listener) {
        String results[] =new String[wrapperListeners.length + 1];
        for (int i = 0; i < wrapperListeners.length; i++)
            results[i] = wrapperListeners[i];
        results[wrapperListeners.length] = listener;
        wrapperListeners = results;
        fireContainerEvent("addWrapperListener", listener);
    }


    /**
     * Factory method to create and return a new Wrapper instance, of
     * the Java implementation class appropriate for this Context
     * implementation.  The constructor of the instantiated Wrapper
     * will have been called, but no properties will have been set.
     */
    public Wrapper createWrapper() {

        Wrapper wrapper = null;
        if (wrapperClass != null) {
            try {
                wrapper = (Wrapper) wrapperClass.newInstance();
            } catch (Throwable t) {
                throw new IllegalStateException(sm.getString("standardContext.createWrapper.failed"), t);
            }
        } else {
            wrapper = new StandardWrapper();
        }

        for (int i = 0; i < instanceListeners.length; i++) {
            try {
                Class<?> clazz = Class.forName(instanceListeners[i]);
                InstanceListener listener =
                    (InstanceListener) clazz.newInstance();
                wrapper.addInstanceListener(listener);
            } catch (Throwable t) {
                throw new IllegalStateException(sm.getString("standardContext.createWrapper.failed"), t);
            }
        }

        for (int i = 0; i < wrapperLifecycles.length; i++) {
            try {
                Class<?> clazz = Class.forName(wrapperLifecycles[i]);
                LifecycleListener listener =
                    (LifecycleListener) clazz.newInstance();
                if (wrapper instanceof Lifecycle)
                    ((Lifecycle) wrapper).addLifecycleListener(listener);
            } catch (Throwable t) {
                throw new IllegalStateException(sm.getString("standardContext.createWrapper.failed"), t);
            }
        }

        for (int i = 0; i < wrapperListeners.length; i++) {
            try {
                Class<?> clazz = Class.forName(wrapperListeners[i]);
                ContainerListener listener =
                    (ContainerListener) clazz.newInstance();
                wrapper.addContainerListener(listener);
            } catch (Throwable t) {
                throw new IllegalStateException(sm.getString("standardContext.createWrapper.failed"), t);
            }
        }

        return (wrapper);

    }


    /**
     * Return the application filter for the given name.
     */
    public ApplicationFilterConfig findApplicationFilterConfig(String name) {
        return (ApplicationFilterConfig) filterConfigs.get(name);
    }


    /**
     * Return the application filter for the given name.
     */
    public ApplicationFilterConfig[] findApplicationFilterConfigs() {
        return filterConfigs.values().toArray(new ApplicationFilterConfig[0]);
    }


    /**
     * Return the set of application listener class names configured
     * for this application.
     */
    public String[] findApplicationListeners() {
        return (applicationListeners);
    }


    /**
     * Return the set of application parameters for this application.
     */
    public ApplicationParameter[] findApplicationParameters() {
        return (applicationParameters);
    }


    /**
     * Return the security constraints for this web application.
     * If there are none, a zero-length array is returned.
     */
    public SecurityConstraint[] findConstraints() {
        return (constraints);
    }


    /**
     * Return the error page entry for the specified HTTP error code,
     * if any; otherwise return <code>null</code>.
     *
     * @param errorCode Error code to look up
     */
    public ErrorPage findErrorPage(int errorCode) {
        if (errorCode == 200) {
            return (okErrorPage);
        } else {
            return ((ErrorPage) statusPages.get(errorCode));
        }
    }


    /**
     * Return the error page entry for the specified Java exception type,
     * if any; otherwise return <code>null</code>.
     *
     * @param exceptionType Exception type to look up
     */
    public ErrorPage findErrorPage(String exceptionType) {
        return ((ErrorPage) exceptionPages.get(exceptionType));
    }


    /**
     * Return the set of defined error pages for all specified error codes
     * and exception types.
     */
    public ErrorPage[] findErrorPages() {
        ErrorPage results1[] = new ErrorPage[exceptionPages.size()];
        results1 =
            (ErrorPage[]) exceptionPages.values().toArray(results1);
        ErrorPage results2[] = new ErrorPage[statusPages.size()];
        results2 =
            (ErrorPage[]) statusPages.values().toArray(results2);
        ErrorPage results[] =
            new ErrorPage[results1.length + results2.length];
        for (int i = 0; i < results1.length; i++)
            results[i] = results1[i];
        for (int i = results1.length; i < results.length; i++)
            results[i] = results2[i - results1.length];
        return (results);
    }


    /**
     * Return the filter definition for the specified filter name, if any;
     * otherwise return <code>null</code>.
     *
     * @param filterName Filter name to look up
     */
    public FilterDef findFilterDef(String filterName) {
        return ((FilterDef) filterDefs.get(filterName));
    }


    /**
     * Return the set of defined filters for this Context.
     */
    public FilterDef[] findFilterDefs() {
        FilterDef results[] = new FilterDef[filterDefs.size()];
        return ((FilterDef[]) filterDefs.values().toArray(results));
    }


    /**
     * Return the set of filter mappings for this Context.
     */
    public FilterMap[] findFilterMaps() {
        return (filterMaps);
    }


    /**
     * Return the set of InstanceListener classes that will be added to
     * newly created Wrappers automatically.
     */
    public String[] findInstanceListeners() {
        return (instanceListeners);
    }


    /**
     * Return the set of JSP property groups.
     */
    public JspPropertyGroup[] findJspPropertyGroups() {
        JspPropertyGroup results[] =
            new JspPropertyGroup[jspPropertyGroups.size()];
        return jspPropertyGroups.values().toArray(results);
    }


    /**
     * FIXME: Fooling introspection ...
     */
    public Context findMappingObject() {
        return (Context) getMappingObject();
    }
    
    
    /**
     * Return the MIME type to which the specified extension is mapped,
     * if any; otherwise return <code>null</code>.
     *
     * @param extension Extension to map to a MIME type
     */
    public String findMimeMapping(String extension) {
        return ((String) mimeMappings.get(extension));
    }


    /**
     * Return the extensions for which MIME mappings are defined.  If there
     * are none, a zero-length array is returned.
     */
    public String[] findMimeMappings() {
        String results[] = new String[mimeMappings.size()];
        return ((String[]) mimeMappings.keySet().toArray(results));
    }


    /**
     * Return the value for the specified context initialization
     * parameter name, if any; otherwise return <code>null</code>.
     *
     * @param name Name of the parameter to return
     */
    public String findParameter(String name) {
        return ((String) parameters.get(name));
    }


    /**
     * Return the names of all defined context initialization parameters
     * for this Context.  If no parameters are defined, a zero-length
     * array is returned.
     */
    public String[] findParameters() {
        String results[] = new String[parameters.size()];
        return ((String[]) parameters.keySet().toArray(results));
    }


    /**
     * For the given security role (as used by an application), return the
     * corresponding role name (as defined by the underlying Realm) if there
     * is one.  Otherwise, return the specified role unchanged.
     *
     * @param role Security role to map
     */
    public String findRoleMapping(String role) {
        String realRole = (String) roleMappings.get(role);
        if (realRole != null)
            return (realRole);
        else
            return (role);
    }


    /**
     * Return <code>true</code> if the specified security role is defined
     * for this application; otherwise return <code>false</code>.
     *
     * @param role Security role to verify
     */
    public boolean findSecurityRole(String role) {
        for (int i = 0; i < securityRoles.length; i++) {
            if (role.equals(securityRoles[i]))
                return (true);
        }
        return (false);
    }


    /**
     * Return the security roles defined for this application.  If none
     * have been defined, a zero-length array is returned.
     */
    public String[] findSecurityRoles() {
        return (securityRoles);
    }


    /**
     * Return the servlet name mapped by the specified pattern (if any);
     * otherwise return <code>null</code>.
     *
     * @param pattern Pattern for which a mapping is requested
     */
    public String findServletMapping(String pattern) {
        return ((String) servletMappings.get(pattern));
    }


    /**
     * Return the patterns of all defined servlet mappings for this
     * Context.  If no mappings are defined, a zero-length array is returned.
     */
    public String[] findServletMappings() {
        String results[] = new String[servletMappings.size()];
        return ((String[]) servletMappings.keySet().toArray(results));
    }


    /**
     * Return the set of HTTP status codes for which error pages have
     * been specified.  If none are specified, a zero-length array
     * is returned.
     */
    public int[] findStatusPages() {
        int results[] = new int[statusPages.size()];
        Iterator<Integer> elements = statusPages.keySet().iterator();
        int i = 0;
        while (elements.hasNext())
            results[i++] = elements.next().intValue();
        return (results);
    }


    /**
     * Return the tag library descriptor location for the specified taglib
     * URI, if any; otherwise, return <code>null</code>.
     *
     * @param uri URI, relative to the web.xml file
     */
    public String findTaglib(String uri) {
        return ((String) taglibs.get(uri));
    }


    /**
     * Return the URIs of all tag libraries for which a tag library
     * descriptor location has been specified.  If none are specified,
     * a zero-length array is returned.
     */
    public String[] findTaglibs() {
        String results[] = new String[taglibs.size()];
        return ((String[]) taglibs.keySet().toArray(results));
    }


    /**
     * Return <code>true</code> if the specified welcome file is defined
     * for this Context; otherwise return <code>false</code>.
     *
     * @param name Welcome file to verify
     */
    public boolean findWelcomeFile(String name) {
        for (int i = 0; i < welcomeFiles.length; i++) {
            if (name.equals(welcomeFiles[i]))
                return (true);
        }
        return (false);
    }


    /**
     * Return the set of welcome files defined for this Context.  If none are
     * defined, a zero-length array is returned.
     */
    public String[] findWelcomeFiles() {
        return (welcomeFiles);
    }


    /**
     * Return the set of LifecycleListener classes that will be added to
     * newly created Wrappers automatically.
     */
    public String[] findWrapperLifecycles() {
        return (wrapperLifecycles);
    }


    /**
     * Return the set of ContainerListener classes that will be added to
     * newly created Wrappers automatically.
     */
    public String[] findWrapperListeners() {
        return (wrapperListeners);
    }


    /**
     * Reload this web application, if reloading is supported.
     * <p>
     * <b>IMPLEMENTATION NOTE</b>:  This method is designed to deal with
     * reloads required by changes to classes in the underlying repositories
     * of our class loader.  It does not handle changes to the web application
     * deployment descriptor.  If that has occurred, you should stop this
     * Context and create (and start) a new Context instance instead.
     *
     * @exception IllegalStateException if the <code>reloadable</code>
     *  property is set to <code>false</code>.
     */
    public synchronized void reload() {

        // Validate our current component state
        if (!started)
            throw new IllegalStateException
                (sm.getString("containerBase.notStarted", logName()));

        // Make sure reloading is enabled
        //      if (!reloadable)
        //          throw new IllegalStateException
        //              (sm.getString("standardContext.notReloadable"));
        if(log.isInfoEnabled())
            log.info(sm.getString("standardContext.reloadingStarted"));

        // Stop accepting requests temporarily
        setPaused(true);

        try {
            stop();
        } catch (LifecycleException e) {
            log.error(sm.getString("standardContext.stoppingContext"), e);
        }

        try {
            start();
        } catch (LifecycleException e) {
            log.error(sm.getString("standardContext.startingContext"), e);
        }

        setPaused(false);

    }


    /**
     * Remove the specified application listener class from the set of
     * listeners for this application.
     *
     * @param listener Java class name of the listener to be removed
     */
    public void removeApplicationListener(String listener) {

        // Make sure this welcome file is currently present
        int n = -1;
        for (int i = 0; i < applicationListeners.length; i++) {
            if (applicationListeners[i].equals(listener)) {
                n = i;
                break;
            }
        }
        if (n < 0)
            return;

        // Remove the specified constraint
        int j = 0;
        String results[] = new String[applicationListeners.length - 1];
        for (int i = 0; i < applicationListeners.length; i++) {
            if (i != n)
                results[j++] = applicationListeners[i];
        }
        applicationListeners = results;

        // Inform interested listeners
        fireContainerEvent("removeApplicationListener", listener);

        // FIXME - behavior if already started?

    }


    /**
     * Remove the application parameter with the specified name from
     * the set for this application.
     *
     * @param name Name of the application parameter to remove
     */
    public void removeApplicationParameter(String name) {

        // Make sure this parameter is currently present
        int n = -1;
        for (int i = 0; i < applicationParameters.length; i++) {
            if (name.equals(applicationParameters[i].getName())) {
                n = i;
                break;
            }
        }
        if (n < 0)
            return;

        // Remove the specified parameter
        int j = 0;
        ApplicationParameter results[] =
            new ApplicationParameter[applicationParameters.length - 1];
        for (int i = 0; i < applicationParameters.length; i++) {
            if (i != n)
                results[j++] = applicationParameters[i];
        }
        applicationParameters = results;

        // Inform interested listeners
        fireContainerEvent("removeApplicationParameter", name);

    }


    /**
     * Add a child Container, only if the proposed child is an implementation
     * of Wrapper.
     *
     * @param child Child container to be added
     *
     * @exception IllegalArgumentException if the proposed container is
     *  not an implementation of Wrapper
     */
    public void removeChild(Container child) {

        if (!(child instanceof Wrapper)) {
            throw new IllegalArgumentException
                (sm.getString("standardContext.notWrapper"));
        }

        super.removeChild(child);

    }


    /**
     * Remove the specified security constraint from this web application.
     *
     * @param constraint Constraint to be removed
     */
    public void removeConstraint(SecurityConstraint constraint) {

        // Make sure this constraint is currently present
        int n = -1;
        for (int i = 0; i < constraints.length; i++) {
            if (constraints[i].equals(constraint)) {
                n = i;
                break;
            }
        }
        if (n < 0)
            return;

        // Remove the specified constraint
        int j = 0;
        SecurityConstraint results[] =
            new SecurityConstraint[constraints.length - 1];
        for (int i = 0; i < constraints.length; i++) {
            if (i != n)
                results[j++] = constraints[i];
        }
        constraints = results;

        // Inform interested listeners
        fireContainerEvent("removeConstraint", constraint);

    }


    /**
     * Remove the error page for the specified error code or
     * Java language exception, if it exists; otherwise, no action is taken.
     *
     * @param errorPage The error page definition to be removed
     */
    public void removeErrorPage(ErrorPage errorPage) {
        String exceptionType = errorPage.getExceptionType();
        if (exceptionType != null) {
            exceptionPages.remove(exceptionType);
        } else {
            if (errorPage.getErrorCode() == 200) {
                this.okErrorPage = null;
            }
            statusPages.remove(errorPage.getErrorCode());
        }
        fireContainerEvent("removeErrorPage", errorPage);
    }


    /**
     * Remove the specified filter definition from this Context, if it exists;
     * otherwise, no action is taken.
     *
     * @param filterDef Filter definition to be removed
     */
    public void removeFilterDef(FilterDef filterDef) {
        filterDefs.remove(filterDef.getFilterName());
        fireContainerEvent("removeFilterDef", filterDef);
    }


    /**
     * Remove a filter mapping from this Context.
     *
     * @param filterMap The filter mapping to be removed
     */
    public void removeFilterMap(FilterMap filterMap) {

        // Make sure this filter mapping is currently present
        int n = -1;
        for (int i = 0; i < filterMaps.length; i++) {
            if (filterMaps[i] == filterMap) {
                n = i;
                break;
            }
        }
        if (n < 0)
            return;

        // Remove the specified filter mapping
        FilterMap results[] = new FilterMap[filterMaps.length - 1];
        System.arraycopy(filterMaps, 0, results, 0, n);
        System.arraycopy(filterMaps, n + 1, results, n,
                (filterMaps.length - 1) - n);
        if (n < filterMapInsertPoint) {
            filterMapInsertPoint--;
        }
        filterMaps = results;

        // Inform interested listeners
        fireContainerEvent("removeFilterMap", filterMap);

    }


    /**
     * Remove a class name from the set of InstanceListener classes that
     * will be added to newly created Wrappers.
     *
     * @param listener Class name of an InstanceListener class to be removed
     */
    public void removeInstanceListener(String listener) {

        // Make sure this welcome file is currently present
        int n = -1;
        for (int i = 0; i < instanceListeners.length; i++) {
            if (instanceListeners[i].equals(listener)) {
                n = i;
                break;
            }
        }
        if (n < 0)
            return;

        // Remove the specified constraint
        int j = 0;
        String results[] = new String[instanceListeners.length - 1];
        for (int i = 0; i < instanceListeners.length; i++) {
            if (i != n)
                results[j++] = instanceListeners[i];
        }
        instanceListeners = results;

        // Inform interested listeners
        fireContainerEvent("removeInstanceListener", listener);

    }


    /**
     * Remove the MIME mapping for the specified extension, if it exists;
     * otherwise, no action is taken.
     *
     * @param extension Extension to remove the mapping for
     */
    public void removeMimeMapping(String extension) {
        mimeMappings.remove(extension);
        fireContainerEvent("removeMimeMapping", extension);
    }


    /**
     * Remove the context initialization parameter with the specified
     * name, if it exists; otherwise, no action is taken.
     *
     * @param name Name of the parameter to remove
     */
    public void removeParameter(String name) {
        parameters.remove(name);
        fireContainerEvent("removeParameter", name);
    }


    /**
     * Remove any security role reference for the specified name
     *
     * @param role Security role (as used in the application) to remove
     */
    public void removeRoleMapping(String role) {
        roleMappings.remove(role);
        fireContainerEvent("removeRoleMapping", role);
    }


    /**
     * Remove any security role with the specified name.
     *
     * @param role Security role to remove
     */
    public void removeSecurityRole(String role) {

        // Make sure this security role is currently present
        int n = -1;
        for (int i = 0; i < securityRoles.length; i++) {
            if (role.equals(securityRoles[i])) {
                n = i;
                break;
            }
        }
        if (n < 0)
            return;

        // Remove the specified security role
        int j = 0;
        String results[] = new String[securityRoles.length - 1];
        for (int i = 0; i < securityRoles.length; i++) {
            if (i != n)
                results[j++] = securityRoles[i];
        }
        securityRoles = results;

        // Inform interested listeners
        fireContainerEvent("removeSecurityRole", role);

    }


    /**
     * Remove any servlet mapping for the specified pattern, if it exists;
     * otherwise, no action is taken.
     *
     * @param pattern URL pattern of the mapping to remove
     */
    public void removeServletMapping(String pattern) {
        String name = servletMappings.remove(pattern);
        Wrapper wrapper = (Wrapper) findChild(name);
        if( wrapper != null ) {
            wrapper.removeMapping(pattern);
        }
        mapper.removeWrapper(pattern);
        fireContainerEvent("removeServletMapping", pattern);
    }


    /**
     * Remove the tag library location forthe specified tag library URI.
     *
     * @param uri URI, relative to the web.xml file
     */
    public void removeTaglib(String uri) {
        taglibs.remove(uri);
        fireContainerEvent("removeTaglib", uri);
    }


    /**
     * Remove the specified welcome file name from the list recognized
     * by this Context.
     *
     * @param name Name of the welcome file to be removed
     */
    public void removeWelcomeFile(String name) {

        // Make sure this welcome file is currently present
        int n = -1;
        for (int i = 0; i < welcomeFiles.length; i++) {
            if (welcomeFiles[i].equals(name)) {
                n = i;
                break;
            }
        }
        if (n < 0)
            return;

        // Remove the specified constraint
        int j = 0;
        String results[] = new String[welcomeFiles.length - 1];
        for (int i = 0; i < welcomeFiles.length; i++) {
            if (i != n)
                results[j++] = welcomeFiles[i];
        }
        welcomeFiles = results;

        // Inform interested listeners
        if (started)
            postContextAttributes();
        fireContainerEvent("removeWelcomeFile", name);

    }


    /**
     * Remove a class name from the set of LifecycleListener classes that
     * will be added to newly created Wrappers.
     *
     * @param listener Class name of a LifecycleListener class to be removed
     */
    public void removeWrapperLifecycle(String listener) {


        // Make sure this welcome file is currently present
        int n = -1;
        for (int i = 0; i < wrapperLifecycles.length; i++) {
            if (wrapperLifecycles[i].equals(listener)) {
                n = i;
                break;
            }
        }
        if (n < 0)
            return;

        // Remove the specified constraint
        int j = 0;
        String results[] = new String[wrapperLifecycles.length - 1];
        for (int i = 0; i < wrapperLifecycles.length; i++) {
            if (i != n)
                results[j++] = wrapperLifecycles[i];
        }
        wrapperLifecycles = results;

        // Inform interested listeners
        fireContainerEvent("removeWrapperLifecycle", listener);

    }


    /**
     * Remove a class name from the set of ContainerListener classes that
     * will be added to newly created Wrappers.
     *
     * @param listener Class name of a ContainerListener class to be removed
     */
    public void removeWrapperListener(String listener) {


        // Make sure this welcome file is currently present
        int n = -1;
        for (int i = 0; i < wrapperListeners.length; i++) {
            if (wrapperListeners[i].equals(listener)) {
                n = i;
                break;
            }
        }
        if (n < 0)
            return;

        // Remove the specified constraint
        int j = 0;
        String results[] = new String[wrapperListeners.length - 1];
        for (int i = 0; i < wrapperListeners.length; i++) {
            if (i != n)
                results[j++] = wrapperListeners[i];
        }
        wrapperListeners = results;

        // Inform interested listeners
        fireContainerEvent("removeWrapperListener", listener);

    }


    /**
     * Gets the cumulative processing times of all servlets in this
     * StandardContext.
     *
     * @return Cumulative processing times of all servlets in this
     * StandardContext
     */
    public long getProcessingTime() {
        
        long result = 0;

        Container[] children = findChildren();
        if (children != null) {
            for( int i=0; i< children.length; i++ ) {
                result += ((StandardWrapper)children[i]).getProcessingTime();
            }
        }

        return result;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Configure and initialize the set of filters for this Context.
     * Return <code>true</code> if all filter initialization completed
     * successfully, or <code>false</code> otherwise.
     */
    protected boolean filterStart() {

        if (getLogger().isDebugEnabled())
            getLogger().debug("Starting filters");
        // Instantiate and record a FilterConfig for each defined filter
        boolean ok = true;
        Iterator<ApplicationFilterConfig> filterConfigsIterator = 
            filterConfigs.values().iterator();
        while (filterConfigsIterator.hasNext()) {
            ApplicationFilterConfig filterConfig = filterConfigsIterator.next();
            try {
                filterConfig.getFilter();
            } catch (Throwable t) {
                getLogger().error
                (sm.getString("standardContext.filterStart", name), t);
                ok = false;
            }
        }
        Iterator<String> names = filterDefs.keySet().iterator();
        while (names.hasNext()) {
            String name = names.next();
            if (getLogger().isDebugEnabled())
                getLogger().debug(" Starting filter '" + name + "'");
            ApplicationFilterConfig filterConfig = null;
            try {
                filterConfig = new ApplicationFilterConfig
                (this, (FilterDef) filterDefs.get(name));
                filterConfig.getFilter();
                filterConfigs.put(name, filterConfig);
            } catch (Throwable t) {
                getLogger().error
                (sm.getString("standardContext.filterStart", name), t);
                ok = false;
            }
        }

        return (ok);

    }


    /**
     * Finalize and release the set of filters for this Context.
     * Return <code>true</code> if all filter finalization completed
     * successfully, or <code>false</code> otherwise.
     */
    protected boolean filterStop() {

        if (getLogger().isDebugEnabled())
            getLogger().debug("Stopping filters");

        // Release all Filter and FilterConfig instances
        Iterator<String> names = filterConfigs.keySet().iterator();
        while (names.hasNext()) {
            String name = names.next();
            if (getLogger().isDebugEnabled())
                getLogger().debug(" Stopping filter '" + name + "'");
            ApplicationFilterConfig filterConfig =
                (ApplicationFilterConfig) filterConfigs.get(name);
            filterConfig.release();
        }
        filterConfigs.clear();
        return (true);

    }


    /**
     * Find and return the initialized <code>FilterConfig</code> for the
     * specified filter name, if any; otherwise return <code>null</code>.
     *
     * @param name Name of the desired filter
     */
    public FilterConfig findFilterConfig(String name) {
        return ((FilterConfig) filterConfigs.get(name));
    }


    /**
     * Configure the set of instantiated application event listeners
     * for this Context.  Return <code>true</code> if all listeners wre
     * initialized successfully, or <code>false</code> otherwise.
     * Only context listeners are actually set in stone at this point,
     * all others are stored in a temporary location.
     */
    public boolean contextListenerStart() {

        if (log.isDebugEnabled())
            log.debug("Configuring application event listeners");

        // Instantiate the required listeners
        String listeners[] = findApplicationListeners();
        EventListener listenerInstances[] = applicationListenerInstances;
        EventListener results[] = new EventListener[listeners.length + listenerInstances.length];
        boolean ok = true;
        for (int i = 0; i < listeners.length; i++) {
            if (getLogger().isDebugEnabled())
                getLogger().debug(" Configuring event listener class '" +
                    listeners[i] + "'");
            try {
                results[i] = (EventListener) instanceManager.newInstance(listeners[i]);
            } catch (Throwable t) {
                getLogger().error
                    (sm.getString("standardContext.applicationListener",
                                  listeners[i]), t);
                ok = false;
            }
        }
        for (int i = 0; i < listenerInstances.length; i++) {
            results[i + listeners.length] = listenerInstances[i];
        }
        applicationListenerInstances = new EventListener[0];
        if (!ok) {
            getLogger().error(sm.getString("standardContext.applicationSkipped"));
            return (false);
        }

        // Sort listeners in two arrays
        ArrayList<EventListener> otherListeners = new ArrayList<EventListener>();
        ArrayList<EventListener> contextLifecycleListeners = new ArrayList<EventListener>();
        for (int i = 0; i < results.length; i++) {
            if (results[i] instanceof ServletContextListener) {
                contextLifecycleListeners.add(results[i]);
            }
            otherListeners.add(results[i]);
        }

        this.listenersInstances = otherListeners.toArray();
        setApplicationLifecycleListeners(contextLifecycleListeners.toArray());

        // Send application start events

        if (getLogger().isDebugEnabled())
            getLogger().debug("Sending application start events");

        Object instances[] = getApplicationLifecycleListeners();
        if (instances == null)
            return (ok);
        ServletContextEvent event =
          new ServletContextEvent(getServletContext());
        for (int i = 0; i < instances.length; i++) {
            if (instances[i] == null)
                continue;
            if (!(instances[i] instanceof ServletContextListener))
                continue;
            ServletContextListener listener =
                (ServletContextListener) instances[i];
            try {
                context.setRestricted(isRestricted(listener));
                fireContainerEvent("beforeContextInitialized", listener);
                listener.contextInitialized(event);
                fireContainerEvent("afterContextInitialized", listener);
            } catch (Throwable t) {
                fireContainerEvent("afterContextInitialized", listener);
                getLogger().error
                    (sm.getString("standardContext.listenerStart",
                                  instances[i].getClass().getName()), t);
                ok = false;
            } finally {
                context.setRestricted(false);
            }
        }
        return (ok);

    }


    /**
     * Configure the set of instantiated application event listeners
     * for this Context.  Return <code>true</code> if all listeners wre
     * initialized successfully, or <code>false</code> otherwise.
     */
    public boolean listenerStart() {

        if (log.isDebugEnabled())
            log.debug("Configuring application event listeners");

        // Instantiate the required listeners
        Object listeners[] = listenersInstances;
        EventListener listenerInstances[] = applicationListenerInstances;
        EventListener results[] = new EventListener[listeners.length + listenerInstances.length];
        boolean ok = true;
        for (int i = 0; i < listeners.length; i++) {
            try {
                results[i] = (EventListener) listeners[i];
            } catch (Throwable t) {
                getLogger().error
                    (sm.getString("standardContext.applicationListener",
                                  listeners[i]), t);
                ok = false;
            }
        }
        for (int i = 0; i < listenerInstances.length; i++) {
            results[i + listeners.length] = listenerInstances[i];
        }
        if (!ok) {
            getLogger().error(sm.getString("standardContext.applicationSkipped"));
            return (false);
        }
        this.listenersInstances = results;

        // Sort listeners in two arrays
        ArrayList<EventListener> eventListeners = new ArrayList<EventListener>();
        ArrayList<EventListener> sessionLifecycleListeners = new ArrayList<EventListener>();
        for (int i = 0; i < results.length; i++) {
            if ((results[i] instanceof ServletContextAttributeListener)
                || (results[i] instanceof ServletRequestAttributeListener)
                || (results[i] instanceof ServletRequestListener)
                || (results[i] instanceof HttpSessionAttributeListener)) {
                eventListeners.add(results[i]);
            }
            if (results[i] instanceof HttpSessionListener) {
                sessionLifecycleListeners.add(results[i]);
            }
        }

        setApplicationEventListeners(eventListeners.toArray());
        setApplicationSessionLifecycleListeners(sessionLifecycleListeners.toArray());

        return (ok);

    }
    
    
    /**
     * Send an application stop event to all interested listeners.
     * Return <code>true</code> if all events were sent successfully,
     * or <code>false</code> otherwise.
     */
    public boolean listenerStop() {

        if (log.isDebugEnabled())
            log.debug("Sending application stop events");

        boolean ok = true;
        Object listeners[] = getApplicationLifecycleListeners();
        if (listeners != null && (listeners.length > 0)) {
            ServletContextEvent event =
                new ServletContextEvent(getServletContext());
            for (int i = listeners.length - 1; i >= 0; i--) {
                if (listeners[i] == null)
                    continue;
                if (listeners[i] instanceof ServletContextListener) {
                    ServletContextListener listener =
                        (ServletContextListener) listeners[i];
                    try {
                        fireContainerEvent("beforeContextDestroyed", listener);
                        listener.contextDestroyed(event);
                        fireContainerEvent("afterContextDestroyed", listener);
                    } catch (Throwable t) {
                        fireContainerEvent("afterContextDestroyed", listener);
                        getLogger().error
                            (sm.getString("standardContext.listenerStop",
                                listeners[i].getClass().getName()), t);
                        ok = false;
                    }
                }
            }
        }

        // Annotation processing
        listeners = this.listenersInstances;
        if (listeners != null && (listeners.length > 0)) {
            for (int i = listeners.length - 1; i >= 0; i--) {
                if (listeners[i] == null)
                    continue;
                try {
                    getInstanceManager().destroyInstance(listeners[i]);
                } catch (Throwable t) {
                    getLogger().error
                        (sm.getString("standardContext.listenerStop",
                            listeners[i].getClass().getName()), t);
                    ok = false;
                }
            }
        }
        
        setApplicationEventListeners(null);
        setApplicationLifecycleListeners(null);
        setApplicationSessionLifecycleListeners(null);
        this.listenersInstances = null;

        return (ok);

    }


    /**
     * Allocate resources, including proxy.
     * Return <code>true</code> if initialization was successfull,
     * or <code>false</code> otherwise.
     */
    public boolean resourcesStart() {

        boolean ok = true;

        Hashtable env = new Hashtable();
        if (getParent() != null)
            env.put(ProxyDirContext.HOST, getParent().getName());
        env.put(ProxyDirContext.CONTEXT, getName());

        try {
            ProxyDirContext proxyDirContext =
                new ProxyDirContext(env, webappResources);
            if (webappResources instanceof FileDirContext) {
                filesystemBased = true;
                ((FileDirContext) webappResources).setCaseSensitive
                    (isCaseSensitive());
                ((FileDirContext) webappResources).setAllowLinking
                    (isAllowLinking());
            }
            if (webappResources instanceof BaseDirContext) {
                ((BaseDirContext) webappResources).setDocBase(getBasePath());
                ((BaseDirContext) webappResources).setCached
                    (isCachingAllowed());
                ((BaseDirContext) webappResources).setCacheTTL(getCacheTTL());
                ((BaseDirContext) webappResources).setCacheMaxSize
                    (getCacheMaxSize());
                ((BaseDirContext) webappResources).allocate();
            }
            // Register the cache in JMX
            if (org.apache.tomcat.util.Constants.ENABLE_MODELER) {
                if (isCachingAllowed()) {
                    ObjectName resourcesName = 
                        new ObjectName(this.getDomain() + ":type=Cache,host=" 
                                + getHostname() + ",path=" 
                                + (("".equals(getPath()))?"/":getPath()));
                    Registry.getRegistry(null, null).registerComponent
                        (proxyDirContext.getCache(), resourcesName, null);
                }
            }
            this.resources = proxyDirContext;
        } catch (Throwable t) {
            log.error(sm.getString("standardContext.resourcesStart"), t);
            ok = false;
        }

        return (ok);

    }


    /**
     * Deallocate resources and destroy proxy.
     */
    public boolean resourcesStop() {

        boolean ok = true;

        try {
            if (resources != null) {
                if (resources instanceof Lifecycle) {
                    ((Lifecycle) resources).stop();
                }
                if (webappResources instanceof BaseDirContext) {
                    ((BaseDirContext) webappResources).release();
                }
                // Unregister the cache in JMX
                if (org.apache.tomcat.util.Constants.ENABLE_MODELER) {
                    if (isCachingAllowed()) {
                        ObjectName resourcesName = 
                            new ObjectName(this.getDomain()
                                    + ":type=Cache,host=" 
                                    + getHostname() + ",path=" 
                                    + (("".equals(getPath()))?"/"
                                            :getPath()));
                        Registry.getRegistry(null, null)
                        .unregisterComponent(resourcesName);
                    }
                }
            }
        } catch (Throwable t) {
            log.error(sm.getString("standardContext.resourcesStop"), t);
            ok = false;
        }

        this.resources = null;

        return (ok);

    }


    /**
     * Load and initialize all servlets marked "load on startup" in the
     * web application deployment descriptor.
     *
     * @param children Array of wrappers for all currently defined
     *  servlets (including those not declared load on startup)
     */
    public void loadOnStartup(Container children[]) {

        // Collect "load on startup" servlets that need to be initialized
        TreeMap<Integer, ArrayList<Wrapper>> map =
            new TreeMap<Integer, ArrayList<Wrapper>>();
        for (int i = 0; i < children.length; i++) {
            Wrapper wrapper = (Wrapper) children[i];
            int loadOnStartup = wrapper.getLoadOnStartup();
            if (loadOnStartup < 0)
                continue;
            Integer key = Integer.valueOf(loadOnStartup);
            ArrayList<Wrapper> list = map.get(key);
            if (list == null) {
                list = new ArrayList<Wrapper>();
                map.put(key, list);
            }
            list.add(wrapper);
        }

        // Load the collected "load on startup" servlets
        for (ArrayList<Wrapper> list : map.values()) {
            for (Wrapper wrapper : list) {
                try {
                    wrapper.load();
                } catch (ServletException e) {
                    getLogger().error(sm.getString("standardWrapper.loadException",
                                      getName()), StandardWrapper.getRootCause(e));
                    // NOTE: load errors (including a servlet that throws
                    // UnavailableException from tht init() method) are NOT
                    // fatal to application startup
                }
            }
        }

    }


    /**
     * Start this Context component.
     *
     * @exception LifecycleException if a startup error occurs
     */
    public synchronized void start() throws LifecycleException {
        if (started) {
            return;
        }
        if( !initialized ) { 
            try {
                init();
            } catch( Exception ex ) {
                throw new LifecycleException("Error initializaing ", ex);
            }
        }
        if(log.isDebugEnabled())
            log.debug("Starting " + ("".equals(getName()) ? "ROOT" : getName()));

        if (org.apache.tomcat.util.Constants.ENABLE_MODELER) {
            // Set JMX object name for proper pipeline registration
            preRegisterJMX();

            if ((oname != null) && 
                    (Registry.getRegistry(null, null).getMBeanServer().isRegistered(oname))) {
                // As things depend on the JMX registration, the context
                // must be reregistered again once properly initialized
                Registry.getRegistry(null, null).unregisterComponent(oname);
            }
        }

        // Notify our interested LifecycleListeners
        lifecycle.fireLifecycleEvent(BEFORE_START_EVENT, null);

        setAvailable(false);
        setStarting(true);
        setConfigured(false);
        boolean ok = true;

        // Add missing components as necessary
        if (webappResources == null) {   // (1) Required by Loader
            if (log.isDebugEnabled())
                log.debug("Configuring default Resources");
            try {
                if ((docBase != null) && (docBase.endsWith(".war")) && (!(new File(getBasePath())).isDirectory()))
                    setResources(new WARDirContext());
                else
                    setResources(new FileDirContext());
            } catch (IllegalArgumentException e) {
                log.error("Error initializing resources: " + e.getMessage());
                ok = false;
            }
        }
        if (ok) {
            if (!resourcesStart()) {
                log.error( "Error in resourceStart()");
                ok = false;
            }
        }

        // Initialize character set mapper
        getCharsetMapper();

        // Post work directory
        postWorkDirectory();

        // Standard container startup
        if (log.isDebugEnabled())
            log.debug("Processing standard container startup");

        // Binding thread
        ClassLoader oldCCL = bindThread();

        try {

            if (ok) {
                
                started = true;

                // Start our subordinate components, if any
                if ((loader != null) && (loader instanceof Lifecycle))
                    ((Lifecycle) loader).start();

                if ((loader != null) && (loader.getClassLoader() != null)) {
                    DirContextURLStreamHandler.bind(loader.getClassLoader(), getResources());
                }

                // Unbinding thread
                unbindThread(oldCCL);

                // Binding thread
                oldCCL = bindThread();

                // Initialize logger again. Other components might have used it too early, 
                // so it should be reset.
                logger = null;
                getLogger();
                if ((logger != null) && (logger instanceof Lifecycle))
                    ((Lifecycle) logger).start();
                
                if ((cluster != null) && (cluster instanceof Lifecycle))
                    ((Lifecycle) cluster).start();
                if ((realm != null) && (realm instanceof Lifecycle))
                    ((Lifecycle) realm).start();
                if ((resources != null) && (resources instanceof Lifecycle))
                    ((Lifecycle) resources).start();

                // Start our child containers, if any
                Container children[] = findChildren();
                for (int i = 0; i < children.length; i++) {
                    if (children[i] instanceof Lifecycle)
                        ((Lifecycle) children[i]).start();
                }

                // Start the Valves in our pipeline (including the basic),
                // if any
                if (pipeline instanceof Lifecycle) {
                    ((Lifecycle) pipeline).start();
                }
                
                // Notify our interested LifecycleListeners
                lifecycle.fireLifecycleEvent(START_EVENT, null);
                
                // Acquire clustered manager
                Manager contextManager = null;
                if (manager == null) {
                    if ( (getCluster() != null) && distributable) {
                        contextManager = getCluster().createManager(getName());
                    } else {
                        contextManager = new StandardManager();
                    }
                } 
                
                // Configure default manager if none was specified
                if (contextManager != null) {
                    setManager(contextManager);
                }

                // Configure the session timeout
                if (manager != null) {
                    manager.setMaxInactiveInterval(sessionTimeout * 60);
                }

                if (manager!=null && (getCluster() != null) && distributable) {
                    //let the cluster know that there is a context that is distributable
                    //and that it has its own manager
                    getCluster().registerManager(manager);
                }

            }

        } catch (Throwable t) {
            // This can happen in rare cases with custom components
            ok = false;
            log.error(sm.getString("standardContext.startFailed", getName()), t);
        } finally {
            // Unbinding thread
            unbindThread(oldCCL);
        }

        if (!getConfigured()) {
            ok = false;
        }

        // Binding thread
        oldCCL = bindThread();

        try {
            
            // Create context attributes that will be required
            if (ok) {
                postContextAttributes();
            }
            
            if (ok) {
                // Notify our interested LifecycleListeners
                lifecycle.fireLifecycleEvent(AFTER_START_EVENT, null);
            }
            
            // Configure and call application event listeners
            if (ok) {
                if (!contextListenerStart()) {
                    log.error( "Error listenerStart");
                    ok = false;
                }
            }

           // Start manager
            if (ok && (manager != null) && (manager instanceof Lifecycle)) {
                ok = false;
                ((Lifecycle) getManager()).start();
                ok = true;
            }

            // Configure and call application filters
            if (ok) {
                if (!filterStart()) {
                    log.error( "Error filterStart");
                    ok = false;
                }
            }
            
            // Load and initialize all "load on startup" servlets
            if (ok) {
                loadOnStartup(findChildren());
            }
            
            if (ok) {
                if (!listenerStart()) {
                    log.error( "Error listenerStart");
                    ok = false;
                }
            }

            if (ok) {
                // Notify our interested LifecycleListeners
                lifecycle.fireLifecycleEvent(COMPLETE_CONFIG_EVENT, null);
            }
            
            if (!getConfigured()) {
                ok = false;
            }

            // Start ContainerBackgroundProcessor thread
            if (ok) {
                super.threadStart();
            }

        } catch (Throwable t) {
            // This can happen in rare cases with custom components
            ok = false;
            log.error(sm.getString("standardContext.startFailed", getName()), t);
        } finally {
            // Unbinding thread
            unbindThread(oldCCL);
        }

        // Initialize associated mapper
        mapper.setContext(getPath(), welcomeFiles, resources);

        // Set available status depending upon startup success
        if (ok) {
            if (log.isDebugEnabled())
                log.debug("Starting completed");
            setAvailable(true);
        } else {
            log.error(sm.getString("standardContext.startFailed", getName()));
            try {
                stop();
            } catch (Throwable t) {
                log.error(sm.getString("standardContext.startCleanup"), t);
            }
            setAvailable(false);
        }
        setStarting(false);

        // JMX registration
        if (org.apache.tomcat.util.Constants.ENABLE_MODELER) {
            registerJMX();
        }

        startTime=System.currentTimeMillis();
        
        // Send j2ee.state.running notification 
        if (ok && (this.getObjectName() != null)) {
            Notification notification = 
                new Notification("j2ee.state.running", this.getObjectName(), 
                                sequenceNumber++);
            broadcaster.sendNotification(notification);
        }

        // Reinitializing if something went wrong
        if (!ok && started) {
            stop();
        }

        //cacheContext();
    }

    /**
     * Stop this Context component.
     *
     * @exception LifecycleException if a shutdown error occurs
     */
    public synchronized void stop() throws LifecycleException {

        // Validate and update our current component state
        if (!started) {
            if(log.isInfoEnabled())
                log.info(sm.getString("containerBase.notStarted", logName()));
            return;
        }

        // Notify our interested LifecycleListeners
        lifecycle.fireLifecycleEvent(BEFORE_STOP_EVENT, null);
        
        // Send j2ee.state.stopping notification 
        if (this.getObjectName() != null) {
            Notification notification = 
                new Notification("j2ee.state.stopping", this.getObjectName(), 
                                sequenceNumber++);
            broadcaster.sendNotification(notification);
        }
        
        // Mark this application as unavailable while we shut down
        setAvailable(false);

        // Binding thread
        ClassLoader oldCCL = bindThread();

        try {

            // Stop our child containers, if any
            Container[] children = findChildren();
            for (int i = 0; i < children.length; i++) {
                if (children[i] instanceof Lifecycle)
                    ((Lifecycle) children[i]).stop();
            }

            // Stop our filters
            filterStop();

            // Stop ContainerBackgroundProcessor thread
            super.threadStop();

            if ((manager != null) && (manager instanceof Lifecycle)) {
                ((Lifecycle) manager).stop();
            }

            // Stop our application listeners
            listenerStop();

            // Finalize our character set mapper
            setCharsetMapper(null);

            // Normal container shutdown processing
            if (log.isDebugEnabled())
                log.debug("Processing standard container shutdown");
            // Notify our interested LifecycleListeners
            lifecycle.fireLifecycleEvent(STOP_EVENT, null);
            started = false;

            // Stop the Valves in our pipeline (including the basic), if any
            if (pipeline instanceof Lifecycle) {
                ((Lifecycle) pipeline).stop();
            }

            // Clear all application-originated servlet context attributes
            if (context != null)
                context.clearAttributes();

            // Stop resources
            resourcesStop();

            if ((realm != null) && (realm instanceof Lifecycle)) {
                ((Lifecycle) realm).stop();
            }
            if ((cluster != null) && (cluster instanceof Lifecycle)) {
                ((Lifecycle) cluster).stop();
            }
            if ((logger != null) && (logger instanceof Lifecycle)) {
                ((Lifecycle) logger).stop();
            }
            if ((loader != null) && (loader.getClassLoader() != null)) {
                DirContextURLStreamHandler.unbind(loader.getClassLoader());
            }
            if ((loader != null) && (loader instanceof Lifecycle)) {
                ((Lifecycle) loader).stop();
            }

        } finally {

            // Unbinding thread
            unbindThread(oldCCL);

        }

        // Send j2ee.state.stopped notification 
        if (this.getObjectName() != null) {
            Notification notification = 
                new Notification("j2ee.state.stopped", this.getObjectName(), 
                                sequenceNumber++);
            broadcaster.sendNotification(notification);
        }
        
        // Reset application context
        context = null;

        // This object will no longer be visible or used. 
        try {
            resetContext();
        } catch( Exception ex ) {
            log.error( "Error reseting context " + this + " " + ex, ex );
        }
        
        // Notify our interested LifecycleListeners
        lifecycle.fireLifecycleEvent(AFTER_STOP_EVENT, null);

        if (log.isDebugEnabled())
            log.debug("Stopping complete");

    }

    /** Destroy needs to clean up the context completely.
     * 
     * The problem is that undoing all the config in start() and restoring 
     * a 'fresh' state is impossible. After stop()/destroy()/init()/start()
     * we should have the same state as if a fresh start was done - i.e
     * read modified web.xml, etc. This can only be done by completely 
     * removing the context object and remapping a new one, or by cleaning
     * up everything.
     */ 
    public synchronized void destroy() throws Exception {
        if( oname != null ) { 
            // Send j2ee.object.deleted notification 
            Notification notification = 
                new Notification("j2ee.object.deleted", this.getObjectName(), 
                                sequenceNumber++);
            broadcaster.sendNotification(notification);
        } 
        super.destroy();

        // Notify our interested LifecycleListeners
        lifecycle.fireLifecycleEvent(DESTROY_EVENT, null);

        instanceListeners = new String[0];

    }
    
    protected void resetContext() throws Exception, MBeanRegistrationException {
        // Restore the original state ( pre reading web.xml in start )
        // If you extend this - override this method and make sure to clean up
        children=new HashMap();
        startupTime = 0;
        startTime = 0;
        tldScanTime = 0;

        // Bugzilla 32867
        distributable = false;

        applicationListeners = new String[0];
        applicationEventListenersInstances = null;
        applicationLifecycleListenersInstances = null;
        applicationSessionLifecycleListenersInstances = null;
        listenersInstances = null;
        instanceManager = null;
        
        authenticator = null;
        
        if(log.isDebugEnabled())
            log.debug("resetContext " + oname);
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
        sb.append("StandardContext[");
        sb.append(getName());
        sb.append("]");
        return (sb.toString());

    }


    // ------------------------------------------------------ Protected Methods


    /**
     * Adjust the URL pattern to begin with a leading slash, if appropriate
     * (i.e. we are running a servlet 2.2 application).  Otherwise, return
     * the specified URL pattern unchanged.
     *
     * @param urlPattern The URL pattern to be adjusted (if needed)
     *  and returned
     */
    protected String adjustURLPattern(String urlPattern) {

        if (urlPattern == null)
            return (urlPattern);
        if (urlPattern.startsWith("/") || urlPattern.startsWith("*."))
            return (urlPattern);
        if (!isServlet22())
            return (urlPattern);
        if(log.isDebugEnabled())
            log.debug(sm.getString("standardContext.urlPattern.patternWarning",
                         urlPattern));
        return ("/" + urlPattern);

    }


    /**
     * Is the specified listener restricted ?
     */
    protected boolean isRestricted(Object listener) {
        return restrictedApplicationListeners.contains(listener.getClass().getName());
    }


   /**
     * Are we processing a version 2.2 deployment descriptor?
     */
    protected boolean isServlet22() {

        if (this.publicId == null)
            return (false);
        if (this.publicId.equals
            (org.apache.catalina.startup.Constants.WebDtdPublicId_22))
            return (true);
        else
            return (false);

    }


    /**
     * Return a File object representing the base directory for the
     * entire servlet container (i.e. the Engine container if present).
     */
    protected File engineBase() {
        String base=System.getProperty("catalina.base");
        if( base == null ) {
            StandardEngine eng=(StandardEngine)this.getParent().getParent();
            base=eng.getBaseDir();
        }
        return (new File(base));
    }


    protected File workBase() {
        String base = System.getProperty("catalina.work");
        if( base == null ) {
            return engineBase();
        } else {
            return (new File(base));
        }
    }


    // -------------------------------------------------------- protected Methods


    /**
     * Bind current thread, both for CL purposes and for JNDI ENC support
     * during : startup, shutdown and realoading of the context.
     *
     * @return the previous context class loader
     */
    protected ClassLoader bindThread() {

        ClassLoader oldContextClassLoader =
            Thread.currentThread().getContextClassLoader();

        if ((getLoader() != null) && getLoader().getClassLoader() != null) {
            Thread.currentThread().setContextClassLoader
                (getLoader().getClassLoader());
        }

        threadBindingListener.bind();
        lifecycle.fireLifecycleEvent(BIND_THREAD_EVENT, null);
        
        return oldContextClassLoader;

    }


    /**
     * Unbind thread.
     */
    protected void unbindThread(ClassLoader oldContextClassLoader) {

        lifecycle.fireLifecycleEvent(UNBIND_THREAD_EVENT, null);
        threadBindingListener.unbind();

        Thread.currentThread().setContextClassLoader(oldContextClassLoader);

        oldContextClassLoader = null;

    }



    /**
     * Get base path.
     */
    protected String getBasePath() {
        String docBase = null;
        Container container = this;
        while (container != null) {
            if (container instanceof Host)
                break;
            container = container.getParent();
        }
        File file = new File(getDocBase());
        if (!file.isAbsolute()) {
            if (container == null) {
                docBase = (new File(engineBase(), getDocBase())).getPath();
            } else {
                // Use the "appBase" property of this container
                String appBase = ((Host) container).getAppBase();
                file = new File(appBase);
                if (!file.isAbsolute())
                    file = new File(engineBase(), appBase);
                docBase = (new File(file, getDocBase())).getPath();
            }
        } else {
            docBase = file.getPath();
        }
        return docBase;
    }


    /**
     * Get app base.
     */
    protected String getAppBase() {
        String appBase = null;
        Container container = this;
        while (container != null) {
            if (container instanceof Host)
                break;
            container = container.getParent();
        }
        if (container != null) {
            appBase = ((Host) container).getAppBase();
        }
        return appBase;
    }


    /**
     * Get config base.
     */
    public File getConfigBase() {
        File configBase = 
            new File(System.getProperty("catalina.base"), "conf");
        if (!configBase.exists()) {
            return null;
        }
        Container container = this;
        Container host = null;
        Container engine = null;
        while (container != null) {
            if (container instanceof Host)
                host = container;
            if (container instanceof Engine)
                engine = container;
            container = container.getParent();
        }
        if (engine != null) {
            configBase = new File(configBase, engine.getName());
        }
        if (host != null) {
            configBase = new File(configBase, host.getName());
        }
        return configBase;
    }


    /**
     * Given a context path, get the config file name.
     */
    protected String getDefaultConfigFile() {
        String basename = null;
        String path = getPath();
        if (path.equals("")) {
            basename = "ROOT";
        } else {
            basename = path.substring(1).replace('/', '#');
        }
        return (basename + ".xml");
    }


    /**
     * Copy a file.
     */
    protected boolean copy(File src, File dest) {
        FileInputStream is = null;
        FileOutputStream os = null;
        try {
            is = new FileInputStream(src);
            os = new FileOutputStream(dest);
            byte[] buf = new byte[4096];
            while (true) {
                int len = is.read(buf);
                if (len < 0)
                    break;
                os.write(buf, 0, len);
            }
            is.close();
            os.close();
        } catch (IOException e) {
            return false;
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (Exception e) {
                // Ignore
            }
            try {
                if (os != null) {
                    os.close();
                }
            } catch (Exception e) {
                // Ignore
            }
        }
        return true;
    }


    /**
     * Return the request processing paused flag for this Context.
     */
    public boolean getPaused() {

        return (this.paused);

    }


    /**
     * Create mandatory servlet context attributes.
     */
    protected void postContextAttributes() {
        ServletContext context = getServletContext();
        context.setAttribute(Globals.SERVLET_VERSION, version);
        context.setAttribute(Globals.RESOURCES_ATTR, getResources());
        context.setAttribute(Globals.WELCOME_FILES_ATTR, welcomeFiles);
        // Jasper attributes
        context.setAttribute(Globals.JSP_PROPERTY_GROUPS, jspPropertyGroups);
        context.setAttribute(Globals.JSP_TAG_LIBRARIES, jspTagLibraries);
        // Instance manager (also used by Jasper)
        context.setAttribute(InstanceManager.class.getName(), instanceManager);
    }


    public String getHostname() {
        Container parentHost = getParent();
        if (parentHost != null) {
            hostName = parentHost.getName();
        }
        if ((hostName == null) || (hostName.length() < 1))
            hostName = "_";
        return hostName;
    }

    /**
     * Set the appropriate context attribute for our work directory.
     */
    protected void postWorkDirectory() {

        // Acquire (or calculate) the work directory path
        String workDir = getWorkDir();
        if (workDir == null || workDir.length() == 0) {

            // Retrieve our parent (normally a host) name
            String hostName = null;
            String engineName = null;
            String hostWorkDir = null;
            Container parentHost = getParent();
            if (parentHost != null) {
                hostName = parentHost.getName();
                if (parentHost instanceof StandardHost) {
                    hostWorkDir = ((StandardHost)parentHost).getWorkDir();
                }
                Container parentEngine = parentHost.getParent();
                if (parentEngine != null) {
                   engineName = parentEngine.getName();
                }
            }
            if ((hostName == null) || (hostName.length() < 1))
                hostName = "_";
            if ((engineName == null) || (engineName.length() < 1))
                engineName = "_";

            String temp = getPath();
            if (temp.startsWith("/"))
                temp = temp.substring(1);
            temp = temp.replace('/', '_');
            temp = temp.replace('\\', '_');
            if (temp.length() < 1)
                temp = "_";
            if (hostWorkDir != null ) {
                workDir = hostWorkDir + File.separator + temp;
            } else {
                workDir = "work" + File.separator + engineName +
                    File.separator + hostName + File.separator + temp;
            }
            this.workDir = workDir;
        }

        // Create this directory if necessary
        File dir = new File(getWorkPath());
        dir.mkdirs();

        // Set the appropriate servlet context attribute
        getServletContext();
        context.setAttribute(ServletContext.TEMPDIR, dir);
        context.setAttributeReadOnly(ServletContext.TEMPDIR);

    }


    /**
     * Set the request processing paused flag for this Context.
     *
     * @param paused The new request processing paused flag
     */
    protected void setPaused(boolean paused) {

        this.paused = paused;

    }


    /**
     * Validate the syntax of a proposed <code>&lt;url-pattern&gt;</code>
     * for conformance with specification requirements.
     *
     * @param urlPattern URL pattern to be validated
     */
    protected boolean validateURLPattern(String urlPattern) {

        if (urlPattern == null)
            return (false);
        if (urlPattern.indexOf('\n') >= 0 || urlPattern.indexOf('\r') >= 0) {
            return (false);
        }
        if (urlPattern.equals("")) {
            return (true);
        }
        if (urlPattern.startsWith("*.")) {
            if (urlPattern.indexOf('/') < 0) {
                checkUnusualURLPattern(urlPattern);
                return (true);
            } else {
                return (false);
            }
        }
        if ( (urlPattern.startsWith("/")) &&
                (urlPattern.indexOf("*.") < 0)) {
            checkUnusualURLPattern(urlPattern);
            return (true);
        } else {
            return (false);
        }

    }


    /**
     * Check for unusual but valid <code>&lt;url-pattern&gt;</code>s.
     * See Bugzilla 34805, 43079 & 43080
     */
    protected void checkUnusualURLPattern(String urlPattern) {
        if (log.isInfoEnabled()) {
            if(urlPattern.endsWith("*") && (urlPattern.length() < 2 ||
                    urlPattern.charAt(urlPattern.length()-2) != '/')) {
                log.info("Suspicious url pattern: \"" + urlPattern + "\"" +
                        " in context [" + getName() + "] - see" +
                " section SRV.11.2 of the Servlet specification" );
            }
        }
    }


    // ------------------------------------------------------------- Operations


    /**
     * JSR77 deploymentDescriptor attribute
     *
     * @return string deployment descriptor 
     */
    public String getDeploymentDescriptor() {
    
        InputStream stream = null;
        ServletContext servletContext = getServletContext();
        if (servletContext != null) {
            stream = servletContext.getResourceAsStream(
                org.apache.catalina.startup.Constants.ApplicationWebXml);
        }
        if (stream == null) {
            return "";
        }
        BufferedReader br = null;
        StringBuilder sb = new StringBuilder();
        try {
            br = new BufferedReader(new InputStreamReader(stream));
            String strRead = "";
            while (strRead != null) {
                sb.append(strRead);
                strRead = br.readLine();
            }
        } catch (IOException e) {
            return "";
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }

        return sb.toString(); 
    
    }
    
    
    /**
     * JSR77 servlets attribute
     *
     * @return list of all servlets ( we know about )
     */
    public String[] getServlets() {
        
        String[] result = null;

        Container[] children = findChildren();
        if (children != null) {
            result = new String[children.length];
            for( int i=0; i< children.length; i++ ) {
                result[i] = ((StandardWrapper)children[i]).getObjectName();
            }
        }

        return result;
    }
    

    public ObjectName createObjectName(String hostDomain, ObjectName parentName)
            throws MalformedObjectNameException
    {
        String onameStr;
        StandardHost hst=(StandardHost)getParent();
        
        String pathName=getName();
        String hostName=getParent().getName();
        String name= "//" + ((hostName==null)? "DEFAULT" : hostName) +
                (("".equals(pathName))?"/":pathName );

        String suffix=",J2EEApplication=" +
                getJ2EEApplication() + ",J2EEServer=" +
                getJ2EEServer();

        onameStr="j2eeType=WebModule,name=" + name + suffix;
        if( log.isDebugEnabled())
            log.debug("Registering " + onameStr + " for " + oname);
        
        // default case - no domain explictely set.
        if( getDomain() == null ) domain=hst.getDomain();

        ObjectName oname=new ObjectName(getDomain() + ":" + onameStr);
        return oname;        
    }    
    
    protected void preRegisterJMX() {
        try {
            StandardHost host = (StandardHost) getParent();
            if ((oname == null) 
                || (oname.getKeyProperty("j2eeType") == null)) {
                oname = createObjectName(host.getDomain(), host.getJmxName());
                controller = oname;
            }
        } catch(Exception ex) {
            if(log.isInfoEnabled())
                log.info("Error registering ctx with jmx " + this + " " +
                     oname + " " + ex.toString(), ex );
        }
    }

    protected void registerJMX() {
        try {
            if (log.isDebugEnabled()) {
                log.debug("Checking for " + oname );
            }
            if(! Registry.getRegistry(null, null)
                .getMBeanServer().isRegistered(oname)) {
                controller = oname;
                Registry.getRegistry(null, null)
                    .registerComponent(this, oname, null);
                
                // Send j2ee.object.created notification 
                if (this.getObjectName() != null) {
                    Notification notification = new Notification(
                                                        "j2ee.object.created", 
                                                        this.getObjectName(), 
                                                        sequenceNumber++);
                    broadcaster.sendNotification(notification);
                }
            }
            Container children[] = findChildren();
            for (int i=0; children!=null && i<children.length; i++) {
                ((StandardWrapper)children[i]).registerJMX( this );
            }
        } catch (Exception ex) {
            if(log.isInfoEnabled())
                log.info("Error registering wrapper with jmx " + this + " " +
                    oname + " " + ex.toString(), ex );
        }
    }

    /** There are 2 cases:
     *   1.The context is created and registered by internal APIS
     *   2. The context is created by JMX, and it'll self-register.
     *
     * @param server The server
     * @param name The object name
     * @return ObjectName The name of the object
     * @throws Exception If an error occurs
     */
    public ObjectName preRegister(MBeanServer server,
                                  ObjectName name)
            throws Exception
    {
        if( oname != null ) {
            //log.info( "Already registered " + oname + " " + name);
            // Temporary - /admin uses the old names
            return name;
        }
        ObjectName result=super.preRegister(server,name);
        return name;
    }

    public void preDeregister() throws Exception {
        if( started ) {
            try {
                stop();
            } catch( Exception ex ) {
                log.error( "error stopping ", ex);
            }
        }
    }

    public synchronized void init() throws Exception {

        super.init();
        
        // Notify our interested LifecycleListeners
        lifecycle.fireLifecycleEvent(INIT_EVENT, null);

        // Send j2ee.state.starting notification 
        if (this.getObjectName() != null) {
            Notification notification = new Notification("j2ee.state.starting", 
                                                        this.getObjectName(), 
                                                        sequenceNumber++);
            broadcaster.sendNotification(notification);
        }
        
    }


    public boolean isInitialized() {
        return initialized;
    }


    public ObjectName getParentName() throws MalformedObjectNameException {
        // "Life" update
        String path=oname.getKeyProperty("name");
        if( path == null ) {
            log.error( "No name attribute " +name );
            return null;
        }
        if( ! path.startsWith( "//")) {
            log.error("Invalid name " + name);
        }
        path=path.substring(2);
        int delim=path.indexOf( "/" );
        hostName="localhost"; // Should be default...
        if( delim > 0 ) {
            hostName=path.substring(0, delim);
            path = path.substring(delim);
            if (path.equals("/")) {
                this.setName("");
            } else {
                this.setName(path);
            }
        } else {
            if(log.isDebugEnabled())
                log.debug("Setting path " +  path );
            this.setName( path );
        }
        // XXX The service and domain should be the same.
        String parentDomain=getEngineName();
        if( parentDomain == null ) parentDomain=domain;
        ObjectName parentName=new ObjectName( parentDomain + ":" +
                "type=Host,host=" + hostName);
        return parentName;
    }
    
    public void create() throws Exception{
        init();
    }

    /* Remove a JMX notficationListener 
     * @see javax.management.NotificationEmitter#removeNotificationListener(javax.management.NotificationListener, javax.management.NotificationFilter, java.lang.Object)
     */
    public void removeNotificationListener(NotificationListener listener, 
            NotificationFilter filter, Object object) throws ListenerNotFoundException {
    	broadcaster.removeNotificationListener(listener,filter,object);
    	
    }
    
    protected MBeanNotificationInfo[] notificationInfo;
    
    /* Get JMX Broadcaster Info
     * @TODO use StringManager for international support!
     * @TODO This two events we not send j2ee.state.failed and j2ee.attribute.changed!
     * @see javax.management.NotificationBroadcaster#getNotificationInfo()
     */
    public MBeanNotificationInfo[] getNotificationInfo() {
    	// FIXME: i18n
    	if(notificationInfo == null) {
    		notificationInfo = new MBeanNotificationInfo[]{
    				new MBeanNotificationInfo(new String[] {
    				"j2ee.object.created"},
					Notification.class.getName(),
					"web application is created"
    				), 
					new MBeanNotificationInfo(new String[] {
					"j2ee.state.starting"},
					Notification.class.getName(),
					"change web application is starting"
					),
					new MBeanNotificationInfo(new String[] {
					"j2ee.state.running"},
					Notification.class.getName(),
					"web application is running"
					),
					new MBeanNotificationInfo(new String[] {
					"j2ee.state.stopping"},
					Notification.class.getName(),
					"web application start to stopped"
					),
					new MBeanNotificationInfo(new String[] {
					"j2ee.object.stopped"},
					Notification.class.getName(),
					"web application is stopped"
					),
					new MBeanNotificationInfo(new String[] {
					"j2ee.object.deleted"},
					Notification.class.getName(),
					"web application is deleted"
					)
    		};
    		
    	}
    	
    	return notificationInfo;
    }
    
    
    /* Add a JMX-NotificationListener
     * @see javax.management.NotificationBroadcaster#addNotificationListener(javax.management.NotificationListener, javax.management.NotificationFilter, java.lang.Object)
     */
    public void addNotificationListener(NotificationListener listener, 
            NotificationFilter filter, Object object) throws IllegalArgumentException {
    	broadcaster.addNotificationListener(listener,filter,object);
    	
    }
    
    
    /**
     * Remove a JMX-NotificationListener 
     * @see javax.management.NotificationBroadcaster#removeNotificationListener(javax.management.NotificationListener)
     */
    public void removeNotificationListener(NotificationListener listener) 
    throws ListenerNotFoundException {
    	broadcaster.removeNotificationListener(listener);
    	
    }
    
    
    // ------------------------------------------------------------- Attributes


    /**
     * Return the naming resources associated with this web application.
     */
    public javax.naming.directory.DirContext getStaticResources() {

        return getResources();

    }


    /**
     * Return the naming resources associated with this web application.
     * FIXME: Fooling introspection ... 
     */
    public javax.naming.directory.DirContext findStaticResources() {

        return getResources();

    }


    /**
     * Return the naming resources associated with this web application.
     */
    public String[] getWelcomeFiles() {

        return findWelcomeFiles();

    }

    /** 
     * Support for "stateManageable" JSR77 
     */
    public boolean isStateManageable() {
        return true;
    }
    
    public void startRecursive() throws LifecycleException {
        // nothing to start recursive, the servlets will be started by load-on-startup
        start();
    }
    
    public int getState() {
        if( started ) {
            return 1; // RUNNING
        }
        if( initialized ) {
            return 0; // starting ? 
        }
        if( ! available ) { 
            return 4; //FAILED
        }
        // 2 - STOPPING
        return 3; // STOPPED
    }
    
    /**
     * The J2EE Server ObjectName this module is deployed on.
     */     
    protected String server = null;
    
    /**
     * The Java virtual machines on which this module is running.
     */       
    protected String[] javaVMs = null;
    
    public String getServer() {
        return server;
    }
        
    public String setServer(String server) {
        return this.server=server;
    }
        
    public String[] getJavaVMs() {
        return javaVMs;
    }
        
    public String[] setJavaVMs(String[] javaVMs) {
        return this.javaVMs = javaVMs;
    }
    
    /**
     * Gets the time this context was started.
     *
     * @return Time (in milliseconds since January 1, 1970, 00:00:00) when this
     * context was started 
     */
    public long getStartTime() {
        return startTime;
    }
    
    public boolean isEventProvider() {
        return false;
    }
    
    public boolean isStatisticsProvider() {
        return false;
    }

    
}
