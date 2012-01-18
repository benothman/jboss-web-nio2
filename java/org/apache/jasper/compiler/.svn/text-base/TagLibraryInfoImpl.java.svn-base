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


package org.apache.jasper.compiler;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.jsp.tagext.FunctionInfo;
import javax.servlet.jsp.tagext.PageData;
import javax.servlet.jsp.tagext.TagAttributeInfo;
import javax.servlet.jsp.tagext.TagExtraInfo;
import javax.servlet.jsp.tagext.TagFileInfo;
import javax.servlet.jsp.tagext.TagInfo;
import javax.servlet.jsp.tagext.TagLibraryInfo;
import javax.servlet.jsp.tagext.TagLibraryValidator;
import javax.servlet.jsp.tagext.TagVariableInfo;
import javax.servlet.jsp.tagext.ValidationMessage;
import javax.servlet.jsp.tagext.VariableInfo;

import org.apache.catalina.Globals;
import org.apache.catalina.util.RequestUtil;
import org.apache.jasper.JasperException;
import org.apache.jasper.JspCompilationContext;
import org.jboss.logging.Logger;

/**
 * Implementation of the TagLibraryInfo class from the JSP spec.
 * 
 * @author Anil K. Vijendran
 * @author Mandar Raje
 * @author Pierre Delisle
 * @author Kin-man Chung
 * @author Jan Luehe
 */
class TagLibraryInfoImpl extends TagLibraryInfo implements TagConstants {

    /**
     * The types of URI one may specify for a tag library
     */
    public static final int ABS_URI = 0;
    public static final int ROOT_REL_URI = 1;
    public static final int NOROOT_REL_URI = 2;

    // Logger
    private Logger log = Logger.getLogger(TagLibraryInfoImpl.class);

    private JspCompilationContext ctxt;
    
    private PageInfo pi;

    private ErrorDispatcher err;

    private ParserController parserController;

    private final void print(String name, String value, PrintWriter w) {
        if (value != null) {
            w.print(name + " = {\n\t");
            w.print(value);
            w.print("\n}\n");
        }
    }

    public String toString() {
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        print("tlibversion", tlibversion, out);
        print("jspversion", jspversion, out);
        print("shortname", shortname, out);
        print("urn", urn, out);
        print("info", info, out);
        print("uri", uri, out);
        print("tagLibraryValidator", "" + tagLibraryValidator, out);

        for (int i = 0; i < tags.length; i++)
            out.println(tags[i].toString());

        for (int i = 0; i < tagFiles.length; i++)
            out.println(tagFiles[i].toString());

        for (int i = 0; i < functions.length; i++)
            out.println(functions[i].toString());

        return sw.toString();
    }

