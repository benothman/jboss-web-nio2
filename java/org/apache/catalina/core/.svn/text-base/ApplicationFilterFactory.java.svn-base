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


import javax.servlet.Servlet;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestWrapper;

import org.apache.catalina.Globals;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.RequestFacade;
import org.apache.catalina.deploy.FilterMap;
import org.jboss.servlet.http.HttpEventFilter;

/**
 * Factory for the creation and caching of Filters and creationg 
 * of Filter Chains.
 *
 * @author Greg Murray
 * @author Remy Maucherat
 * @version $Revision: 1.0
 */

public final class ApplicationFilterFactory {


    // -------------------------------------------------------------- Constants


    private static final int ERROR = 1;
    public static final Integer ERROR_INTEGER = new Integer(ERROR);
    private static final int FORWARD = 2;
    public static final Integer FORWARD_INTEGER = new Integer(FORWARD);
    private static final int INCLUDE = 4;
    public static final Integer INCLUDE_INTEGER = new Integer(INCLUDE);
    private static final int REQUEST = 8;
    public static final Integer REQUEST_INTEGER = new Integer(REQUEST);
    private static final int ASYNC = 16;
    public static final Integer ASYNC_INTEGER = new Integer(ASYNC);

    public static final String DISPATCHER_TYPE_ATTR = 
        Globals.DISPATCHER_TYPE_ATTR;
    public static final String DISPATCHER_REQUEST_PATH_ATTR = 
        Globals.DISPATCHER_REQUEST_PATH_ATTR;

    private static ApplicationFilterFactory factory = null;;


    // ----------------------------------------------------------- Constructors


