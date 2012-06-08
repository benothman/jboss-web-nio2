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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import org.apache.coyote.ActionCode;
import org.apache.coyote.OutputBuffer;
import org.apache.coyote.Response;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.CharChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.HttpMessages;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.res.StringManager;
import org.jboss.logging.Logger;

/**
 * {@code AbstractInternalOutputBuffer}
 * 
 * Created on Jan 10, 2012 at 12:08:51 PM
 * 
 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
 */
public abstract class AbstractInternalOutputBuffer implements OutputBuffer {

	/**
	 * 
	 */
	protected static final Logger log = Logger.getLogger(AbstractInternalOutputBuffer.class);

	/**
	 * The string manager for this package.
	 */
	protected static StringManager sm = StringManager.getManager(Constants.Package);

	/**
	 * Associated Coyote response.
	 */
	protected Response response;

	/**
	 * Headers of the associated request.
	 */
	protected MimeHeaders headers;

	/**
	 * Committed flag.
	 */
	protected boolean committed;

	/**
	 * Finished flag.
	 */
	protected boolean finished;

	/**
	 * Pointer to the current write buffer.
	 */
	protected byte[] buf;

	/**
	 * Position in the buffer.
	 */
	protected int pos;

	/**
	 * Underlying output buffer.
	 */
	protected OutputBuffer outputBuffer;

	/**
	 * Filter library. Note: Filter[0] is always the "chunked" filter.
	 */
	protected OutputFilter[] filterLibrary;

	/**
	 * Active filter (which is actually the top of the pipeline).
	 */
	protected OutputFilter[] activeFilters;

	/**
	 * Index of the last active filter.
	 */
	protected int lastActiveFilter;

	/**
	 * Direct byte buffer used for writing.
	 */
	protected ByteBuffer bbuf = null;

	/**
	 * Leftover bytes which could not be written during a non blocking write.
	 */
	protected ByteChunk leftover = null;

	/**
	 * Non blocking mode.
	 */
	protected boolean nonBlocking = false;

	/**
	 * Write timeout
	 */
	protected int writeTimeout = -1;

	/**
	 * Create a new instance of {@code AbstractInternalOutputBuffer}
	 * 
	 * @param response
	 * @param headerBufferSize
	 */
	public AbstractInternalOutputBuffer(Response response, int headerBufferSize) {

		this.response = response;
		this.headers = response.getMimeHeaders();
		buf = new byte[headerBufferSize];
		if (headerBufferSize < Constants.MIN_BUFFER_SIZE) {
			bbuf = ByteBuffer.allocateDirect(6 * 1500);
		} else {
			bbuf = ByteBuffer.allocateDirect((headerBufferSize / 1500 + 1) * 1500);
		}

		outputBuffer = new OutputBufferImpl();
		filterLibrary = new OutputFilter[0];
		activeFilters = new OutputFilter[0];
		lastActiveFilter = -1;

		committed = false;
		finished = false;

		leftover = new ByteChunk();
		nonBlocking = false;

		// Cause loading of HttpMessages
		HttpMessages.getMessage(200);
	}

	/**
	 * Initialize the internal output buffer
	 */
	protected abstract void init();

	/**
	 * Set the non blocking flag.
	 * 
	 * @param nonBlocking
	 */
	public void setNonBlocking(boolean nonBlocking) {
		this.nonBlocking = nonBlocking;
	}

	/**
	 * Get the non blocking flag value.
	 * 
	 * @return non blocking
	 */
	public boolean getNonBlocking() {
		return nonBlocking;
	}

	/**
	 * Add an output filter to the filter library.
	 * 
	 * @param filter
	 */
	public void addFilter(OutputFilter filter) {

		OutputFilter[] newFilterLibrary = new OutputFilter[filterLibrary.length + 1];
		for (int i = 0; i < filterLibrary.length; i++) {
			newFilterLibrary[i] = filterLibrary[i];
		}
		newFilterLibrary[filterLibrary.length] = filter;
		filterLibrary = newFilterLibrary;
		activeFilters = new OutputFilter[filterLibrary.length];
	}

