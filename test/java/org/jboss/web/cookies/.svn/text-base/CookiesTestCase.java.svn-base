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
 * @author Jean-Frederic Clere
 */


package org.jboss.web.cookies;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

public class CookiesTestCase extends TestCase {

    /**
     * Construct a new instance of this test case.
     *
     * @param name Name of the test case
     */
    public CookiesTestCase(String name) {
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
        return (new TestSuite(CookiesTestCase.class));
    }

    /**
     * Tear down instance variables required by this test case.
     */
    public void tearDown() {
    }
   
    /* should create foo (bar) and a (b) */ 
    public void testTest1() { readTest(1, "foo=bar; a=b"); }
    public void testTest2() { readTest(2, "foo=bar;a=b"); }
    public void testTest3() { readTest(3, "foo=bar;a=b;"); }
    public void testTest4() { readTest(4, "foo=bar;a=b; "); }
    public void testTest5() { readTest(5, "foo=bar;a=b; ;"); }

    /* should create foo () and a (b) */
    public void testTest6() { readTest(6, "foo=;a=b; ;"); }
    public void testTest7() { readTest(7, "foo;a=b; ;"); }

    /* v1 create foo (bar) and a (b) */
    public void testTest8() { readTest(8, "$Version=1; foo=bar;a=b"); }
    public void testTest9() { readTest(9, "$Version=1;foo=bar;a=b; ; "); }
    /* v1 create foo () and a (b) */
    public void testTest10() { readTest(10, "$Version=1;foo=;a=b; ; "); }
    public void testTest11() { readTest(11, "$Version=1;foo= ;a=b; ; "); }
    public void testTest12() { readTest(12, "$Version=1;foo;a=b; ; "); }

    /* v1 create foo (bar) and a (b) */
    public void testTest13() { readTest(13, "$Version=1;foo=\"bar\";a=b; ; "); }
    /* use domain */
    public void testTest14() { readTest(14, "$Version=1;foo=\"bar\";$Domain=apache.org;a=b"); }
    public void testTest15() { readTest(15, "$Version=1;foo=\"bar\";$Domain=apache.org;a=b;$Domain=yahoo.com"); }
    /* rfc2965 */
    public void testTest16() { readTest(16, "$Version=1;foo=\"bar\";$Domain=apache.org;$Port=8080;a=b"); }
    // make sure these never split into two cookies - JVK
    public void testTest17() { readTest(17, "$Version=1;foo=\"b\"ar\";$Domain=apache.org;$Port=8080;a=b"); }
    public void testTest18() { readTest(18, "$Version=1;foo=\"b\\\"ar\";$Domain=apache.org;$Port=8080;a=b"); }
    public void testTest19() { readTest(19, "$Version=1;foo=\"b'ar\";$Domain=apache.org;$Port=8080;a=b"); }
    // JFC: sure it is "b" and not b'ar ?
    public void testTest20() { readTest(20, "$Version=1;foo=b'ar;$Domain=apache.org;$Port=8080;a=b"); }
    // Ends in quoted value
    public void testTest21() { readTest(21, "foo=bar;a=\"b\""); }
    public void testTest22() { readTest(22, "foo=bar;a=\"b\";"); }

    // Testing bad stuff
    public void testTest23() { readTest(23, "$Version=\"1\"; foo='bar'; $Path=/path; $Domain=\"localhost\""); }

    // wrong, path should not have '/' JVK ???
    public void testTest24() { readTest(24, "$Version=1;foo=\"bar\";$Path=/examples;a=b; ; "); }
    // Test name-only at the end of the header
    public void testTest25() { readTest(25, "foo;a=b;bar"); }
    public void testTest26() { readTest(26, "foo;a=b;bar;"); }
    public void testTest27() { readTest(27, "foo;a=b;bar "); }
    public void testTest28() { readTest(28, "foo;a=b;bar ;"); }
    // BUG -- the ' ' needs to be skipped.
    public void testTest29() { readTest(29, "foo;a=b; ;bar"); }
    // BUG -- ';' needs skipping
    public void testTest30() { readTest(30, "foo;a=b;;bar"); }
    public void testTest31() { readTest(31, "foo;a=b; ;;bar=rab"); }
    public void testTest32() { readTest(32, "foo;a=b;; ;bar=rab"); }

    public void testTest33() { readTest(33, "a=b;#;bar=rab"); }
    public void testTest34() { readTest(34, "a=b;;\\;bar=rab"); }

