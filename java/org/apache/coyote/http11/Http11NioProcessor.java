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

package org.apache.coyote.http11;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Locale;

import org.apache.coyote.ActionCode;
import org.apache.coyote.Request;
import org.apache.coyote.RequestInfo;
import org.apache.coyote.Response;
import org.apache.coyote.http11.filters.BufferedInputFilter;
import org.apache.coyote.http11.filters.ChunkedInputFilter;
import org.apache.coyote.http11.filters.ChunkedOutputFilter;
import org.apache.coyote.http11.filters.GzipOutputFilter;
import org.apache.coyote.http11.filters.IdentityInputFilter;
import org.apache.coyote.http11.filters.IdentityOutputFilter;
import org.apache.coyote.http11.filters.SavedRequestInputFilter;
import org.apache.coyote.http11.filters.VoidInputFilter;
import org.apache.coyote.http11.filters.VoidOutputFilter;
import org.apache.tomcat.jni.SSL;
import org.apache.tomcat.jni.SSLSocket;
import org.apache.tomcat.jni.Socket;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.HexUtils;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.FastHttpDateFormat;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.net.NioChannel;
import org.apache.tomcat.util.net.NioEndpoint;
import org.apache.tomcat.util.net.NioEndpoint.ChannelInfo;
import org.apache.tomcat.util.net.NioEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.jsse.SSLNioChannel;
import org.apache.tomcat.util.net.SocketStatus;

/**
 * Processes HTTP requests.
 * 
 * @author Remy Maucherat
 */
public class Http11NioProcessor extends Http11AbstractProcessor {

	/**
	 * Input.
	 */
	protected InternalNioInputBuffer inputBuffer = null;

	/**
	 * Output.
	 */
	protected InternalNioOutputBuffer outputBuffer = null;

	/**
	 * Sendfile data.
	 */
	protected NioEndpoint.SendfileData sendfileData = null;

	/**
	 * Channel associated with the current connection.
	 */
	protected NioChannel channel;

	/**
	 * Associated endpoint.
	 */
	protected NioEndpoint endpoint;

	/**
	 * Create a new instance of {@code Http11NioProcessor}
	 * 
	 * @param headerBufferSize
	 * @param endpoint
	 */
	public Http11NioProcessor(int headerBufferSize, NioEndpoint endpoint) {

		this.endpoint = endpoint;
		request = new Request();
		inputBuffer = new InternalNioInputBuffer(request, headerBufferSize, endpoint);
		request.setInputBuffer(inputBuffer);
		if (endpoint.getUseSendfile()) {
			request.setSendfile(true);
		}

		response = new Response();
		response.setHook(this);
		outputBuffer = new InternalNioOutputBuffer(response, headerBufferSize, endpoint);
		response.setOutputBuffer(outputBuffer);
		request.setResponse(response);
		sslEnabled = endpoint.getSSLEnabled();
		initializeFilters();

		// Cause loading of HexUtils
		int foo = HexUtils.DEC[0];

		// Cause loading of FastHttpDateFormat
		FastHttpDateFormat.getCurrentDate();
	}

	/**
	 * Mark the start of processing
	 */
	public void startProcessing() {
		eventProcessing = true;
	}

	/**
	 * Mark the end of processing
	 */
	public void endProcessing() {
		eventProcessing = false;
	}

	/**
	 * @return true if the input buffer is available
	 */
	public boolean isAvailable() {
		return inputBuffer.available();
	}

