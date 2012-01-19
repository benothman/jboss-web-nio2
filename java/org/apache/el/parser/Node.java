/* Generated By:JJTree: Do not edit this line. Node.java */

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.el.parser;

import javax.el.ELException;
import javax.el.MethodInfo;
import javax.el.ValueReference;

import org.apache.el.lang.EvaluationContext;


/* All AST nodes must implement this interface.  It provides basic
   machinery for constructing the parent and child relationships
   between nodes. */

/**
 * @author Jacob Hookom [jacob@hookom.net]
 * @version $Change: 181177 $$DateTime: 2001/06/26 08:45:09 $$Author: remy.maucherat@jboss.com $
 */
public interface Node {

  /** This method is called after the node has been made the current
    node.  It indicates that child nodes can now be added to it. */
  public void jjtOpen();

  /** This method is called after all the child nodes have been
    added. */
  public void jjtClose();

  /** This pair of methods are used to inform the node of its
    parent. */
  public void jjtSetParent(Node n);
  public Node jjtGetParent();

  /** This method tells the node to add its argument to the node's
    list of children.  */
  public void jjtAddChild(Node n, int i);

  /** This method returns a child node.  The children are numbered
     from zero, left to right. */
  public Node jjtGetChild(int i);

  /** Return the number of children the node has. */
  public int jjtGetNumChildren();
  
  public String getImage();
  
  public Object getValue(EvaluationContext ctx) throws ELException;
  public ValueReference getValueReference(EvaluationContext ctx);
  public void setValue(EvaluationContext ctx, Object value) throws ELException;
  public Class getType(EvaluationContext ctx) throws ELException;
  public boolean isReadOnly(EvaluationContext ctx) throws ELException;
  public void accept(NodeVisitor visitor) throws Exception;
  public MethodInfo getMethodInfo(EvaluationContext ctx, Class[] paramTypes) throws ELException;
  public Object invoke(EvaluationContext ctx, Class[] paramTypes, Object[] paramValues) throws ELException;
  public boolean isParametersProvided();
}