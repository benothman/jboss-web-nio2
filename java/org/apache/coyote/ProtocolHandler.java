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

package org.apache.coyote;

import java.util.Iterator;


/**
 * Abstract the protocol implementation, including threading, etc.
 * Processor is single threaded and specific to stream-based protocols,
 * will not fit Jk protocols like JNI.
 *
 * This is the main interface to be implemented by a coyoute connector.
 * Adapter is the main interface to be impleneted by a coyote servlet container.
 *
 * @author Remy Maucherat
 * @author Costin Manolache
 * @see Adapter
 */
public interface ProtocolHandler {


    /**
     * Pass config info.
     */
    public void setAttribute(String name, Object value);


    public Object getAttribute(String name);
    public Iterator getAttributeNames();

    /**
     * The adapter, used to call the connector.
     */
    public void setAdapter(Adapter adapter);


    public Adapter getAdapter();


    public boolean hasIoEvents();


    public RequestGroupInfo getRequestGroupInfo();


   /**
     * Init the protocol.
     */
    public void init()
        throws Exception;


    /**
     * Start the protocol.
     */
    public void start()
        throws Exception;

    /**
     * Pause the protocol (optional).
     */
    public void pause()
        throws Exception;

    /**
     * Resume the protocol (optional).
     */
    public void resume()
        throws Exception;

    public void destroy()
        throws Exception;


}
