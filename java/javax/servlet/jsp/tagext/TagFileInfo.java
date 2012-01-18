/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2008 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 *
 *
 * This file incorporates work covered by the following copyright and
 * permission notice:
 *
 * Copyright 2004 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

 
package javax.servlet.jsp.tagext;

/**
 * Tag information for a tag file in a Tag Library;
 * This class is instantiated from the Tag Library Descriptor file (TLD)
 * and is available only at translation time.
 *
 * @since JSP 2.0
 */
public class TagFileInfo {

    /**
     * Constructor for TagFileInfo from data in the JSP 2.0 format for TLD.
     * This class is to be instantiated only from the TagLibrary code
     * under request from some JSP code that is parsing a
     * TLD (Tag Library Descriptor).
     *
     * Note that, since TagLibibraryInfo reflects both TLD information
     * and taglib directive information, a TagFileInfo instance is
     * dependent on a taglib directive.  This is probably a
     * design error, which may be fixed in the future.
     *
     * @param name The unique action name of this tag
     * @param path Where to find the .tag file implementing this 
     *     action, relative to the location of the TLD file.
     * @param tagInfo The detailed information about this tag, as parsed
     *     from the directives in the tag file.
     */
    public TagFileInfo( String name, String path, TagInfo tagInfo ) {
        this.name = name;
        this.path = path;
        this.tagInfo = tagInfo;
    }

    /**
     * The unique action name of this tag.
     *
     * @return The (short) name of the tag.
     */
    public String getName() {
        return name;
    }

    /**
     * Where to find the .tag file implementing this action.
     *
     * @return The path of the tag file, relative to the TLD, or "." if 
     *     the tag file was defined in an implicit tag file.
     */
    public String getPath() {
        return path;
    }

    /**
     * Returns information about this tag, parsed from the directives 
     * in the tag file.
     *
     * @return a TagInfo object containing information about this tag
     */
    public TagInfo getTagInfo() {
        return tagInfo;
    }

    // private fields for 2.0 info
    private String name;
    private String path;
    private TagInfo tagInfo;
}
