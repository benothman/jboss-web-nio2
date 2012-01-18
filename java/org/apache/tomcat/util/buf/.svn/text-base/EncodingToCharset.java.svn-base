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


package org.apache.tomcat.util.buf;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Helper class for charset handling.
 * 
 * @author Mark Thomas
 * @author Remy Maucherat
 */
public class EncodingToCharset {

    private static final Map<String, Charset> encodingToCharsetCache =
        new HashMap<String, Charset>();

    static {
        for (Charset charset: Charset.availableCharsets().values()) {
            encodingToCharsetCache.put(charset.name().toUpperCase(Locale.US), charset);
            for (String alias : charset.aliases()) {
                encodingToCharsetCache.put(alias.toUpperCase(Locale.US), charset);
            }
        }
    }

    public static Charset toCharset(String encoding)
        throws UnsupportedEncodingException {
        // Encoding names should all be ASCII
        String enc = encoding.toUpperCase(Locale.US);

        Charset charset = encodingToCharsetCache.get(enc);
        
        if (charset == null) {
            // Pre-population of the cache means this must be invalid
            throw new UnsupportedEncodingException(encoding);
        }
        return charset;

    }

}