    /**
     * Constructor.
     */
    public TagLibraryInfoImpl(JspCompilationContext ctxt, ParserController pc, PageInfo pi,
            String prefix, String uriIn, String[] location, ErrorDispatcher err)
            throws JasperException {
        super(prefix, uriIn);

        this.ctxt = ctxt;
        this.parserController = pc;
        this.pi = pi;
        this.err = err;
        
        if (location == null) {
            // The URI points to the TLD itself or to a JAR file in which the
            // TLD is stored
            location = generateTLDLocation(uri, ctxt);
            if (location != null) {
                uri = location[0];
            }
        }

        URL jarFileUrl = null;
        if (location == null) {
            err.jspError("jsp.error.file.not.found", uriIn);
        }
        if (location[0] != null && location[0].endsWith(".jar")) {
            try {
                URL jarUrl = ctxt.getServletContext().getResource(location[0]);
                if (jarUrl != null) {
                    jarFileUrl = new URL("jar:" + jarUrl + "!/");
                }
            } catch (MalformedURLException ex) {
                err.jspError("jsp.error.file.not.found", uriIn);
            }
        }
        
        org.apache.catalina.deploy.jsp.TagLibraryInfo tagLibraryInfo = 
            ((HashMap<String, org.apache.catalina.deploy.jsp.TagLibraryInfo>) 
            ctxt.getServletContext().getAttribute(Globals.JSP_TAG_LIBRARIES)).get(uri);
        if (tagLibraryInfo == null) {
            err.jspError("jsp.error.file.not.found", uriIn);
        }

        ArrayList<TagInfo> tagInfos = new ArrayList<TagInfo>();
        ArrayList<TagFileInfo> tagFileInfos = new ArrayList<TagFileInfo>();
        HashMap<String, FunctionInfo> functionInfos = new HashMap<String, FunctionInfo>();

        this.jspversion = tagLibraryInfo.getJspversion();
        this.tlibversion = tagLibraryInfo.getTlibversion();
        this.shortname = tagLibraryInfo.getShortname();
        this.urn = tagLibraryInfo.getUri();
        this.info = tagLibraryInfo.getInfo();
        if (tagLibraryInfo.getValidator() != null) {
            this.tagLibraryValidator = createValidator(tagLibraryInfo);
        }
        org.apache.catalina.deploy.jsp.TagInfo tagInfosArray[] = tagLibraryInfo.getTags();
        for (int i = 0; i < tagInfosArray.length; i++) {
            TagInfo tagInfo = createTagInfo(tagInfosArray[i]);
            tagInfos.add(tagInfo);
        }
        org.apache.catalina.deploy.jsp.TagFileInfo tagFileInfosArray[] = tagLibraryInfo.getTagFileInfos();
        for (int i = 0; i < tagFileInfosArray.length; i++) {
            TagFileInfo tagFileInfo = createTagFileInfo(tagFileInfosArray[i], jarFileUrl);
            tagFileInfos.add(tagFileInfo);
        }
        org.apache.catalina.deploy.jsp.FunctionInfo functionInfosArray[] = tagLibraryInfo.getFunctionInfos();
        for (int i = 0; i < functionInfosArray.length; i++) {
            FunctionInfo functionInfo = createFunctionInfo(functionInfosArray[i]);
            if (functionInfos.containsKey(functionInfo.getName())) {
                err.jspError("jsp.error.tld.fn.duplicate.name", functionInfo.getName(),
                        uri);
            }
            functionInfos.put(functionInfo.getName(), functionInfo);
        }
        
        if (tlibversion == null) {
            err.jspError("jsp.error.tld.mandatory.element.missing",
                    "tlib-version");
        }
        if (jspversion == null) {
            err.jspError("jsp.error.tld.mandatory.element.missing",
                    "jsp-version");
        }

        this.tags = tagInfos.toArray(new TagInfo[0]);
        this.tagFiles = tagFileInfos.toArray(new TagFileInfo[0]);
        this.functions = functionInfos.values().toArray(new FunctionInfo[0]);
    }

    /**
     * @param uri The uri of the TLD @param ctxt The compilation context
     * 
     * @return String array whose first element denotes the path to the TLD. If
     * the path to the TLD points to a jar file, then the second element denotes
     * the name of the TLD entry in the jar file, which is hardcoded to
     * META-INF/taglib.tld.
     */
    private String[] generateTLDLocation(String uri, JspCompilationContext ctxt)
            throws JasperException {

        int uriType = uriType(uri);
        if (uriType == ABS_URI) {
            err.jspError("jsp.error.taglibDirective.absUriCannotBeResolved",
                    uri);
        } else if (uriType == NOROOT_REL_URI) {
            uri = ctxt.resolveRelativeUri(uri);
            if (uri != null) {
                uri = RequestUtil.normalize(uri);
            }
        }

        String[] location = new String[2];
        location[0] = uri;
        if (location[0].endsWith("jar")) {
            URL url = null;
            try {
                url = ctxt.getResource(location[0]);
            } catch (Exception ex) {
                err.jspError("jsp.error.tld.unable_to_get_jar", location[0], ex
                        .toString());
            }
            if (url == null) {
                err.jspError("jsp.error.tld.missing_jar", location[0]);
            }
            location[0] = url.toString();
            location[1] = "META-INF/taglib.tld";
        }

        return location;
    }

    public TagLibraryInfo[] getTagLibraryInfos() {
        Collection coll = pi.getTaglibs();
        return (TagLibraryInfo[]) coll.toArray(new TagLibraryInfo[0]);
    }
    