	/**
	 * Get filters.
	 * 
	 * @return the list of filters
	 */
	public OutputFilter[] getFilters() {
		return filterLibrary;
	}

	/**
	 * Clear filters.
	 */
	public void clearFilters() {
		filterLibrary = new OutputFilter[0];
		lastActiveFilter = -1;
	}

	/**
	 * Perform a write operation. The operation may be blocking or non-blocking
	 * depending on the value of {@code nonBlocking} flag.
	 * 
	 * @param timeout
	 *            a timeout for the operation
	 * @param unit
	 *            The time unit of the timeout
	 * @return
	 */
	protected abstract int write(final long timeout, final TimeUnit unit);

	/**
	 * Add an output filter to the filter library.
	 * 
	 * @param filter
	 */
	public void addActiveFilter(OutputFilter filter) {

		if (lastActiveFilter == -1) {
			filter.setBuffer(outputBuffer);
		} else {
			for (int i = 0; i <= lastActiveFilter; i++) {
				if (activeFilters[i] == filter)
					return;
			}
			filter.setBuffer(activeFilters[lastActiveFilter]);
		}

		activeFilters[++lastActiveFilter] = filter;
		filter.setResponse(response);
	}

	/**
	 * Flush the response.
	 * 
	 * @throws IOException
	 *             an undelying I/O error occured
	 */
	public void flush() throws IOException {
		if (!committed) {

			// Send the connector a request for commit. The connector should
			// then validate the headers, send them (using sendHeader) and
			// set the filters accordingly.
			response.action(ActionCode.ACTION_COMMIT, null);
		}

		// Flush the current buffer
		flushBuffer();
	}

	/**
	 * 
	 */
	protected void clearBuffer() {
		synchronized (this.bbuf) {
			this.bbuf.clear();
		}
	}

	/**
	 * Recycle this object
	 */
	public void recycle() {
		// Recycle Request object
		response.recycle();
		this.clearBuffer();
		pos = 0;
		lastActiveFilter = -1;
		committed = false;
		finished = false;
	}

	/**
	 * End processing of current HTTP request. Note: All bytes of the current
	 * request should have been already consumed. This method only resets all
	 * the pointers so that we are ready to parse the next HTTP request.
	 */
	public void nextRequest() {
		// Recycle Request object
		response.recycle();

		// Recycle filters
		for (int i = 0; i <= lastActiveFilter; i++) {
			activeFilters[i].recycle();
		}

		// Reset pointers
		leftover.recycle();
		pos = 0;
		lastActiveFilter = -1;
		committed = false;
		finished = false;
		nonBlocking = false;
	}

	/**
	 * End request.
	 * 
	 * @throws IOException
	 *             an undelying I/O error occured
	 */
	public void endRequest() throws IOException {

		if (!committed) {
			// Send the connector a request for commit. The connector should
			// then validate the headers, send them (using sendHeader) and
			// set the filters accordingly.
			response.action(ActionCode.ACTION_COMMIT, null);
		}

		if (finished) {
			return;
		}

		if (lastActiveFilter != -1) {
			activeFilters[lastActiveFilter].end();
		}

		flushBuffer();
		finished = true;
	}

	// ------------------------------------------------ HTTP/1.1 Output Methods

	/**
	 * Send an acknowledgment.
	 * 
	 * @throws Exception
	 */
	public abstract void sendAck() throws Exception;

