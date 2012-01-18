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


package org.jboss.web.mapper;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

public class MapperTestCase extends TestCase {

    /**
     * Construct a new instance of this test case.
     *
     * @param name Name of the test case
     */
    public MapperTestCase(String name) {
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
        return (new TestSuite(MapperTestCase.class));
    }

    /**
     * Tear down instance variables required by this test case.
     */
    public void tearDown() {
    }

    /*
    public static void main(String args[]) {

        try {

        Mapper mapper = new Mapper();
        System.out.println("Start");

        mapper.addHost("sjbjdvwsbvhrb", new String[0], "blah1");
        mapper.addHost("sjbjdvwsbvhr/", new String[0], "blah1");
        mapper.addHost("wekhfewuifweuibf", new String[0], "blah2");
        mapper.addHost("ylwrehirkuewh", new String[0], "blah3");
        mapper.addHost("iohgeoihro", new String[0], "blah4");
        mapper.addHost("fwehoihoihwfeo", new String[0], "blah5");
        mapper.addHost("owefojiwefoi", new String[0], "blah6");
        mapper.addHost("iowejoiejfoiew", new String[0], "blah7");
        mapper.addHost("iowejoiejfoiew", new String[0], "blah17");
        mapper.addHost("ohewoihfewoih", new String[0], "blah8");
        mapper.addHost("fewohfoweoih", new String[0], "blah9");
        mapper.addHost("ttthtiuhwoih", new String[0], "blah10");
        mapper.addHost("lkwefjwojweffewoih", new String[0], "blah11");
        mapper.addHost("zzzuyopjvewpovewjhfewoih", new String[0], "blah12");
        mapper.addHost("xxxxgqwiwoih", new String[0], "blah13");
        mapper.addHost("qwigqwiwoih", new String[0], "blah14");

        System.out.println("Map:");
        for (int i = 0; i < mapper.hosts.length; i++) {
            System.out.println(mapper.hosts[i].name);
        }

        mapper.setDefaultHostName("ylwrehirkuewh");

        String[] welcomes = new String[2];
        welcomes[0] = "boo/baba";
        welcomes[1] = "bobou";

        mapper.addContext("iowejoiejfoiew", "", "context0", new String[0], null);
        mapper.addContext("iowejoiejfoiew", "/foo", "context1", new String[0], null);
        mapper.addContext("iowejoiejfoiew", "/foo/bar", "context2", welcomes, null);
        mapper.addContext("iowejoiejfoiew", "/foo/bar/bla", "context3", new String[0], null);

        mapper.addWrapper("iowejoiejfoiew", "/foo/bar", "/fo/*", "wrapper0");
        mapper.addWrapper("iowejoiejfoiew", "/foo/bar", "/", "wrapper1");
        mapper.addWrapper("iowejoiejfoiew", "/foo/bar", "/blh", "wrapper2");
        mapper.addWrapper("iowejoiejfoiew", "/foo/bar", "*.jsp", "wrapper3");
        mapper.addWrapper("iowejoiejfoiew", "/foo/bar", "/blah/bou/*", "wrapper4");
        mapper.addWrapper("iowejoiejfoiew", "/foo/bar", "/blah/bobou/*", "wrapper5");
        mapper.addWrapper("iowejoiejfoiew", "/foo/bar", "*.htm", "wrapper6");

        MappingData mappingData = new MappingData();
        MessageBytes host = MessageBytes.newInstance();
        host.setString("iowejoiejfoiew");
        MessageBytes uri = MessageBytes.newInstance();
        uri.setString("/foo/bar/blah/bobou/foo");
        uri.toChars();
        uri.getCharChunk().setLimit(-1);

        mapper.map(host, uri, mappingData);
        System.out.println("MD Host:" + mappingData.host);
        System.out.println("MD Context:" + mappingData.context);
        System.out.println("MD Wrapper:" + mappingData.wrapper);

        System.out.println("contextPath:" + mappingData.contextPath);
        System.out.println("wrapperPath:" + mappingData.wrapperPath);
        System.out.println("pathInfo:" + mappingData.pathInfo);
        System.out.println("redirectPath:" + mappingData.redirectPath);

        mappingData.recycle();
        mapper.map(host, uri, mappingData);
        System.out.println("MD Host:" + mappingData.host);
        System.out.println("MD Context:" + mappingData.context);
        System.out.println("MD Wrapper:" + mappingData.wrapper);

        System.out.println("contextPath:" + mappingData.contextPath);
        System.out.println("wrapperPath:" + mappingData.wrapperPath);
        System.out.println("pathInfo:" + mappingData.pathInfo);
        System.out.println("redirectPath:" + mappingData.redirectPath);

        for (int i = 0; i < 1000000; i++) {
            mappingData.recycle();
            mapper.map(host, uri, mappingData);
        }

        long time = System.currentTimeMillis();
        for (int i = 0; i < 1000000; i++) {
            mappingData.recycle();
            mapper.map(host, uri, mappingData);
        }
        System.out.println("Elapsed:" + (System.currentTimeMillis() - time));

        System.out.println("MD Host:" + mappingData.host);
        System.out.println("MD Context:" + mappingData.context);
        System.out.println("MD Wrapper:" + mappingData.wrapper);

        System.out.println("contextPath:" + mappingData.contextPath);
        System.out.println("wrapperPath:" + mappingData.wrapperPath);
        System.out.println("requestPath:" + mappingData.requestPath);
        System.out.println("pathInfo:" + mappingData.pathInfo);
        System.out.println("redirectPath:" + mappingData.redirectPath);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
    */

}