    // Try all the separators of version1 in version0 cookie.
    public void testTest35() { readTest(35, "a=()<>@:\\\"/[]?={}\t; foo=bar; a=b"); }

    // Just test the version.
    public void testTest36() { readTest(36, "$Version=1;foo=bar"); }
    public void testTest37() { readTest(37, "$Version=0;foo=bar"); }

    // JBAS-6766..
    public void testTest38() { readTest(38, "a==:;foo=b=:ar"); }
    public void testTest39() { readTest(39, "a=:;foo=b:ar"); }
    public void testTest40() { readTest(40, "a==;foo=b=ar"); }
    public void testTestW38() { writeTest(38); }
    public void testTestW39() { writeTest(39); }
    public void testTestW40() { writeTest(40); }

    // Bugzilla 45272
    public void testTestW41() { writeTest(41, "a", "Path=/"); }
    public void testTestW42() { writeTest(42, "a", "Version=1; Path=/"); }


    // Store the received cookies.
    private String cookies [] = null;

    public void writeTest(int test) {
        writeTest(test, null, null);
    }
    public void writeTest(int test, String name, String token) {
        try {
        String result = Mytest(test, null, false);
        if (result != null)
           fail(result);
        } catch (Exception ex) {
            ex.printStackTrace();
            fail("Test failed because of " + ex);
            return;
        }
        /* Tests the cookies */
        if (name == null)
            return; // nothing to do...
        if (cookies != null) {
            String sname = name + "=";
            for (int i=0; i<cookies.length; i++) {
                if (cookies[i].startsWith(sname)) {
                    if (cookies[i].indexOf(token)==-1) {
                        fail("Can't find token in " + cookies[i]);
                        return;
                    }
                }
            }
        }
    }

    public void readTest(int test, String cookie) {
        try {
        String result = Mytest(test, cookie, true);
        if (result != null)
           fail(result);
        } catch (Exception ex) {
            ex.printStackTrace();
            fail("Test failed because of " + ex);
        }
    }
    public String Mytest(int test, String cookie, boolean read) throws Exception {
        Socket socket = new Socket("localhost", 8080);
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        // writer.write("GET /cookies/test.jsp HTTP/1.0");
        writer.write("GET /myapp/test.jsp HTTP/1.0\r\n");
        writer.write("User-Agent: CookiesTestCase/1.0\r\n");
        writer.write("Connection: Keep-Alive\r\n");
        writer.write("TEST: " + test + "\r\n");
        if (read)
            writer.write("ACTION: READ\r\n");
        else
            writer.write("ACTION: CREATE\r\n");
        if (cookie != null)
            writer.write("Cookie: " + cookie + "\r\n");
        writer.write("\r\n");
        writer.flush();

        String responseStatus = reader.readLine();
        if (responseStatus == null) {
            return "Can't read answer" ;
        }
        responseStatus = responseStatus.substring(responseStatus.indexOf(' ') + 1, responseStatus.indexOf(' ', responseStatus.indexOf(' ') + 1));
        int status = Integer.parseInt(responseStatus);

        // read all the headers.
        String header = reader.readLine();
        int contentLength = 0;
        String error = "Unknown";
        cookies = null;
        while (!"".equals(header)) {
            int colon = header.indexOf(':');
            String headerName = header.substring(0, colon).trim();
            String headerValue = header.substring(colon + 1).trim();
            if ("content-length".equalsIgnoreCase(headerName)) {
                contentLength = Integer.parseInt(headerValue);
            }
            if ("ERROR".equalsIgnoreCase(headerName)) {
                error = headerValue;
            }
            if ("set-cookie".equalsIgnoreCase(headerName)) {
                if (cookies == null) {
                    cookies = new String [1];
                    cookies[0] = headerValue;
                } else {
                    String [] oldcookies = cookies;
                    cookies = new String [oldcookies.length + 1];
                    for (int i=0; i<oldcookies.length; i++) {
                        cookies[i] = oldcookies[i];
                    }
                    cookies[oldcookies.length] = headerValue;
                }
            }
            header = reader.readLine();
        }
        if (contentLength > 0) {
            char[] buf = new char[512];
            while (contentLength > 0) {
                int thisTime = (contentLength > buf.length) ? buf.length : contentLength;
                int n = reader.read(buf, 0, thisTime);
                if (n <= 0) {
                    return "Read content failed";
                } else {
                    contentLength -= n;
                }
           }
        }
        if (status != 200) {
            return "Error " + error + " from Servlet";
        }
        return null;
    }
    
}
