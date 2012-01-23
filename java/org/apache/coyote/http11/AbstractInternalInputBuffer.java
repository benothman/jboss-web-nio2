/**
 * JBoss, Home of Professional Open Source. Copyright 2012, Red Hat, Inc., and
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

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import org.apache.coyote.InputBuffer;
import org.apache.coyote.Request;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.res.StringManager;
import org.jboss.logging.Logger;

/**
 * {@code AbstractInternalInputBuffer}
 * 
 * Created on Jan 10, 2012 at 10:31:06 AM
 * 
 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
 */
public abstract class AbstractInternalInputBuffer implements InputBuffer {

	/**
	 * 
	 */
	protected static final Logger log = Logger.getLogger(AbstractInternalInputBuffer.class);

	/**
	 * The string manager for this package.
	 */
	protected static StringManager sm = StringManager.getManager(Constants.Package);

	/**
	 * Associated Coyote request.
	 */
	protected Request request;

	/**
	 * Headers of the associated request.
	 */
	protected MimeHeaders headers;

	/**
	 * State.
	 */
	protected boolean parsingHeader;

	/**
	 * Swallow input ? (in the case of an expectation)
	 */
	protected boolean swallowInput;

	/**
	 * Pointer to the current read buffer.
	 */
	protected byte[] buf;

	/**
	 * Direct byte buffer used to perform actual reading.
	 */
	protected ByteBuffer bbuf;

	/**
	 * Last valid byte.
	 */
	protected int lastValid;

	/**
	 * Position in the buffer.
	 */
	protected int pos;

	/**
	 * Position of the end of the header in the buffer, which is also the start
	 * of the body.
	 */
	protected int end;

	/**
	 * Filter library. Note: Filter[0] is always the "chunked" filter.
	 */
	protected InputFilter[] filterLibrary;

	/**
	 * Active filters (in order).
	 */
	protected InputFilter[] activeFilters;

	/**
	 * Index of the last active filter.
	 */
	protected int lastActiveFilter;

	/**
	 * Underlying input buffer.
	 */
	protected InputBuffer inputBuffer;

	/**
	 * Read timeout
	 */
	protected int readTimeout = -1;

	/**
	 * The default time unit
	 */
	protected static final TimeUnit unit = TimeUnit.MILLISECONDS;

	/**
	 * Create a new instance of {@code AbstractInternalInputBuffer}
	 * 
	 * @param request
	 */
	public AbstractInternalInputBuffer(Request request) {
		this(request, Constants.DEFAULT_HTTP_HEADER_BUFFER_SIZE);
	}

	/**
	 * Create a new instance of {@code AbstractInternalInputBuffer}
	 * 
	 * @param request
	 * @param headerBufferSize
	 */
	public AbstractInternalInputBuffer(Request request, int headerBufferSize) {
		this.request = request;
		headers = request.getMimeHeaders();
		buf = new byte[headerBufferSize];
		filterLibrary = new InputFilter[0];
		activeFilters = new InputFilter[0];
		lastActiveFilter = -1;
		parsingHeader = true;
		swallowInput = true;

		if (headerBufferSize < (8 * 1024)) {
			bbuf = ByteBuffer.allocateDirect(6 * 1500);
		} else {
			bbuf = ByteBuffer.allocateDirect((headerBufferSize / 1500 + 1) * 1500);
		}
	}

	/**
	 * Get filters.
	 * 
	 * @return the list of filters
	 */
	public InputFilter[] getFilters() {
		return filterLibrary;
	}

	/**
	 * Set the swallow input flag.
	 * 
	 * @param swallowInput
	 */
	public void setSwallowInput(boolean swallowInput) {
		this.swallowInput = swallowInput;
	}

	/**
	 * Add an input filter to the filter library.
	 * 
	 * @param filter
	 */
	public void addFilter(InputFilter filter) {
		if (filter == null) {
			return;
		}

		InputFilter[] newFilterLibrary = new InputFilter[filterLibrary.length + 1];
		for (int i = 0; i < filterLibrary.length; i++) {
			newFilterLibrary[i] = filterLibrary[i];
		}
		newFilterLibrary[filterLibrary.length] = filter;
		filterLibrary = newFilterLibrary;
		activeFilters = new InputFilter[filterLibrary.length];
	}

	/**
	 * Clear filters.
	 */
	public void clearFilters() {
		filterLibrary = new InputFilter[0];
		lastActiveFilter = -1;
	}

	/**
	 * Add an input filter to the filter library.
	 * 
	 * @param filter
	 */
	public void addActiveFilter(InputFilter filter) {

		if (lastActiveFilter == -1) {
			filter.setBuffer(inputBuffer);
		} else {
			for (int i = 0; i <= lastActiveFilter; i++) {
				if (activeFilters[i] == filter)
					return;
			}
			filter.setBuffer(activeFilters[lastActiveFilter]);
		}

		activeFilters[++lastActiveFilter] = filter;
		filter.setRequest(request);
	}

