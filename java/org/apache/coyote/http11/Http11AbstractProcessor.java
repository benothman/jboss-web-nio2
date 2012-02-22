/**
 * JBoss, Home of Professional Open Source. Copyright 2011, Red Hat, Inc., and
 * individual contributors as indicated by the @author tags. See the
 * copyright.txt file in the distribution for a full listing of individual
 * contributors.
 * 
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 * 
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 * site: http://www.fsf.org.
 */
package org.apache.coyote.http11;

import java.util.StringTokenizer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.coyote.ActionHook;
import org.apache.coyote.Adapter;
import org.apache.coyote.Request;
import org.apache.coyote.Response;
import org.apache.tomcat.util.buf.Ascii;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.res.StringManager;

/**
 * {@code Http11AbstractProcessor}
 * 
 * Created on Dec 19, 2011 at 2:35:14 PM
 * 
 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
 */
public abstract class Http11AbstractProcessor implements ActionHook {

	/**
	 * Logger.
	 */
	protected static org.jboss.logging.Logger log = org.jboss.logging.Logger
			.getLogger(Http11Processor.class);

	/**
	 * The string manager for this package.
	 */
	protected static StringManager sm = StringManager.getManager(Constants.Package);

	protected static final boolean CHUNK_ON_CLOSE = Boolean.valueOf(
			System.getProperty("org.apache.coyote.http11.Http11Processor.CHUNK_ON_CLOSE", "false"))
			.booleanValue();

	/**
	 * Thread local marker.
	 */
	public static ThreadLocal<Boolean> containerThread = new ThreadLocal<Boolean>();

	/**
	 * Associated adapter.
	 */
	protected Adapter adapter = null;

	/**
	 * Request object.
	 */
	protected Request request = null;

	/**
	 * Response object.
	 */
	protected Response response = null;
	/**
	 * Error flag.
	 */
	protected boolean error = false;

	/**
	 * Keep-alive.
	 */
	protected boolean keepAlive = true;

	/**
	 * HTTP/1.1 flag.
	 */
	protected boolean http11 = true;

	/**
	 * HTTP/0.9 flag.
	 */
	protected boolean http09 = false;

	/**
	 * Content delimitator for the request (if false, the connection will be
	 * closed at the end of the request).
	 */
	protected boolean contentDelimitation = true;

	/**
	 * Is there an expectation ?
	 */
	protected boolean expectation = false;

	/**
	 * List of restricted user agents.
	 */
	protected Pattern[] restrictedUserAgents = null;

	/**
	 * Maximum number of Keep-Alive requests to honor.
	 */
	protected int maxKeepAliveRequests = -1;

	/**
	 * The number of seconds Tomcat will wait for a subsequent request before
	 * closing the connection.
	 */
	protected int keepAliveTimeout = -1;

	/**
	 * SSL information.
	 */
	protected SSLSupport sslSupport;

	/**
	 * Remote Address associated with the current connection.
	 */
	protected String remoteAddr = null;

	/**
	 * Remote Host associated with the current connection.
	 */
	protected String remoteHost = null;

	/**
	 * Local Host associated with the current connection.
	 */
	protected String localName = null;

	/**
	 * Local port to which the socket is connected
	 */
	protected int localPort = -1;

	/**
	 * Remote port to which the socket is connected
	 */
	protected int remotePort = -1;

	/**
	 * The local Host address.
	 */
	protected String localAddr = null;

	/**
	 * Flag to disable setting a different time-out on uploads.
	 */
	protected boolean disableUploadTimeout = false;

	/**
	 * Allowed compression level.
	 */
	protected int compressionLevel = 0;

	/**
	 * Minimum contentsize to make compression.
	 */
	protected int compressionMinSize = 2048;

	/**
	 * Max saved post size.
	 */
	protected int maxSavePostSize = 4 * 1024;

	/**
	 * List of user agents to not use gzip with
	 */
	protected Pattern noCompressionUserAgents[] = null;

	/**
	 * List of MIMES which could be gzipped
	 */
	protected String[] compressableMimeTypes = { "text/html", "text/xml", "text/plain" };

