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

package org.apache.tomcat.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;

// Depends: JDK1.1

/**
 * Utils for introspection and reflection
 * Note: For the complete legacy version, see JBoss Web 2.1
 */
public final class IntrospectionUtils {

    
    private static org.jboss.logging.Logger log=
        org.jboss.logging.Logger.getLogger( IntrospectionUtils.class );
    
    /**
     * Find a method with the right name If found, call the method ( if param is
     * int or boolean we'll convert value to the right type before) - that means
     * you can have setDebug(1).
     */
    public static boolean setProperty(Object o, String name, String value) {
        if (dbg > 1)
            d("setProperty(" + o.getClass() + " " + name + "=" + value + ")");

        String setter = "set" + capitalize(name);

        try {
            Method methods[] = o.getClass().getMethods();
            Method setPropertyMethodVoid = null;
            Method setPropertyMethodBool = null;

            // First, the ideal case - a setFoo( String ) method
            for (int i = 0; i < methods.length; i++) {
                Class paramT[] = methods[i].getParameterTypes();
                if (setter.equals(methods[i].getName()) && paramT.length == 1
                        && "java.lang.String".equals(paramT[0].getName())) {

                    methods[i].invoke(o, new Object[] { value });
                    return true;
                }
            }

            // Try a setFoo ( int ) or ( boolean )
            for (int i = 0; i < methods.length; i++) {
                boolean ok = true;
                if (setter.equals(methods[i].getName())
                        && methods[i].getParameterTypes().length == 1) {

                    // match - find the type and invoke it
                    Class paramType = methods[i].getParameterTypes()[0];
                    Object params[] = new Object[1];

                    // Try a setFoo ( int )
                    if ("java.lang.Integer".equals(paramType.getName())
                            || "int".equals(paramType.getName())) {
                        try {
                            params[0] = new Integer(value);
                        } catch (NumberFormatException ex) {
                            ok = false;
                        }
                    // Try a setFoo ( long )
                    }else if ("java.lang.Long".equals(paramType.getName())
                                || "long".equals(paramType.getName())) {
                            try {
                                params[0] = new Long(value);
                            } catch (NumberFormatException ex) {
                                ok = false;
                            }

                        // Try a setFoo ( boolean )
                    } else if ("java.lang.Boolean".equals(paramType.getName())
                            || "boolean".equals(paramType.getName())) {
                        params[0] = new Boolean(value);

                        // Try a setFoo ( InetAddress )
                    } else if ("java.net.InetAddress".equals(paramType
                            .getName())) {
                        try {
                            params[0] = InetAddress.getByName(value);
                        } catch (UnknownHostException exc) {
                            d("Unable to resolve host name:" + value);
                            ok = false;
                        }

                        // Unknown type
                    } else {
                        d("Unknown type " + paramType.getName());
                    }

                    if (ok) {
                        methods[i].invoke(o, params);
                        return true;
                    }
                }

                // save "setProperty" for later
                if ("setProperty".equals(methods[i].getName())) { 
                    if (methods[i].getReturnType() == Boolean.TYPE){
                        setPropertyMethodBool = methods[i];
                    } else {
                        setPropertyMethodVoid = methods[i];    
                    }
                }
            }

            // Ok, no setXXX found, try a setProperty("name", "value")
            if (setPropertyMethodBool != null || setPropertyMethodVoid != null) {
                Object params[] = new Object[2];
                params[0] = name;
                params[1] = value;
                if (setPropertyMethodBool != null) {
                    return (Boolean) setPropertyMethodBool.invoke(o, params);
                } else {
                    setPropertyMethodVoid.invoke(o, params);
                    return true;
                }
            }

        } catch (IllegalArgumentException ex2) {
            log.warn("IAE " + o + " " + name + " " + value, ex2);
        } catch (SecurityException ex1) {
            if (dbg > 0)
                d("SecurityException for " + o.getClass() + " " + name + "="
                        + value + ")");
            if (dbg > 1)
                ex1.printStackTrace();
        } catch (IllegalAccessException iae) {
            if (dbg > 0)
                d("IllegalAccessException for " + o.getClass() + " " + name
                        + "=" + value + ")");
            if (dbg > 1)
                iae.printStackTrace();
        } catch (InvocationTargetException ie) {
            if (dbg > 0)
                d("InvocationTargetException for " + o.getClass() + " " + name
                        + "=" + value + ")");
            if (dbg > 1)
                ie.printStackTrace();
        }
        return false;
    }

