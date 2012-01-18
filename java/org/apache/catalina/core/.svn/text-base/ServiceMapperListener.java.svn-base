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

import org.apache.catalina.Container;
import org.apache.catalina.ContainerEvent;
import org.apache.catalina.ContainerListener;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Service;
import org.apache.catalina.Wrapper;
import org.apache.tomcat.util.http.mapper.Mapper;


/**
 * Mapper listener.
 *
 * @author Remy Maucherat
 * @author Costin Manolache
 */
public class ServiceMapperListener
    implements LifecycleListener, ContainerListener
 {


    // ----------------------------------------------------- Instance Variables


    /**
     * Associated mapper.
     */
    protected Mapper mapper = null;
    

    // ----------------------------------------------------------- Constructors


    /**
     * Create mapper listener.
     */
    public ServiceMapperListener(Mapper mapper) {
        this.mapper = mapper;
    }


    // --------------------------------------------------------- Public Methods


    public void containerEvent(ContainerEvent event) {

        Container container = event.getContainer();
        String type = event.getType();

        if (type.equals(Container.ADD_CHILD_EVENT)) {
            if (container instanceof Host) {
                // Deploying a webapp
                Context context = (Context) event.getData();
                ((Lifecycle) context).addLifecycleListener(this);
                if (context.isStarted()) {
                    addContext(context);
                }
            } else if (container instanceof Engine) {
                // Deploying a host
                Host host = (Host) event.getData();
                host.addContainerListener(this);
                mapper.addHost(host.getName(), host.findAliases(), host);
            }
        } else if (type.equals(Container.REMOVE_CHILD_EVENT)) {
            if (container instanceof Host) {
                // Undeploying a webapp
                Context context = (Context) event.getData();
                ((Lifecycle) context).removeLifecycleListener(this);
                mapper.removeContext(container.getName(), context.getName());
            } else if (container instanceof Engine) {
                // Undeploying a host
                Host host = (Host) event.getData();
                host.removeContainerListener(this);
                mapper.removeHost(host.getName());
            }
        } else if (type == Host.ADD_ALIAS_EVENT) {
            mapper.addHostAlias(((Host) event.getSource()).getName(),
                    event.getData().toString());
        } else if (type == Host.REMOVE_ALIAS_EVENT) {
            mapper.removeHostAlias(event.getData().toString());
        }

    }

    
    public void lifecycleEvent(LifecycleEvent event) {

        Object source = event.getLifecycle();

        if (Lifecycle.START_EVENT.equals(event.getType())) {
            if (source instanceof Service) {
                Service service = (Service) source;
                Engine engine = (Engine) service.getContainer();
                engine.addContainerListener(this);
                ((Lifecycle) engine).addLifecycleListener(this);
                if (engine.getDefaultHost() != null) {
                    mapper.setDefaultHostName(engine.getDefaultHost());
                }
                for (Container host : engine.findChildren()) {
                    host.addContainerListener(this);
                    mapper.addHost(host.getName(), ((Host) host).findAliases(), host);
                    for (Container context : host.findChildren()) {
                        ((Lifecycle) context).addLifecycleListener(this);
                    }
                }
            }
        } else if (Lifecycle.STOP_EVENT.equals(event.getType())) {
            if (source instanceof Context) {
                // Stop a webapp
                Context context = (Context) source;
                mapper.removeContext(context.getParent().getName(), context.getName());
            } else if (source instanceof Service) {
                Service service = (Service) source;
                Engine engine = (Engine) service.getContainer();
                engine.removeContainerListener(this);
                ((Lifecycle) engine).removeLifecycleListener(this);
                for (Container host : engine.findChildren()) {
                    host.removeContainerListener(this);
                    mapper.removeHost(host.getName());
                    for (Container context : host.findChildren()) {
                        ((Lifecycle) context).removeLifecycleListener(this);
                        mapper.removeContext(host.getName(), context.getName());
                    }
                }
            }
        } else if (Context.COMPLETE_CONFIG_EVENT.equals(event.getType())) {
            Context context = (Context) source;
            addContext(context);
        }

    }
    
    protected void addContext(Context context) {
        mapper.addContext(context.getParent().getName(), context.getName(), context, 
                context.findWelcomeFiles(), context.getResources());
        // Add all wrappers
        for (Container child : context.findChildren()) {
            Wrapper wrapper = (Wrapper) child;
            if (wrapper.getEnabled()) {
                for (String mapping : wrapper.findMappings()) {
                    boolean jspWildCard = ("jsp".equals(wrapper.getName()) 
                            && mapping.endsWith("/*"));
                    mapper.addWrapper(context.getParent().getName(), context.getName(), 
                            mapping, wrapper, jspWildCard);
                }
            }
        }
    }

}
