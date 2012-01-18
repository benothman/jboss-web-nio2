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


import java.io.IOException;
import java.security.Principal;
import java.security.PrivilegedActionException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Globals;
import org.apache.catalina.InstanceEvent;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.RequestFacade;
import org.apache.catalina.security.SecurityUtil;
import org.apache.catalina.util.InstanceSupport;
import org.apache.catalina.util.StringManager;
import org.jboss.servlet.http.HttpEvent;
import org.jboss.servlet.http.HttpEventFilter;
import org.jboss.servlet.http.HttpEventFilterChain;
import org.jboss.servlet.http.HttpEventServlet;

/**
 * Implementation of <code>javax.servlet.FilterChain</code> used to manage
 * the execution of a set of filters for a particular request.  When the
 * set of defined filters has all been executed, the next call to
 * <code>doFilter()</code> will execute the servlet's <code>service()</code>
 * method itself.
 *
 * @author Craig R. McClanahan
 * @version $Revision$ $Date$
 */

public final class ApplicationFilterChain implements FilterChain, HttpEventFilterChain {

    // Used to enforce requirements of SRV.8.2 / SRV.14.2.5.1
    private final static ThreadLocal lastServicedRequest;
    private final static ThreadLocal lastServicedResponse;

    static {
        if (Globals.STRICT_SERVLET_COMPLIANCE) {
            lastServicedRequest = new ThreadLocal();
            lastServicedResponse = new ThreadLocal();
        } else {
            lastServicedRequest = null;
            lastServicedResponse = null;
        }
    }

    
    // -------------------------------------------------------------- Constants


    public static final int INCREMENT = 10;


    // ----------------------------------------------------------- Constructors


