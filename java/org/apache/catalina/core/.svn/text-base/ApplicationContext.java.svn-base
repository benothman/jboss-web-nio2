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
 * 
 * 
 * This file incorporates work covered by the following copyright and
 * permission notice:
 *
 * Copyright 1999-2009 The Apache Software Foundation
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */


package org.apache.catalina.core;


import java.io.File;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.naming.Binding;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.servlet.Filter;
import javax.servlet.FilterRegistration;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextAttributeEvent;
import javax.servlet.ServletContextAttributeListener;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.ServletRequestAttributeListener;
import javax.servlet.ServletRequestListener;
import javax.servlet.SessionCookieConfig;
import javax.servlet.SessionTrackingMode;
import javax.servlet.descriptor.JspConfigDescriptor;
import javax.servlet.descriptor.JspPropertyGroupDescriptor;
import javax.servlet.descriptor.TaglibDescriptor;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionListener;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.Host;
import org.apache.catalina.Wrapper;
import org.apache.catalina.deploy.ApplicationParameter;
import org.apache.catalina.deploy.FilterDef;
import org.apache.catalina.deploy.JspPropertyGroup;
import org.apache.catalina.util.Enumerator;
import org.apache.catalina.util.RequestUtil;
import org.apache.catalina.util.ResourceSet;
import org.apache.catalina.util.ServerInfo;
import org.apache.catalina.util.StringManager;
import org.apache.naming.resources.DirContextURLStreamHandler;
import org.apache.naming.resources.Resource;
import org.apache.tomcat.util.buf.CharChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.mapper.MappingData;


/**
 * Standard implementation of <code>ServletContext</code> that represents
 * a web application's execution environment.  An instance of this class is
 * associated with each instance of <code>StandardContext</code>.
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 * @version $Revision$ $Date$
 */