	/**
	 * Send the response status line.
	 */
	public void sendStatus() {

		// Write protocol name
		write(Constants.HTTP_11_BYTES);
		buf[pos++] = Constants.SP;

		// Write status code
		int status = response.getStatus();
		switch (status) {
		case 200:
			write(Constants._200_BYTES);
			break;
		case 400:
			write(Constants._400_BYTES);
			break;
		case 404:
			write(Constants._404_BYTES);
			break;
		default:
			write(status);
		}

		buf[pos++] = Constants.SP;

		// Write message
		String message = null;
		if (org.apache.coyote.Constants.USE_CUSTOM_STATUS_MSG_IN_HEADER) {
			message = response.getMessage();
		}
		if (message == null) {
			write(HttpMessages.getMessage(status));
		} else {
			write(message.replace('\n', ' ').replace('\r', ' '));
		}

		// End the response status line
		buf[pos++] = Constants.CR;
		buf[pos++] = Constants.LF;
	}

	/**
	 * Send a header.
	 * 
	 * @param name
	 *            Header name
	 * @param value
	 *            Header value
	 */
	public void sendHeader(MessageBytes name, MessageBytes value) {
		if (name.getLength() > 0 && !value.isNull()) {
			write(name);
			buf[pos++] = Constants.COLON;
			buf[pos++] = Constants.SP;
			write(value);
			buf[pos++] = Constants.CR;
			buf[pos++] = Constants.LF;
		}
	}

	/**
	 * Send a header.
	 * 
	 * @param name
	 *            Header name
	 * @param value
	 *            Header value
	 */
	public void sendHeader(ByteChunk name, ByteChunk value) {
		write(name);
		buf[pos++] = Constants.COLON;
		buf[pos++] = Constants.SP;
		write(value);
		buf[pos++] = Constants.CR;
		buf[pos++] = Constants.LF;
	}

	/**
	 * Send a header.
	 * 
	 * @param name
	 *            Header name
	 * @param value
	 *            Header value
	 */
	public void sendHeader(String name, String value) {
		write(name);
		buf[pos++] = Constants.COLON;
		buf[pos++] = Constants.SP;
		write(value);
		buf[pos++] = Constants.CR;
		buf[pos++] = Constants.LF;
	}

	/**
	 * End the header block.
	 */
	public void endHeaders() {
		buf[pos++] = Constants.CR;
		buf[pos++] = Constants.LF;
	}

	/**
	 * Write the contents of a byte chunk.
	 * 
	 * @param chunk
	 *            byte chunk
	 * @return number of bytes written
	 * @throws IOException
	 *             an undelying I/O error occured
	 */
	public abstract int doWrite(ByteChunk chunk, Response res) throws IOException;

	/**
	 * Commit the response.
	 * 
	 * @throws IOException
	 *             an undelying I/O error occured
	 */
	protected void commit() throws IOException {

		// The response is now committed
		committed = true;
		response.setCommitted(true);

		if (pos > 0) {
			// Sending the response header buffer
			bbuf.put(buf, 0, pos);
		}
	}

	/**
	 * This method will write the contents of the specyfied message bytes buffer
	 * to the output stream, without filtering. This method is meant to be used
	 * to write the response header.
	 * 
	 * @param mb
	 *            data to be written
	 */
	protected void write(MessageBytes mb) {
		if (mb == null) {
			return;
		}

		switch (mb.getType()) {
		case MessageBytes.T_BYTES:
			write(mb.getByteChunk());
			break;
		case MessageBytes.T_CHARS:
			write(mb.getCharChunk());
			break;
		default:
			write(mb.toString());
			break;
		}
	}

	/**
	 * This method will write the contents of the specyfied message bytes buffer
	 * to the output stream, without filtering. This method is meant to be used
	 * to write the response header.
	 * 
	 * @param bc
	 *            data to be written
	 */
	protected void write(ByteChunk bc) {
		// Writing the byte chunk to the output buffer
		int length = bc.getLength();
		System.arraycopy(bc.getBytes(), bc.getStart(), buf, pos, length);
		pos = pos + length;
	}

