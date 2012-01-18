<%@ page pageEncoding="UTF-8"%>
<%@ page import="java.io.*"%>
<HTML>
<HEAD>
<META http-equiv="Content-Type" content="text/html; charset=UTF-8">
<TITLE>request#getReader test.</TITLE>
</HEAD>
<BODY>

<%!static char JP[] = "あいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやゆよわゐゑをん"
            .toCharArray();

    static char ASCII[] = "abcdefghijklmnopqrstuvwxyz".toCharArray();

    static String formName = "n";%>

<%
            response.setContentType("text/html; charset=UTF-8");

            int size = 8192;
            String sizeS = request.getParameter("size");
            if (sizeS != null) {
                size = Integer.parseInt(sizeS);
            }

            boolean isAscii = true;
            String ascii = request.getParameter("ascii");
            if (ascii != null) {
                isAscii = Boolean.parseBoolean(ascii);
            }

            char[] chars = isAscii ? ASCII : JP;

            StringBuffer sb = new StringBuffer(size + formName.length() + 1
                    + chars.length);
            while (sb.length() < size) {
                sb.append(chars);
            }
            if (sb.length() > size) {
                sb.delete(size, sb.length());
            }

            session.setAttribute("expected", sb.toString());
            session.setAttribute("formName", formName);

            String kind = request.getParameter("kind");

            if ("read".equals(kind)) {
%>

<FORM method="POST" action="<%= response.encodeURL("readLine.jsp") %>"
	enctype="multipart/form-data">request#getReader()#readLine test<BR>
<input type="text" name="<%= formName %>" value="<%= sb.toString() %>" />
<input type="submit" value="send" /></FORM>

<FORM method="POST" action="<%= response.encodeURL("read.jsp") %>"
	enctype="multipart/form-data">request#getReader()#read() test<BR>
<input type="text" name="<%= formName %>" value="<%= sb.toString() %>" />
<input type="submit" value="send" /></FORM>

<FORM method="POST" action="<%= response.encodeURL("readCharB.jsp") %>"
	enctype="multipart/form-data">request#getReader()#read(char[1])
test<BR>
<input type="text" name="<%= formName %>" value="<%= sb.toString() %>" />
<input type="submit" value="send" /></FORM>
<%
} else if ("garbage".equals(kind)) {
            session.setAttribute("readForGarbage",request.getParameter("readForGarbage"));
%>
<FORM method="POST"
	action="<%= response.encodeURL("garbage.jsp")%>"
	enctype="multipart/form-data">garbage in bufffer test<BR>
<input type="text" name="<%= formName %>" value="<%= sb.toString() %>" />
<input type="submit" value="send" /></FORM>
<%
} else if ("mark/reset".equals(kind)) {
            session.setAttribute("readBeforeMark",request.getParameter("readBeforeMark"));
            session.setAttribute("readAheadLimit",request.getParameter("readAheadLimit"));
            session.setAttribute("readAfterMark",request.getParameter("readAfterMark"));
%>
<FORM method="POST"
	action="<%= response.encodeURL("mark.jsp") %>"
	enctype="multipart/form-data">mark/reset test<BR>
<input type="text" name="<%= formName %>" value="<%= sb.toString() %>" />
<input type="submit" value="send" /></FORM>
<%
}
%>
</BODY>
</HTML>
