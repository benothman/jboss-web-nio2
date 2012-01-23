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
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.nio.channels.InterruptedByTimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.coyote.InputBuffer;
import org.apache.coyote.Request;
import org.apache.tomcat.jni.Status;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.NioChannel;
import org.apache.tomcat.util.net.NioEndpoint;

/**
 * {@code InternalNioInputBuffer}
 * <p>
 * Implementation of InputBuffer which provides HTTP request header parsing as
 * well as transfer decoding.
 * </p>
 * 
 * Created on Dec 14, 2011 at 9:06:18 AM
 * 
 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
 */
public class InternalNioInputBuffer extends AbstractInternalInputBuffer {

	/**
	 * Underlying channel.
	 */
	protected NioChannel channel;

	/**
	 * Non blocking mode.
	 */
	protected boolean nonBlocking = false;

	/**
	 * Non blocking mode.
	 */
	protected boolean available = false;

	/**
	 * NIO endpoint.
	 */
	protected NioEndpoint endpoint = null;

	/**
	 * Create a new instance of {@code InternalNioInputBuffer}
	 * 
	 * @param request
	 * @param headerBufferSize
	 * @param endpoint
	 */
	public InternalNioInputBuffer(Request request, int headerBufferSize, NioEndpoint endpoint) {
		super(request, headerBufferSize);
		this.endpoint = endpoint;
		this.inputBuffer = new InputBufferImpl();
		this.readTimeout = (endpoint.getKeepAliveTimeout() > 0 ? endpoint.getKeepAliveTimeout()
				: endpoint.getSoTimeout());
	}

	/**
	 * Set the underlying channel.
	 * 
	 * @param channel
	 */
	public void setChannel(NioChannel channel) {
		this.channel = channel;
		// Socket.setrbb(this.socket, bbuf);
	}