    public static Object getProperty(Object o, String name) {
        String getter = "get" + capitalize(name);
        String isGetter = "is" + capitalize(name);

        try {
            Method methods[] = o.getClass().getMethods();
            Method getPropertyMethod = null;

            // First, the ideal case - a getFoo() method
            for (int i = 0; i < methods.length; i++) {
                Class paramT[] = methods[i].getParameterTypes();
                if (getter.equals(methods[i].getName()) && paramT.length == 0) {
                    return methods[i].invoke(o, (Object[]) null);
                }
                if (isGetter.equals(methods[i].getName()) && paramT.length == 0) {
                    return methods[i].invoke(o, (Object[]) null);
                }

                if ("getProperty".equals(methods[i].getName())) {
                    getPropertyMethod = methods[i];
                }
            }

            // Ok, no setXXX found, try a getProperty("name")
            if (getPropertyMethod != null) {
                Object params[] = new Object[1];
                params[0] = name;
                return getPropertyMethod.invoke(o, params);
            }

        } catch (IllegalArgumentException ex2) {
            log.warn("IAE " + o + " " + name, ex2);
        } catch (SecurityException ex1) {
            if (dbg > 0)
                d("SecurityException for " + o.getClass() + " " + name + ")");
            if (dbg > 1)
                ex1.printStackTrace();
        } catch (IllegalAccessException iae) {
            if (dbg > 0)
                d("IllegalAccessException for " + o.getClass() + " " + name
                        + ")");
            if (dbg > 1)
                iae.printStackTrace();
        } catch (InvocationTargetException ie) {
            if (dbg > 0)
                d("InvocationTargetException for " + o.getClass() + " " + name
                        + ")");
            if (dbg > 1)
                ie.printStackTrace();
        }
        return null;
    }

    public static Object callMethod1(Object target, String methodN,
            Object param1, String typeParam1, ClassLoader cl) throws Exception {
        if (target == null || param1 == null) {
            d("Assert: Illegal params " + target + " " + param1);
        }
        if (dbg > 0)
            d("callMethod1 " + target.getClass().getName() + " "
                    + param1.getClass().getName() + " " + typeParam1);

        Class params[] = new Class[1];
        if (typeParam1 == null)
            params[0] = param1.getClass();
        else
            params[0] = cl.loadClass(typeParam1);
        Method m = findMethod(target.getClass(), methodN, params);
        if (m == null)
            throw new NoSuchMethodException(target.getClass().getName() + " "
                    + methodN);
        return m.invoke(target, new Object[] { param1 });
    }

    public static Method findMethod(Class c, String name, Class params[]) {
        Method methods[] = c.getMethods();
        if (methods == null)
            return null;
        for (int i = 0; i < methods.length; i++) {
            if (methods[i].getName().equals(name)) {
                Class methodParams[] = methods[i].getParameterTypes();
                if (methodParams == null)
                    if (params == null || params.length == 0)
                        return methods[i];
                if (params == null)
                    if (methodParams == null || methodParams.length == 0)
                        return methods[i];
                if (params.length != methodParams.length)
                    continue;
                boolean found = true;
                for (int j = 0; j < params.length; j++) {
                    if (params[j] != methodParams[j]) {
                        found = false;
                        break;
                    }
                }
                if (found)
                    return methods[i];
            }
        }
        return null;
    }

    /**
     * Reverse of Introspector.decapitalize
     */
    public static String capitalize(String name) {
        if (name == null || name.length() == 0) {
            return name;
        }
        char chars[] = name.toCharArray();
        chars[0] = Character.toUpperCase(chars[0]);
        return new String(chars);
    }

    public static String unCapitalize(String name) {
        if (name == null || name.length() == 0) {
            return name;
        }
        char chars[] = name.toCharArray();
        chars[0] = Character.toLowerCase(chars[0]);
        return new String(chars);
    }

    // debug --------------------
    static final int dbg = 0;

    static void d(String s) {
        if (log.isDebugEnabled())
            log.debug("IntrospectionUtils: " + s);
    }
}