	/**
	 * Add input or output filter.
	 * 
	 * @param className
	 *            class name of the filter
	 */
	protected void addFilter(String className) {
		try {
			Class<?> clazz = Class.forName(className);
			Object obj = clazz.newInstance();
			if (obj instanceof InputFilter) {
				inputBuffer.addFilter((InputFilter) obj);
			} else if (obj instanceof OutputFilter) {
				outputBuffer.addFilter((OutputFilter) obj);
			} else {
				log.warn(sm.getString("http11processor.filter.unknown", className));
			}
		} catch (Exception e) {
			log.error(sm.getString("http11processor.filter.error", className), e);
		}
	}

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
			System.arraycopy(sArray, 0, result, 0, sArray.length);
			// for (int i = 0; i < sArray.length; i++)
			// result[i] = sArray[i];
			result[sArray.length] = value;
		}
		return result;
	}

	/**
	 * Process pipelined HTTP requests using the specified input and output
	 * streams.
	 * 
	 * @param status
	 * @return
	 * 
	 * @throws IOException
	 *             error during an I/O operation
	 */
	public SocketState event(SocketStatus status) throws IOException {

		RequestInfo rp = request.getRequestProcessor();
		try {
			// If processing a write event, must flush any leftover bytes first
			if (status == SocketStatus.OPEN_WRITE) {
				// If the flush does not manage to flush all leftover bytes, the
				// socket should
				// go back to the poller.
				if (!outputBuffer.flushLeftover()) {
					return SocketState.LONG;
				}
				// The write notification is now done
				writeNotification = false;
				// Allow convenient synchronous blocking writes
				response.setFlushLeftovers(true);
			} else if (status == SocketStatus.OPEN_CALLBACK) {
				// The resume notification is now done
				resumeNotification = false;
			} else if (status == SocketStatus.ERROR) {
				// Set error flag right away
				error = true;
			}
			containerThread.set(Boolean.TRUE);
			rp.setStage(org.apache.coyote.Constants.STAGE_SERVICE);
			error = !adapter.event(request, response, status);
		} catch (InterruptedIOException e) {
			error = true;
		} catch (Throwable t) {
			log.error(sm.getString("http11processor.request.process"), t);
			// 500 - Internal Server Error
			response.setStatus(500);
			error = true;
		}

		rp.setStage(org.apache.coyote.Constants.STAGE_ENDED);

		if (error) {
			inputBuffer.nextRequest();
			outputBuffer.nextRequest();
			recycle();
			return SocketState.CLOSED;
		} else if (!event) {
			endRequest();
			boolean pipelined = inputBuffer.nextRequest();
			outputBuffer.nextRequest();
			recycle();
			return (pipelined || !keepAlive) ? SocketState.CLOSED : SocketState.OPEN;
		} else {
			return SocketState.LONG;
		}
	}

	/**
	 * 
	 */
	private void reset() {
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
	 * Process pipelined HTTP requests using the specified input and output
	 * streams.
	 * 
	 * @param channel
	 * @return
	 * 
	 * @throws IOException
	 *             error during an I/O operation
	 */
	public SocketState process(NioChannel channel) throws IOException {
		RequestInfo rp = request.getRequestProcessor();
		rp.setStage(org.apache.coyote.Constants.STAGE_PARSE);

		this.reset();

		// Setting up the channel
		this.channel = channel;
		inputBuffer.setChannel(channel);
		outputBuffer.setChannel(channel);
		int keepAliveLeft = maxKeepAliveRequests;
		int soTimeout = endpoint.getSoTimeout();
		boolean keptAlive = false;
		boolean openSocket = false;

		while (!error && keepAlive && !event) {

			// Parsing the request header
			try {

				if (!disableUploadTimeout && keptAlive && soTimeout > 0) {
					endpoint.setSoTimeout(soTimeout * 1000);
				}
				if (!inputBuffer.parseRequestLine(keptAlive)) {
					// This means that no data is available right now
					// (long keepalive), so that the processor should be
					// recycled
					// and the method should return true
					openSocket = true;
					break;
				}
				request.setStartTime(System.currentTimeMillis());
				keptAlive = true;
				if (!disableUploadTimeout) {
					endpoint.setSoTimeout(timeout * 1000);
				}
				inputBuffer.parseHeaders();
			} catch (IOException e) {
				error = true;
				break;
			} catch (Throwable t) {
				if (log.isDebugEnabled()) {
					log.debug(sm.getString("http11processor.header.parse"), t);
				}
				// 400 - Bad Request
				response.setStatus(400);
				error = true;
			}

			// Setting up filters, and parse some request headers
			rp.setStage(org.apache.coyote.Constants.STAGE_PREPARE);
			try {
				prepareRequest();
			} catch (Throwable t) {
				if (log.isDebugEnabled()) {
					log.debug(sm.getString("http11processor.request.prepare"), t);
				}
				// 400 - Internal Server Error
				response.setStatus(400);
				error = true;
			}

			if (maxKeepAliveRequests > 0 && --keepAliveLeft == 0)
				keepAlive = false;

			// Process the request in the adapter
			if (!error) {
				try {
					rp.setStage(org.apache.coyote.Constants.STAGE_SERVICE);
					adapter.service(request, response);
					// Handle when the response was committed before a serious
					// error occurred. Throwing a ServletException should both
					// set the status to 500 and set the errorException.
					// If we fail here, then the response is likely already
					// committed, so we can't try and set headers.
					if (keepAlive && !error) { // Avoid checking twice.
						error = response.getErrorException() != null
								|| statusDropsConnection(response.getStatus());
					}
				} catch (InterruptedIOException e) {
					error = true;
				} catch (Throwable t) {
					log.error(sm.getString("http11processor.request.process"), t);
					// 500 - Internal Server Error
					response.setStatus(500);
					error = true;
				}
			}

			// Finish the handling of the request
			if (error) {
				// If there is an unspecified error, the connection will be
				// closed
				inputBuffer.setSwallowInput(false);
			}
			if (!event) {
				endRequest();
			}

			// If there was an error, make sure the request is counted as
			// and error, and update the statistics counter
			if (error) {
				response.setStatus(500);
			}
			request.updateCounters();

			boolean pipelined = false;
			if (!event) {
				// Next request
				pipelined = inputBuffer.nextRequest();
				outputBuffer.nextRequest();
			}
			
			rp.setStage(org.apache.coyote.Constants.STAGE_KEEPALIVE);
		}

		rp.setStage(org.apache.coyote.Constants.STAGE_ENDED);

		if (event) {
			if (error) {
				inputBuffer.nextRequest();
				outputBuffer.nextRequest();
				recycle();
				return SocketState.CLOSED;
			} else {
				eventProcessing = false;
				return SocketState.LONG;
			}
		} else {
			recycle();
			return (openSocket) ? SocketState.OPEN : SocketState.CLOSED;
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.coyote.http11.Http11AbstractProcessor#endRequest()
	 */
	public void endRequest() {

		// Finish the handling of the request
		try {
			inputBuffer.endRequest();
		} catch (IOException e) {
			error = true;
		} catch (Throwable t) {
			log.error(sm.getString("http11processor.request.finish"), t);
			// 500 - Internal Server Error
			response.setStatus(500);
			error = true;
		}
		try {
			outputBuffer.endRequest();
		} catch (IOException e) {
			error = true;
		} catch (Throwable t) {
			log.error(sm.getString("http11processor.response.finish"), t);
			error = true;
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.coyote.http11.Http11AbstractProcessor#recycle()
	 */
	public void recycle() {
		inputBuffer.recycle();
		outputBuffer.recycle();
		this.channel = null;
		timeout = -1;
		readNotifications = true;
		writeNotification = false;
		resumeNotification = false;
		eventProcessing = true;
	}

	// ----------------------------------------------------- ActionHook Methods

	/**
	 * Commit the action
	 * 
	 * @param param
	 */
	private void commit(Object param) {
		if (!response.isCommitted()) {
			// Validate and write response headers
			prepareResponse();
			try {
				outputBuffer.commit();
			} catch (IOException e) {
				// Set error flag
				error = true;
			}
		}
	}

	/**
	 * Send a 100 status back if it makes sense (response not committed yet, and
	 * client specified an expectation for 100-continue)
	 * 
	 * @param param
	 */
	private void sendAck(Object param) {

		if ((response.isCommitted()) || !expectation) {
			return;
		}

		inputBuffer.setSwallowInput(true);
		try {
			outputBuffer.sendAck();
		} catch (Exception e) {
			// Set error flag
			error = true;
		}
	}

	/**
	 * Flush the output buffer
	 */
	private void flush() {
		try {
			outputBuffer.flush();
		} catch (IOException e) {
			// Set error flag
			error = true;
			response.setErrorException(e);
		}
	}

	/**
	 * End the processing of the current request, and stop any further
	 * transactions with the client
	 */
	private void close() {
		event = false;
		try {
			outputBuffer.endRequest();
		} catch (IOException e) {
			// Set error flag
			error = true;
		}
	}

	/**
	 * Get the remote host address
	 */
	private void requestHostAddressAttr() {
		if (remoteAddr == null && (channel != null)) {
			try {
				remoteAddr = ((InetSocketAddress) this.channel.getRemoteAddress()).getHostName();
			} catch (Exception e) {
				log.warn(sm.getString("http11processor.socket.info"), e);
			}
		}
		request.remoteAddr().setString(remoteAddr);
	}

	/**
	 * Request the local name attribute
	 */
	private void requestLocalNameAttr() {
		if (localName == null && (channel != null)) {
			try {
				localName = ((InetSocketAddress) this.channel.getLocalAddress()).getHostName();
			} catch (Exception e) {
				log.warn(sm.getString("http11processor.socket.info"), e);
			}
		}
		request.localName().setString(localName);
	}

	/**
	 * Get remote host name
	 */
	private void requestHostAttribute() {
		if (remoteHost == null && (channel != null)) {
			try {
				remoteHost = ((InetSocketAddress) this.channel.getRemoteAddress()).getHostName();
				if (remoteHost == null) {
					remoteAddr = ((InetSocketAddress) this.channel.getRemoteAddress()).getAddress()
							.getHostAddress();
					remoteHost = remoteAddr;
				}
			} catch (Exception e) {
				log.warn(sm.getString("http11processor.socket.info"), e);
			}
		}
		request.remoteHost().setString(remoteHost);
	}

	/**
	 * Get local host address
	 */
	private void requestLocalHostAddressAttr() {
		if (localAddr == null && (channel != null)) {
			try {
				localAddr = ((InetSocketAddress) this.channel.getLocalAddress()).getAddress()
						.getHostAddress();
			} catch (Exception e) {
				log.warn(sm.getString("http11processor.socket.info"), e);
			}
		}

		request.localAddr().setString(localAddr);
	}

	/**
	 * Get remote port
	 */
	private void requestRemotePortAttr() {
		if (remotePort == -1 && (channel != null)) {
			try {
				remotePort = ((InetSocketAddress) this.channel.getRemoteAddress()).getPort();
			} catch (Exception e) {
				log.warn(sm.getString("http11processor.socket.info"), e);
			}
		}
		request.setRemotePort(remotePort);
	}

	/**
	 * Get local port
	 */
	private void requestLocalPortAttr() {
		if (localPort == -1 && (channel != null)) {
			try {
				localPort = ((InetSocketAddress) this.channel.getLocalAddress()).getPort();
			} catch (Exception e) {
				log.warn(sm.getString("http11processor.socket.info"), e);
			}
		}
		request.setLocalPort(localPort);
	}

	/**
	 * Get the SSL attribute
	 */
	private void requestSSLAttr() {
		if (sslEnabled && (channel != null)) {
			try {

				SSLNioChannel sslChannel = (SSLNioChannel) channel;
				// Cipher suite
				// Object sslO = SSLSocket.getInfoS(socket,
				// SSL.SSL_INFO_CIPHER);
				Object sslO = sslChannel.getSSLSession().getCipherSuite();
				if (sslO != null) {
					request.setAttribute(org.apache.tomcat.util.net.Constants.CIPHER_SUITE_KEY,
							sslO);
				}
				// Get client certificate and the certificate chain if
				// present
				// certLength == -1 indicates an error
				// int certLength = SSLSocket.getInfoI(socket,
				// SSL.SSL_INFO_CLIENT_CERT_CHAIN);
				// TODO not sure that is correct
				int certLength = sslChannel.getSSLSession().getPeerCertificates().length;

				// byte[] clientCert = SSLSocket.getInfoB(socket,
				// SSL.SSL_INFO_CLIENT_CERT);
				// TODO not sure that is correct
				byte[] clientCert = sslChannel.getSSLSession().getPeerCertificates()[0]
						.getEncoded();
				X509Certificate[] certs = null;
				if (clientCert != null && certLength > -1) {
					certs = new X509Certificate[certLength + 1];
					CertificateFactory cf = CertificateFactory.getInstance("X.509");
					certs[0] = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(
							clientCert));
					for (int i = 0; i < certLength; i++) {
						// TODO not sure that is correct
						// byte[] data = SSLSocket.getInfoB(socket,
						// SSL.SSL_INFO_CLIENT_CERT_CHAIN + i);
						byte[] data = sslChannel.getSSLSession().getPeerCertificateChain()[i]
								.getEncoded();
						certs[i + 1] = (X509Certificate) cf
								.generateCertificate(new ByteArrayInputStream(data));
					}
				}
				if (certs != null) {
					request.setAttribute(org.apache.tomcat.util.net.Constants.CERTIFICATE_KEY,
							certs);
				}
				// User key size
				// sslO = new Integer(SSLSocket.getInfoI(socket,
				// SSL.SSL_INFO_CIPHER_USEKEYSIZE));
				sslO = sslChannel.getSSLSession().getValue("javax.servlet.request.key_size");
				request.setAttribute(org.apache.tomcat.util.net.Constants.KEY_SIZE_KEY, sslO);
				// SSL session ID
				// sslO = SSLSocket.getInfoS(socket, SSL.SSL_INFO_SESSION_ID);
				sslO = sslChannel.getSSLSession().getId();
				if (sslO != null) {
					request.setAttribute(org.apache.tomcat.util.net.Constants.SESSION_ID_KEY, sslO);
				}
			} catch (Exception e) {
				log.warn(sm.getString("http11processor.socket.ssl"), e);
			}
		}
	}

	/**
	 * Get the SSL certificate
	 */
	private void requestSSLCertificate() {
		/*
		 * if (sslEnabled && (channel != null)) { // Consume and buffer the
		 * request body, so that it does not // interfere with the client's
		 * handshake messages if (maxSavePostSize != 0) { BufferedInputFilter
		 * buffredInputFilter = new BufferedInputFilter();
		 * buffredInputFilter.setLimit(maxSavePostSize);
		 * inputBuffer.addActiveFilter(buffredInputFilter); } try { // Configure
		 * connection to require a certificate SSLNioChannel sslChannel =
		 * (SSLNioChannel) channel;
		 * sslChannel.getSslEngine().setWantClientAuth(true); //
		 * SSLSocket.setVerify(socket, SSL.SSL_CVERIFY_REQUIRE, //
		 * endpoint.getSSLVerifyDepth()); // Renegociate certificates if
		 * (SSLSocket.renegotiate(socket) == 0) { // Don't look for certs unless
		 * we know renegotiation // worked. // Get client certificate and the
		 * certificate chain if // present // certLength == -1 indicates an
		 * error // int certLength = SSLSocket.getInfoI(socket, //
		 * SSL.SSL_INFO_CLIENT_CERT_CHAIN); int certLength =
		 * sslChannel.getSSLSession().getPeerCertificateChain().length; byte[]
		 * clientCert = SSLSocket.getInfoB(socket, SSL.SSL_INFO_CLIENT_CERT);
		 * X509Certificate[] certs = null; if (clientCert != null && certLength
		 * > -1) { certs = new X509Certificate[certLength + 1];
		 * CertificateFactory cf = CertificateFactory.getInstance("X.509");
		 * certs[0] = (X509Certificate) cf .generateCertificate(new
		 * ByteArrayInputStream(clientCert)); for (int i = 0; i < certLength;
		 * i++) { byte[] data = SSLSocket.getInfoB(socket,
		 * SSL.SSL_INFO_CLIENT_CERT_CHAIN + i); certs[i + 1] = (X509Certificate)
		 * cf .generateCertificate(new ByteArrayInputStream(data)); } } if
		 * (certs != null) {
		 * request.setAttribute(org.apache.tomcat.util.net.Constants
		 * .CERTIFICATE_KEY, certs); } } } catch (Exception e) {
		 * log.warn(sm.getString("http11processor.socket.ssl"), e); } }
		 */
	}

	/**
	 * 
	 * @param param
	 */
	private void requestSetBodyReplay(Object param) {
		ByteChunk body = (ByteChunk) param;

		InputFilter savedBody = new SavedRequestInputFilter(body);
		savedBody.setRequest(request);

		InternalNioInputBuffer internalBuffer = (InternalNioInputBuffer) request.getInputBuffer();
		internalBuffer.addActiveFilter(savedBody);
	}

	/**
	 * Begin an event
	 * 
	 * @param param
	 *            the vent parameter
	 */
	private void beginEvent(Object param) {
		event = true;
		// Set socket to non blocking mode
		if (param == Boolean.TRUE) {
			outputBuffer.setNonBlocking(true);
			inputBuffer.setNonBlocking(true);
		}
	}

	/**
	 * End the event
	 * 
	 * @param param
	 *            the event parameter
	 */
	private void endEvent(Object param) {
		event = false;
		// End non blocking mode
		if (outputBuffer.getNonBlocking()) {
			outputBuffer.setNonBlocking(false);
			inputBuffer.setNonBlocking(false);
		}
	}

	/**
	 * Resume the event
	 * 
	 * @param param
	 *            the vent parameter
	 */
	private void resumeEvent(Object param) {
		readNotifications = true;
		// An event is being processed already: adding for resume will be
		// done
		// when the channel gets back to the poller
		if (!eventProcessing && !resumeNotification) {
			// endpoint.getEventPoller().add(channel, timeout,
			// ChannelInfo.RESUME);
		}
		resumeNotification = true;
	}

	/**
	 * Write Event
	 * 
	 * @param param
	 */
	private void writeEvent(Object param) {
		// An event is being processed already: adding for write will be
		// done
		// when the channel gets back to the poller
		if (!eventProcessing && !writeNotification) {
			// endpoint.getEventPoller().add(channel, timeout,
			// ChannelInfo.WRITE);
		}
		writeNotification = true;
	}

	/**
	 * Suspend Event
	 */
	private void suspendEvent() {
		readNotifications = false;
	}

	/**
	 * Timeout event
	 * 
	 * @param param
	 *            the timeout value
	 */
	private void timeoutEvent(Object param) {
		timeout = ((Integer) param).intValue();
	}

	/**
	 * Make the input buffer available
	 */
	private void makeAvailable() {
		inputBuffer.useAvailable();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.coyote.ActionHook#action(org.apache.coyote.ActionCode,
	 * java.lang.Object)
	 */
	public void action(ActionCode actionCode, Object param) {

		if (actionCode == ActionCode.ACTION_COMMIT) {
			// Commit current response
			commit(param);
		} else if (actionCode == ActionCode.ACTION_ACK) {
			// Acknowledge request
			sendAck(param);
		} else if (actionCode == ActionCode.ACTION_CLIENT_FLUSH) {
			// Flush
			flush();
		} else if (actionCode == ActionCode.ACTION_CLOSE) {
			// Close
			close();
		} else if (actionCode == ActionCode.ACTION_CUSTOM) {
			// DO NOTHING
		} else if (actionCode == ActionCode.ACTION_REQ_HOST_ADDR_ATTRIBUTE) {
			// Get remote host address
			requestHostAddressAttr();
		} else if (actionCode == ActionCode.ACTION_REQ_LOCAL_NAME_ATTRIBUTE) {
			// Get local host name
			requestLocalNameAttr();
		} else if (actionCode == ActionCode.ACTION_REQ_HOST_ATTRIBUTE) {
			// Get remote host name
			requestHostAttribute();
		} else if (actionCode == ActionCode.ACTION_REQ_LOCAL_ADDR_ATTRIBUTE) {
			// Get local host address
			requestLocalHostAddressAttr();
		} else if (actionCode == ActionCode.ACTION_REQ_REMOTEPORT_ATTRIBUTE) {
			// Get remote port
			requestRemotePortAttr();
		} else if (actionCode == ActionCode.ACTION_REQ_LOCALPORT_ATTRIBUTE) {
			// Get local port
			requestLocalPortAttr();
		} else if (actionCode == ActionCode.ACTION_REQ_SSL_ATTRIBUTE) {
			// request for the SSL attribute
			requestSSLAttr();
		} else if (actionCode == ActionCode.ACTION_REQ_SSL_CERTIFICATE) {
			// Request for the SSL certificate
			requestSSLCertificate();
		} else if (actionCode == ActionCode.ACTION_REQ_SET_BODY_REPLAY) {
			//
			requestSetBodyReplay(param);
		} else if (actionCode == ActionCode.ACTION_AVAILABLE) {
			// make the input buffer available
			makeAvailable();
		} else if (actionCode == ActionCode.ACTION_EVENT_BEGIN) {
			// Begin event
			beginEvent(param);
		} else if (actionCode == ActionCode.ACTION_EVENT_END) {
			// End event
			endEvent(param);
		} else if (actionCode == ActionCode.ACTION_EVENT_SUSPEND) {
			// Suspend event
			suspendEvent();
		} else if (actionCode == ActionCode.ACTION_EVENT_RESUME) {
			// Resume event
			resumeEvent(param);
		} else if (actionCode == ActionCode.ACTION_EVENT_WRITE) {
			// Write event
			writeEvent(param);
		} else if (actionCode == ActionCode.ACTION_EVENT_WRITE) {
			// Timeout event
			timeoutEvent(param);
		}
	}

	// ------------------------------------------------------ Protected Methods

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.coyote.http11.Http11AbstractProcessor#prepareRequest()
	 */
	protected void prepareRequest() {

		http11 = true;
		http09 = false;
		contentDelimitation = false;
		expectation = false;
		sendfileData = null;
		if (sslEnabled) {
			request.scheme().setString("https");
		}
		MessageBytes protocolMB = request.protocol();
		if (protocolMB.equals(Constants.HTTP_11)) {
			http11 = true;
			protocolMB.setString(Constants.HTTP_11);
		} else if (protocolMB.equals(Constants.HTTP_10)) {
			http11 = false;
			keepAlive = false;
			protocolMB.setString(Constants.HTTP_10);
		} else if (protocolMB.equals("")) {
			// HTTP/0.9
			http09 = true;
			http11 = false;
			keepAlive = false;
		} else {
			// Unsupported protocol
			http11 = false;
			error = true;
			// Send 505; Unsupported HTTP version
			response.setStatus(505);
		}

		MessageBytes methodMB = request.method();
		if (methodMB.equals(Constants.GET)) {
			methodMB.setString(Constants.GET);
		} else if (methodMB.equals(Constants.POST)) {
			methodMB.setString(Constants.POST);
		}

		MimeHeaders headers = request.getMimeHeaders();

		// Check connection header
		MessageBytes connectionValueMB = headers.getValue("connection");
		if (connectionValueMB != null) {
			ByteChunk connectionValueBC = connectionValueMB.getByteChunk();
			if (findBytes(connectionValueBC, Constants.CLOSE_BYTES) != -1) {
				keepAlive = false;
			} else if (findBytes(connectionValueBC, Constants.KEEPALIVE_BYTES) != -1) {
				keepAlive = true;
			}
		}

		MessageBytes expectMB = null;
		if (http11)
			expectMB = headers.getValue("expect");
		if ((expectMB != null) && (expectMB.indexOfIgnoreCase("100-continue", 0) != -1)) {
			inputBuffer.setSwallowInput(false);
			expectation = true;
		}

		// Check user-agent header
		if ((restrictedUserAgents != null) && ((http11) || (keepAlive))) {
			MessageBytes userAgentValueMB = headers.getValue("user-agent");
			// Check in the restricted list, and adjust the http11
			// and keepAlive flags accordingly
			if (userAgentValueMB != null) {
				String userAgentValue = userAgentValueMB.toString();
				for (int i = 0; i < restrictedUserAgents.length; i++) {
					if (restrictedUserAgents[i].matcher(userAgentValue).matches()) {
						http11 = false;
						keepAlive = false;
						break;
					}
				}
			}
		}

		// Check for a full URI (including protocol://host:port/)
		ByteChunk uriBC = request.requestURI().getByteChunk();
		if (uriBC.startsWithIgnoreCase("http", 0)) {

			int pos = uriBC.indexOf("://", 0, 3, 4);
			int uriBCStart = uriBC.getStart();
			int slashPos = -1;
			if (pos != -1) {
				byte[] uriB = uriBC.getBytes();
				slashPos = uriBC.indexOf('/', pos + 3);
				if (slashPos == -1) {
					slashPos = uriBC.getLength();
					// Set URI as "/"
					request.requestURI().setBytes(uriB, uriBCStart + pos + 1, 1);
				} else {
					request.requestURI().setBytes(uriB, uriBCStart + slashPos,
							uriBC.getLength() - slashPos);
				}
				MessageBytes hostMB = headers.setValue("host");
				hostMB.setBytes(uriB, uriBCStart + pos + 3, slashPos - pos - 3);
			}

		}

		// Input filter setup
		InputFilter[] inputFilters = inputBuffer.getFilters();

		// Parse transfer-encoding header
		MessageBytes transferEncodingValueMB = null;
		if (http11)
			transferEncodingValueMB = headers.getValue("transfer-encoding");
		if (transferEncodingValueMB != null) {
			String transferEncodingValue = transferEncodingValueMB.toString();
			// Parse the comma separated list. "identity" codings are ignored
			int startPos = 0;
			int commaPos = transferEncodingValue.indexOf(',');
			String encodingName = null;
			while (commaPos != -1) {
				encodingName = transferEncodingValue.substring(startPos, commaPos)
						.toLowerCase(Locale.ENGLISH).trim();
				if (!addInputFilter(inputFilters, encodingName)) {
					// Unsupported transfer encoding
					error = true;
					// 501 - Unimplemented
					response.setStatus(501);
				}
				startPos = commaPos + 1;
				commaPos = transferEncodingValue.indexOf(',', startPos);
			}
			encodingName = transferEncodingValue.substring(startPos).toLowerCase(Locale.ENGLISH)
					.trim();
			if (!addInputFilter(inputFilters, encodingName)) {
				// Unsupported transfer encoding
				error = true;
				// 501 - Unimplemented
				response.setStatus(501);
			}
		}

		// Parse content-length header
		long contentLength = request.getContentLengthLong();
		if (contentLength >= 0 && !contentDelimitation) {
			inputBuffer.addActiveFilter(inputFilters[Constants.IDENTITY_FILTER]);
			contentDelimitation = true;
		}

		MessageBytes valueMB = headers.getValue("host");

		// Check host header
		if (http11 && (valueMB == null)) {
			error = true;
			// 400 - Bad request
			response.setStatus(400);
		}

		parseHost(valueMB);

		if (!contentDelimitation) {
			// If there's no content length
			// (broken HTTP/1.0 or HTTP/1.1), assume
			// the client is not broken and didn't send a body
			inputBuffer.addActiveFilter(inputFilters[Constants.VOID_FILTER]);
			contentDelimitation = true;
		}

	}

	/**
	 * Parse host.
	 */
	protected void parseHost(MessageBytes valueMB) {

		if (valueMB == null || valueMB.isNull()) {
			// HTTP/1.0
			// Default is what the socket tells us. Overriden if a host is
			// found/parsed
			request.setServerPort(endpoint.getPort());
			return;
		}

		ByteChunk valueBC = valueMB.getByteChunk();
		byte[] valueB = valueBC.getBytes();
		int valueL = valueBC.getLength();
		int valueS = valueBC.getStart();
		int colonPos = -1;
		if (hostNameC.length < valueL) {
			hostNameC = new char[valueL];
		}

		boolean ipv6 = (valueB[valueS] == '[');
		boolean bracketClosed = false;
		for (int i = 0; i < valueL; i++) {
			char b = (char) valueB[i + valueS];
			hostNameC[i] = b;
			if (b == ']') {
				bracketClosed = true;
			} else if (b == ':') {
				if (!ipv6 || bracketClosed) {
					colonPos = i;
					break;
				}
			}
		}

		if (colonPos < 0) {
			if (!sslEnabled) {
				// 80 - Default HTTP port
				request.setServerPort(80);
			} else {
				// 443 - Default HTTPS port
				request.setServerPort(443);
			}
			request.serverName().setChars(hostNameC, 0, valueL);
		} else {

			request.serverName().setChars(hostNameC, 0, colonPos);

			int port = 0;
			int mult = 1;
			for (int i = valueL - 1; i > colonPos; i--) {
				int charValue = HexUtils.DEC[valueB[i + valueS]];
				if (charValue == -1) {
					// Invalid character
					error = true;
					// 400 - Bad request
					response.setStatus(400);
					break;
				}
				port = port + (charValue * mult);
				mult = 10 * mult;
			}
			request.setServerPort(port);

		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.coyote.http11.Http11AbstractProcessor#prepareResponse()
	 */
	protected void prepareResponse() {

		boolean entityBody = true;
		contentDelimitation = false;

		OutputFilter[] outputFilters = outputBuffer.getFilters();

		if (http09 == true) {
			// HTTP/0.9
			outputBuffer.addActiveFilter(outputFilters[Constants.IDENTITY_FILTER]);
			return;
		}

		int statusCode = response.getStatus();
		if ((statusCode == 204) || (statusCode == 205) || (statusCode == 304)) {
			// No entity body
			outputBuffer.addActiveFilter(outputFilters[Constants.VOID_FILTER]);
			entityBody = false;
			contentDelimitation = true;
		}

		MessageBytes methodMB = request.method();
		if (methodMB.equals("HEAD")) {
			// No entity body
			outputBuffer.addActiveFilter(outputFilters[Constants.VOID_FILTER]);
			contentDelimitation = true;
		}

		// Sendfile support
		if (response.getSendfilePath() != null) {
			// No entity body sent here
			outputBuffer.addActiveFilter(outputFilters[Constants.VOID_FILTER]);
			contentDelimitation = true;
			sendfileData = new NioEndpoint.SendfileData();
			sendfileData.setFileName(response.getSendfilePath());
			sendfileData.setStart(response.getSendfileStart());
			sendfileData.setEnd(response.getSendfileEnd());
		}

		// Check for compression
		boolean useCompression = false;
		if (entityBody && (compressionLevel > 0) && (sendfileData == null)) {
			useCompression = isCompressable();
			// Change content-length to -1 to force chunking
			if (useCompression) {
				response.setContentLength(-1);
			}
		}

		MimeHeaders headers = response.getMimeHeaders();
		if (!entityBody) {
			response.setContentLength(-1);
		} else {
			String contentType = response.getContentType();
			if (contentType != null) {
				headers.setValue("Content-Type").setString(contentType);
			}
			String contentLanguage = response.getContentLanguage();
			if (contentLanguage != null) {
				headers.setValue("Content-Language").setString(contentLanguage);
			}
		}

		long contentLength = response.getContentLengthLong();
		if (contentLength != -1) {
			headers.setValue("Content-Length").setLong(contentLength);
			outputBuffer.addActiveFilter(outputFilters[Constants.IDENTITY_FILTER]);
			contentDelimitation = true;
		} else {
			if (entityBody && http11 && (keepAlive || CHUNK_ON_CLOSE)) {
				outputBuffer.addActiveFilter(outputFilters[Constants.CHUNKED_FILTER]);
				contentDelimitation = true;
				headers.addValue(Constants.TRANSFERENCODING).setString(Constants.CHUNKED);
			} else {
				outputBuffer.addActiveFilter(outputFilters[Constants.IDENTITY_FILTER]);
			}
		}

		if (useCompression) {
			outputBuffer.addActiveFilter(outputFilters[Constants.GZIP_FILTER]);
			headers.setValue("Content-Encoding").setString("gzip");
			// Make Proxies happy via Vary (from mod_deflate)
			headers.addValue("Vary").setString("Accept-Encoding");
		}

		// Add date header
		headers.setValue("Date").setString(FastHttpDateFormat.getCurrentDate());

		// FIXME: Add transfer encoding header

		if ((entityBody) && (!contentDelimitation)) {
			// Mark as close the connection after the request, and add the
			// connection: close header
			keepAlive = false;
		}

		// If we know that the request is bad this early, add the
		// Connection: close header.
		keepAlive = keepAlive && !statusDropsConnection(statusCode);
		if (!keepAlive) {
			headers.addValue(Constants.CONNECTION).setString(Constants.CLOSE);
		} else if (!http11 && !error) {
			headers.addValue(Constants.CONNECTION).setString(Constants.KEEPALIVE);
		}

		// Build the response header
		outputBuffer.sendStatus();

		// Add server header
		if (server != null) {
			headers.setValue("Server").setString(server);
		} else {
			outputBuffer.write(Constants.SERVER_BYTES);
		}

		int size = headers.size();
		for (int i = 0; i < size; i++) {
			outputBuffer.sendHeader(headers.getName(i), headers.getValue(i));
		}
		outputBuffer.endHeaders();

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.coyote.http11.Http11AbstractProcessor#initializeFilters()
	 */
	protected void initializeFilters() {

		// Create and add the identity filters.
		inputBuffer.addFilter(new IdentityInputFilter());
		outputBuffer.addFilter(new IdentityOutputFilter());

		// Create and add the chunked filters.
		inputBuffer.addFilter(new ChunkedInputFilter());
		outputBuffer.addFilter(new ChunkedOutputFilter());

		// Create and add the void filters.
		inputBuffer.addFilter(new VoidInputFilter());
		outputBuffer.addFilter(new VoidOutputFilter());

		// Create and add the chunked filters.
		// inputBuffer.addFilter(new GzipInputFilter());
		outputBuffer.addFilter(new GzipOutputFilter());

	}

	/**
	 * Add an input filter to the current request.
	 * 
	 * @return false if the encoding was not found (which would mean it is
	 *         unsupported)
	 */
	protected boolean addInputFilter(InputFilter[] inputFilters, String encodingName) {
		if (encodingName.equals("identity")) {
			// Skip
		} else if (encodingName.equals("chunked")) {
			inputBuffer.addActiveFilter(inputFilters[Constants.CHUNKED_FILTER]);
			contentDelimitation = true;
		} else {
			for (int i = 2; i < inputFilters.length; i++) {
				if (inputFilters[i].getEncodingName().toString().equals(encodingName)) {
					inputBuffer.addActiveFilter(inputFilters[i]);
					return true;
				}
			}
			return false;
		}
		return true;
	}

}
