<%@ page pageEncoding="UTF-8"%>
<%@ page import="java.io.*"%>
<HTML>
<HEAD>
<META http-equiv="Content-Type" content="text/html; charset=UTF-8">
<TITLE>request#getReader test.</TITLE>
</HEAD>
<BODY>
mark.jsp is called.
<HR>

<%
            request.setCharacterEncoding("UTF-8");
            response.setContentType("text/html; charset=UTF-8");

            String expected = (String) session.getAttribute("expected");
            String formName = (String) session.getAttribute("formName");
            String readBeforeMarkS = (String) session
                    .getAttribute("readBeforeMark");
            int readBeforeMark = Integer.parseInt(readBeforeMarkS);
            String readAheadLimitS = (String) session
                    .getAttribute("readAheadLimit");
            int readAheadLimit = Integer.parseInt(readAheadLimitS);
            String readAfterMarkS = (String) session
                    .getAttribute("readAfterMark");
            int readAfterMark = Integer.parseInt(readAfterMarkS);

            BufferedReader reader = request.getReader();

            String boundary = null;
            String contentType = request.getContentType();
            if (contentType != null) {
                int delim = contentType.indexOf("boundary=");
                boundary = contentType.substring(delim + 9).trim();
            }
            expected = "--" + boundary
                    + "\r\nContent-Disposition: form-data; name=\"" + formName
                    + "\"\r\n\r\n" + expected + "\r\n--" + boundary + "--\r\n";

            if (expected.length() < readBeforeMark) {
                readBeforeMark = expected.length();
            }
            if (expected.length() < readBeforeMark + readAfterMark) {
                readAfterMark = expected.length() - readBeforeMark;
            }

            String expectedBeforeM = expected.substring(0, readBeforeMark);
            StringBuffer beforeMSB = new StringBuffer();
            String expectedAfterM = expected.substring(readBeforeMark,
                    readAfterMark + readBeforeMark);
            StringBuffer afterMSB = new StringBuffer();
            String expectedAfterR = null;
            String expectedAfterRex = expected.substring(readBeforeMark + readAfterMark);
            String expectedAfterRok = expected.substring(readBeforeMark);
            StringBuffer afterRSB = new StringBuffer();
            boolean isResetFailExpected = (readAfterMark > readAheadLimit);

            readCharB(reader, beforeMSB, 1, readBeforeMark);
            reader.mark(readAheadLimit);
            readCharB(reader, afterMSB, 1, readAfterMark);
            String resetMessage = null;
            boolean isExOccur = false;
            try {
                reader.reset();
                resetMessage = "<I>no throw</I>";
                expectedAfterR = expectedAfterRok;
            } catch (Exception e) {
                resetMessage = ((e instanceof IOException) && (isResetFailExpected)) ? ""
                        : "<B><I>N.G.</I></B>:";
                resetMessage += "<I>" + e.toString() + "</I>";
                isExOccur = true;
                expectedAfterR = expectedAfterRex;
            }
            readCharB(reader, afterRSB, 1, -1);

            outln(out, "Content-Type:" + request.getContentType());
            outln(out, "Character Encoding:" + request.getCharacterEncoding());
            outln(out, "Content-Length:" + request.getContentLength());
%>
<HR>
<%
            outln(out, "read before mark expected:" + readBeforeMark
                    + " result:" + beforeMSB.length() + " correct:<B><I>"
                    + expectedBeforeM.equals(beforeMSB.toString())+"</I></B>");
            outln(out, "mark(" + readAheadLimit + ")");
            outln(out, "read after mark expected:" + expectedAfterM.length() + " result:"
                    + afterMSB.length() + " correct:<B><I>"
                    + expectedAfterM.equals(afterMSB.toString())+"</I></B>");
            outln(out, "reset() is <B><I>"+((isResetFailExpected)?"":"not ")+" allowed</I></B> to throw IOException, result:" + resetMessage);
            
            outln(out, "read after reset expected:" + expectedAfterR.length()
                    + " result:" + afterRSB.length() + " correct:<B><I>"
                    + expectedAfterR.equals(afterRSB.toString())+"</I></B>");
%>

</BODY>
</HTML>
<%!void readCharB(BufferedReader br, StringBuffer sb, int bufferSize, int size)
            throws IOException {
        char[] buf = new char[bufferSize];
        int read = 0;
        while (((size == -1) || (sb.length() < size))
                && ((read = br.read(buf)) != -1)) {
            sb.append(buf, 0, read);
        }
    }

    void outln(JspWriter out, String str) throws IOException {
        out.println(str + "<BR>");
        System.out.println(str);
    }%>
