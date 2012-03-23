/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.apache.catalina.connector;

import java.io.IOException;
import java.io.Reader;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.Locale;

import org.apache.catalina.security.SecurityUtil;
import org.apache.catalina.util.StringManager;
import org.apache.coyote.ActionCode;
import org.apache.coyote.Request;
import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.CharChunk;

/**
 * The buffer used by Tomcat request. This is a derivative of the Tomcat 3.3
 * OutputBuffer, adapted to handle input instead of output. This allows complete
 * recycling of the facade objects (the ServletInputStream and the
 * BufferedReader).
 * 
 * @author Remy Maucherat
 */
public class InputBuffer extends Reader implements ByteChunk.ByteInputChannel,
		CharChunk.CharInputChannel {

	// -------------------------------------------------------------- Constants

	/**
	 * The string manager for this package.
	 */
	protected static StringManager sm = StringManager.getManager(Constants.Package);

	public static final String DEFAULT_ENCODING = org.apache.coyote.Constants.DEFAULT_CHARACTER_ENCODING;
	public static final int DEFAULT_BUFFER_SIZE = 8 * 1024;

	// The buffer can be used for byte[] and char[] reading
	// ( this is needed to support ServletInputStream and BufferedReader )
	public final int INITIAL_STATE = 0;
	public final int CHAR_STATE = 1;
	public final int BYTE_STATE = 2;

	// ----------------------------------------------------- Instance Variables

	/**
	 * The byte buffer.
	 */
	private ByteChunk bb;

	/**
	 * The chunk buffer.
	 */
	private CharChunk cb;

	/**
	 * State of the output buffer.
	 */
	private int state = 0;

	/**
	 * Flag which indicates if the input buffer is closed.
	 */
	private boolean closed = false;

	/**
	 * Flag which indicates if the end of stream has been reached.
	 */
	private boolean eof = false;

	/**
	 * Encoding to use.
	 */
	private String enc;

	/**
	 * Encoder is set.
	 */
	private boolean gotEnc = false;

	/**
	 * List of encoders.
	 */
	protected HashMap<String, B2CConverter> encoders = new HashMap<String, B2CConverter>();

	/**
	 * Current byte to char converter.
	 */
	protected B2CConverter conv;

	/**
	 * Associated Coyote request.
	 */
	private Request coyoteRequest;

	/**
	 * Associated request.
	 */
	private org.apache.catalina.connector.Request request;

	/**
	 * Buffer position.
	 */
	private int markPos = -1;

	/**
	 * Buffer size.
	 */
	private int size = -1;

	// ----------------------------------------------------------- Constructors

	/**
	 * Default constructor. Allocate the buffer with the default buffer size.
	 */
	public InputBuffer(org.apache.catalina.connector.Request request) {

		this(request, DEFAULT_BUFFER_SIZE);

	}

	/**
	 * Alternate constructor which allows specifying the initial buffer size.
	 * 
	 * @param size
	 *            Buffer size to use
	 */
	public InputBuffer(org.apache.catalina.connector.Request request, int size) {

		this.request = request;
		this.size = size;
		bb = new ByteChunk(size);
		bb.setLimit(size);
		bb.setByteInputChannel(this);
		cb = new CharChunk(size);
		cb.setLimit(size);
		cb.setOptimizedWrite(false);
		cb.setCharInputChannel(this);

	}

	// ------------------------------------------------------------- Properties

	/**
	 * Associated Coyote request.
	 * 
	 * @param coyoteRequest
	 *            Associated Coyote request
	 */
	public void setRequest(Request coyoteRequest) {
		this.coyoteRequest = coyoteRequest;
	}

	/**
	 * Get associated Coyote request.
	 * 
	 * @return the associated Coyote request
	 */
	public Request getRequest() {
		return this.coyoteRequest;
	}

	// --------------------------------------------------------- Public Methods

	/**
	 * Recycle the output buffer.
	 */
	public void recycle() {

		state = INITIAL_STATE;

		// If usage of mark made the buffer too big, reallocate it
		if (cb.getChars().length > size) {
			cb = new CharChunk(size);
			cb.setLimit(size);
			cb.setOptimizedWrite(false);
			cb.setCharInputChannel(this);
		} else {
			cb.recycle();
		}
		markPos = -1;
		bb.recycle();
		closed = false;
		eof = false;

		if (conv != null) {
			conv.recycle();
		}

		gotEnc = false;
		enc = null;

	}

	/**
	 * Clear cached encoders (to save memory for event requests).
	 */
	public void clearEncoders() {
		encoders.clear();
	}

	/**
	 * Close the input buffer.
	 * 
	 * @throws IOException
	 *             An underlying IOException occurred
	 */
	public void close() throws IOException {
		closed = true;
	}

	/**
	 * Returns if the request is closed.
	 */
	public boolean isClosed() {
		return closed;
	}

	/**
	 * Returns if the eof has been reached.
	 */
	public boolean isEof() {
		return eof;
	}

	public int available() {
		if (eof || closed) {
			return -1;
		}
		int available = 0;
		if (state != CHAR_STATE) {
			available = bb.getLength();
			if (request.isEventMode() && available == 0) {
				try {
					coyoteRequest.action(ActionCode.ACTION_AVAILABLE, null);
					available = realReadBytes(null, 0, 0);
				} catch (IOException e) {
					// Ignore, will return 0, and another error
					// will occur elsewhere
				}
			}
		} else {
			available = cb.getLength();
			if (request.isEventMode() && available == 0) {
				try {
					coyoteRequest.action(ActionCode.ACTION_AVAILABLE, null);
					available = realReadChars(null, 0, cb.getBuffer().length);
				} catch (IOException e) {
					// Ignore, will return 0, and another error
					// will occur elsewhere
				}
			}
		}
		return available;
	}

	public int getAvailable() {
		if (eof || closed) {
			return -1;
		}
		int available = 0;
		if (state != CHAR_STATE) {
			available = bb.getLength();
		} else {
			available = cb.getLength();
		}
		return available;
	}

	// ------------------------------------------------- Bytes Handling Methods

	/**
	 * Reads new bytes in the byte chunk.
	 * 
	 * @param cbuf
	 *            Byte buffer to be written to the response
	 * @param off
	 *            Offset
	 * @param len
	 *            Length
	 * 
	 * @throws IOException
	 *             An underlying IOException occurred
	 */
	public int realReadBytes(byte cbuf[], int off, int len) throws IOException {

		if (eof) {
			return -1;
		}

		if (coyoteRequest == null) {
			return -1;
		}

		if (state == INITIAL_STATE) {
			state = BYTE_STATE;
		}

		try {
			int n = coyoteRequest.doRead(bb);
			if (n < 0) {
				eof = true;
			}
			return n;
		} catch (IOException e) {
			// An IOException on a read is almost always due to
			// the remote client aborting the request or a timeout occurring.
			// Wrap this so that it can be handled better by the error
			// dispatcher.
			throw new ClientAbortException(e);
		}

	}

	/**
	 * 
	 * @return
	 * @throws IOException
	 */
	public int readByte() throws IOException {

		if (closed) {
			throw new IOException(sm.getString("inputBuffer.streamClosed"));
		}

		return bb.substract();
	}

	public int read(byte[] b, int off, int len) throws IOException {

		if (closed)
			throw new IOException(sm.getString("inputBuffer.streamClosed"));

		return bb.substract(b, off, len);
	}

	// ------------------------------------------------- Chars Handling Methods

	public void setEncoding(String s) {
		enc = s;
	}

	public int realReadChars(char cbuf[], int off, int len) throws IOException {

		if (!gotEnc)
			setConverter();

		if (bb.getLength() <= 0) {
			if (realReadBytes(bb.getBytes(), 0, bb.getBytes().length) < 0) {
				return -1;
			}
		}

		if (markPos == -1) {
			cb.setOffset(0);
			cb.setEnd(0);
		} else {
			// Make sure there's enough space in the worst case
			cb.makeSpace(bb.getLength());
			if ((cb.getBuffer().length - cb.getEnd()) == 0) {
				// We went over the limit
				cb.setOffset(0);
				cb.setEnd(0);
				markPos = -1;
			}
		}

		state = CHAR_STATE;
		conv.convert(bb, cb);

		return cb.getLength();
	}

	@Override
	public int read() throws IOException {

		if (closed) {
			throw new IOException(sm.getString("inputBuffer.streamClosed"));
		}

		return cb.substract();
	}

	public int read(char[] cbuf) throws IOException {
		return read(cbuf, 0, cbuf.length);
	}

	public int read(char[] cbuf, int off, int len) throws IOException {

		if (closed)
			throw new IOException(sm.getString("inputBuffer.streamClosed"));

		return cb.substract(cbuf, off, len);
	}

	public long skip(long n) throws IOException {

		if (closed)
			throw new IOException(sm.getString("inputBuffer.streamClosed"));

		if (n < 0) {
			throw new IllegalArgumentException();
		}

		long nRead = 0;
		while (nRead < n) {
			if (cb.getLength() >= n) {
				cb.setOffset(cb.getStart() + (int) n);
				nRead = n;
			} else {
				nRead += cb.getLength();
				cb.setOffset(cb.getEnd());
				int toRead = 0;
				if (cb.getChars().length < (n - nRead)) {
					toRead = cb.getChars().length;
				} else {
					toRead = (int) (n - nRead);
				}
				int nb = realReadChars(cb.getChars(), 0, toRead);
				if (nb < 0)
					break;
			}
		}

		return nRead;

	}

	public boolean ready() throws IOException {

		if (closed)
			throw new IOException(sm.getString("inputBuffer.streamClosed"));

		return (available() > 0);
	}

	public boolean markSupported() {
		return true;
	}

	public void mark(int readAheadLimit) throws IOException {

		if (closed)
			throw new IOException(sm.getString("inputBuffer.streamClosed"));

		if (cb.getLength() <= 0) {
			cb.setOffset(0);
			cb.setEnd(0);
		} else {
			if ((cb.getBuffer().length > (2 * size)) && (cb.getLength()) < (cb.getStart())) {
				System.arraycopy(cb.getBuffer(), cb.getStart(), cb.getBuffer(), 0, cb.getLength());
				cb.setEnd(cb.getLength());
				cb.setOffset(0);
			}
		}
		if (cb.getStart() + readAheadLimit > cb.getLimit()) {
			cb.setLimit(cb.getStart() + readAheadLimit);
		}
		markPos = cb.getStart();
	}

	public void reset() throws IOException {

		if (closed)
			throw new IOException(sm.getString("inputBuffer.streamClosed"));

		if (state == CHAR_STATE) {
			if (markPos < 0) {
				throw new IOException();
			} else {
				cb.setOffset(markPos);
			}
		} else {
			bb.recycle();
		}
	}

	public void checkConverter() throws IOException {

		if (!gotEnc)
			setConverter();

	}

	protected void setConverter() throws IOException {

		if (coyoteRequest != null)
			enc = coyoteRequest.getCharacterEncoding();

		gotEnc = true;
		enc = (enc == null) ? DEFAULT_ENCODING : enc.toUpperCase(Locale.US);
		conv = encoders.get(enc);
		if (conv == null) {
			if (SecurityUtil.isPackageProtectionEnabled()) {
				try {
					conv = (B2CConverter) AccessController
							.doPrivileged(new PrivilegedExceptionAction<B2CConverter>() {
								public B2CConverter run() throws IOException {
									return new B2CConverter(enc);
								}
							});
				} catch (PrivilegedActionException ex) {
					Exception e = ex.getException();
					if (e instanceof IOException)
						throw (IOException) e;
				}
			} else {
				conv = new B2CConverter(enc);
			}
			encoders.put(enc, conv);
		}

	}

}
