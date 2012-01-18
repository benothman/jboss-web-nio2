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
import java.util.Iterator;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.InstanceEvent;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.ClientAbortException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.connector.Request.AsyncListenerRegistration;
import org.apache.catalina.util.InstanceSupport;
import org.apache.catalina.util.StringManager;
import org.apache.catalina.valves.ValveBase;
import org.jboss.servlet.http.HttpEvent;
import org.jboss.servlet.http.HttpEventServlet;
import org.jboss.servlet.http.HttpEvent.EventType;

/**
 * Valve that implements the default basic behavior for the
 * <code>StandardWrapper</code> container implementation.
 *
 * @author Craig R. McClanahan
 * @version $Revision: 1764 $ $Date: 2011-07-04 15:55:32 +0200 (Mon, 04 Jul 2011) $
 */

final class StandardWrapperValve
    extends ValveBase {

    
    protected static final boolean SERVLET_STATS = 
        Globals.STRICT_SERVLET_COMPLIANCE
        || Boolean.valueOf(System.getProperty("org.apache.catalina.core.StandardWrapperValve.SERVLET_STATS", "false")).booleanValue();


    // ----------------------------------------------------- Instance Variables


    // Some JMX statistics. This vavle is associated with a StandardWrapper.
    // We expose the StandardWrapper as JMX ( j2eeType=Servlet ). The fields
    // are here for performance.
    private volatile long processingTime;
    private volatile long maxTime;
    private volatile long minTime = Long.MAX_VALUE;
    private volatile int eventCount;
    private volatile int requestCount;
    private volatile int errorCount;
    private InstanceSupport support;


    /**
     * The string manager for this package.
     */
    private static final StringManager sm =
        StringManager.getManager(Constants.Package);


    // --------------------------------------------------------- Public Methods


    public void setContainer(Container container) {
        super.setContainer(container);
        if (container instanceof Wrapper) {
            support = ((Wrapper) container).getInstanceSupport();
        }
        if (support == null) {
            throw new IllegalStateException(sm.getString("standardWrapper.noInstanceSupport", container.getName()));
        }
    }
    
    /**
     * Invoke the servlet we are managing, respecting the rules regarding
     * servlet lifecycle and SingleThreadModel support.
     *
     * @param request Request to be processed
     * @param response Response to be produced
     * @param valveContext Valve context used to forward to the next Valve
     *
     * @exception IOException if an input/output error occurred
     * @exception ServletException if a servlet error occurred
     */
    public final void invoke(Request request, Response response)
        throws IOException, ServletException {

        // Initialize local variables we may need
        boolean unavailable = false;
        Throwable throwable = null;
        // This should be a Request attribute...
        long t1 = 0;
        if (SERVLET_STATS) {
            t1 = System.currentTimeMillis();
            requestCount++;
        }
        StandardWrapper wrapper = (StandardWrapper) getContainer();
        Servlet servlet = null;
        Context context = (Context) wrapper.getParent();
        
        // Check for the application being marked unavailable
        if (!context.getAvailable()) {
        	response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                           sm.getString("standardContext.isUnavailable"));
            unavailable = true;
        }

        // Check for the servlet being marked unavailable
        if (!unavailable && wrapper.isUnavailable()) {
            container.getLogger().info(sm.getString("standardWrapper.isUnavailable",
                    wrapper.getName()));
            long available = wrapper.getAvailable();
            if ((available > 0L) && (available < Long.MAX_VALUE)) {
                response.setDateHeader("Retry-After", available);
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        sm.getString("standardWrapper.isUnavailable",
                                wrapper.getName()));
            } else if (available == Long.MAX_VALUE) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND,
                        sm.getString("standardWrapper.notFound",
                                wrapper.getName()));
            }
            unavailable = true;
        }

        // Allocate a servlet instance to process this request
        try {
            if (!unavailable) {
                servlet = wrapper.allocate();
            }
        } catch (UnavailableException e) {
            container.getLogger().error(
                    sm.getString("standardWrapper.allocateException",
                            wrapper.getName()), e);
            long available = wrapper.getAvailable();
            if ((available > 0L) && (available < Long.MAX_VALUE)) {
            	response.setDateHeader("Retry-After", available);
            	response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                           sm.getString("standardWrapper.isUnavailable",
                                        wrapper.getName()));
            } else if (available == Long.MAX_VALUE) {
            	response.sendError(HttpServletResponse.SC_NOT_FOUND,
                           sm.getString("standardWrapper.notFound",
                                        wrapper.getName()));
            }
        } catch (ServletException e) {
            container.getLogger().error(sm.getString("standardWrapper.allocateException",
                             wrapper.getName()), StandardWrapper.getRootCause(e));
            throwable = e;
            exception(request, response, e);
            servlet = null;
        } catch (Throwable e) {
            container.getLogger().error(sm.getString("standardWrapper.allocateException",
                             wrapper.getName()), e);
            throwable = e;
            exception(request, response, e);
            servlet = null;
        }

        // Identify if the request should be switched to event mode now that 
        // the servlet has been allocated
        boolean event = false;
        if (servlet instanceof HttpEventServlet 
                && request.getConnector().hasIoEvents()) {
            event = true;
            request.setEventMode(true);
        }
        
        // Acknowledge the request
        try {
            response.sendAcknowledgement();
        } catch (IOException e) {
        	request.removeAttribute(Globals.JSP_FILE_ATTR);
            container.getLogger().warn(sm.getString("standardWrapper.acknowledgeException",
                             wrapper.getName()), e);
            throwable = e;
            exception(request, response, e);
        } catch (Throwable e) {
            container.getLogger().error(sm.getString("standardWrapper.acknowledgeException",
                             wrapper.getName()), e);
            throwable = e;
            exception(request, response, e);
            servlet = null;
        }
        request.setAttribute
            (ApplicationFilterFactory.DISPATCHER_TYPE_ATTR,
             ApplicationFilterFactory.REQUEST_INTEGER);
        request.setAttribute
            (ApplicationFilterFactory.DISPATCHER_REQUEST_PATH_ATTR,
                    request.getRequestPathMB());
        // Create the filter chain for this request
        ApplicationFilterFactory factory =
            ApplicationFilterFactory.getInstance();
        ApplicationFilterChain filterChain =
            factory.createFilterChain(request, wrapper, servlet);
        // Reset event flag value after creating the filter chain
        request.setEventMode(false);

        // Call the filter chain for this request
        // NOTE: This also calls the servlet's service() method
        String jspFile = wrapper.getJspFile();
        try {
            if (jspFile != null) {
            	request.setAttribute(Globals.JSP_FILE_ATTR, jspFile);
            } else {
            	request.removeAttribute(Globals.JSP_FILE_ATTR);
            }
            support.fireInstanceEvent(InstanceEvent.BEFORE_REQUEST_EVENT,
                    servlet, request, response);
            if ((servlet != null) && (filterChain != null)) {
                if (event) {
                    request.setEventMode(true);
                    request.getSession(true);
                    filterChain.doFilterEvent(request.getEvent());
                } else {
                    filterChain.doFilter
                    (request.getRequest(), response.getResponse());
                }
            }
        } catch (ClientAbortException e) {
            throwable = e;
            exception(request, response, e);
        } catch (IOException e) {
            container.getLogger().error(sm.getString("standardWrapper.serviceException",
                             wrapper.getName()), e);
            throwable = e;
            exception(request, response, e);
        } catch (UnavailableException e) {
            container.getLogger().error(sm.getString("standardWrapper.serviceException",
                             wrapper.getName()), e);
            //            throwable = e;
            //            exception(request, response, e);
            wrapper.unavailable(e);
            long available = wrapper.getAvailable();
            if ((available > 0L) && (available < Long.MAX_VALUE)) {
                response.setDateHeader("Retry-After", available);
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                           sm.getString("standardWrapper.isUnavailable",
                                        wrapper.getName()));
            } else if (available == Long.MAX_VALUE) {
            	response.sendError(HttpServletResponse.SC_NOT_FOUND,
                            sm.getString("standardWrapper.notFound",
                                        wrapper.getName()));
            }
            // Do not save exception in 'throwable', because we
            // do not want to do exception(request, response, e) processing
        } catch (ServletException e) {
            Throwable rootCause = StandardWrapper.getRootCause(e);
            if (!(rootCause instanceof ClientAbortException)) {
                container.getLogger().error(sm.getString("standardWrapper.serviceException",
                                 wrapper.getName()), rootCause);
            }
            throwable = e;
            exception(request, response, e);
        } catch (Throwable e) {
            container.getLogger().error(sm.getString("standardWrapper.serviceException",
                             wrapper.getName()), e);
            throwable = e;
            exception(request, response, e);
        } finally {
            if (jspFile != null) {
                request.removeAttribute(Globals.JSP_FILE_ATTR);
            }
            support.fireInstanceEvent(InstanceEvent.AFTER_REQUEST_EVENT,
                    servlet, request, response, throwable);
        }

        // Release the filter chain (if any) for this request
        if (filterChain != null) {
            if (request.isEventMode() && !request.isAsyncStarted()) {
                // If this is a event request, then the same chain will be used for the
                // processing of all subsequent events.
                filterChain.reuse();
            } else {
                filterChain.release();
            }
        }

        // Deallocate the allocated servlet instance
        try {
            if (servlet != null) {
                wrapper.deallocate(servlet);
            }
        } catch (Throwable e) {
            container.getLogger().error(sm.getString("standardWrapper.deallocateException",
                             wrapper.getName()), e);
            if (throwable == null) {
                throwable = e;
                exception(request, response, e);
            }
        }

        // If this servlet has been marked permanently unavailable,
        // unload it and release this instance
        try {
            if ((servlet != null) &&
                (wrapper.getAvailable() == Long.MAX_VALUE)) {
                wrapper.unload();
            }
        } catch (Throwable e) {
            container.getLogger().error(sm.getString("standardWrapper.unloadException",
                             wrapper.getName()), e);
            if (throwable == null) {
                throwable = e;
                exception(request, response, e);
            }
        }

        if (SERVLET_STATS) {
            long time = System.currentTimeMillis() - t1;
            processingTime += time;
            if (time > maxTime) {
                maxTime = time;
            }
            if (time < minTime) {
                minTime = time;
            }
        }

    }


    /**
     * Process an event. The main differences here are to not use sendError
     * (the response is committed), to avoid creating a new filter chain
     * (which would work but be pointless), and a few very minor tweaks. 
     *
     * @param request The servlet request to be processed
     * @param response The servlet response to be created
     * @param event The event to be processed
     *
     * @exception IOException if an input/output error occurs, or is thrown
     *  by a subsequently invoked Valve, Filter, or Servlet
     * @exception ServletException if a servlet error occurs, or is thrown
     *  by a subsequently invoked Valve, Filter, or Servlet
     */
    public void event(Request request, Response response, HttpEvent event)
        throws IOException, ServletException {
        
        // Async processing
        AsyncContext asyncContext = request.getAsyncContext();
        if (asyncContext != null) {
            async(request, response, event);
            return;
        }
        
        // Initialize local variables we may need
        Throwable throwable = null;
        // This should be a Request attribute...
        long t1 = 0;
        if (SERVLET_STATS) {
            t1 = System.currentTimeMillis();
            eventCount++;
        }
        StandardWrapper wrapper = (StandardWrapper) getContainer();
        Servlet servlet = null;
        Context context = (Context) wrapper.getParent();

        // Check for the application being marked unavailable
        boolean unavailable = !context.getAvailable() || wrapper.isUnavailable();
        
        // Allocate a servlet instance to process this request
        try {
            if (!unavailable) {
                servlet = wrapper.allocate();
            }
        } catch (UnavailableException e) {
            // The response is already committed, so it's not possible to do anything
        } catch (ServletException e) {
            container.getLogger().error(sm.getString("standardWrapper.allocateException",
                             wrapper.getName()), StandardWrapper.getRootCause(e));
            throwable = e;
            exception(request, response, e);
            servlet = null;
        } catch (Throwable e) {
            container.getLogger().error(sm.getString("standardWrapper.allocateException",
                             wrapper.getName()), e);
            throwable = e;
            exception(request, response, e);
            servlet = null;
        }

        request.setAttribute
            (ApplicationFilterFactory.DISPATCHER_TYPE_ATTR,
             ApplicationFilterFactory.ASYNC_INTEGER);
        request.setAttribute
            (ApplicationFilterFactory.DISPATCHER_REQUEST_PATH_ATTR,
                    request.getRequestPathMB());
        
        // Get the current (unchanged) filter chain for this request
        ApplicationFilterChain filterChain = 
            (ApplicationFilterChain) request.getFilterChain();
        // Call the filter chain for this request
        // NOTE: This also calls the servlet's event() method
        String jspFile = wrapper.getJspFile();
        try {
            if (jspFile != null) {
                request.setAttribute(Globals.JSP_FILE_ATTR, jspFile);
            } else {
                request.removeAttribute(Globals.JSP_FILE_ATTR);
            }
            support.fireInstanceEvent(InstanceEvent.BEFORE_REQUEST_EVENT,
                    servlet, request, response);
            if ((servlet != null) && (filterChain != null)) {
                filterChain.doFilterEvent(request.getEvent());
            }
        } catch (ClientAbortException e) {
            throwable = e;
            exception(request, response, e);
        } catch (IOException e) {
            container.getLogger().error(sm.getString("standardWrapper.serviceException",
                             wrapper.getName()), e);
            throwable = e;
            exception(request, response, e);
        } catch (UnavailableException e) {
            container.getLogger().error(sm.getString("standardWrapper.serviceException",
                             wrapper.getName()), e);
            // Do not save exception in 'throwable', because we
            // do not want to do exception(request, response, e) processing
        } catch (ServletException e) {
            Throwable rootCause = StandardWrapper.getRootCause(e);
            if (!(rootCause instanceof ClientAbortException)) {
                container.getLogger().error(sm.getString("standardWrapper.serviceException",
                                 wrapper.getName()), rootCause);
            }
            throwable = e;
            exception(request, response, e);
        } catch (Throwable e) {
            container.getLogger().error(sm.getString("standardWrapper.serviceException",
                             wrapper.getName()), e);
            throwable = e;
            exception(request, response, e);
        } finally {
            if (jspFile != null) {
                request.removeAttribute(Globals.JSP_FILE_ATTR);
            }
            support.fireInstanceEvent(InstanceEvent.AFTER_REQUEST_EVENT,
                    servlet, request, response, throwable);
        }

        // Release the filter chain (if any) for this request
        if (filterChain != null) {
            if (request.isAsyncStarted()) {
                filterChain.release();
            } else {
                filterChain.reuse();
            }
        }

        // Deallocate the allocated servlet instance
        try {
            if (servlet != null) {
                wrapper.deallocate(servlet);
            }
        } catch (Throwable e) {
            container.getLogger().error(sm.getString("standardWrapper.deallocateException",
                             wrapper.getName()), e);
            if (throwable == null) {
                throwable = e;
                exception(request, response, e);
            }
        }

        // If this servlet has been marked permanently unavailable,
        // unload it and release this instance
        try {
            if ((servlet != null) &&
                (wrapper.getAvailable() == Long.MAX_VALUE)) {
                wrapper.unload();
            }
        } catch (Throwable e) {
            container.getLogger().error(sm.getString("standardWrapper.unloadException",
                             wrapper.getName()), e);
            if (throwable == null) {
                throwable = e;
                exception(request, response, e);
            }
        }

        if (SERVLET_STATS) {
            long time = System.currentTimeMillis() - t1;
            processingTime += time;
            if (time > maxTime) {
                maxTime = time;
            }
            if (time < minTime) {
                minTime = time;
            }
        }

    }


    /**
     * Process an async dispatch. This is never a direct wrapper invocation.
     *
     * @param request The servlet request to be processed
     * @param response The servlet response to be processed
     * @param event The event to be processed
     *
     * @exception IOException if an input/output error occurs, or is thrown
     *  by a subsequently invoked Valve, Filter, or Servlet
     * @exception ServletException if a servlet error occurs, or is thrown
     *  by a subsequently invoked Valve, Filter, or Servlet
     */
    public void async(Request request, Response response, HttpEvent event)
        throws IOException, ServletException {
        
        Request.AsyncContextImpl asyncContext = (Request.AsyncContextImpl) request.getAsyncContext();
        if (asyncContext != null) {
            if (event.getType() == EventType.END || event.getType() == EventType.ERROR
                    || event.getType() == EventType.TIMEOUT) {
                // Invoke the listeners with onComplete or onTimeout
                boolean timeout = (event.getType() == EventType.TIMEOUT) ? true : false;
                boolean error = (event.getType() == EventType.ERROR) ? true : false;
                Iterator<AsyncListenerRegistration> asyncListenerRegistrations = 
                    asyncContext.getAsyncListeners().values().iterator();
                while (asyncListenerRegistrations.hasNext()) {
                    AsyncListenerRegistration asyncListenerRegistration = asyncListenerRegistrations.next();
                    AsyncListener asyncListener = asyncListenerRegistration.getListener();
                    try {
                        if (timeout) {
                            AsyncEvent asyncEvent = new AsyncEvent(asyncContext, 
                                    asyncListenerRegistration.getRequest(), asyncListenerRegistration.getResponse());
                            asyncListener.onTimeout(asyncEvent);
                        } else if (error) {
                            Throwable t = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
                            AsyncEvent asyncEvent = new AsyncEvent(asyncContext, 
                                    asyncListenerRegistration.getRequest(), asyncListenerRegistration.getResponse(), t);
                            asyncListener.onError(asyncEvent);
                        } else {
                            AsyncEvent asyncEvent = new AsyncEvent(asyncContext, 
                                    asyncListenerRegistration.getRequest(), asyncListenerRegistration.getResponse());
                            asyncListener.onComplete(asyncEvent);
                        }
                    } catch (Throwable e) {
                        container.getLogger().error(sm.getString("standardWrapper.async.listenerError",
                                getContainer().getName()), e);
                        exception(request, response, e);
                    }
                }
                if (timeout && request.isEventMode() && asyncContext.getPath() == null) {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                }
            } else if (asyncContext.getRunnable() != null) {
                // Execute the runnable
                try {
                    asyncContext.getRunnable().run();
                } catch (Throwable e) {
                    container.getLogger().error(sm.getString("standardWrapper.async.runnableError",
                            getContainer().getName()), e);
                    exception(request, response, e);
                }
            } else if (asyncContext.getPath() != null) {
                // We executed the dispatch
                asyncContext.done();
                // Remap the request, set the dispatch attributes, create the filter chain
                // and invoke the Servlet
                Context context = (Context) getContainer().getParent();
                ServletContext servletContext = context.getServletContext();
                if (asyncContext.getServletContext() != null) {
                    // Cross context
                    servletContext = asyncContext.getServletContext();
                }
                request.setCanStartAsync(true);
                ApplicationDispatcher dispatcher = 
                    (ApplicationDispatcher) servletContext.getRequestDispatcher(asyncContext.getPath());
                // Invoke the dispatcher async method with the attributes flag
                try {
                    dispatcher.async(asyncContext.getRequest(), asyncContext.getResponse(), 
                            asyncContext.getUseAttributes());
                } catch (Throwable e) {
                    container.getLogger().error(sm.getString("standardWrapper.async.dispatchError",
                            getContainer().getName()), e);
                    exception(request, response, e);
                }
                request.setCanStartAsync(false);
                // If there is no new startAsync, then close the response
                // Note: The spec uses the same asyncContext instance
                if (!asyncContext.isReady()) {
                    asyncContext.complete();
                }
            }
        }

    }
        
    // -------------------------------------------------------- Private Methods


    /**
     * Handle the specified ServletException encountered while processing
     * the specified Request to produce the specified Response.  Any
     * exceptions that occur during generation of the exception report are
     * logged and swallowed.
     *
     * @param request The request being processed
     * @param response The response being generated
     * @param exception The exception that occurred (which possibly wraps
     *  a root cause exception
     */
    private void exception(Request request, Response response,
                           Throwable exception) {
    	request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, exception);
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    public long getProcessingTime() {
        return processingTime;
    }

    public void setProcessingTime(long processingTime) {
        this.processingTime = processingTime;
    }

    public long getMaxTime() {
        return maxTime;
    }

    public void setMaxTime(long maxTime) {
        this.maxTime = maxTime;
    }

    public long getMinTime() {
        return minTime;
    }

    public void setMinTime(long minTime) {
        this.minTime = minTime;
    }

    public int getRequestCount() {
        return requestCount;
    }

    public void setRequestCount(int requestCount) {
        this.requestCount = requestCount;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(int errorCount) {
        this.errorCount = errorCount;
    }

    public int getEventCount() {
        return eventCount;
    }

    public void setEventCount(int eventCount) {
        this.eventCount = eventCount;
    }

    // Don't register in JMX

    public ObjectName createObjectName(String domain, ObjectName parent)
            throws MalformedObjectNameException
    {
        return null;
    }
}
