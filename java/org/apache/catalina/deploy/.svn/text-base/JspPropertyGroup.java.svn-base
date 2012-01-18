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

package org.apache.catalina.deploy;

import java.io.Serializable;
import java.util.ArrayList;

import javax.servlet.descriptor.JspPropertyGroupDescriptor;

public class JspPropertyGroup implements Serializable, JspPropertyGroupDescriptor {

    protected ArrayList<String> urlPatterns = new ArrayList<String>();
    protected String elIgnored = null;
    protected String pageEncoding = null;
    protected String scriptingInvalid = null;
    protected String isXml = null;
    protected ArrayList<String> includePreludes = new ArrayList<String>();
    protected ArrayList<String> includeCodas = new ArrayList<String>();
    protected String deferredSyntaxAllowedAsLiteral = null;
    protected String trimDirectiveWhitespaces = null;
    protected String defaultContentType = null;
    protected String buffer = null;
    protected String errorOnUndeclaredNamespace = null;

    public void addUrlPattern(String urlPattern) {
        urlPatterns.add(urlPattern);
    }
    public String getPageEncoding() {
        return pageEncoding;
    }
    public void setPageEncoding(String pageEncoding) {
        this.pageEncoding = pageEncoding;
    }
    public void addIncludePrelude(String includePrelude) {
        includePreludes.add(includePrelude);
    }
    public void addIncludeCoda(String includeCoda) {
        includeCodas.add(includeCoda);
    }
    public String getDefaultContentType() {
        return defaultContentType;
    }
    public void setDefaultContentType(String defaultContentType) {
        this.defaultContentType = defaultContentType;
    }
    public String getBuffer() {
        return buffer;
    }
    public void setBuffer(String buffer) {
        this.buffer = buffer;
    }
    public String getElIgnored() {
        return elIgnored;
    }
    public void setElIgnored(String elIgnored) {
        this.elIgnored = elIgnored;
    }
    public String getScriptingInvalid() {
        return scriptingInvalid;
    }
    public void setScriptingInvalid(String scriptingInvalid) {
        this.scriptingInvalid = scriptingInvalid;
    }
    public String getIsXml() {
        return isXml;
    }
    public void setIsXml(String isXml) {
        this.isXml = isXml;
    }
    public String getDeferredSyntaxAllowedAsLiteral() {
        return deferredSyntaxAllowedAsLiteral;
    }
    public void setDeferredSyntaxAllowedAsLiteral(
            String deferredSyntaxAllowedAsLiteral) {
        this.deferredSyntaxAllowedAsLiteral = deferredSyntaxAllowedAsLiteral;
    }
    public String getTrimDirectiveWhitespaces() {
        return trimDirectiveWhitespaces;
    }
    public void setTrimDirectiveWhitespaces(String trimDirectiveWhitespaces) {
        this.trimDirectiveWhitespaces = trimDirectiveWhitespaces;
    }
    public String getErrorOnUndeclaredNamespace() {
        return errorOnUndeclaredNamespace;
    }
    public void setErrorOnUndeclaredNamespace(String errorOnUndeclaredNamespace) {
        this.errorOnUndeclaredNamespace = errorOnUndeclaredNamespace;
    }
    public ArrayList<String> getUrlPatterns() {
        return urlPatterns;
    }
    public ArrayList<String> getIncludePreludes() {
        return includePreludes;
    }
    public ArrayList<String> getIncludeCodas() {
        return includeCodas;
    }

}
