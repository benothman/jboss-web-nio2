<%@ page pageEncoding="UTF-8"%>
<%@ page import="java.io.*"%>
<HTML>
<HEAD>
<META http-equiv="Content-Type" content="text/html; charset=UTF-8">
<TITLE>cookies#test.</TITLE>
</HEAD>
<BODY>
<%
  /*
   * Just read the header and process the corresponding tests
   */
  String test = request.getHeader("TEST");
  String action = request.getHeader("ACTION");
  int ntest = 0;
  if (test != null)
    ntest = Integer.parseInt(test);

  response.setContentType("text/html; charset=UTF-8");

  if (action !=null && action.compareTo("READ") == 0) {
    switch (ntest) {
      case 1: test(response, request, out, "foo", "bar", "a", "b"); break;
      case 2: test(response, request, out, "foo", "bar", "a", "b"); break;
      case 3: test(response, request, out, "foo", "bar", "a", "b"); break;
      case 4: test(response, request, out, "foo", "bar", "a", "b"); break;
      case 5: test(response, request, out, "foo", "bar", "a", "b"); break;

      case 6: test(response, request, out, "foo", "", "a", "b"); break;
      case 7: test(response, request, out, "foo", "", "a", "b"); break;

      case 8: test(response, request, out, "foo", "bar", "a", "b"); break;
      case 9: test(response, request, out, "foo", "bar", "a", "b"); break;
      
      case 10: test(response, request, out, "foo", "", "a", "b"); break;
      case 11: test(response, request, out, "foo", "", "a", "b"); break;
      case 12: test(response, request, out, "foo", "", "a", "b"); break;

      case 13: test(response, request, out, "foo", "bar", "a", "b"); break;

      case 14: test(response, request, out, "foo", "bar", "a", "b"); break;
      case 15: test(response, request, out, "foo", "bar", "a", "b"); break;

      case 16: test(response, request, out, "foo", "bar", "a", "b"); break;

      case 17: test(response, request, out, "foo", "b", "a", "b"); break;
      case 18: test(response, request, out, "foo", "b\"ar", "a", "b"); break;
      case 19: test(response, request, out, "foo", "b'ar", "a", "b"); break;

      case 20: test(response, request, out, "foo", "b", "a", "b"); break;

      case 21: test(response, request, out, "foo", "bar", "a", "b"); break;
      case 22: test(response, request, out, "foo", "bar", "a", "b"); break;

      case 23: test(response, request, out); break;

      case 24: test(response, request, out, "foo", "bar", "a", "b"); break;

      case 25: test(response, request, out, "foo", "", "a", "b", "bar", ""); break;
      case 26: test(response, request, out, "foo", "", "a", "b", "bar", ""); break;
      case 27: test(response, request, out, "foo", "", "a", "b", "bar", ""); break;
      case 28: test(response, request, out, "foo", "", "a", "b", "bar", ""); break;

      case 29: test(response, request, out, "foo", "", "a", "b", "bar", ""); break;
      case 30: test(response, request, out, "foo", "", "a", "b", "bar", ""); break;
      case 31: test(response, request, out, "foo", "", "a", "b", "bar", "rab"); break;
      case 32: test(response, request, out, "foo", "", "a", "b", "bar", "rab"); break;

      case 33: test(response, request, out, "a", "b", "#", "", "bar", "rab"); break;

      case 34: test(response, request, out, "a", "b", "bar", "rab"); break;

      case 35: test(response, request, out, "foo", "bar", "a", "b"); break;

      case 36: test(response, request, out, 1); break;
      case 37: test(response, request, out, 0); break;

      /* JBAS-6766...
      case 38: test(response, request, out, "a", "=:", "foo", "b=:ar"); break;
      case 39: test(response, request, out, "a", ":", "foo", "b:ar"); break;
      case 40: test(response, request, out, "a", "=", "foo", "b=ar"); break;
       */
      case 38: test(response, request, out, "foo", "b"); break;
      case 39: test(response, request, out, "foo", "b"); break;
      case 40: test(response, request, out, "foo", "b"); break;

      default: sendError(response, "Unknown test");break;
    }
  } else if (action != null && action.compareTo("CREATE") == 0) {
    switch (ntest) {
      // case 1: out.println("OK");break;
      case 38: test(response, out, "a", "=:", "foo", "b=:ar"); break;
      case 39: test(response, out, "a", ":", "foo", "b:ar"); break;
      case 40: test(response, out, "a", "=", "foo", "b=ar"); break;

      case 41: test(response, out, "/", 0); break;
      case 42: test(response, out, "/", 1); break;

      default: sendError(response, "Unknown test");break;
    }
  } else {
    sendError(response, "Unknown command");
  }