	/**
	 * Recycle the input buffer. This should be called when closing the
	 * connection.
	 */
	public void recycle() {
		// Recycle Request object
		request.recycle();
		lastValid = 0;
		reset();
	}

	/**
	 * 
	 */
	protected void reset() {
		pos = 0;
		lastActiveFilter = -1;
		parsingHeader = true;
		swallowInput = true;
	}

	/**
	 * End processing of current HTTP request. Note: All bytes of the current
	 * request should have been already consumed. This method only resets all
	 * the pointers so that we are ready to parse the next HTTP request.
	 * 
	 * @return <tt>true</tt> if the position of the last valid char is > 0, else
	 *         <tt>false</tt>
	 */
	public boolean nextRequest() {

		// Recycle Request object
		request.recycle();

		// Copy leftover bytes to the beginning of the buffer
		if (lastValid - pos > 0) {
			int npos = 0;
			int opos = pos;
			while (lastValid - opos > opos - npos) {
				System.arraycopy(buf, opos, buf, npos, opos - npos);
				npos += pos;
				opos += pos;
			}
			System.arraycopy(buf, opos, buf, npos, lastValid - opos);
		}

		// Recycle filters
		for (int i = 0; i <= lastActiveFilter; i++) {
			activeFilters[i].recycle();
		}

		// Reset pointers
		lastValid = lastValid - pos;
		reset();

		return (lastValid > 0);
	}

	/**
	 * End request (consumes leftover bytes).
	 * 
	 * @throws IOException
	 *             an undelying I/O error occured
	 */
	public void endRequest() throws IOException {

		if (swallowInput && (lastActiveFilter != -1)) {
			int extraBytes = (int) activeFilters[lastActiveFilter].end();
			pos = pos - extraBytes;
		}
	}

	/**
	 * Read the request line. This function is meant to be used during the HTTP
	 * request header parsing. Do NOT attempt to read the request body using it.
	 * 
	 * @throws IOException
	 *             If an exception occurs during the underlying socket read
	 *             operations, or if the given buffer is not big enough to
	 *             accomodate the whole line.
	 */
	public void parseRequestLine() throws IOException {

		int start = 0;
		// Skipping blank lines

		byte chr = 0;
		do {
			// Read new bytes if needed
			if (pos >= lastValid) {
				if (!fill())
					throw new EOFException(sm.getString("iib.eof.error"));
			}

			chr = buf[pos++];
		} while ((chr == Constants.CR) || (chr == Constants.LF));

		pos--;
		// Mark the current buffer position
		start = pos;

		// Reading the method name
		// Method name is always US-ASCII

		boolean space = false;

		while (!space) {

			// Read new bytes if needed
			if (pos >= lastValid) {
				if (!fill()) {
					throw new EOFException(sm.getString("iib.eof.error"));
				}
			}

			// Spec says single SP but it also says be tolerant of HT
			if (buf[pos] == Constants.SP || buf[pos] == Constants.HT) {
				space = true;
				request.method().setBytes(buf, start, pos - start);
			}

			pos++;
		}

		// Spec says single SP but also says be tolerant of multiple and/or HT
		while (space) {
			// Read new bytes if needed
			if (pos >= lastValid) {
				if (!fill()) {
					throw new EOFException(sm.getString("iib.eof.error"));
				}
			}
			if (buf[pos] == Constants.SP || buf[pos] == Constants.HT) {
				pos++;
			} else {
				space = false;
			}
		}

		// Mark the current buffer position
		start = pos;
		int end = 0;
		int questionPos = -1;

		// Reading the URI

		boolean eol = false;

		while (!space) {

			// Read new bytes if needed
			if (pos >= lastValid) {
				if (!fill()) {
					throw new EOFException(sm.getString("iib.eof.error"));
				}
			}

			// Spec says single SP but it also says be tolerant of HT
			if (buf[pos] == Constants.SP || buf[pos] == Constants.HT) {
				space = true;
				end = pos;
			} else if ((buf[pos] == Constants.CR) || (buf[pos] == Constants.LF)) {
				// HTTP/0.9 style request
				eol = true;
				space = true;
				end = pos;
			} else if ((buf[pos] == Constants.QUESTION) && (questionPos == -1)) {
				questionPos = pos;
			}

			pos++;
		}

		request.unparsedURI().setBytes(buf, start, end - start);
		if (questionPos >= 0) {
			request.queryString().setBytes(buf, questionPos + 1, end - questionPos - 1);
			request.requestURI().setBytes(buf, start, questionPos - start);
		} else {
			request.requestURI().setBytes(buf, start, end - start);
		}

		// Spec says single SP but also says be tolerant of multiple and/or HT
		while (space) {
			// Read new bytes if needed
			if (pos >= lastValid) {
				if (!fill()) {
					throw new EOFException(sm.getString("iib.eof.error"));
				}
			}
			if (buf[pos] == Constants.SP || buf[pos] == Constants.HT) {
				pos++;
			} else {
				space = false;
			}
		}

		// Mark the current buffer position
		start = pos;
		end = 0;

		// Reading the protocol
		// Protocol is always US-ASCII

		while (!eol) {

			// Read new bytes if needed
			if (pos >= lastValid) {
				if (!fill()) {
					throw new EOFException(sm.getString("iib.eof.error"));
				}
			}

			if (buf[pos] == Constants.CR) {
				end = pos;
			} else if (buf[pos] == Constants.LF) {
				if (end == 0)
					end = pos;
				eol = true;
			}

			pos++;
		}

		if ((end - start) > 0) {
			request.protocol().setBytes(buf, start, end - start);
		} else {
			request.protocol().setString("");
		}
	}