    /*
     * Prevent instanciation outside of the getInstanceMethod().
     */
    private ApplicationFilterFactory() {
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Return the fqctory instance.
     */
    public static ApplicationFilterFactory getInstance() {
        if (factory == null) {
            factory = new ApplicationFilterFactory();
        }
        return factory;
    }


    /**
     * Construct and return a FilterChain implementation that will wrap the
     * execution of the specified servlet instance.  If we should not execute
     * a filter chain at all, return <code>null</code>.
     *
     * @param request The servlet request we are processing
     * @param wrapper The servlet instance to be wrapped
     */
    public ApplicationFilterChain createFilterChain(ServletRequest request, Wrapper wrapper, Servlet servlet) {

        // get the dispatcher type
        int dispatcher = -1; 
        if (request.getAttribute(DISPATCHER_TYPE_ATTR) != null) {
            Integer dispatcherInt = 
                (Integer) request.getAttribute(DISPATCHER_TYPE_ATTR);
            dispatcher = dispatcherInt.intValue();
        }
        String requestPath = null;
        Object attribute = request.getAttribute(DISPATCHER_REQUEST_PATH_ATTR);
        
        if (attribute != null){
            requestPath = attribute.toString();
        }
        
        // If there is no servlet to execute, return null
        if (wrapper.getServlet() == null)
            return (null);

        boolean event = false;
        
        // Get the request facade object
        RequestFacade requestFacade = null;
        if (request instanceof Request) {
            Request coreRequest = (Request) request;
            event = coreRequest.isEventMode();
            requestFacade = (RequestFacade) coreRequest.getRequest();
        } else {
            ServletRequest current = request;
            while (current != null) {
                // If we run into the container request we are done
                if (current instanceof RequestFacade) {
                    requestFacade = (RequestFacade) current;
                    break;
                }
                // Advance to the next request in the chain
                current = ((ServletRequestWrapper) current).getRequest();
            }
        }
        
        // Create and initialize a filter chain object
        ApplicationFilterChain filterChain = null;
        if (requestFacade == null) {
            // Not normal: some async functions and tracing will be disabled
            filterChain = new ApplicationFilterChain();
        } else {
            // Add this filter chain to the request facade
            requestFacade.nextFilterChain();
            if (Globals.IS_SECURITY_ENABLED) {
                filterChain = new ApplicationFilterChain();
                requestFacade.setFilterChain(filterChain);
            } else {
                filterChain = requestFacade.getFilterChain();
                if (filterChain == null) {
                    filterChain = new ApplicationFilterChain();
                    requestFacade.setFilterChain(filterChain);
                }
            }
            filterChain.setRequestFacade(requestFacade);
        }
        filterChain.setWrapper(wrapper);
        filterChain.setServlet(servlet);

        // Acquire the filter mappings for this Context
        StandardContext context = (StandardContext) wrapper.getParent();
        FilterMap filterMaps[] = context.findFilterMaps();

        // If there are no filter mappings, we are done
        if ((filterMaps == null) || (filterMaps.length == 0))
            return (filterChain);

        // Acquire the information we will need to match filter mappings
        String servletName = wrapper.getName();

        // Add the relevant path-mapped filters to this filter chain
        for (int i = 0; i < filterMaps.length; i++) {
            if (!matchDispatcher(filterMaps[i] ,dispatcher)) {
                continue;
            }
            if (!matchFiltersURL(filterMaps[i], requestPath))
                continue;
            ApplicationFilterConfig filterConfig = (ApplicationFilterConfig)
                context.findFilterConfig(filterMaps[i].getFilterName());
            if (filterConfig == null) {
                ;       // FIXME - log configuration problem
                continue;
            }
            boolean isEventFilter = false;
            if (event) {
                try {
                    isEventFilter = filterConfig.getFilter() instanceof HttpEventFilter;
                } catch (Exception e) {
                    // Note: The try catch is there because getFilter has a lot of 
                    // declared exceptions. However, the filter is allocated much
                    // earlier
                }
                if (isEventFilter) {
                    filterChain.addFilter(filterConfig);
                }
            } else {
                filterChain.addFilter(filterConfig);
            }
        }

        // Add filters that match on servlet name second
        for (int i = 0; i < filterMaps.length; i++) {
            if (!matchDispatcher(filterMaps[i] ,dispatcher)) {
                continue;
            }
            if (!matchFiltersServlet(filterMaps[i], servletName))
                continue;
            ApplicationFilterConfig filterConfig = (ApplicationFilterConfig)
                context.findFilterConfig(filterMaps[i].getFilterName());
            if (filterConfig == null) {
                ;       // FIXME - log configuration problem
                continue;
            }
            boolean isEventFilter = false;
            if (event) {
                try {
                    isEventFilter = filterConfig.getFilter() instanceof HttpEventFilter;
                } catch (Exception e) {
                    // Note: The try catch is there because getFilter has a lot of 
                    // declared exceptions. However, the filter is allocated much
                    // earlier
                }
                if (isEventFilter) {
                    filterChain.addFilter(filterConfig);
                }
            } else {
                filterChain.addFilter(filterConfig);
            }
        }

        // Return the completed filter chain
        return (filterChain);

    }


    // -------------------------------------------------------- Private Methods


    /**
     * Return <code>true</code> if the context-relative request path
     * matches the requirements of the specified filter mapping;
     * otherwise, return <code>false</code>.
     *
     * @param filterMap Filter mapping being checked
     * @param requestPath Context-relative request path of this request
     */
    private boolean matchFiltersURL(FilterMap filterMap, String requestPath) {

        // Check the specific "*" special URL pattern, which also matches
        // named dispatches
        if (filterMap.getMatchAllUrlPatterns())
            return (true);
        
        if (requestPath == null)
            return (false);

        // Match on context relative request path
        String[] testPaths = filterMap.getURLPatterns();
        
        for (int i = 0; i < testPaths.length; i++) {
            if (matchFiltersURL(testPaths[i], requestPath)) {
                return (true);
            }
        }
        
        // No match
        return (false);
        
    }
    

    /**
     * Return <code>true</code> if the context-relative request path
     * matches the requirements of the specified filter mapping;
     * otherwise, return <code>false</code>.
     *
     * @param testPath URL mapping being checked
     * @param requestPath Context-relative request path of this request
     */
    private boolean matchFiltersURL(String testPath, String requestPath) {
        
        if (testPath == null)
            return (false);

        // Case 1 - Exact Match
        if (testPath.equals(requestPath))
            return (true);

        // Case 2 - Path Match ("/.../*")
        if (testPath.equals("/*"))
            return (true);
        if (testPath.endsWith("/*")) {
            if (testPath.regionMatches(0, requestPath, 0, 
                                       testPath.length() - 2)) {
                if (requestPath.length() == (testPath.length() - 2)) {
                    return (true);
                } else if ('/' == requestPath.charAt(testPath.length() - 2)) {
                    return (true);
                }
            }
            return (false);
        }

        // Case 3 - Extension Match
        if (testPath.startsWith("*.")) {
            int slash = requestPath.lastIndexOf('/');
            int period = requestPath.lastIndexOf('.');
            if ((slash >= 0) && (period > slash) 
                && (period != requestPath.length() - 1)
                && ((requestPath.length() - period) 
                    == (testPath.length() - 1))) {
                return (testPath.regionMatches(2, requestPath, period + 1,
                                               testPath.length() - 2));
            }
        }

        // Case 4 - "Default" Match
        return (false); // NOTE - Not relevant for selecting filters

    }


    /**
     * Return <code>true</code> if the specified servlet name matches
     * the requirements of the specified filter mapping; otherwise
     * return <code>false</code>.
     *
     * @param filterMap Filter mapping being checked
     * @param servletName Servlet name being checked
     */
    private boolean matchFiltersServlet(FilterMap filterMap, 
                                        String servletName) {

        if (servletName == null) {
            return (false);
        }
        // Check the specific "*" special servlet name
        else if (filterMap.getMatchAllServletNames()) {
            return (true);
        } else {
            String[] servletNames = filterMap.getServletNames();
            for (int i = 0; i < servletNames.length; i++) {
                if (servletName.equals(servletNames[i])) {
                    return (true);
                }
            }
            return false;
        }

    }


    /**
     * Convienience method which returns true if  the dispatcher type
     * matches the dispatcher types specified in the FilterMap
     */
    private boolean matchDispatcher(FilterMap filterMap, int dispatcher) {
        switch (dispatcher) {
            case FORWARD : {
                if ((filterMap.getDispatcherMapping() & FilterMap.FORWARD) == FilterMap.FORWARD) {
                        return true;
                }
                break;
            }
            case INCLUDE : {
                if ((filterMap.getDispatcherMapping() & FilterMap.INCLUDE) == FilterMap.INCLUDE) {
                    return true;
                }
                break;
            }
            case REQUEST : {
                if ((filterMap.getDispatcherMapping() & FilterMap.REQUEST) == FilterMap.REQUEST) {
                    return true;
                }
                break;
            }
            case ERROR : {
                if ((filterMap.getDispatcherMapping() & FilterMap.ERROR) == FilterMap.ERROR) {
                    return true;
                }
                break;
            }
            case ASYNC : {
                if ((filterMap.getDispatcherMapping() & FilterMap.ASYNC) == FilterMap.ASYNC) {
                    return true;
                }
                break;
            }
        }
        return false;
    }


}