    protected TagInfo createTagInfo(org.apache.catalina.deploy.jsp.TagInfo tagInfo)
        throws JasperException {

        ArrayList<TagAttributeInfo> attributeInfos = new ArrayList<TagAttributeInfo>();
        ArrayList<TagVariableInfo> variableInfos = new ArrayList<TagVariableInfo>();

        boolean dynamicAttributes = JspUtil.booleanValue(tagInfo.getDynamicAttributes());

        org.apache.catalina.deploy.jsp.TagAttributeInfo attributeInfosArray[] = tagInfo.getTagAttributeInfos();
        for (int i = 0; i < attributeInfosArray.length; i++) {
            TagAttributeInfo attributeInfo = createTagAttributeInfo(attributeInfosArray[i]);
            attributeInfos.add(attributeInfo);
        }

        org.apache.catalina.deploy.jsp.TagVariableInfo variableInfosArray[] = tagInfo.getTagVariableInfos();
        for (int i = 0; i < variableInfosArray.length; i++) {
            TagVariableInfo variableInfo = createTagVariableInfo(variableInfosArray[i]);
            variableInfos.add(variableInfo);
        }
        
        TagExtraInfo tei = null;
        String teiClassName = tagInfo.getTagExtraInfo();
        if (teiClassName != null && !teiClassName.equals("")) {
            try {
                Class teiClass = ctxt.getClassLoader().loadClass(teiClassName);
                tei = (TagExtraInfo) teiClass.newInstance();
            } catch (Exception e) {
                err.jspError("jsp.error.teiclass.instantiation", teiClassName,
                        e);
            }
        }

        String tagBodyContent = tagInfo.getBodyContent();
        if (tagBodyContent == null) {
            tagBodyContent = TagInfo.BODY_CONTENT_JSP;
        }

        return new TagInfo(tagInfo.getTagName(), tagInfo.getTagClassName(), tagBodyContent, 
                tagInfo.getInfoString(), this, tei, attributeInfos.toArray(new TagAttributeInfo[0]), 
                tagInfo.getDisplayName(), tagInfo.getSmallIcon(), tagInfo.getLargeIcon(),
                variableInfos.toArray(new TagVariableInfo[0]), dynamicAttributes);
    }
    
    protected TagAttributeInfo createTagAttributeInfo(org.apache.catalina.deploy.jsp.TagAttributeInfo attributeInfo) {

        String type = attributeInfo.getType();
        String expectedType = attributeInfo.getExpectedTypeName();
        String methodSignature = attributeInfo.getMethodSignature();
        boolean rtexprvalue = JspUtil.booleanValue(attributeInfo.getReqTime());
        boolean fragment = JspUtil.booleanValue(attributeInfo.getFragment());
        boolean deferredValue = JspUtil.booleanValue(attributeInfo.getDeferredValue());
        boolean deferredMethod = JspUtil.booleanValue(attributeInfo.getDeferredMethod());
        boolean required = JspUtil.booleanValue(attributeInfo.getRequired());
        
        if (type != null) {
            if ("1.2".equals(jspversion)
                    && (type.equals("Boolean") || type.equals("Byte")
                            || type.equals("Character")
                            || type.equals("Double")
                            || type.equals("Float")
                            || type.equals("Integer")
                            || type.equals("Long") || type.equals("Object")
                            || type.equals("Short") || type
                            .equals("String"))) {
                type = "java.lang." + type;
            }
        }

        if (deferredValue) {
            type = "javax.el.ValueExpression";
            if (expectedType != null) {
                expectedType = expectedType.trim();
            } else {
                expectedType = "java.lang.Object";
            }
        }
        
        if (deferredMethod) {
            type = "javax.el.MethodExpression";
            if (methodSignature != null) {
                methodSignature = methodSignature.trim();
            } else {
                methodSignature = "java.lang.Object method()";
            }
        }

        if (fragment) {
            /*
             * According to JSP.C-3 ("TLD Schema Element Structure - tag"),
             * 'type' and 'rtexprvalue' must not be specified if 'fragment' has
             * been specified (this will be enforced by validating parser).
             * Also, if 'fragment' is TRUE, 'type' is fixed at
             * javax.servlet.jsp.tagext.JspFragment, and 'rtexprvalue' is fixed
             * at true. See also JSP.8.5.2.
             */
            type = "javax.servlet.jsp.tagext.JspFragment";
            rtexprvalue = true;
        }

        if (!rtexprvalue && type == null) {
            // According to JSP spec, for static values (those determined at
            // translation time) the type is fixed at java.lang.String.
            type = "java.lang.String";
        }
        
        return new TagAttributeInfo(attributeInfo.getName(), required, 
                type, rtexprvalue, fragment, attributeInfo.getDescription(), 
                deferredValue, deferredMethod, expectedType,
                methodSignature);
    }
    
