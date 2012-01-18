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

import java.io.IOException;
import java.io.Writer;
import javax.servlet.jsp.*;

/**
 * Encapsulates a portion of JSP code in an object that 
 * can be invoked as many times as needed.  JSP Fragments are defined 
 * using JSP syntax as the body of a tag for an invocation to a SimpleTag 
 * handler, or as the body of a &lt;jsp:attribute&gt; standard action
 * specifying the value of an attribute that is declared as a fragment,
 * or to be of type JspFragment in the TLD.
 * <p>
 * The definition of the JSP fragment must only contain template 
 * text and JSP action elements.  In other words, it must not contain
 * scriptlets or scriptlet expressions.  At translation time, the 
 * container generates an implementation of the JspFragment abstract class
 * capable of executing the defined fragment.
 * <p>
 * A tag handler can invoke the fragment zero or more times, or 
 * pass it along to other tags, before returning.  To communicate values
 * to/from a JSP fragment, tag handlers store/retrieve values in 
 * the JspContext associated with the fragment.
 * <p>
 * Note that tag library developers and page authors should not generate
 * JspFragment implementations manually.
 * <p>
 * <i>Implementation Note</i>: It is not necessary to generate a 
 * separate class for each fragment.  One possible implementation is 
 * to generate a single helper class for each page that implements 
 * JspFragment. Upon construction, a discriminator can be passed to 
 * select which fragment that instance will execute.
 *
 * @since JSP 2.0
 */
public abstract class JspFragment {

    /**
     * Executes the fragment and directs all output to the given Writer,
     * or the JspWriter returned by the getOut() method of the JspContext
     * associated with the fragment if out is null.
     *
     * @param out The Writer to output the fragment to, or null if 
     *     output should be sent to JspContext.getOut().
     * @throws javax.servlet.jsp.JspException Thrown if an error occured
     *     while invoking this fragment.
     * @throws javax.servlet.jsp.SkipPageException Thrown if the page
     *     that (either directly or indirectly) invoked the tag handler that
     *     invoked this fragment is to cease evaluation.  The container
     *     must throw this exception if a Classic Tag Handler returned
     *     Tag.SKIP_PAGE or if a Simple Tag Handler threw SkipPageException.
     * @throws java.io.IOException If there was an error writing to the 
     *     stream.
     */
    public abstract void invoke( Writer out )
        throws JspException, IOException;

    /**
     * Returns the JspContext that is bound to this JspFragment.
     *
     * @return The JspContext used by this fragment at invocation time.
     */
    public abstract JspContext getJspContext();

}
