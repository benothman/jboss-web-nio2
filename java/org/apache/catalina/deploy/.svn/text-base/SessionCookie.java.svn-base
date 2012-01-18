/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.catalina.deploy;

import java.io.Serializable;

import javax.servlet.SessionCookieConfig;

import org.apache.catalina.Globals;

public class SessionCookie implements SessionCookieConfig, Serializable {

    protected String domain = null;
    protected String path = null;
    protected String comment = null;
    protected boolean httpOnly = false;
    protected boolean secure = false;
    protected int maxAge = -1;
    protected String name = Globals.SESSION_COOKIE_NAME;
    protected String pathParameterName = ";" + Globals.SESSION_PARAMETER_NAME + "=";

    public SessionCookie() {
    }

    public String getDomain() {
        return domain;
    }

    public String getPath() {
        return path;
    }

    public String getComment() {
        return comment;
    }

    public boolean isHttpOnly() {
        return httpOnly;
    }

    public boolean isSecure() {
        return secure;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public void setHttpOnly(boolean httpOnly) {
        this.httpOnly = httpOnly;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    public int getMaxAge() {
        return maxAge;
    }

    public void setMaxAge(int maxAge) {
        this.maxAge = maxAge;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name != null) {
            this.name = name;
            pathParameterName = ";" + name + "=";
        }
    }

    public String getPathParameterName() {
        return pathParameterName;
    }

}
