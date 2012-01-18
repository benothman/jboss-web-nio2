<html>
<!--
 Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements.  See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

<body bgcolor="white">
<h1> Request Information </h1>
<font size="4">
JSP Request Method: <%= request.getMethod() %>
<br>
Request URI: <%= request.getRequestURI() %>
<br>
Request Protocol: <%= request.getProtocol() %>
<br>
Servlet path: <%= request.getServletPath() %>
<br>
Path info: <%= request.getPathInfo() %>
<br>
Query string: <%= request.getQueryString() %>
<br>
Content length: <%= request.getContentLength() %>
<br>
Content type: <%= request.getContentType() %>
<br>
Server name: <%= request.getServerName() %>
<br>
Server port: <%= request.getServerPort() %>
<br>
Remote user: <%= request.getRemoteUser() %>
<br>
Remote address: <%= request.getRemoteAddr() %>
<br>
Remote host: <%= request.getRemoteHost() %>
<br>
Authorization scheme: <%= request.getAuthType() %> 
<br>
Locale: <%= request.getLocale() %>
</font>

<h1> Params Information </h1>
<font size="4">
<%
        java.util.Enumeration enumlist = request.getParameterNames();
        while (enumlist.hasMoreElements()) {
            String name = (String) enumlist.nextElement();
            String value = request.getParameter(name);
            out.println(name + ": " + value + "<br>");
        }
%>
</font>

<h1> Cluster Information </h1>
<font size="4">
JVMRoute: ${param.JVMRoute}
<br>
Reversed: ${param.Reversed}
<br>
Host: ${param.Host}
<br>
Port: ${param.Port}
<br>
Type: ${param.Type}
<br>
context: ${param.context}
</font>

<hr>
The browser you are using is
<%= request.getHeader("User-Agent") %>
<hr>
</body>
</html>