	/**
	 * Host name (used to avoid useless B2C conversion on the host name).
	 */
	protected char[] hostNameC = new char[0];

	/**
	 * Allow a customized the server header for the tin-foil hat folks.
	 */
	protected String server = null;

	protected boolean sslEnabled;

	/**
	 * Event used.
	 */
	protected boolean event = false;

	/**
	 * True if a resume has been requested.
	 */
	protected boolean resumeNotification = false;
	protected boolean readNotifications = true;
	protected boolean writeNotification = false;

	/**
	 * Event processing.
	 */
	protected boolean eventProcessing = true;

	/**
	 * Timeout.
	 */
	protected int timeout = -1;

	/**
	 * @return compression level.
	 */
	public String getCompression() {
		switch (compressionLevel) {
		case 0:
			return "off";
		case 1:
			return "on";
		case 2:
			return "force";
		}
		return "off";
	}

	/**
	 * Set compression level.
	 * 
	 * @param compression
	 */
	public void setCompression(String compression) {
		if (compression.equals("on")) {
			this.compressionLevel = 1;
		} else if (compression.equals("force")) {
			this.compressionLevel = 2;
		} else if (compression.equals("off")) {
			this.compressionLevel = 0;
		} else {
			try {
				// Try to parse compression as an int, which would give the
				// minimum compression size
				compressionMinSize = Integer.parseInt(compression);
				this.compressionLevel = 1;
			} catch (Exception e) {
				this.compressionLevel = 0;
			}
		}
	}

	/**
	 * Add user-agent for which gzip compression didn't works The user agent
	 * String given will be exactly matched to the user-agent header submitted
	 * by the client.
	 * 
	 * @param userAgent
	 *            user-agent string
	 */
	public void addNoCompressionUserAgent(String userAgent) {
		try {
			Pattern nRule = Pattern.compile(userAgent);
			noCompressionUserAgents = addREArray(noCompressionUserAgents, nRule);
		} catch (PatternSyntaxException pse) {
			log.error(sm.getString("http11processor.regexp.error", userAgent), pse);
		}
	}

	/**
	 * Set no compression user agent list. List contains users agents separated
	 * by ',' :
	 * 
	 * ie: "gorilla,desesplorer,tigrus"
	 * 
	 * @param noCompressionUserAgents
	 */
	public void setNoCompressionUserAgents(String noCompressionUserAgents) {
		if (noCompressionUserAgents != null) {
			StringTokenizer st = new StringTokenizer(noCompressionUserAgents, ",");

			while (st.hasMoreTokens()) {
				addNoCompressionUserAgent(st.nextToken().trim());
			}
		}
	}

	/**
	 * Add a mime-type which will be compressable The mime-type String will be
	 * exactly matched in the response mime-type header .
	 * 
	 * @param mimeType
	 *            mime-type string
	 */
	public void addCompressableMimeType(String mimeType) {
		compressableMimeTypes = addStringArray(compressableMimeTypes, mimeType);
	}

	/**
	 * Set compressable mime-type list (this method is best when used with a
	 * large number of connectors, where it would be better to have all of them
	 * referenced a single array).
	 * 
	 * @param compressableMimeTypes
	 */
	public void setCompressableMimeTypes(String[] compressableMimeTypes) {
		this.compressableMimeTypes = compressableMimeTypes;
	}

	/**
	 * Set compressable mime-type list List contains users agents separated by
	 * ',' :
	 * 
	 * ie: "text/html,text/xml,text/plain"
	 * 
	 * @param compressableMimeTypes
	 */
	public void setCompressableMimeTypes(String compressableMimeTypes) {
		if (compressableMimeTypes != null) {
			this.compressableMimeTypes = null;
			StringTokenizer st = new StringTokenizer(compressableMimeTypes, ",");
			while (st.hasMoreTokens()) {
				addCompressableMimeType(st.nextToken().trim());
			}
		}
	}

	/**
	 * @return the list of restricted user agents.
	 */
	public String[] findCompressableMimeTypes() {
		return (compressableMimeTypes);
	}

