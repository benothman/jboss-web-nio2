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
package org.apache.tomcat.bayeux;

import org.apache.cometd.bayeux.Bayeux;
import org.apache.tomcat.bayeux.request.MetaConnectRequest;
import org.apache.tomcat.bayeux.request.MetaDisconnectRequest;
import org.apache.tomcat.bayeux.request.MetaHandshakeRequest;
import org.apache.tomcat.bayeux.request.MetaSubscribeRequest;
import org.apache.tomcat.bayeux.request.MetaUnsubscribeRequest;
import org.apache.tomcat.bayeux.request.PublishRequest;
import org.apache.tomcat.util.json.JSONException;
import org.apache.tomcat.util.json.JSONObject;
import org.jboss.servlet.http.HttpEvent;

public class RequestFactory {

    public static BayeuxRequest getRequest(TomcatBayeux tomcatBayeux, HttpEvent event, JSONObject msg) throws JSONException {
        String channel = msg.optString(Bayeux.CHANNEL_FIELD);
        if (Bayeux.META_HANDSHAKE.equals(channel)) {
            return new MetaHandshakeRequest(tomcatBayeux,event,msg);
        }else if (Bayeux.META_CONNECT.equals(channel)) {
            return new MetaConnectRequest(tomcatBayeux,event,msg);
        }else if (Bayeux.META_DISCONNECT.equals(channel)) {
            return new MetaDisconnectRequest(tomcatBayeux,event,msg);
        }else if (Bayeux.META_SUBSCRIBE.equals(channel)) {
            return new MetaSubscribeRequest(tomcatBayeux,event,msg);
        }else if (Bayeux.META_UNSUBSCRIBE.equals(channel)) {
            return new MetaUnsubscribeRequest(tomcatBayeux,event,msg);
        } else {
            return new PublishRequest(tomcatBayeux,event,msg);
        }
    }
}