%>
</BODY>
</HTML>
<%!
void test(HttpServletResponse response, HttpServletRequest request, JspWriter out, String name1, String val1, String name2, String val2, String name3, String val3) throws Exception {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            if (cookies.length != 3) {
                sendError(response, "Wrong number of cookies (3:" + cookies.length + ")");
                return;
            }
            if (name1.compareTo(cookies[0].getName()) == 0 &&
                val1.compareTo(cookies[0].getValue()) == 0 &&
                name2.compareTo(cookies[1].getName()) == 0 &&
                val2.compareTo(cookies[1].getValue()) == 0 &&
                name3.compareTo(cookies[2].getName()) == 0 &&
                val3.compareTo(cookies[2].getValue()) == 0 )
                out.println("OK");
            else {
                sendError(response, "Value or name don't match");
                return;
            }
        } else {
        sendError(response, "No cookies");
        }
   }
void test(HttpServletResponse response, HttpServletRequest request, JspWriter out, String name1, String val1, String name2, String val2) throws Exception {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            if (cookies.length != 2) {
                sendError(response, "Wrong number of cookies (2:" + cookies.length + ")");
                return;
            }
            if (name1.compareTo(cookies[0].getName()) == 0 &&
                val1.compareTo(cookies[0].getValue()) == 0 &&
                name2.compareTo(cookies[1].getName()) == 0 &&
                val2.compareTo(cookies[1].getValue()) == 0)
                out.println("OK");
            else {
                sendError(response, "Value or name don't match");
                return;
            }
        } else {
        sendError(response, "No cookies");
        }
   }
void test(HttpServletResponse response, HttpServletRequest request, JspWriter out, String name1, String val1) throws Exception {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            if (cookies.length != 1) {
                sendError(response, "Wrong number of cookies (1:" + cookies.length + ")");
                return;
            }
            if (name1.compareTo(cookies[0].getName()) == 0 &&
                val1.compareTo(cookies[0].getValue()) == 0 )
                out.println("OK");
            else {
                sendError(response, "Value or name don't match (got " + cookies[0].getName() + ":" + cookies[0].getValue());
                return;
            }
        } else {
        sendError(response, "No cookies");
        }
   }
void test(HttpServletResponse response, HttpServletRequest request, JspWriter out) throws Exception {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            if (cookies.length != 0) {
                sendError(response, "Wrong number of cookies (0:" + cookies.length + ")");
                return;
            }
        }
        out.println("OK");
   } 
void test(HttpServletResponse response, HttpServletRequest request, JspWriter out, int version) throws Exception {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            if (cookies.length == 1) {
                if (cookies[0].getVersion() == version) {
                    out.println("OK");
                    return;
                }
            }
        }
        sendError(response, "Wrong number of cookies");
   }
void sendError(HttpServletResponse response, String mess) throws Exception {
        response.setHeader("ERROR", mess);
        response.sendError(500, mess);
   }
void test(HttpServletResponse response, JspWriter out, String name1, String val1, String name2, String val2) throws Exception {
        Cookie cookie = new Cookie(name1, val1);
        response.addCookie(cookie);
        cookie = new Cookie(name2, val2); 
        response.addCookie(cookie);
        out.println("OK");
   }
void test(HttpServletResponse response, JspWriter out, String path, int version) throws Exception {
        Cookie cookie = new Cookie("a", "b");
        cookie.setVersion(version);
        cookie.setPath(path);
        response.addCookie(cookie);
        out.println("OK");
   }%>