	/**
	 * Add input or output filter.
	 * 
	 * @param className
	 *            class name of the filter
	 */
	protected abstract void addFilter(String className);

	/**
	 * General use method
	 * 
	 * @param sArray
	 *            the StringArray
	 * @param value
	 *            string
	 */
	protected String[] addStringArray(String sArray[], String value) {
		String[] result = null;
		if (sArray == null) {
			result = new String[1];
			result[0] = value;
		} else {
			result = new String[sArray.length + 1];
			for (int i = 0; i < sArray.length; i++)
				result[i] = sArray[i];
			result[sArray.length] = value;
		}
		return result;
	}

	/**
	 * General use method
	 * 
	 * @param rArray
	 *            the REArray
	 * @param value
	 *            Obj
	 */
	protected Pattern[] addREArray(Pattern rArray[], Pattern value) {
		Pattern[] result = null;
		if (rArray == null) {
			result = new Pattern[1];
			result[0] = value;
		} else {
			result = new Pattern[rArray.length + 1];
			for (int i = 0; i < rArray.length; i++)
				result[i] = rArray[i];
			result[rArray.length] = value;
		}
		return result;
	}

	/**
	 * Checks if any entry in the string array starts with the specified value
	 * 
	 * @param sArray
	 *            the StringArray
	 * @param value
	 *            string
	 */
	protected boolean startsWithStringArray(String sArray[], String value) {
		if (value == null)
			return false;
		for (int i = 0; i < sArray.length; i++) {
			if (value.startsWith(sArray[i])) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Add restricted user-agent (which will downgrade the connector to HTTP/1.0
	 * mode). The user agent String given will be matched via regexp to the
	 * user-agent header submitted by the client.
	 * 
	 * @param userAgent
	 *            user-agent string
	 */
	public void addRestrictedUserAgent(String userAgent) {
		try {
			Pattern nRule = Pattern.compile(userAgent);
			restrictedUserAgents = addREArray(restrictedUserAgents, nRule);
		} catch (PatternSyntaxException pse) {
			log.error(sm.getString("http11processor.regexp.error", userAgent), pse);
		}
	}

	/**
	 * Set restricted user agent list (which will downgrade the connector to
	 * HTTP/1.0 mode). List contains users agents separated by ',' :
	 * 
	 * ie: "gorilla,desesplorer,tigrus"
	 * 
	 * @param restrictedUserAgents
	 */
	public void setRestrictedUserAgents(String restrictedUserAgents) {
		if (restrictedUserAgents != null) {
			StringTokenizer st = new StringTokenizer(restrictedUserAgents, ",");
			while (st.hasMoreTokens()) {
				addRestrictedUserAgent(st.nextToken().trim());
			}
		}
	}

	/**
	 * @return the list of restricted user agents.
	 */
	public String[] findRestrictedUserAgents() {
		String[] sarr = new String[restrictedUserAgents.length];

		for (int i = 0; i < restrictedUserAgents.length; i++)
			sarr[i] = restrictedUserAgents[i].toString();

		return (sarr);
	}

	/**
	 * Set the server header name.
	 * 
	 * @param server
	 */
	public void setServer(String server) {
		if (server == null || server.equals("")) {
			this.server = null;
		} else {
			this.server = server;
		}
	}

	/**
     * 
     */
	public abstract void endRequest();

	/**
     * 
     */
	public abstract void recycle();

	/**
	 * Check for compression
	 */
	protected boolean isCompressable() {

		// Nope Compression could works in HTTP 1.0 also
		// cf: mod_deflate

		// Compression only since HTTP 1.1
		// if (! http11)
		// return false;

		// Check if browser support gzip encoding
		MessageBytes acceptEncodingMB = request.getMimeHeaders().getValue("accept-encoding");

		if ((acceptEncodingMB == null) || (acceptEncodingMB.indexOf("gzip") == -1))
			return false;

		// Check if content is not allready gzipped
		MessageBytes contentEncodingMB = response.getMimeHeaders().getValue("Content-Encoding");

		if ((contentEncodingMB != null) && (contentEncodingMB.indexOf("gzip") != -1))
			return false;

		// If force mode, allways compress (test purposes only)
		if (compressionLevel == 2)
			return true;

		// Check for incompatible Browser
		if (noCompressionUserAgents != null) {
			MessageBytes userAgentValueMB = request.getMimeHeaders().getValue("user-agent");
			if (userAgentValueMB != null) {
				String userAgentValue = userAgentValueMB.toString();

				// If one Regexp rule match, disable compression
				for (int i = 0; i < noCompressionUserAgents.length; i++)
					if (noCompressionUserAgents[i].matcher(userAgentValue).matches())
						return false;
			}
		}

		// Check if suffisant len to trig the compression
		long contentLength = response.getContentLengthLong();
		if ((contentLength == -1) || (contentLength > compressionMinSize)) {
			// Check for compatible MIME-TYPE
			if (compressableMimeTypes != null) {
				return (startsWithStringArray(compressableMimeTypes, response.getContentType()));
			}
		}

		return false;
	}

	/**
	 * After reading the request headers, we have to setup the request filters.
	 */
	protected abstract void prepareRequest();

	/**
	 * When committing the response, we have to validate the set of headers, as
	 * well as setup the response filters.
	 */
	protected abstract void prepareResponse();

	/**
	 * 
	 */
	protected abstract void initializeFilters();

	/**
	 * Reset flags of the Processor
	 */
	protected void reset() {
		// Set the remote address
		remoteAddr = null;
		remoteHost = null;
		localAddr = null;
		localName = null;
		remotePort = -1;
		localPort = -1;

		// Error flag
		error = false;
		event = false;
		keepAlive = true;
	}
	
	
	/**
	 * Determine if we must drop the connection because of the HTTP status code.
	 * Use the same list of codes as Apache/httpd.
	 */
	protected boolean statusDropsConnection(int status) {
		return status == 400 /* SC_BAD_REQUEST */|| status == 408 /* SC_REQUEST_TIMEOUT */
				|| status == 411 /* SC_LENGTH_REQUIRED */|| status == 413 /* SC_REQUEST_ENTITY_TOO_LARGE */
				|| status == 414 /* SC_REQUEST_URI_TOO_LARGE */|| status == 500 /* SC_INTERNAL_SERVER_ERROR */
				|| status == 503 /* SC_SERVICE_UNAVAILABLE */|| status == 501 /* SC_NOT_IMPLEMENTED */;
	}

	/**
	 * Specialized utility method: find a sequence of lower case bytes inside a
	 * ByteChunk.
	 */
	protected int findBytes(ByteChunk bc, byte[] b) {

		byte first = b[0];
		byte[] buff = bc.getBuffer();
		int start = bc.getStart();
		int end = bc.getEnd();

		// Look for first char
		int srcEnd = b.length;

		for (int i = start; i <= (end - srcEnd); i++) {
			if (Ascii.toLower(buff[i]) != first)
				continue;
			// found first char, now look for a match
			int myPos = i + 1;
			for (int srcPos = 1; srcPos < srcEnd;) {
				if (Ascii.toLower(buff[myPos++]) != b[srcPos++])
					break;
				if (srcPos == srcEnd)
					return i - start; // found it
			}
		}
		return -1;

	}

	/**
	 * Getter for adapter
	 * 
	 * @return the adapter
	 */
	public Adapter getAdapter() {
		return this.adapter;
	}

	/**
	 * Setter for the adapter
	 * 
	 * @param adapter
	 *            the adapter to set
	 */
	public void setAdapter(Adapter adapter) {
		this.adapter = adapter;
	}

	/**
	 * Getter for request
	 * 
	 * @return the request
	 */
	public Request getRequest() {
		return this.request;
	}

	/**
	 * Setter for the request
	 * 
	 * @param request
	 *            the request to set
	 */
	public void setRequest(Request request) {
		this.request = request;
	}

	/**
	 * Getter for response
	 * 
	 * @return the response
	 */
	public Response getResponse() {
		return this.response;
	}

	/**
	 * Setter for the response
	 * 
	 * @param response
	 *            the response to set
	 */
	public void setResponse(Response response) {
		this.response = response;
	}

	/**
	 * Getter for error
	 * 
	 * @return the error
	 */
	public boolean isError() {
		return this.error;
	}

	/**
	 * Setter for the error
	 * 
	 * @param error
	 *            the error to set
	 */
	public void setError(boolean error) {
		this.error = error;
	}

	/**
	 * Getter for keepAlive
	 * 
	 * @return the keepAlive
	 */
	public boolean isKeepAlive() {
		return this.keepAlive;
	}

	/**
	 * Setter for the keepAlive
	 * 
	 * @param keepAlive
	 *            the keepAlive to set
	 */
	public void setKeepAlive(boolean keepAlive) {
		this.keepAlive = keepAlive;
	}

	/**
	 * Getter for http11
	 * 
	 * @return the http11
	 */
	public boolean isHttp11() {
		return this.http11;
	}

	/**
	 * Setter for the http11
	 * 
	 * @param http11
	 *            the http11 to set
	 */
	public void setHttp11(boolean http11) {
		this.http11 = http11;
	}

	/**
	 * Getter for http09
	 * 
	 * @return the http09
	 */
	public boolean isHttp09() {
		return this.http09;
	}

	/**
	 * Setter for the http09
	 * 
	 * @param http09
	 *            the http09 to set
	 */
	public void setHttp09(boolean http09) {
		this.http09 = http09;
	}

	/**
	 * Getter for contentDelimitation
	 * 
	 * @return the contentDelimitation
	 */
	public boolean isContentDelimitation() {
		return this.contentDelimitation;
	}

	/**
	 * Setter for the contentDelimitation
	 * 
	 * @param contentDelimitation
	 *            the contentDelimitation to set
	 */
	public void setContentDelimitation(boolean contentDelimitation) {
		this.contentDelimitation = contentDelimitation;
	}

	/**
	 * Getter for expectation
	 * 
	 * @return the expectation
	 */
	public boolean isExpectation() {
		return this.expectation;
	}

	/**
	 * Setter for the expectation
	 * 
	 * @param expectation
	 *            the expectation to set
	 */
	public void setExpectation(boolean expectation) {
		this.expectation = expectation;
	}

	/**
	 * Getter for restrictedUserAgents
	 * 
	 * @return the restrictedUserAgents
	 */
	public Pattern[] getRestrictedUserAgents() {
		return this.restrictedUserAgents;
	}

	/**
	 * Setter for the restrictedUserAgents
	 * 
	 * @param restrictedUserAgents
	 *            the restrictedUserAgents to set
	 */
	public void setRestrictedUserAgents(Pattern[] restrictedUserAgents) {
		this.restrictedUserAgents = restrictedUserAgents;
	}

	/**
	 * Getter for maxKeepAliveRequests
	 * 
	 * @return the maxKeepAliveRequests
	 */
	public int getMaxKeepAliveRequests() {
		return this.maxKeepAliveRequests;
	}

	/**
	 * Setter for the maxKeepAliveRequests
	 * 
	 * @param maxKeepAliveRequests
	 *            the maxKeepAliveRequests to set
	 */
	public void setMaxKeepAliveRequests(int maxKeepAliveRequests) {
		this.maxKeepAliveRequests = maxKeepAliveRequests;
	}

	/**
	 * Getter for keepAliveTimeout
	 * 
	 * @return the keepAliveTimeout
	 */
	public int getKeepAliveTimeout() {
		return this.keepAliveTimeout;
	}

	/**
	 * Setter for the keepAliveTimeout
	 * 
	 * @param keepAliveTimeout
	 *            the keepAliveTimeout to set
	 */
	public void setKeepAliveTimeout(int keepAliveTimeout) {
		this.keepAliveTimeout = keepAliveTimeout;
	}

	/**
	 * Getter for sslSupport
	 * 
	 * @return the sslSupport
	 */
	public SSLSupport getSslSupport() {
		return this.sslSupport;
	}

	/**
	 * Setter for the sslSupport
	 * 
	 * @param sslSupport
	 *            the sslSupport to set
	 */
	public void setSslSupport(SSLSupport sslSupport) {
		this.sslSupport = sslSupport;
	}

	/**
	 * Getter for remoteAddr
	 * 
	 * @return the remoteAddr
	 */
	public String getRemoteAddr() {
		return this.remoteAddr;
	}

	/**
	 * Setter for the remoteAddr
	 * 
	 * @param remoteAddr
	 *            the remoteAddr to set
	 */
	public void setRemoteAddr(String remoteAddr) {
		this.remoteAddr = remoteAddr;
	}

	/**
	 * Getter for remoteHost
	 * 
	 * @return the remoteHost
	 */
	public String getRemoteHost() {
		return this.remoteHost;
	}

	/**
	 * Setter for the remoteHost
	 * 
	 * @param remoteHost
	 *            the remoteHost to set
	 */
	public void setRemoteHost(String remoteHost) {
		this.remoteHost = remoteHost;
	}

	/**
	 * Getter for localName
	 * 
	 * @return the localName
	 */
	public String getLocalName() {
		return this.localName;
	}

	/**
	 * Setter for the localName
	 * 
	 * @param localName
	 *            the localName to set
	 */
	public void setLocalName(String localName) {
		this.localName = localName;
	}

	/**
	 * Getter for localPort
	 * 
	 * @return the localPort
	 */
	public int getLocalPort() {
		return this.localPort;
	}

	/**
	 * Setter for the localPort
	 * 
	 * @param localPort
	 *            the localPort to set
	 */
	public void setLocalPort(int localPort) {
		this.localPort = localPort;
	}

	/**
	 * Getter for remotePort
	 * 
	 * @return the remotePort
	 */
	public int getRemotePort() {
		return this.remotePort;
	}

	/**
	 * Setter for the remotePort
	 * 
	 * @param remotePort
	 *            the remotePort to set
	 */
	public void setRemotePort(int remotePort) {
		this.remotePort = remotePort;
	}

	/**
	 * Getter for localAddr
	 * 
	 * @return the localAddr
	 */
	public String getLocalAddr() {
		return this.localAddr;
	}

	/**
	 * Setter for the localAddr
	 * 
	 * @param localAddr
	 *            the localAddr to set
	 */
	public void setLocalAddr(String localAddr) {
		this.localAddr = localAddr;
	}

	/**
	 * Getter for disableUploadTimeout
	 * 
	 * @return the disableUploadTimeout
	 */
	public boolean getDisableUploadTimeout() {
		return this.disableUploadTimeout;
	}

	/**
	 * Setter for the disableUploadTimeout
	 * 
	 * @param disableUploadTimeout
	 *            the disableUploadTimeout to set
	 */
	public void setDisableUploadTimeout(boolean disableUploadTimeout) {
		this.disableUploadTimeout = disableUploadTimeout;
	}

	/**
	 * Getter for compressionLevel
	 * 
	 * @return the compressionLevel
	 */
	public int getCompressionLevel() {
		return this.compressionLevel;
	}

	/**
	 * Setter for the compressionLevel
	 * 
	 * @param compressionLevel
	 *            the compressionLevel to set
	 */
	public void setCompressionLevel(int compressionLevel) {
		this.compressionLevel = compressionLevel;
	}

	/**
	 * Getter for compressionMinSize
	 * 
	 * @return the compressionMinSize
	 */
	public int getCompressionMinSize() {
		return this.compressionMinSize;
	}

	/**
	 * Setter for the compressionMinSize
	 * 
	 * @param compressionMinSize
	 *            the compressionMinSize to set
	 */
	public void setCompressionMinSize(int compressionMinSize) {
		this.compressionMinSize = compressionMinSize;
	}

	/**
	 * Getter for maxSavePostSize
	 * 
	 * @return the maxSavePostSize
	 */
	public int getMaxSavePostSize() {
		return this.maxSavePostSize;
	}

	/**
	 * Setter for the maxSavePostSize
	 * 
	 * @param maxSavePostSize
	 *            the maxSavePostSize to set
	 */
	public void setMaxSavePostSize(int maxSavePostSize) {
		this.maxSavePostSize = maxSavePostSize;
	}

	/**
	 * Getter for noCompressionUserAgents
	 * 
	 * @return the noCompressionUserAgents
	 */
	public Pattern[] getNoCompressionUserAgents() {
		return this.noCompressionUserAgents;
	}

	/**
	 * Setter for the noCompressionUserAgents
	 * 
	 * @param noCompressionUserAgents
	 *            the noCompressionUserAgents to set
	 */
	public void setNoCompressionUserAgents(Pattern[] noCompressionUserAgents) {
		this.noCompressionUserAgents = noCompressionUserAgents;
	}

	/**
	 * Getter for hostNameC
	 * 
	 * @return the hostNameC
	 */
	public char[] getHostNameC() {
		return this.hostNameC;
	}

	/**
	 * Setter for the hostNameC
	 * 
	 * @param hostNameC
	 *            the hostNameC to set
	 */
	public void setHostNameC(char[] hostNameC) {
		this.hostNameC = hostNameC;
	}

	/**
	 * Getter for event
	 * 
	 * @return the event
	 */
	public boolean isEvent() {
		return this.event;
	}

	/**
	 * Setter for the event
	 * 
	 * @param event
	 *            the event to set
	 */
	public void setEvent(boolean event) {
		this.event = event;
	}

	/**
	 * Getter for resumeNotification
	 * 
	 * @return the resumeNotification
	 */
	public boolean getResumeNotification() {
		return this.resumeNotification;
	}

	/**
	 * Setter for the resumeNotification
	 * 
	 * @param resumeNotification
	 *            the resumeNotification to set
	 */
	public void setResumeNotification(boolean resumeNotification) {
		this.resumeNotification = resumeNotification;
	}

	/**
	 * Getter for eventProcessing
	 * 
	 * @return the eventProcessing
	 */
	public boolean getEventProcessing() {
		return this.eventProcessing;
	}

	/**
	 * Setter for the eventProcessing
	 * 
	 * @param eventProcessing
	 *            the eventProcessing to set
	 */
	public void setEventProcessing(boolean eventProcessing) {
		this.eventProcessing = eventProcessing;
	}

	/**
	 * Getter for timeout
	 * 
	 * @return the timeout
	 */
	public int getTimeout() {
		return this.timeout;
	}

	/**
	 * Setter for the timeout
	 * 
	 * @param timeout
	 *            the timeout to set
	 */
	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	/**
	 * Getter for compressableMimeTypes
	 * 
	 * @return the compressableMimeTypes
	 */
	public String[] getCompressableMimeTypes() {
		return this.compressableMimeTypes;
	}

	/**
	 * Getter for server
	 * 
	 * @return the server
	 */
	public String getServer() {
		return this.server;
	}

	/**
	 * Getter for readNotifications
	 * 
	 * @return the readNotifications
	 */
	public boolean getReadNotifications() {
		return this.readNotifications;
	}

	/**
	 * Setter for the readNotifications
	 * 
	 * @param readNotifications
	 *            the readNotifications to set
	 */
	public void setReadNotifications(boolean readNotifications) {
		this.readNotifications = readNotifications;
	}

	/**
	 * Getter for writeNotification
	 * 
	 * @return the writeNotification
	 */
	public boolean getWriteNotification() {
		return this.writeNotification;
	}

	/**
	 * Setter for the writeNotification
	 * 
	 * @param writeNotification
	 *            the writeNotification to set
	 */
	public void setWriteNotification(boolean writeNotification) {
		this.writeNotification = writeNotification;
	}

	/**
	 * Getter for sslEnabled
	 * 
	 * @return the sslEnabled
	 */
	public boolean getSSLEnabled() {
		return this.sslEnabled;
	}

	/**
	 * Setter for the sslEnabled
	 * 
	 * @param sslEnabled
	 *            the sslEnabled to set
	 */
	public void setSSLEnabled(boolean sslEnabled) {
		this.sslEnabled = sslEnabled;
	}
}