	/**
	 * Parse the HTTP headers.
	 * 
	 * @throws IOException
	 */
	public void parseHeaders() throws IOException {
		while (parseHeader()) {
		}

		parsingHeader = false;
		end = pos;
	}

	/**
	 * Parse an HTTP header.
	 * 
	 * @return false after reading a blank line (which indicates that the HTTP
	 *         header parsing is done
	 * @throws IOException
	 */
	public boolean parseHeader() throws IOException {
		// Check for blank line
		byte chr = 0;
		while (true) {
			// Read new bytes if needed
			if (pos >= lastValid) {
				if (!fill())
					throw new EOFException(sm.getString("iib.eof.error"));
			}

			chr = buf[pos];

			if ((chr == Constants.CR) || (chr == Constants.LF)) {
				if (chr == Constants.LF) {
					pos++;
					return false;
				}
			} else {
				break;
			}

			pos++;
		}

		// Mark the current buffer position
		int start = pos;

		// Reading the header name
		// Header name is always US-ASCII
		boolean colon = false;
		MessageBytes headerValue = null;

		while (!colon) {
			// Read new bytes if needed
			if (pos >= lastValid) {
				if (!fill()) {
					throw new EOFException(sm.getString("iib.eof.error"));
				}
			}

			if (buf[pos] == Constants.COLON) {
				colon = true;
				headerValue = headers.addValue(buf, start, pos - start);
			}
			chr = buf[pos];
			if ((chr >= Constants.A) && (chr <= Constants.Z)) {
				buf[pos] = (byte) (chr - Constants.LC_OFFSET);
			}

			pos++;
		}

		// Mark the current buffer position
		start = pos;
		int realPos = pos;

		// Reading the header value (which can be spanned over multiple lines)

		boolean eol = false;
		boolean validLine = true;

		while (validLine) {

			boolean space = true;

			// Skipping spaces
			while (space) {
				// Read new bytes if needed
				if (pos >= lastValid) {
					if (!fill()) {
						throw new EOFException(sm.getString("iib.eof.error"));
					}
				}

				if ((buf[pos] == Constants.SP) || (buf[pos] == Constants.HT)) {
					pos++;
				} else {
					space = false;
				}
			}

			int lastSignificantChar = realPos;

			// Reading bytes until the end of the line
			while (!eol) {

				// Read new bytes if needed
				if (pos >= lastValid) {
					if (!fill()) {
						throw new EOFException(sm.getString("iib.eof.error"));
					}
				}

				if (buf[pos] == Constants.CR) {
				} else if (buf[pos] == Constants.LF) {
					eol = true;
				} else if (buf[pos] == Constants.SP) {
					buf[realPos] = buf[pos];
					realPos++;
				} else {
					buf[realPos] = buf[pos];
					realPos++;
					lastSignificantChar = realPos;
				}

				pos++;
			}

			realPos = lastSignificantChar;

			// Checking the first character of the new line. If the character
			// is a LWS, then it's a multiline header

			// Read new bytes if needed
			if (pos >= lastValid) {
				if (!fill()) {
					throw new EOFException(sm.getString("iib.eof.error"));
				}
			}

			chr = buf[pos];
			if ((chr != Constants.SP) && (chr != Constants.HT)) {
				validLine = false;
			} else {
				eol = false;
				// Copying one extra space in the buffer (since there must
				// be at least one space inserted between the lines)
				buf[realPos] = chr;
				realPos++;
			}
		}

		// Set the header value
		headerValue.setBytes(buf, start, realPos - start);

		return true;
	}

	/**
	 * Fill the internal buffer using data from the undelying input stream.
	 * 
	 * @return false if at end of stream
	 */
	protected abstract boolean fill() throws IOException;

}