	/**
	 * This method will write the contents of the specyfied char buffer to the
	 * output stream, without filtering. This method is meant to be used to
	 * write the response header.
	 * 
	 * @param cc
	 *            data to be written
	 */
	protected void write(CharChunk cc) {
		int start = cc.getStart();
		int end = cc.getEnd();
		char[] cbuf = cc.getBuffer();
		for (int i = start; i < end; i++) {
			char c = cbuf[i];
			// Note: This is clearly incorrect for many strings,
			// but is the only consistent approach within the current
			// servlet framework. It must suffice until servlet output
			// streams properly encode their output.
			if (((c <= 31) && (c != 9)) || c == 127 || c > 255) {
				c = ' ';
			}
			buf[pos++] = (byte) c;
		}

	}

	/**
	 * This method will write the contents of the specyfied byte buffer to the
	 * output stream, without filtering. This method is meant to be used to
	 * write the response header.
	 * 
	 * @param b
	 *            data to be written
	 */
	public void write(byte[] b) {
		// Writing the byte chunk to the output buffer
		System.arraycopy(b, 0, buf, pos, b.length);
		pos = pos + b.length;
	}

	/**
	 * This method will write the contents of the specyfied String to the output
	 * stream, without filtering. This method is meant to be used to write the
	 * response header.
	 * 
	 * @param s
	 *            data to be written
	 */
	protected void write(String s) {
		if (s == null) {
			return;
		}

		// From the Tomcat 3.3 HTTP/1.0 connector
		int len = s.length();
		for (int i = 0; i < len; i++) {
			char c = s.charAt(i);
			// Note: This is clearly incorrect for many strings,
			// but is the only consistent approach within the current
			// servlet framework. It must suffice until servlet output
			// streams properly encode their output.

			if ((c <= 31 && c != 9) || c == 127) {
				c = ' ';
			}

			buf[pos++] = (byte) c;
		}
	}

	/**
	 * This method will print the specified integer to the output stream,
	 * without filtering. This method is meant to be used to write the response
	 * header.
	 * 
	 * @param i
	 *            data to be written
	 */
	protected void write(int i) {
		write(String.valueOf(i));
	}

	/**
	 * Callback to write data from the buffer.
	 */
	protected abstract void flushBuffer() throws IOException;

	/**
	 * Flush leftover bytes.
	 * 
	 * @return true if all leftover bytes have been flushed
	 * @throws IOException
	 */
	public abstract boolean flushLeftover() throws IOException;

	// ----------------------------------- OutputBufferImpl Inner Class

	/**
	 * {@code OutputBufferImpl} This class is an output buffer which will write
	 * data to an output stream/channel.
	 * 
	 * Created on Jan 10, 2012 at 12:20:15 PM
	 * 
	 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
	 */
	class OutputBufferImpl implements OutputBuffer {

		/**
		 * Write chunk.
		 */
		public int doWrite(ByteChunk chunk, Response res) throws IOException {
			// If non blocking (event) and there are leftover bytes,
			// put all remaining bytes in the leftover buffer (they are
			// part of the same write operation)
			if (leftover.getLength() > 0) {
				leftover.append(chunk);
				return chunk.getLength();
			}

			int len = chunk.getLength();
			int start = chunk.getStart();
			byte[] b = chunk.getBuffer();

			while (len > 0) {
				int thisTime = len;
				// if (bbuf.position() == bbuf.capacity()) {
				if (!bbuf.hasRemaining()) {
					flushBuffer();
					if (leftover.getLength() > 0) {
						// If non blocking (event) and there are leftover bytes,
						// put all remaining bytes in the leftover buffer (they
						// are
						// part of the same write operation)
						int oldStart = chunk.getOffset();
						chunk.setOffset(start);
						leftover.append(chunk);
						chunk.setOffset(oldStart);
						// After that, all content has been "written"
						return chunk.getLength();
					}
				}
				// if (thisTime > bbuf.capacity() - bbuf.position()) {
				if (thisTime > bbuf.remaining()) {
					// thisTime = bbuf.capacity() - bbuf.position();
					thisTime = bbuf.remaining();
				}

				bbuf.put(b, start, thisTime);
				len = len - thisTime;
				start = start + thisTime;
			}
			return chunk.getLength();
		}
	}

}