    /**
     * Construct a new chain instance with no defined filters.
     */
    public ApplicationFilterChain() {

        super();

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * Associated request facade.
     */
    private RequestFacade requestFacade = null;
    
    
    /**
     * Filters.
     */
    private ApplicationFilterConfig[] filters = 
        new ApplicationFilterConfig[0];


    /**
     * The int which is used to track the component currently executed.
     */
    private int pointer = 0;


   /**
     * The int which is used to maintain the current position 
     * in the filter chain.
     */
    private int pos = 0;


    /**
     * The int which gives the current number of filters in the chain.
     */
    private int filterCount = 0;


    /**
     * The wrapper to be executed by this chain.
     */
    private Wrapper wrapper = null;


    /**
     * The servlet to be executed by this chain.
     */
    private Servlet servlet = null;


   /**
     * The string manager for our package.
     */
    private static final StringManager sm =
      StringManager.getManager(Constants.Package);


    /**
     * Static class array used when the SecurityManager is turned on and 
     * <code>doFilter</code> is invoked.
     */
    private static Class[] classType = new Class[]{ServletRequest.class, 
                                                   ServletResponse.class,
                                                   FilterChain.class};
                                                   
    /**
     * Static class array used when the SecurityManager is turned on and 
     * <code>service</code> is invoked.
     */                                                 
    private static Class[] classTypeUsedInService = new Class[]{
                                                         ServletRequest.class,
                                                         ServletResponse.class};

    /**
     * Static class array used when the SecurityManager is turned on and 
     * <code>doFilterEvent</code> is invoked.
     */
    private static Class[] cometClassType = 
        new Class[]{ HttpEvent.class, HttpEventFilterChain.class};
                                                   
    /**
     * Static class array used when the SecurityManager is turned on and 
     * <code>event</code> is invoked.
     */                                                 
    private static Class[] classTypeUsedInEvent = 
        new Class[] { HttpEvent.class };

    // ---------------------------------------------------- FilterChain Methods


    /**
     * Invoke the next filter in this chain, passing the specified request
     * and response.  If there are no more filters in this chain, invoke
     * the <code>service()</code> method of the servlet itself.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet exception occurs
     */
    public void doFilter(ServletRequest request, ServletResponse response)
        throws IOException, ServletException {

        if( Globals.IS_SECURITY_ENABLED ) {
            final ServletRequest req = request;
            final ServletResponse res = response;
            try {
                java.security.AccessController.doPrivileged(
                    new java.security.PrivilegedExceptionAction() {
                        public Object run() 
                            throws ServletException, IOException {
                            internalDoFilter(req,res);
                            return null;
                        }
                    }
                );
            } catch( PrivilegedActionException pe) {
                Exception e = pe.getException();
                if (e instanceof ServletException)
                    throw (ServletException) e;
                else if (e instanceof IOException)
                    throw (IOException) e;
                else if (e instanceof RuntimeException)
                    throw (RuntimeException) e;
                else
                    throw new ServletException(e.getMessage(), e);
            }
        } else {
            internalDoFilter(request,response);
        }
    }

    private void internalDoFilter(ServletRequest request, 
                                  ServletResponse response)
        throws IOException, ServletException {

        InstanceSupport support = wrapper.getInstanceSupport();
        Throwable throwable = null;

        // Call the next filter if there is one
        if (pos < filterCount) {
            ApplicationFilterConfig filterConfig = filters[pos++];
            pointer++;
            Filter filter = null;
            try {
                filter = filterConfig.getFilter();
                support.fireInstanceEvent(InstanceEvent.BEFORE_FILTER_EVENT,
                                          filter, request, response);
                if( Globals.IS_SECURITY_ENABLED ) {
                    final ServletRequest req = request;
                    final ServletResponse res = response;
                    Principal principal = 
                        ((HttpServletRequest) req).getUserPrincipal();

                    Object[] args = new Object[]{req, res, this};
                    SecurityUtil.doAsPrivilege
                        ("doFilter", filter, classType, args, principal);
                    
                    args = null;
                } else {  
                    filter.doFilter(request, response, this);
                }
            } catch (IOException e) {
                throwable = e;
                throw e;
            } catch (ServletException e) {
                throwable = e;
                throw e;
            } catch (RuntimeException e) {
                throwable = e;
                throw e;
            } catch (Throwable e) {
                throwable = e;
                throw new ServletException(sm.getString("filterChain.filter"), e);
            } finally {
                pointer--;
                if (filter != null) {
                    support.fireInstanceEvent(InstanceEvent.AFTER_FILTER_EVENT,
                                              filter, request, response, throwable);
                }
            }
            return;
        }

        // We fell off the end of the chain -- call the servlet instance
        pointer++;
        try {
            support.fireInstanceEvent(InstanceEvent.BEFORE_SERVICE_EVENT,
                    servlet, request, response);
            if (Globals.STRICT_SERVLET_COMPLIANCE) {
                lastServicedRequest.set(request);
                lastServicedResponse.set(response);
            }
            if ((request instanceof HttpServletRequest) &&
                (response instanceof HttpServletResponse)) {
                    
                if( Globals.IS_SECURITY_ENABLED ) {
                    final ServletRequest req = request;
                    final ServletResponse res = response;
                    Principal principal = 
                        ((HttpServletRequest) req).getUserPrincipal();
                    Object[] args = new Object[]{req, res};
                    SecurityUtil.doAsPrivilege("service",
                                               servlet,
                                               classTypeUsedInService, 
                                               args,
                                               principal);   
                    args = null;
                } else {  
                    servlet.service((HttpServletRequest) request,
                                    (HttpServletResponse) response);
                }
            } else {
                servlet.service(request, response);
            }
        } catch (IOException e) {
            throwable = e;
            throw e;
        } catch (ServletException e) {
            throwable = e;
            throw e;
        } catch (RuntimeException e) {
            throwable = e;
            throw e;
        } catch (Throwable e) {
            throwable = e;
            throw new ServletException
              (sm.getString("filterChain.servlet"), e);
        } finally {
            pointer--;
            if (Globals.STRICT_SERVLET_COMPLIANCE) {
                lastServicedRequest.set(null);
                lastServicedResponse.set(null);
            }
            support.fireInstanceEvent(InstanceEvent.AFTER_SERVICE_EVENT,
                    servlet, request, response, throwable);
        }

    }


    /**
     * Invoke the next filter in this chain, passing the specified request
     * and response.  If there are no more filters in this chain, invoke
     * the <code>service()</code> method of the servlet itself.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet exception occurs
     */
    public void doFilterEvent(HttpEvent event)
        throws IOException, ServletException {

        if( Globals.IS_SECURITY_ENABLED ) {
            final HttpEvent ev = event;
            try {
                java.security.AccessController.doPrivileged(
                    new java.security.PrivilegedExceptionAction() {
                        public Object run() 
                            throws ServletException, IOException {
                            internalDoFilterEvent(ev);
                            return null;
                        }
                    }
                );
            } catch( PrivilegedActionException pe) {
                Exception e = pe.getException();
                if (e instanceof ServletException)
                    throw (ServletException) e;
                else if (e instanceof IOException)
                    throw (IOException) e;
                else if (e instanceof RuntimeException)
                    throw (RuntimeException) e;
                else
                    throw new ServletException(e.getMessage(), e);
            }
        } else {
            internalDoFilterEvent(event);
        }
    }

    
    /**
     * The last request passed to a servlet for servicing from the current
     * thread.
     * 
     * @return The last request to be serviced. 
     */
    public static ServletRequest getLastServicedRequest() {
        return (ServletRequest) lastServicedRequest.get();
    }

    
    /**
     * The last response passed to a servlet for servicing from the current
     * thread.
     * 
     * @return The last response to be serviced. 
     */
    public static ServletResponse getLastServicedResponse() {
        return (ServletResponse) lastServicedResponse.get();
    }
    
    
    private void internalDoFilterEvent(HttpEvent event)
        throws IOException, ServletException {

        InstanceSupport support = wrapper.getInstanceSupport();
        Throwable throwable = null;

        // Call the next filter if there is one
        if (pos < filterCount) {
            ApplicationFilterConfig filterConfig = filters[pos++];
            pointer++;
            HttpEventFilter filter = null;
            try {
                filter = (HttpEventFilter) filterConfig.getFilter();
                support.fireInstanceEvent(InstanceEvent.BEFORE_FILTER_EVENT,
                        filter, event);
                if( Globals.IS_SECURITY_ENABLED ) {
                    final HttpEvent ev = event;
                    Principal principal = 
                        ev.getHttpServletRequest().getUserPrincipal();

                    Object[] args = new Object[]{ev, this};
                    SecurityUtil.doAsPrivilege
                        ("doFilterEvent", (Filter) filter, cometClassType, args, principal);

                    args = null;
                } else {  
                    filter.doFilterEvent(event, this);
                }
            } catch (IOException e) {
                throwable = e;
                throw e;
            } catch (ServletException e) {
                throwable = e;
                throw e;
            } catch (RuntimeException e) {
                throwable = e;
                throw e;
            } catch (Throwable e) {
                throwable = e;
                throw new ServletException
                    (sm.getString("filterChain.filter"), e);
            } finally {
                pointer--;
                if (filter != null) {
                    support.fireInstanceEvent
                        (InstanceEvent.AFTER_FILTER_EVENT, filter, event, throwable);
                }
            }
            return;
        }

        // We fell off the end of the chain -- call the servlet instance
        pointer++;
        try {
            support.fireInstanceEvent(InstanceEvent.BEFORE_SERVICE_EVENT,
                    servlet, event);
            if( Globals.IS_SECURITY_ENABLED ) {
                final HttpEvent ev = event;
                Principal principal = 
                    ev.getHttpServletRequest().getUserPrincipal();
                Object[] args = new Object[]{ ev };
                SecurityUtil.doAsPrivilege("event",
                        servlet,
                        classTypeUsedInEvent, 
                        args,
                        principal);
                args = null;
            } else {  
                ((HttpEventServlet) servlet).event(event);
            }
        } catch (IOException e) {
            throwable = e;
            throw e;
        } catch (ServletException e) {
            throwable = e;
            throw e;
        } catch (RuntimeException e) {
            throwable = e;
            throw e;
        } catch (Throwable e) {
            throwable = e;
            throw new ServletException
                (sm.getString("filterChain.servlet"), e);
        } finally {
            pointer--;
            support.fireInstanceEvent(InstanceEvent.AFTER_SERVICE_EVENT,
                    servlet, event, throwable);
        }

    }


    // -------------------------------------------------------- Package Methods



    /**
     * Add a filter to the set of filters that will be executed in this chain.
     *
     * @param filterConfig The FilterConfig for the servlet to be executed
     */
    void addFilter(ApplicationFilterConfig filterConfig) {

        if (filterCount == filters.length) {
            ApplicationFilterConfig[] newFilters =
                new ApplicationFilterConfig[filterCount + INCREMENT];
            System.arraycopy(filters, 0, newFilters, 0, filterCount);
            filters = newFilters;
        }
        filters[filterCount++] = filterConfig;

    }


    /**
     * Release references to the filters and wrapper executed by this chain.
     */
    void release() {

        for (int i = 0; i < filterCount; i++) {
            filters[i] = null;
        }
        filterCount = 0;
        pos = 0;
        pointer = 0;
        servlet = null;
        wrapper = null;
        requestFacade.releaseFilterChain();

    }


    /**
     * Prepare for reuse of the filters and wrapper executed by this chain.
     */
    void reuse() {
        pos = 0;
        pointer = 0;
    }


    /**
     * Set the wrapper that will be executed at the end of this chain.
     *
     * @param wrapper The Wrapper to be executed
     */
    void setWrapper(Wrapper wrapper) {

        this.wrapper = wrapper;

    }


    /**
     * Set the servlet that will be executed at the end of this chain.
     *
     * @param servlet The Servlet to be executed
     */
    void setServlet(Servlet servlet) {

        this.servlet = servlet;

    }


   /**
     * Set the RequestFacade object used for removing the association of the
     * chain from the request facade.
     *
     * @param requestFacade The RequestFacade object
     */
    void setRequestFacade(RequestFacade requestFacade) {

        this.requestFacade = requestFacade;

    }

    
    public ApplicationFilterConfig[] getFilters() {
        return filters;
    }

    public int getFilterCount() {
        return filterCount;
    }

    public int getPointer() {
        return pointer;
    }

    public Wrapper getWrapper() {
        return wrapper;
    }

    public Servlet getServlet() {
        return servlet;
    }


}