    protected TagVariableInfo createTagVariableInfo(org.apache.catalina.deploy.jsp.TagVariableInfo variableInfo) {
        int scope = VariableInfo.NESTED;
        String s = variableInfo.getScope();
        if (s != null) {
            if ("NESTED".equals(s)) {
                scope = VariableInfo.NESTED;
            } else if ("AT_BEGIN".equals(s)) {
                scope = VariableInfo.AT_BEGIN;
            } else if ("AT_END".equals(s)) {
                scope = VariableInfo.AT_END;
            }
        }
        String className = variableInfo.getClassName();
        if (className == null) {
            className = "java.lang.String";
        }
        boolean declare = true;
        if (variableInfo.getDeclare() != null) {
            declare = JspUtil.booleanValue(variableInfo.getDeclare());
        }
        return new TagVariableInfo(variableInfo.getNameGiven(), variableInfo.getNameFromAttribute(), 
                className, declare, scope);
    }

    protected TagFileInfo createTagFileInfo(org.apache.catalina.deploy.jsp.TagFileInfo tagFileInfo, URL jarFileUrl)
        throws JasperException {
        String name = tagFileInfo.getName();
        String path = tagFileInfo.getPath();
        if (path.startsWith("/META-INF/tags")) {
            // Tag file packaged in JAR
            // See https://issues.apache.org/bugzilla/show_bug.cgi?id=46471
            // This needs to be removed once all the broken code that depends on
            // it has been removed
            ctxt.setTagFileJarUrl(path, jarFileUrl);
        } else if (!path.startsWith("/WEB-INF/tags")) {
            err.jspError("jsp.error.tagfile.illegalPath", path);
        }
        TagInfo tagInfo = TagFileProcessor.parseTagFileDirectives(
                parserController, name, path, jarFileUrl, this);
        return new TagFileInfo(name, path, tagInfo);
    }
    
    protected FunctionInfo createFunctionInfo(org.apache.catalina.deploy.jsp.FunctionInfo functionInfo) {
        return new FunctionInfo(functionInfo.getName(), 
                functionInfo.getFunctionClass(), functionInfo.getFunctionSignature());
    }
    
    
    /** 
     * Returns the type of a URI:
     *     ABS_URI
     *     ROOT_REL_URI
     *     NOROOT_REL_URI
     */
    public static int uriType(String uri) {
        if (uri.indexOf(':') != -1) {
            return ABS_URI;
        } else if (uri.startsWith("/")) {
            return ROOT_REL_URI;
        } else {
            return NOROOT_REL_URI;
        }
    }

    private TagLibraryValidator createValidator(org.apache.catalina.deploy.jsp.TagLibraryInfo tagLibraryInfo)
            throws JasperException {
        org.apache.catalina.deploy.jsp.TagLibraryValidatorInfo tlvInfo = tagLibraryInfo.getValidator();
        String validatorClass = tlvInfo.getValidatorClass();
        Map<String, Object> initParams = tlvInfo.getInitParams();

        TagLibraryValidator tlv = null;
        if (validatorClass != null && !validatorClass.equals("")) {
            try {
                Class tlvClass = ctxt.getClassLoader()
                        .loadClass(validatorClass);
                tlv = (TagLibraryValidator) tlvClass.newInstance();
            } catch (Exception e) {
                err.jspError("jsp.error.tlvclass.instantiation",
                        validatorClass, e);
            }
        }
        if (tlv != null) {
            tlv.setInitParameters(initParams);
        }
        return tlv;
    }

    // *********************************************************************
    // Until javax.servlet.jsp.tagext.TagLibraryInfo is fixed

    /**
     * The instance (if any) for the TagLibraryValidator class.
     * 
     * @return The TagLibraryValidator instance, if any.
     */
    public TagLibraryValidator getTagLibraryValidator() {
        return tagLibraryValidator;
    }

    /**
     * Translation-time validation of the XML document associated with the JSP
     * page. This is a convenience method on the associated TagLibraryValidator
     * class.
     * 
     * @param thePage
     *            The JSP page object
     * @return A string indicating whether the page is valid or not.
     */
    public ValidationMessage[] validate(PageData thePage) {
        TagLibraryValidator tlv = getTagLibraryValidator();
        if (tlv == null)
            return null;

        String uri = getURI();
        if (uri.startsWith("/")) {
            uri = URN_JSPTLD + uri;
        }

        return tlv.validate(getPrefixString(), uri, thePage);
    }

    protected TagLibraryValidator tagLibraryValidator;
}
