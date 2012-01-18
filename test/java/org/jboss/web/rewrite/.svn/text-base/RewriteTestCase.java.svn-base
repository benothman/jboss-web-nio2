/*
 * JBoss, Home of Professional Open Source
 * Copyright 2006, JBoss Inc., and individual contributors as indicated
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


package org.jboss.web.rewrite;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class RewriteTestCase extends TestCase {

    protected class TestResolver extends Resolver {

        public String resolve(String key) {
            return "server_variable_value[" + key + "]";
        }

        public String resolveHttp(String key) {
            return "http_header_value[" + key + "]";
        }

        public String resolveSsl(String key) {
            return "ssl_property_value[" + key + "]";
        }
        
        public boolean resolveResource(int type, String name) {
            return true;
        }

    }
    
    /**
     * Construct a new instance of this test case.
     *
     * @param name Name of the test case
     */
    public RewriteTestCase(String name) {
        super(name);
    }

    /**
     * Set up instance variables required by this test case.
     */
    public void setUp() {
    }

    /**
     * Return the tests included in this test suite.
     */
    public static Test suite() {
        return (new TestSuite(RewriteTestCase.class));
    }

    /**
     * Tear down instance variables required by this test case.
     */
    public void tearDown() {
    }
    
    public void testCanonicalUrl() {
        Resolver resolver = new TestResolver();
        
        RewriteRule rule1 = new RewriteRule();
        rule1.setPatternString("^/~([^/]+)/?(.*)");
        rule1.setSubstitutionString("/u/$1/$2");
        
        RewriteRule rule2 = new RewriteRule();
        rule2.setPatternString("^/([uge])/([^/]+)$");
        rule2.setSubstitutionString("/$1/$2/");

        RewriteRule[] rules = new RewriteRule[2];
        rules[0] = rule1;
        rules[1] = rule2;
        for (int i = 0; i < rules.length; i++) {
            rules[i].parse(null);
        }
        
        String result = rewriteUrl("/~user/foo/bar", resolver, rules).toString();
        assertEquals("/u/user/foo/bar", result);
        result = rewriteUrl("/u/user", resolver, rules).toString();
        assertEquals("/u/user/", result);
    }
    
    public void testCanonicalHostname() {
        Resolver resolver = new TestResolver();
        
        RewriteRule rule = new RewriteRule();
        rule.setPatternString("^/(.*)");
        rule.setSubstitutionString("http://fully.qualified.domain.name:%{SERVER_PORT}/$1");
        RewriteRule[] rules = new RewriteRule[1];
        rules[0] = rule;
        
        RewriteCond cond1 = new RewriteCond();
        cond1.setTestString("%{HTTP_HOST}");
        cond1.setCondPattern("!^fully\\.qualified\\.domain\\.name");
        cond1.setNocase(true);
        
        RewriteCond cond2 = new RewriteCond();
        cond2.setTestString("%{HTTP_HOST}");
        cond2.setCondPattern("!^$");
        
        RewriteCond cond3 = new RewriteCond();
        cond3.setTestString("%{SERVER_PORT}");
        cond3.setCondPattern("!^80$");
        
        rule.addCondition(cond1);
        rule.addCondition(cond2);
        rule.addCondition(cond3);
        
        for (int i = 0; i < rules.length; i++) {
            rules[i].parse(null);
        }
        
        String result = rewriteUrl("/foo/bar", resolver, rules).toString();
        assertEquals("http://fully.qualified.domain.name:server_variable_value[SERVER_PORT]/foo/bar", result);
    }
    
    public void testMovedDocumentRoot() {
        Resolver resolver = new TestResolver();

        RewriteRule rule = new RewriteRule();
        rule.setPatternString("^/$");
        rule.setSubstitutionString("/about/");
        
        RewriteRule[] rules = new RewriteRule[1];
        rules[0] = rule;
        for (int i = 0; i < rules.length; i++) {
            rules[i].parse(null);
        }
        
        String result = rewriteUrl("/", resolver, rules).toString();
        assertEquals("/about/", result);
    }
    
    public void testParsing() {
        Object result = null;
        String test = null;
        RewriteRule resultRule = null;
        RewriteCond resultCond = null;
        
        test = "RewriteRule   ^/~([^/]+)/?(.*)    /u/$1/$2  [R]";
        result = RewriteValve.parse(test);
        if (result instanceof RewriteRule) {
            resultRule = (RewriteRule) result;
        }
        assertNotNull(resultRule);
        assertTrue(resultRule.isRedirect());
        assertEquals(resultRule.getPatternString(), "^/~([^/]+)/?(.*)");
        assertEquals(resultRule.getSubstitutionString(), "/u/$1/$2");
        resultRule = null;
        
        test = "RewriteRule ^/(.*)         http://fully.qualified.domain.name:%{SERVER_PORT}/$1 [L,R]";
        result = RewriteValve.parse(test);
        if (result instanceof RewriteRule) {
            resultRule = (RewriteRule) result;
        }
        assertNotNull(resultRule);
        assertTrue(resultRule.isRedirect());
        assertTrue(resultRule.isLast());
        assertEquals(resultRule.getPatternString(), "^/(.*)");
        assertEquals(resultRule.getSubstitutionString(), "http://fully.qualified.domain.name:%{SERVER_PORT}/$1");
        resultRule = null;
        
        test = "RewriteCond %{HTTP_HOST}   !^fully\\.qualified\\.domain\\.name [NC]";
        result = RewriteValve.parse(test);
        if (result instanceof RewriteCond) {
            resultCond = (RewriteCond) result;
        }
        assertNotNull(resultCond);
        assertTrue(resultCond.isNocase());
        assertEquals(resultCond.getTestString(), "%{HTTP_HOST}");
        assertEquals(resultCond.getCondPattern(), "!^fully\\.qualified\\.domain\\.name");
        resultCond = null;
        
    }
    
    
    public static CharSequence rewriteUrl(CharSequence url, Resolver resolver, RewriteRule[] rules) {
        if (rules == null)
            return url;
        for (int i = 0; i < rules.length; i++) {
            CharSequence newurl = rules[i].evaluate(url, resolver);
            if (newurl != null) {
                // Check some extra flags, including redirect, proxying, etc
                // FIXME
                url = newurl;
            }
        }
        return url;
    }

}