	/**
	 * Get the underlying socket input stream.
	 * 
	 * @return the channel
	 */
	public NioChannel getChannel() {
		return channel;
	}

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
	 * @return true if the buffer is non-blocking else false
	 */
	public boolean getNonBlocking() {
		return nonBlocking;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.coyote.http11.AbstractInternalInputBuffer#recycle()
	 */
	public void recycle() {
		super.recycle();
		bbuf.clear();
		channel = null;
		available = false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.coyote.http11.AbstractInternalInputBuffer#nextRequest()
	 */
	public boolean nextRequest() {
		boolean result = super.nextRequest();
		nonBlocking = false;
		available = false;

		return result;
	}

	/**
	 * Read the request line. This function is meant to be used during the HTTP
	 * request header parsing. Do NOT attempt to read the request body using it.
	 * 
	 * @param useAvailableData
	 * 
	 * @throws IOException
	 *             If an exception occurs during the underlying socket read
	 *             operations, or if the given buffer is not big enough to
	 *             accommodate the whole line.
	 * @return true if data is properly fed; false if no data is available
	 *         immediately and thread should be freed
	 */
	public boolean parseRequestLine(boolean useAvailableData) throws IOException {

		int start = 0;
		// Skipping blank lines

		byte chr = 0;
		do {

			// Read new bytes if needed
			if (pos >= lastValid) {
				if (useAvailableData) {
					return false;
				}
				if (!fill()) {
					throw new EOFException(sm.getString("iib.eof.error"));
				}
			}

			chr = buf[pos++];
		} while ((chr == Constants.CR) || (chr == Constants.LF));

		pos--;

		// Mark the current buffer position
		start = pos;

		if (pos >= lastValid) {
			if (useAvailableData) {
				return false;
			}
			if (!fill()) {
				throw new EOFException(sm.getString("iib.eof.error"));
			}
		}

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

		//
		// Reading the URI
		//
		boolean eol = false;

		while (!space) {

			// Read new bytes if needed
			if (pos >= lastValid) {
				if (!fill())
					throw new EOFException(sm.getString("iib.eof.error"));
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
				if (!fill())
					throw new EOFException(sm.getString("iib.eof.error"));
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

		//
		// Reading the protocol
		// Protocol is always US-ASCII
		//

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

		return true;
	}

	/**
	 * Available bytes (note that due to encoding, this may not correspond )
	 */
	public void useAvailable() {
		available = true;
	}

	/**
	 * Available bytes in the buffer ? (these may not translate to application
	 * readable data)
	 * 
	 * @return the number of available bytes in the buffer
	 */
	public boolean available() {
		return (lastValid - pos > 0);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.coyote.http11.AbstractInternalInputBuffer#endRequest()
	 */
	public void endRequest() throws IOException {
		super.endRequest();
		String connectionHeader = request.getHeader("Connection");
		
		if (connectionHeader == null || !connectionHeader.trim().equalsIgnoreCase("keep-alive")) {
			close(channel);
		}
		
		
		
		
		if (connectionHeader != null && connectionHeader.trim().equalsIgnoreCase("keep-alive")) {
			final ByteBuffer bb = ByteBuffer.allocateDirect(1);
			channel.read(bb, readTimeout, unit, channel,
					new CompletionHandler<Integer, NioChannel>() {

						@Override
						public void completed(Integer nBytes, NioChannel attachment) {
							System.out.println("Completed -> " + nBytes);
							if (nBytes < 0) {
								close(attachment);
							}
						}

						@Override
						public void failed(Throwable exc, NioChannel attachment) {
							exc.printStackTrace();
							if (exc instanceof InterruptedByTimeoutException) {
								close(attachment);
							}
						}
					});
		} else {
			// Closing the channel
			close(channel);
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.coyote.InputBuffer#doRead(org.apache.tomcat.util.buf.ByteChunk
	 * , org.apache.coyote.Request)
	 */
	public int doRead(ByteChunk chunk, Request req) throws IOException {
		return (lastActiveFilter == -1) ? inputBuffer.doRead(chunk, req)
				: activeFilters[lastActiveFilter].doRead(chunk, req);
	}

	// ------------------------------------------------------ Protected Methods

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.coyote.http11.AbstractInternalInputBuffer#fill()
	 */
	protected boolean fill() throws IOException {
		int nRead = 0;

		bbuf.clear();
		if (parsingHeader) {
			if (lastValid == buf.length) {
				throw new IllegalArgumentException(sm.getString("iib.requestheadertoolarge.error"));
			}

			if (nonBlocking) {
				nonBlockingRead(bbuf, readTimeout, unit);
			} else {
				nRead = blockingRead(bbuf, readTimeout, unit);
				if (nRead > 0) {
					bbuf.flip();
					bbuf.get(buf, pos, nRead);

					System.out.println(new String(buf));

					lastValid = pos + nRead;
				} else {
					if ((-nRead) == Status.EAGAIN) {
						return false;
					} else {
						throw new IOException(sm.getString("iib.failedread"));
					}
				}
			}
		} else {
			if (buf.length - end < 4500) {
				// In this case, the request header was really large, so we
				// allocate a
				// brand new one; the old one will get GCed when subsequent
				// requests
				// clear all references
				buf = new byte[buf.length];
				end = 0;
			}
			pos = end;
			lastValid = pos;

			if (nonBlocking) {
				nonBlockingRead(bbuf, readTimeout, unit);
			} else {
				nRead = blockingRead(bbuf, readTimeout, unit);

				if (nRead > 0) {
					bbuf.flip();
					bbuf.get(buf, pos, nRead);
					lastValid = pos + nRead;
				} else if (nRead <= 0) {
					if ((-nRead) == Status.ETIMEDOUT || (-nRead) == Status.TIMEUP) {
						throw new SocketTimeoutException(sm.getString("iib.failedread"));
					} else if ((-nRead) == Status.EAGAIN && nonBlocking) {
						// As asynchronous reads are forbidden, this test is not
						// useful
						// && (Http11AprProcessor.containerThread.get() ==
						// Boolean.TRUE)
						if (available) {
							nRead = 0;
						} else {
							// In this specific situation, perform the read
							// again in
							// blocking mode (the user is not
							// using available and simply wants to read all
							// data)
							nRead = blockingRead(bbuf, readTimeout, unit);

							if (nRead > 0) {
								bbuf.flip();
								bbuf.get(buf, pos, nRead);
								lastValid = pos + nRead;
							} else if (nRead <= 0) {
								if ((-nRead) == Status.ETIMEDOUT || (-nRead) == Status.TIMEUP) {
									throw new SocketTimeoutException(sm.getString("iib.failedread"));
								} else {
									throw new IOException(sm.getString("iib.failedread"));
								}
							}
						}
					} else {
						throw new IOException(sm.getString("iib.failedread"));
					}
				}
			}
		}
		return (nRead >= 0);
	}

	/**
	 * Close the channel
	 */
	private static void close(NioChannel channel) {
		System.out.println("Closing channel : " + channel);
		try {
			channel.close();
		} catch (IOException e) {
			// NOTHING
		}
	}

	/**
	 * Read a sequence of bytes in non-blocking mode from he current channel
	 * 
	 * @param bb
	 *            the byte buffer which will contain the bytes read from the
	 *            current channel
	 */
	private void nonBlockingRead(final ByteBuffer bb, long timeout, TimeUnit unit) {

		this.channel.read(bb, timeout, unit, channel, new CompletionHandler<Integer, NioChannel>() {

			@Override
			public void completed(Integer nBytes, NioChannel attachment) {
				if (nBytes < 0) {
					close(attachment);
				}

				if (nBytes > 0) {
					bb.flip();
					bb.get(buf, pos, nBytes);
					lastValid = pos + nBytes;
				}
			}

			@Override
			public void failed(Throwable exc, NioChannel attachment) {
				if (exc instanceof InterruptedByTimeoutException) {
					close(attachment);
				}
			}
		});
	}

	/**
	 * Read a sequence of bytes in blocking mode from he current channel
	 * 
	 * @param bb
	 * @return the number of bytes read or -1 if the end of the stream was
	 *         reached
	 */
	private int blockingRead(ByteBuffer bb, long timeout, TimeUnit unit) {
		try {
			if (timeout > 0) {
				return this.channel.read(bb).get(timeout, unit);
			}

			return this.channel.read(bb).get();
		} catch (TimeoutException te) {
			close(channel);
		} catch (Exception e) {
			// NOP
			log.warn("An error occurs when trying a blocking read");
			log.error(e.getMessage(), e);
		}
		return -1;
	}

	// ------------------------------------- ChannelInputBuffer Inner Class

	/**
	 * This class is an input buffer which will read its data from an input
	 * stream.
	 */
	protected class InputBufferImpl implements InputBuffer {

		/**
		 * Read bytes into the specified chunk.
		 */
		public int doRead(ByteChunk chunk, Request req) throws IOException {

			if (pos >= lastValid) {
				if (!fill()) {
					return -1;
				}
			}

			int length = lastValid - pos;
			chunk.setBytes(buf, pos, length);
			pos = lastValid;

			return (length);
		}
	}
}