public class ApplicationContext
    implements ServletContext {

    // ----------------------------------------------------------- Constructors


    /**
     * Construct a new instance of this class, associated with the specified
     * Context instance.
     *
     * @param context The associated Context instance
     */
    public ApplicationContext(String basePath, StandardContext context) {
        super();
        this.context = context;
        this.basePath = basePath;
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * The context attributes for this context.
     */
    protected Map attributes = new ConcurrentHashMap();


    /**
     * List of read only attributes for this context.
     */
    private Map readOnlyAttributes = new ConcurrentHashMap();


    /**
     * The Context instance with which we are associated.
     */
    private StandardContext context = null;


    /**
     * Empty collection to serve as the basis for empty enumerations.
     * <strong>DO NOT ADD ANY ELEMENTS TO THIS COLLECTION!</strong>
     */
    private static final ArrayList empty = new ArrayList();


    /**
     * The facade around this object.
     */
    private ServletContext facade = new ApplicationContextFacade(this);


    /**
     * The merged context initialization parameters for this Context.
     */
    private Map parameters = null;


    /**
     * The string manager for this package.
     */
    private static final StringManager sm =
      StringManager.getManager(Constants.Package);


    /**
     * Base path.
     */
    private String basePath = null;


    /**
     * Thread local data used during request dispatch.
     */
    private ThreadLocal<DispatchData> dispatchData =
        new ThreadLocal<DispatchData>();

    
    /**
     * The restricted flag.
     */
    private boolean restricted = false;
    

    // --------------------------------------------------------- Public Methods


    /**
     * Return the resources object that is mapped to a specified path.
     * The path must begin with a "/" and is interpreted as relative to the
     * current context root.
     */
    public DirContext getResources() {

        return context.getResources();

    }


    public boolean isRestricted() {
        return restricted;
    }


    public void setRestricted(boolean restricted) {
        this.restricted = restricted;
    }


    // ------------------------------------------------- ServletContext Methods


    /**
     * Return the value of the specified context attribute, if any;
     * otherwise return <code>null</code>.
     *
     * @param name Name of the context attribute to return
     */
    public Object getAttribute(String name) {

        return (attributes.get(name));

    }


    /**
     * Return an enumeration of the names of the context attributes
     * associated with this context.
     */
    public Enumeration getAttributeNames() {

        return new Enumerator(attributes.keySet(), true);

    }


    /**
     * Return a <code>ServletContext</code> object that corresponds to a
     * specified URI on the server.  This method allows servlets to gain
     * access to the context for various parts of the server, and as needed
     * obtain <code>RequestDispatcher</code> objects or resources from the
     * context.  The given path must be absolute (beginning with a "/"),
     * and is interpreted based on our virtual host's document root.
     *
     * @param uri Absolute URI of a resource on the server
     */
    public ServletContext getContext(String uri) {

        // Validate the format of the specified argument
        if ((uri == null) || (!uri.startsWith("/")))
            return (null);

        Context child = null;
        try {
            Host host = (Host) context.getParent();
            String mapuri = uri;
            while (true) {
                child = (Context) host.findChild(mapuri);
                if (child != null)
                    break;
                int slash = mapuri.lastIndexOf('/');
                if (slash < 0)
                    break;
                mapuri = mapuri.substring(0, slash);
            }
        } catch (Throwable t) {
            return (null);
        }

        if (child == null)
            return (null);

        if (context.getCrossContext()) {
            // If crossContext is enabled, can always return the context
            return child.getServletContext();
        } else if (child == context) {
            // Can still return the current context
            return context.getServletContext();
        } else {
            // Nothing to return
            return (null);
        }
    }

    
    /**
     * Return the main path associated with this context.
     */
    public String getContextPath() {
        return context.getPath();
    }
    

    /**
     * Return the value of the specified initialization parameter, or
     * <code>null</code> if this parameter does not exist.
     *
     * @param name Name of the initialization parameter to retrieve
     */
    public String getInitParameter(final String name) {

        mergeParameters();
        return ((String) parameters.get(name));

    }


    /**
     * Return the names of the context's initialization parameters, or an
     * empty enumeration if the context has no initialization parameters.
     */
    public Enumeration getInitParameterNames() {

        mergeParameters();
        return (new Enumerator(parameters.keySet()));

    }


    /**
     * Return the major version of the Java Servlet API that we implement.
     */
    public int getMajorVersion() {

        return (Constants.MAJOR_VERSION);

    }


    /**
     * Return the minor version of the Java Servlet API that we implement.
     */
    public int getMinorVersion() {

        return (Constants.MINOR_VERSION);

    }


    /**
     * Return the MIME type of the specified file, or <code>null</code> if
     * the MIME type cannot be determined.
     *
     * @param file Filename for which to identify a MIME type
     */
    public String getMimeType(String file) {

        if (file == null)
            return (null);
        int period = file.lastIndexOf(".");
        if (period < 0)
            return (null);
        String extension = file.substring(period + 1);
        if (extension.length() < 1)
            return (null);
        return (context.findMimeMapping(extension));

    }


    /**
     * Return a <code>RequestDispatcher</code> object that acts as a
     * wrapper for the named servlet.
     *
     * @param name Name of the servlet for which a dispatcher is requested
     */
    public RequestDispatcher getNamedDispatcher(String name) {

        // Validate the name argument
        if (name == null)
            return (null);

        // Create and return a corresponding request dispatcher
        Wrapper wrapper = (Wrapper) context.findChild(name);
        if (wrapper == null)
            return (null);
        
        return new ApplicationDispatcher(wrapper, null, null, null, null, null, name);

    }


    /**
     * Return the real path for a given virtual path, if possible; otherwise
     * return <code>null</code>.
     *
     * @param path The path to the desired resource
     */
    public String getRealPath(String path) {

        if (!context.isFilesystemBased())
            return null;

        if (path == null) {
            return null;
        }

        File file = new File(basePath, path);
        return (file.getAbsolutePath());

    }


    /**
     * Return a <code>RequestDispatcher</code> instance that acts as a
     * wrapper for the resource at the given path.  The path must begin
     * with a "/" and is interpreted as relative to the current context root.
     *
     * @param path The path to the desired resource.
     */
    public RequestDispatcher getRequestDispatcher(String path) {

        // Validate the path argument
        if (path == null)
            return (null);
        if (path.equals(""))
            path = "/";
        if (path.startsWith("?"))
            path = "/" + path;
        if (!path.startsWith("/"))
            throw new IllegalArgumentException
                (sm.getString
                 ("applicationContext.requestDispatcher.iae", path));

        // Get query string
        String queryString = null;
        int pos = path.indexOf('?');
        if (pos >= 0) {
            queryString = path.substring(pos + 1);
            path = path.substring(0, pos);
        }
 
        path = RequestUtil.normalize(path);
        if (path == null)
            return (null);

        pos = path.length();

        // Use the thread local URI and mapping data
        DispatchData dd = dispatchData.get();
        if (dd == null) {
            dd = new DispatchData();
            dispatchData.set(dd);
        }

        MessageBytes uriMB = dd.uriMB;
        uriMB.recycle();

        // Use the thread local mapping data
        MappingData mappingData = dd.mappingData;

        // Map the URI
        CharChunk uriCC = uriMB.getCharChunk();
        try {
            uriCC.append(context.getPath(), 0, context.getPath().length());
            /*
             * Ignore any trailing path params (separated by ';') for mapping
             * purposes
             */
            int semicolon = path.indexOf(';');
            if (pos >= 0 && semicolon > pos) {
                semicolon = -1;
            }
            uriCC.append(path, 0, semicolon > 0 ? semicolon : pos);
            context.getMapper().map(uriMB, mappingData);
            if (mappingData.wrapper == null) {
                return (null);
            }
            /*
             * Append any trailing path params (separated by ';') that were
             * ignored for mapping purposes, so that they're reflected in the
             * RequestDispatcher's requestURI
             */
            if (semicolon > 0) {
                uriCC.append(path, semicolon, pos - semicolon);
            }
        } catch (Exception e) {
            // Should never happen
            log(sm.getString("applicationContext.mapping.error"), e);
            return (null);
        }

        Wrapper wrapper = (Wrapper) mappingData.wrapper;
        String requestPath = mappingData.requestPath.toString();
        String wrapperPath = mappingData.wrapperPath.toString();
        String pathInfo = mappingData.pathInfo.toString();

        mappingData.recycle();
        
        // Construct a RequestDispatcher to process this request
        return new ApplicationDispatcher
            (wrapper, uriCC.toString(), requestPath, wrapperPath, pathInfo, 
             queryString, null);

    }



    /**
     * Return the URL to the resource that is mapped to a specified path.
     * The path must begin with a "/" and is interpreted as relative to the
     * current context root.
     *
     * @param path The path to the desired resource
     *
     * @exception MalformedURLException if the path is not given
     *  in the correct form
     */
    public URL getResource(String path)
        throws MalformedURLException {

        if (path == null || (Globals.STRICT_SERVLET_COMPLIANCE && !path.startsWith("/"))) {
            throw new MalformedURLException(sm.getString("applicationContext.requestDispatcher.iae", path));
        }
        
        path = RequestUtil.normalize(path);
        if (path == null)
            return (null);

        String libPath = "/WEB-INF/lib/";
        if ((path.startsWith(libPath)) && (path.endsWith(".jar"))) {
            File jarFile = null;
            if (context.isFilesystemBased()) {
                jarFile = new File(basePath, path);
            } else {
                jarFile = new File(context.getWorkPath(), path);
            }
            if (jarFile.exists()) {
                return jarFile.toURL();
            } else {
                return null;
            }
        } else {

            DirContext resources = context.getResources();
            if (resources != null) {
                String fullPath = context.getName() + path;
                String hostName = context.getParent().getName();
                try {
                    resources.lookup(path);
                    return new URL
                        ("jndi", "", 0, getJNDIUri(hostName, fullPath),
                         new DirContextURLStreamHandler(resources));
                } catch (Exception e) {
                    // Ignore
                }
            }
        }

        return (null);

    }


    /**
     * Return the requested resource as an <code>InputStream</code>.  The
     * path must be specified according to the rules described under
     * <code>getResource</code>.  If no such resource can be identified,
     * return <code>null</code>.
     *
     * @param path The path to the desired resource.
     */
    public InputStream getResourceAsStream(String path) {

        if (path == null || (Globals.STRICT_SERVLET_COMPLIANCE && !path.startsWith("/")))
            return (null);

        path = RequestUtil.normalize(path);
        if (path == null)
            return (null);

        DirContext resources = context.getResources();
        if (resources != null) {
            try {
                Object resource = resources.lookup(path);
                if (resource instanceof Resource)
                    return (((Resource) resource).streamContent());
            } catch (Exception e) {
            }
        }
        return (null);

    }


    /**
     * Return a Set containing the resource paths of resources member of the
     * specified collection. Each path will be a String starting with
     * a "/" character. The returned set is immutable.
     *
     * @param path Collection path
     */
    public Set getResourcePaths(String path) {

        // Validate the path argument
        if (path == null) {
            return null;
        }
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException
                (sm.getString("applicationContext.resourcePaths.iae", path));
        }

        path = RequestUtil.normalize(path);
        if (path == null)
            return (null);

        DirContext resources = context.getResources();
        if (resources != null) {
            return (getResourcePathsInternal(resources, path));
        }
        return (null);

    }


    /**
     * Internal implementation of getResourcesPath() logic.
     *
     * @param resources Directory context to search
     * @param path Collection path
     */
    private Set getResourcePathsInternal(DirContext resources, String path) {

        ResourceSet set = new ResourceSet();
        try {
            listCollectionPaths(set, resources, path);
        } catch (NamingException e) {
            return (null);
        }
        set.setLocked(true);
        return (set);

    }


    /**
     * Return the name and version of the servlet container.
     */
    public String getServerInfo() {

        return (ServerInfo.getServerInfo());

    }


    /**
     * @deprecated As of Java Servlet API 2.1, with no direct replacement.
     */
    public Servlet getServlet(String name) {

        return (null);

    }


    /**
     * Return the display name of this web application.
     */
    public String getServletContextName() {

        return (context.getDisplayName());

    }


    /**
     * @deprecated As of Java Servlet API 2.1, with no direct replacement.
     */
    public Enumeration getServletNames() {
        return (new Enumerator(empty));
    }


    /**
     * @deprecated As of Java Servlet API 2.1, with no direct replacement.
     */
    public Enumeration getServlets() {
        return (new Enumerator(empty));
    }


    /**
     * Writes the specified message to a servlet log file.
     *
     * @param message Message to be written
     */
    public void log(String message) {

        context.getLogger().info(message);

    }


    /**
     * Writes the specified exception and message to a servlet log file.
     *
     * @param exception Exception to be reported
     * @param message Message to be written
     *
     * @deprecated As of Java Servlet API 2.1, use
     *  <code>log(String, Throwable)</code> instead
     */
    public void log(Exception exception, String message) {
        
        context.getLogger().error(message, exception);

    }


    /**
     * Writes the specified message and exception to a servlet log file.
     *
     * @param message Message to be written
     * @param throwable Exception to be reported
     */
    public void log(String message, Throwable throwable) {
        
        context.getLogger().error(message, throwable);

    }


    /**
     * Remove the context attribute with the specified name, if any.
     *
     * @param name Name of the context attribute to be removed
     */
    public void removeAttribute(String name) {

        Object value = null;
        boolean found = false;

        // Remove the specified attribute
        // Check for read only attribute
        if (readOnlyAttributes.containsKey(name))
            return;
        found = attributes.containsKey(name);
        if (found) {
            value = attributes.get(name);
            attributes.remove(name);
        } else {
            return;
        }

        // Notify interested application event listeners
        Object listeners[] = context.getApplicationEventListeners();
        if ((listeners == null) || (listeners.length == 0))
            return;
        ServletContextAttributeEvent event =
          new ServletContextAttributeEvent(context.getServletContext(),
                                            name, value);
        for (int i = 0; i < listeners.length; i++) {
            if (!(listeners[i] instanceof ServletContextAttributeListener))
                continue;
            ServletContextAttributeListener listener =
                (ServletContextAttributeListener) listeners[i];
            try {
                context.fireContainerEvent("beforeContextAttributeRemoved",
                                           listener);
                listener.attributeRemoved(event);
                context.fireContainerEvent("afterContextAttributeRemoved",
                                           listener);
            } catch (Throwable t) {
                context.fireContainerEvent("afterContextAttributeRemoved",
                                           listener);
                // FIXME - should we do anything besides log these?
                log(sm.getString("applicationContext.attributeEvent"), t);
            }
        }

    }


    /**
     * Bind the specified value with the specified context attribute name,
     * replacing any existing value for that name.
     *
     * @param name Attribute name to be bound
     * @param value New attribute value to be bound
     */
    public void setAttribute(String name, Object value) {

        // Name cannot be null
        if (name == null)
            throw new IllegalArgumentException
                (sm.getString("applicationContext.setAttribute.namenull"));

        // Null value is the same as removeAttribute()
        if (value == null) {
            removeAttribute(name);
            return;
        }

        Object oldValue = null;
        boolean replaced = false;

        // Add or replace the specified attribute
        // Check for read only attribute
        if (readOnlyAttributes.containsKey(name))
            return;
        oldValue = attributes.get(name);
        if (oldValue != null)
            replaced = true;
        attributes.put(name, value);

        // Notify interested application event listeners
        Object listeners[] = context.getApplicationEventListeners();
        if ((listeners == null) || (listeners.length == 0))
            return;
        ServletContextAttributeEvent event = null;
        if (replaced)
            event =
                new ServletContextAttributeEvent(context.getServletContext(),
                                                 name, oldValue);
        else
            event =
                new ServletContextAttributeEvent(context.getServletContext(),
                                                 name, value);

        for (int i = 0; i < listeners.length; i++) {
            if (!(listeners[i] instanceof ServletContextAttributeListener))
                continue;
            ServletContextAttributeListener listener =
                (ServletContextAttributeListener) listeners[i];
            try {
                if (replaced) {
                    context.fireContainerEvent
                        ("beforeContextAttributeReplaced", listener);
                    listener.attributeReplaced(event);
                    context.fireContainerEvent("afterContextAttributeReplaced",
                                               listener);
                } else {
                    context.fireContainerEvent("beforeContextAttributeAdded",
                                               listener);
                    listener.attributeAdded(event);
                    context.fireContainerEvent("afterContextAttributeAdded",
                                               listener);
                }
            } catch (Throwable t) {
                if (replaced)
                    context.fireContainerEvent("afterContextAttributeReplaced",
                                               listener);
                else
                    context.fireContainerEvent("afterContextAttributeAdded",
                                               listener);
                // FIXME - should we do anything besides log these?
                log(sm.getString("applicationContext.attributeEvent"), t);
            }
        }

    }


    public FilterRegistration.Dynamic addFilter(String filterName, String className)
            throws IllegalArgumentException, IllegalStateException {
        if (restricted) {
            throw new UnsupportedOperationException(sm.getString("applicationContext.restricted"));
        }
        if (!context.isStarting()) {
            throw new IllegalStateException(sm.getString("applicationContext.alreadyInitialized",
                            getContextPath()));
        }
        if (context.findFilterDef(filterName) != null) {
            return null;
        }
        FilterDef filterDef = new FilterDef();
        filterDef.setFilterName(filterName);
        filterDef.setFilterClass(className);
        context.addFilterDef(filterDef);
        ApplicationFilterConfig filterConfig = new ApplicationFilterConfig(context, filterDef);
        filterConfig.setDynamic(true);
        context.addApplicationFilterConfig(filterConfig);
        return (FilterRegistration.Dynamic) filterConfig.getFacade();
    }


    public FilterRegistration.Dynamic addFilter(String filterName, Filter filter) {
        if (restricted) {
            throw new UnsupportedOperationException(sm.getString("applicationContext.restricted"));
        }
        if (!context.isStarting()) {
            throw new IllegalStateException(sm.getString("applicationContext.alreadyInitialized",
                            getContextPath()));
        }
        if (context.findFilterDef(filterName) != null) {
            return null;
        }
        // Filter instance unicity
        for (Container container : context.getParent().findChildren()) {
            if (container instanceof StandardContext) {
                for (ApplicationFilterConfig filterConfig : ((StandardContext) container).findApplicationFilterConfigs()) {
                    if (filterConfig.getFilterInstance() == filter) {
                        return null;
                    }
                }
            }
        }
        FilterDef filterDef = new FilterDef();
        filterDef.setFilterName(filterName);
        filterDef.setFilterClass(filter.getClass().getName());
        context.addFilterDef(filterDef);
        ApplicationFilterConfig filterConfig = new ApplicationFilterConfig(context, filterDef);
        filterConfig.setDynamic(true);
        filterConfig.setFilter(filter);
        context.addApplicationFilterConfig(filterConfig);
        return (FilterRegistration.Dynamic) filterConfig.getFacade();
    }


    public FilterRegistration.Dynamic addFilter(String filterName,
            Class<? extends Filter> filterClass) {
        return addFilter(filterName, filterClass.getName());
    }


    public ServletRegistration.Dynamic addServlet(String servletName, String className)
            throws IllegalArgumentException, IllegalStateException {
        if (restricted) {
            throw new UnsupportedOperationException(sm.getString("applicationContext.restricted"));
        }
        if (!context.isStarting()) {
            throw new IllegalStateException(sm.getString("applicationContext.alreadyInitialized",
                            getContextPath()));
        }
        if (context.findChild(servletName) != null) {
            return null;
        }
        Wrapper wrapper = context.createWrapper();
        wrapper.setDynamic(true);
        wrapper.setName(servletName);
        wrapper.setServletClass(className);
        context.addChild(wrapper);
        return (ServletRegistration.Dynamic) wrapper.getFacade();
    }


    public ServletRegistration.Dynamic addServlet(String servletName,
            Class<? extends Servlet> clazz) throws IllegalArgumentException,
            IllegalStateException {
        return addServlet(servletName, clazz.getName());
    }


    public ServletRegistration.Dynamic addServlet(String servletName, Servlet servlet) {
        if (restricted) {
            throw new UnsupportedOperationException(sm.getString("applicationContext.restricted"));
        }
        if (!context.isStarting()) {
            throw new IllegalStateException(sm.getString("applicationContext.alreadyInitialized",
                            getContextPath()));
        }
        if (context.findChild(servletName) != null) {
            return null;
        }
        // Servlet instance unicity
        for (Container container : context.getParent().findChildren()) {
            for (Container wrapper : container.findChildren()) {
                if (((Wrapper) wrapper).getServlet() == servlet) {
                    return null;
                }
            }
        }
        Wrapper wrapper = context.createWrapper();
        wrapper.setDynamic(true);
        wrapper.setName(servletName);
        wrapper.setServletClass(servlet.getClass().getName());
        wrapper.setServlet(servlet);
        context.addChild(wrapper);
        return (ServletRegistration.Dynamic) wrapper.getFacade();
    }


    public FilterRegistration getFilterRegistration(String filterName) {
        if (restricted) {
            throw new UnsupportedOperationException(sm.getString("applicationContext.restricted"));
        }
        ApplicationFilterConfig filterConfig = context.findApplicationFilterConfig(filterName);
        if (filterConfig == null) {
            FilterDef filterDef = context.findFilterDef(filterName);
            if (filterDef == null) {
                return null;
            } else {
                filterConfig = new ApplicationFilterConfig(context, filterDef);
                context.addApplicationFilterConfig(filterConfig);
            }
        }
        return filterConfig.getFacade();
    }


    public ServletRegistration getServletRegistration(String servletName) {
        if (restricted) {
            throw new UnsupportedOperationException(sm.getString("applicationContext.restricted"));
        }
        Wrapper wrapper = (Wrapper) context.findChild(servletName);
        if (wrapper != null) {
            return wrapper.getFacade();
        } else {
            return null;
        }
    }


    public Map<String, FilterRegistration> getFilterRegistrations() {
        if (restricted) {
            throw new UnsupportedOperationException(sm.getString("applicationContext.restricted"));
        }
        HashMap<String, FilterRegistration> result = 
            new HashMap<String, FilterRegistration>();
        ApplicationFilterConfig[] filterConfigs = context.findApplicationFilterConfigs();
        for (int i = 0; i < filterConfigs.length; i++) {
            result.put(filterConfigs[i].getFilterName(), filterConfigs[i].getFacade());
        }
        return Collections.unmodifiableMap(result);
    }


    public Map<String, ServletRegistration> getServletRegistrations() {
        if (restricted) {
            throw new UnsupportedOperationException(sm.getString("applicationContext.restricted"));
        }
        HashMap<String, ServletRegistration> result = 
            new HashMap<String, ServletRegistration>();
        Container[] wrappers = context.findChildren();
        for (int i = 0; i < wrappers.length; i++) {
            Wrapper wrapper = (Wrapper) wrappers[i];
            result.put(wrapper.getName(), wrapper.getFacade());
        }
        return Collections.unmodifiableMap(result);
    }


    public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
        if (restricted) {
            throw new UnsupportedOperationException(sm.getString("applicationContext.restricted"));
        }
        return context.getDefaultSessionTrackingModes();
    }

    public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
        if (restricted) {
            throw new UnsupportedOperationException(sm.getString("applicationContext.restricted"));
        }
        return context.getSessionTrackingModes();
    }


    public SessionCookieConfig getSessionCookieConfig() {
        if (restricted) {
            throw new UnsupportedOperationException(sm.getString("applicationContext.restricted"));
        }
        return context.getSessionCookie();
    }


    public <T extends Filter> T createFilter(Class<T> c)
            throws ServletException {
        if (restricted) {
            throw new UnsupportedOperationException(sm.getString("applicationContext.restricted"));
        }
        try {
            return (T) context.getInstanceManager().newInstance(c);
        } catch (Throwable e) {
            throw new ServletException(sm.getString("applicationContext.create"), e);
        }
    }


    public <T extends Servlet> T createServlet(Class<T> c)
            throws ServletException {
        if (restricted) {
            throw new UnsupportedOperationException(sm.getString("applicationContext.restricted"));
        }
        try {
            return (T) context.getInstanceManager().newInstance(c);
        } catch (Throwable e) {
            throw new ServletException(sm.getString("applicationContext.create"), e);
        }
    }


    public boolean setInitParameter(String name, String value) {
        if (restricted) {
            throw new UnsupportedOperationException(sm.getString("applicationContext.restricted"));
        }
        if (!context.isStarting()) {
            throw new IllegalStateException(sm.getString("applicationContext.alreadyInitialized",
                            getContextPath()));
        }
        mergeParameters();
        if (parameters.get(name) != null) {
            return false;
        } else {
            parameters.put(name, value);
            return true;
        }
    }


    public void setSessionTrackingModes(Set<SessionTrackingMode> sessionTrackingModes) {
        if (restricted) {
            throw new UnsupportedOperationException(sm.getString("applicationContext.restricted"));
        }
        if (!context.isStarting()) {
            throw new IllegalStateException(sm.getString("applicationContext.alreadyInitialized",
                            getContextPath()));
        }
        // Check that only supported tracking modes have been requested
        for (SessionTrackingMode sessionTrackingMode : sessionTrackingModes) {
            if (!getDefaultSessionTrackingModes().contains(sessionTrackingMode)) {
                throw new IllegalArgumentException(sm.getString(
                        "applicationContext.setSessionTracking.iae",
                        sessionTrackingMode.toString(), getContextPath()));
            }
        }
        // If SSL is specified, it should be the only one used
        if (sessionTrackingModes.contains(SessionTrackingMode.SSL) && sessionTrackingModes.size() > 1) {
            throw new IllegalArgumentException(sm.getString(
                    "applicationContext.setSessionTracking.ssl", getContextPath()));
        }
        context.setSessionTrackingModes(sessionTrackingModes);
    }


    public void addListener(String className) {
        if (restricted) {
            throw new UnsupportedOperationException(sm.getString("applicationContext.restricted"));
        }
        if (!context.isStarting()) {
            throw new IllegalStateException(sm.getString("applicationContext.alreadyInitialized",
                            getContextPath()));
        }
        EventListener listenerInstance = null;
        try {
            Class<?> clazz = context.getLoader().getClassLoader().loadClass(className);
            listenerInstance = (EventListener) context.getInstanceManager().newInstance(clazz);
        } catch (Throwable t) {
            throw new IllegalArgumentException(sm.getString("applicationContext.badListenerClass", 
                    className, getContextPath()), t);
        }
        checkListenerType(listenerInstance);
        if (context.getApplicationLifecycleListeners() != null && listenerInstance instanceof ServletContextListener) {
            throw new IllegalArgumentException(sm.getString("applicationContext.badListenerClass", 
                    className, getContextPath()));
        }
        context.addApplicationListenerInstance(listenerInstance);
    }


    public <T extends EventListener> void addListener(T listener) {
        if (restricted) {
            throw new UnsupportedOperationException(sm.getString("applicationContext.restricted"));
        }
        if (!context.isStarting()) {
            throw new IllegalStateException(sm.getString("applicationContext.alreadyInitialized",
                            getContextPath()));
        }
        checkListenerType(listener);
        if (context.getApplicationLifecycleListeners() != null && listener instanceof ServletContextListener) {
            throw new IllegalArgumentException(sm.getString("applicationContext.badListenerClass", 
                    listener.getClass().getName(), getContextPath()));
        }
        context.addApplicationListenerInstance(listener);
    }


    public void addListener(Class<? extends EventListener> listenerClass) {
        if (restricted) {
            throw new UnsupportedOperationException(sm.getString("applicationContext.restricted"));
        }
        if (!context.isStarting()) {
            throw new IllegalStateException(sm.getString("applicationContext.alreadyInitialized",
                            getContextPath()));
        }
        EventListener listenerInstance = null;
        try {
            listenerInstance = (EventListener) context.getInstanceManager().newInstance(listenerClass);
        } catch (Exception e) {
            throw new IllegalArgumentException(sm.getString("applicationContext.badListenerClass", 
                    listenerClass.getName(), getContextPath()), e);
        }
        checkListenerType(listenerInstance);
        if (context.getApplicationLifecycleListeners() != null && listenerInstance instanceof ServletContextListener) {
            throw new IllegalArgumentException(sm.getString("applicationContext.badListenerClass", 
                    listenerClass.getName(), getContextPath()));
        }
        context.addApplicationListenerInstance(listenerInstance);
    }


    public <T extends EventListener> T createListener(Class<T> clazz)
            throws ServletException {
        if (restricted) {
            throw new UnsupportedOperationException(sm.getString("applicationContext.restricted"));
        }
        if (!context.isStarting()) {
            throw new IllegalStateException(sm.getString("applicationContext.alreadyInitialized",
                            getContextPath()));
        }
        T listenerInstance = null;
        try {
            listenerInstance = (T) context.getInstanceManager().newInstance(clazz);
        } catch (Throwable t) {
            throw new ServletException(sm.getString("applicationContext.create"), t);
        }
        checkListenerType(listenerInstance);
        return listenerInstance;
    }


    public ClassLoader getClassLoader() {
        if (restricted) {
            throw new UnsupportedOperationException(sm.getString("applicationContext.restricted"));
        }
        return context.getLoader().getClassLoader();
    }


    public JspConfigDescriptor getJspConfigDescriptor() {
        if (restricted) {
            throw new UnsupportedOperationException(sm.getString("applicationContext.restricted"));
        }
        ArrayList<TaglibDescriptor> taglibDescriptors = new ArrayList<TaglibDescriptor>();
        String[] taglibURIs = context.findTaglibs();
        for (int i = 0; i < taglibURIs.length; i++) {
            String taglibLocation = context.findTaglib(taglibURIs[i]);
            TaglibDescriptor taglibDescriptor = 
                new TaglibDescriptorImpl(taglibURIs[i], taglibLocation);
            taglibDescriptors.add(taglibDescriptor);
        }
        ArrayList<JspPropertyGroupDescriptor> jspPropertyGroupDescriptors = 
            new ArrayList<JspPropertyGroupDescriptor>();
        JspPropertyGroup[] jspPropertyGroups = context.findJspPropertyGroups();
        for (int i = 0; i < jspPropertyGroups.length; i++) {
            jspPropertyGroupDescriptors.add(jspPropertyGroups[i]);
        }
        return new JspConfigDescriptorImpl(jspPropertyGroupDescriptors, taglibDescriptors);
    }


    public int getEffectiveMajorVersion() {
        if (restricted) {
            throw new UnsupportedOperationException(sm.getString("applicationContext.restricted"));
        }
        return context.getVersionMajor();
    }


    public int getEffectiveMinorVersion() {
        if (restricted) {
            throw new UnsupportedOperationException(sm.getString("applicationContext.restricted"));
        }
        return context.getVersionMinor();
    }

    public void declareRoles(String... roleNames) {
        if (restricted) {
            throw new UnsupportedOperationException(sm.getString("applicationContext.restricted"));
        }
        if (!context.isStarting()) {
            throw new IllegalStateException(sm.getString("applicationContext.alreadyInitialized",
                            getContextPath()));
        }
        for (String role: roleNames) {
            if (role == null || "".equals(role)) {
                throw new IllegalArgumentException(sm.getString("applicationContext.emptyRole",
                        getContextPath()));
            }
            context.addSecurityRole(role);
        }
    }
    
    // -------------------------------------------------------- Package Methods
    
    protected void checkListenerType(EventListener listener) {
        if (!(listener instanceof ServletContextListener)
                && !(listener instanceof ServletContextAttributeListener)
                && !(listener instanceof ServletRequestListener)
                && !(listener instanceof ServletRequestAttributeListener)
                && !(listener instanceof HttpSessionListener)
                && !(listener instanceof HttpSessionAttributeListener)) {
            throw new IllegalArgumentException(sm.getString("applicationContext.badListenerClass", 
                    listener.getClass().getName(), getContextPath()));
        }
    }
    
    protected StandardContext getContext() {
        return this.context;
    }
    
    protected Map getReadonlyAttributes() {
        return this.readOnlyAttributes;
    }
    /**
     * Clear all application-created attributes.
     */
    protected void clearAttributes() {

        // Create list of attributes to be removed
        ArrayList list = new ArrayList();
        Iterator iter = attributes.keySet().iterator();
        while (iter.hasNext()) {
            list.add(iter.next());
        }

        // Remove application originated attributes
        // (read only attributes will be left in place)
        Iterator keys = list.iterator();
        while (keys.hasNext()) {
            String key = (String) keys.next();
            removeAttribute(key);
        }
        
    }
    
    
    /**
     * Return the facade associated with this ApplicationContext.
     */
    protected ServletContext getFacade() {

        return (this.facade);

    }


    /**
     * Set an attribute as read only.
     */
    void setAttributeReadOnly(String name) {

        if (attributes.containsKey(name))
            readOnlyAttributes.put(name, name);

    }


    // -------------------------------------------------------- Private Methods


    /**
     * Merge the context initialization parameters specified in the application
     * deployment descriptor with the application parameters described in the
     * server configuration, respecting the <code>override</code> property of
     * the application parameters appropriately.
     */
    private void mergeParameters() {

        if (parameters != null)
            return;
        Map results = new ConcurrentHashMap();
        String names[] = context.findParameters();
        for (int i = 0; i < names.length; i++)
            results.put(names[i], context.findParameter(names[i]));
        ApplicationParameter params[] =
            context.findApplicationParameters();
        for (int i = 0; i < params.length; i++) {
            if (params[i].getOverride()) {
                if (results.get(params[i].getName()) == null)
                    results.put(params[i].getName(), params[i].getValue());
            } else {
                results.put(params[i].getName(), params[i].getValue());
            }
        }
        parameters = results;

    }


    /**
     * List resource paths (recursively), and store all of them in the given
     * Set.
     */
    private static void listCollectionPaths
        (Set set, DirContext resources, String path)
        throws NamingException {

        Enumeration childPaths = resources.listBindings(path);
        while (childPaths.hasMoreElements()) {
            Binding binding = (Binding) childPaths.nextElement();
            String name = binding.getName();
            StringBuilder childPath = new StringBuilder(path);
            if (!"/".equals(path) && !path.endsWith("/"))
                childPath.append("/");
            childPath.append(name);
            Object object = binding.getObject();
            if (object instanceof DirContext) {
                childPath.append("/");
            }
            set.add(childPath.toString());
        }

    }


    /**
     * Get full path, based on the host name and the context path.
     */
    private static String getJNDIUri(String hostName, String path) {
        if (!path.startsWith("/"))
            return "/" + hostName + "/" + path;
        else
            return "/" + hostName + path;
    }


    /**
     * Internal class used as thread-local storage when doing path
     * mapping during dispatch.
     */
    private static final class DispatchData {

        public MessageBytes uriMB;
        public MappingData mappingData;

        public DispatchData() {
            uriMB = MessageBytes.newInstance();
            CharChunk uriCC = uriMB.getCharChunk();
            uriCC.setLimit(-1);
            mappingData = new MappingData();
        }
    }

    
    /**
     * JSP config metadata class (not used for Jasper).
     */
    private static final class JspConfigDescriptorImpl implements JspConfigDescriptor {

        private Collection<JspPropertyGroupDescriptor> jspPropertyGroups;
        private Collection<TaglibDescriptor> taglibs;
        public JspConfigDescriptorImpl(Collection<JspPropertyGroupDescriptor> jspPropertyGroups,
                Collection<TaglibDescriptor> taglibs) {
            this.jspPropertyGroups = jspPropertyGroups;
            this.taglibs = taglibs;
        }

        public Collection<JspPropertyGroupDescriptor> getJspPropertyGroups() {
            return jspPropertyGroups;
        }

        public Collection<TaglibDescriptor> getTaglibs() {
            return taglibs;
        }
        
    }
    
    /**
     * JSP taglib descriptor metadata class (not used for Jasper).
     */
    private static final class TaglibDescriptorImpl implements TaglibDescriptor {

        private String taglibLocation;
        private String taglibURI;
        
        public TaglibDescriptorImpl(String taglibURI, String taglibLocation) {
            this.taglibLocation = taglibLocation;
            this.taglibURI = taglibURI;
        }

        public String getTaglibLocation() {
            return taglibLocation;
        }

        public String getTaglibURI() {
            return taglibURI;
        }

    }

}
