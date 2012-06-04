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
package org.apache.tomcat.util.net;

import java.net.InetAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tomcat.util.res.StringManager;

/**
 * {@code AbstractEndpoint}
 * 
 * Created on Dec 14, 2011 at 2:58:58 PM
 * 
 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
 */
public abstract class AbstractEndpoint {

	protected static StringManager sm = StringManager.getManager("org.apache.tomcat.util.net.res");

	/**
	 * The default socket timeout = 60 sec
	 */
	protected static final int DEFAULT_SO_TIMEOUT = 60 * 1000;

	/**
	 * Running state of the endpoint.
	 */
	protected volatile boolean running = false;

	/**
	 * Will be set to true whenever the endpoint is paused.
	 */
	protected volatile boolean paused = false;

	/**
	 * Track the initialization state of the endpoint.
	 */
	protected boolean initialized = false;

	/**
	 * The number of open connections
	 */
	protected AtomicInteger counter = new AtomicInteger();

	/**
	 * 
	 */
	protected boolean reuseAddress = false;

	/**
	 * Current worker threads busy count.
	 */
	protected int curThreadsBusy = 0;

	/**
	 * Current worker threads count.
	 */
	protected int curThreads = 0;

	/**
	 * Sequence number used to generate thread names.
	 */
	protected int sequence = 0;

	/**
	 * Acceptor thread count.
	 */
	protected int acceptorThreadCount = 0;

	/**
	 * External Executor based thread pool.
	 */
	protected Executor executor = null;
	/**
	 * Maximum amount of worker threads.
	 */
	protected int maxThreads = (org.apache.tomcat.util.Constants.LOW_MEMORY) ? 32 : 32 * Runtime
			.getRuntime().availableProcessors();

	/**
	 * The maximum number of connections
	 */
	protected int maxConnections = 1024;

	/**
	 * Priority of the acceptor and poller threads.
	 */
	protected int threadPriority = Thread.NORM_PRIORITY;

	/**
	 * Size of the sendfile (= concurrent files which can be served).
	 */
	protected int sendfileSize = -1;

	/**
	 * Server socket port.
	 */
	protected int port;

	/**
	 * Address for the server socket.
	 */
	protected InetAddress address;

	/**
	 * The default thread factory
	 */
	protected ThreadFactory threadFactory;

	/**
	 * Allows the server developer to specify the backlog that should be used
	 * for server sockets. By default, this value is 100.
	 */
	// protected int backlog = 100;
	protected int backlog = 511;

	/**
	 * Socket TCP no delay.
	 */
	protected boolean tcpNoDelay = true;

	/**
	 * Socket linger.
	 */
	protected int soLinger = 100;
	/**
	 * Socket timeout.
	 */
	protected int soTimeout = -1;
	/**
	 * Defer accept.
	 */
	protected boolean deferAccept = true;

	/**
	 * Keep-Alive timeout.
	 */
	protected int keepAliveTimeout = -1;

	/**
	 * The default is true - the created threads will be in daemon mode. If set
	 * to false, the control thread will not be daemon - and will keep the
	 * process alive.
	 */
	protected boolean daemon = true;
	/**
	 * Name of the thread pool, which will be used for naming child threads.
	 */
	protected String name = "TP";
	/**
	 * Use sendfile for sending static files.
	 */
	protected boolean useSendfile = true;

	/**
	 * Reverse connection. In this proxied mode, the endpoint will not use a
	 * server socket, but will connect itself to the front end server.
	 */
	protected boolean reverseConnection = false;
	/**
	 * SSL engine.
	 */
	protected boolean SSLEnabled = false;

	/**
	 * SSL protocols.
	 */
	protected String SSLProtocol = "all";

	/**
	 * SSL password (if a cert is encrypted, and no password has been provided,
	 * a callback will ask for a password).
	 */
	protected String SSLPassword = null;

	/**
	 * SSL cipher suite.
	 */
	protected String SSLCipherSuite = "ALL";
	/**
	 * SSL certificate file.
	 */
	protected String SSLCertificateFile = null;
	/**
	 * SSL certificate chain file.
	 */
	protected String SSLCertificateChainFile = null;

	/**
	 * SSL CA certificate path.
	 */
	protected String SSLCACertificatePath = null;

	/**
	 * SSL CA certificate file.
	 */
	protected String SSLCACertificateFile = null;
	/**
	 * SSL CA revocation path.
	 */
	protected String SSLCARevocationPath = null;
	/**
	 * SSL CA revocation file.
	 */
	protected String SSLCARevocationFile = null;
	/**
	 * SSL verify client.
	 */
	protected String SSLVerifyClient = "none";
	/**
	 * SSL verify depth.
	 */
	protected int SSLVerifyDepth = 10;
	/**
	 * SSL allow insecure renegotiation for the the client that does not support
	 * the secure renegotiation.
	 */
	protected boolean SSLInsecureRenegotiation = false;
	/**
	 * SSL certificate key file.
	 */
	protected String SSLCertificateKeyFile = null;

	/**
	 * Initialize the endpoint
	 * 
	 * @throws Exception
	 */
	public abstract void init() throws Exception;

	/**
	 * Start the endpoint, creating acceptor, poller and sendfile threads, etc.
	 * 
	 * @throws Exception
	 */
	public abstract void start() throws Exception;

	/**
	 * Pause the endpoint, which will make it stop accepting new sockets.
	 */
	public void pause() {
		if (running && !paused) {
			paused = true;
			unlockAccept();
		}
	}

	/**
	 * Resume the endpoint, which will make it start accepting new connections
	 * again.
	 */
	public void resume() {
		if (running) {
			paused = false;
		}
	}

	/**
	 * Stop the endpoint. This will cause all processing threads to stop.
	 */
	public abstract void stop();

	/**
	 * Deallocate the memory pools, and close server socket.
	 * 
	 * @throws Exception
	 */
	public abstract void destroy() throws Exception;

	/**
	 * Unlock the server socket accept using a bogus connection.
	 */
	protected void unlockAccept() {
		java.net.Socket s = null;
		try {
			// Need to create a connection to unlock the accept();
			if (address == null) {
				s = new java.net.Socket("localhost", port);
			} else {
				s = new java.net.Socket(address, port);
				// setting soLinger to a small value will help shutdown the
				// connection quicker
				s.setSoLinger(true, 0);
			}
			// If deferAccept is enabled, send at least one byte
			if (deferAccept) {
				s.getOutputStream().write(" ".getBytes());
				s.getOutputStream().flush();
			}
		} catch (Exception e) {
			// Ignore
		} finally {
			if (s != null) {
				try {
					s.close();
				} catch (Exception e) {
					// Ignore
				}
			}
		}
	}

	/**
	 * Create a new thread for the specified target
	 *  
	 * @param target
	 * @param name
	 * @param daemon
	 * @return an instance of a new thread
	 */
	protected Thread newThread(Runnable target, String name, boolean daemon) {
		Thread thread = this.threadFactory != null ? this.threadFactory.newThread(target)
				: new Thread(target);

		thread.setName(getName() +"-" + name);
		thread.setPriority(threadPriority);
		thread.setDaemon(daemon);

		return thread;
	}

	// --------------------------------------------
	/**
	 * Getter for running
	 * 
	 * @return the running
	 */
	public boolean isRunning() {
		return this.running;
	}

	/**
	 * Setter for the running
	 * 
	 * @param running
	 *            the running to set
	 */
	public void setRunning(boolean running) {
		this.running = running;
	}

	/**
	 * Getter for paused
	 * 
	 * @return the paused
	 */
	public boolean isPaused() {
		return this.paused;
	}

	/**
	 * Setter for the paused
	 * 
	 * @param paused
	 *            the paused to set
	 */
	public void setPaused(boolean paused) {
		this.paused = paused;
	}

	/**
	 * Getter for initialized
	 * 
	 * @return the initialized
	 */
	public boolean isInitialized() {
		return this.initialized;
	}

	/**
	 * Setter for the initialized
	 * 
	 * @param initialized
	 *            the initialized to set
	 */
	public void setInitialized(boolean initialized) {
		this.initialized = initialized;
	}

	/**
	 * Getter for curThreadsBusy
	 * 
	 * @return the curThreadsBusy
	 */
	public int getCurThreadsBusy() {
		return this.curThreadsBusy;
	}

	/**
	 * Setter for the curThreadsBusy
	 * 
	 * @param curThreadsBusy
	 *            the curThreadsBusy to set
	 */
	public void setCurThreadsBusy(int curThreadsBusy) {
		this.curThreadsBusy = curThreadsBusy;
	}

	/**
	 * Getter for curThreads
	 * 
	 * @return the curThreads
	 */
	public int getCurThreads() {
		return this.curThreads;
	}

	/**
	 * Setter for the curThreads
	 * 
	 * @param curThreads
	 *            the curThreads to set
	 */
	public void setCurThreads(int curThreads) {
		this.curThreads = curThreads;
	}

	/**
	 * Getter for sequence
	 * 
	 * @return the sequence
	 */
	public int getSequence() {
		return this.sequence++;
	}

	/**
	 * Setter for the sequence
	 * 
	 * @param sequence
	 *            the sequence to set
	 */
	public void setSequence(int sequence) {
		this.sequence = sequence;
	}

	/**
	 * Getter for executor
	 * 
	 * @return the executor
	 */
	public Executor getExecutor() {
		return this.executor;
	}

	/**
	 * Setter for the executor
	 * 
	 * @param executor
	 *            the executor to set
	 */
	public void setExecutor(Executor executor) {
		this.executor = executor;
	}

	/**
	 * Getter for maxThreads
	 * 
	 * @return the maxThreads
	 */
	public int getMaxThreads() {
		return this.maxThreads;
	}

	/**
	 * Setter for the maxThreads
	 * 
	 * @param maxThreads
	 *            the maxThreads to set
	 */
	public void setMaxThreads(int maxThreads) {
		this.maxThreads = maxThreads;
	}

	/**
	 * Getter for maxConnections
	 * 
	 * @return the maxConnections
	 */
	public int getMaxConnections() {
		return this.maxConnections;
	}

	/**
	 * Setter for the maxConnections
	 * 
	 * @param maxConnections
	 *            the maxConnections to set
	 */
	public void setMaxConnections(int maxConnections) {
		this.maxConnections = maxConnections;
	}

	/**
	 * Getter for threadPriority
	 * 
	 * @return the threadPriority
	 */
	public int getThreadPriority() {
		return this.threadPriority;
	}

	/**
	 * Setter for the threadPriority
	 * 
	 * @param threadPriority
	 *            the threadPriority to set
	 */
	public void setThreadPriority(int threadPriority) {
		this.threadPriority = threadPriority;
	}

	/**
	 * Getter for sendfileSize
	 * 
	 * @return the sendfileSize
	 */
	public int getSendfileSize() {
		return this.sendfileSize;
	}

	/**
	 * Setter for the sendfileSize
	 * 
	 * @param sendfileSize
	 *            the sendfileSize to set
	 */
	public void setSendfileSize(int sendfileSize) {
		this.sendfileSize = sendfileSize;
	}

	/**
	 * Getter for port
	 * 
	 * @return the port
	 */
	public int getPort() {
		return this.port;
	}

	/**
	 * Setter for the port
	 * 
	 * @param port
	 *            the port to set
	 */
	public void setPort(int port) {
		this.port = port;
	}

	/**
	 * Getter for address
	 * 
	 * @return the address
	 */
	public InetAddress getAddress() {
		return this.address;
	}

	/**
	 * Setter for the address
	 * 
	 * @param address
	 *            the address to set
	 */
	public void setAddress(InetAddress address) {
		this.address = address;
	}

	/**
	 * Getter for backlog
	 * 
	 * @return the backlog
	 */
	public int getBacklog() {
		return this.backlog;
	}

	/**
	 * Setter for the backlog
	 * 
	 * @param backlog
	 *            the backlog to set
	 */
	public void setBacklog(int backlog) {
		if (backlog > 0)
			this.backlog = backlog;
	}

	/**
	 * Getter for tcpNoDelay
	 * 
	 * @return the tcpNoDelay
	 */
	public boolean getTcpNoDelay() {
		return this.tcpNoDelay;
	}

	/**
	 * Setter for the tcpNoDelay
	 * 
	 * @param tcpNoDelay
	 *            the tcpNoDelay to set
	 */
	public void setTcpNoDelay(boolean tcpNoDelay) {
		this.tcpNoDelay = tcpNoDelay;
	}

	/**
	 * Getter for soLinger
	 * 
	 * @return the soLinger
	 */
	public int getSoLinger() {
		return this.soLinger;
	}

	/**
	 * Setter for the soLinger
	 * 
	 * @param soLinger
	 *            the soLinger to set
	 */
	public void setSoLinger(int soLinger) {
		this.soLinger = soLinger;
	}

	/**
	 * Getter for soTimeout
	 * 
	 * @return the soTimeout
	 */
	public int getSoTimeout() {
		return this.soTimeout;
	}

	/**
	 * Setter for the soTimeout
	 * 
	 * @param soTimeout
	 *            the soTimeout to set
	 */
	public void setSoTimeout(int soTimeout) {
		this.soTimeout = soTimeout;
	}

	/**
	 * Getter for deferAccept
	 * 
	 * @return the deferAccept
	 */
	public boolean getDeferAccept() {
		return this.deferAccept;
	}

	/**
	 * Setter for the deferAccept
	 * 
	 * @param deferAccept
	 *            the deferAccept to set
	 */
	public void setDeferAccept(boolean deferAccept) {
		this.deferAccept = deferAccept;
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
	 * Getter for daemon
	 * 
	 * @return the daemon
	 */
	public boolean getDaemon() {
		return this.daemon;
	}

	/**
	 * Setter for the daemon
	 * 
	 * @param daemon
	 *            the daemon to set
	 */
	public void setDaemon(boolean daemon) {
		this.daemon = daemon;
	}

	/**
	 * Getter for name
	 * 
	 * @return the name
	 */
	public String getName() {
		return this.name;
	}

	/**
	 * Setter for the name
	 * 
	 * @param name
	 *            the name to set
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Getter for useSendfile
	 * 
	 * @return the useSendfile
	 */
	public boolean getUseSendfile() {
		return this.useSendfile;
	}

	/**
	 * Setter for the useSendfile
	 * 
	 * @param useSendfile
	 *            the useSendfile to set
	 */
	public void setUseSendfile(boolean useSendfile) {
		this.useSendfile = useSendfile;
	}

	/**
	 * Getter for reverseConnection
	 * 
	 * @return the reverseConnection
	 */
	public boolean isReverseConnection() {
		return this.reverseConnection;
	}

	/**
	 * Setter for the reverseConnection
	 * 
	 * @param reverseConnection
	 *            the reverseConnection to set
	 */
	public void setReverseConnection(boolean reverseConnection) {
		this.reverseConnection = reverseConnection;
	}

	/**
	 * Getter for sSLEnabled
	 * 
	 * @return the sSLEnabled
	 */
	public boolean getSSLEnabled() {
		return this.SSLEnabled;
	}

	/**
	 * Setter for the sSLEnabled
	 * 
	 * @param sSLEnabled
	 *            the sSLEnabled to set
	 */
	public void setSSLEnabled(boolean sSLEnabled) {
		this.SSLEnabled = sSLEnabled;
	}

	/**
	 * Getter for sSLProtocol
	 * 
	 * @return the sSLProtocol
	 */
	public String getSSLProtocol() {
		return this.SSLProtocol;
	}

	/**
	 * Setter for the sSLProtocol
	 * 
	 * @param sSLProtocol
	 *            the sSLProtocol to set
	 */
	public void setSSLProtocol(String sSLProtocol) {
		this.SSLProtocol = sSLProtocol;
	}

	/**
	 * Getter for sSLPassword
	 * 
	 * @return the sSLPassword
	 */
	public String getSSLPassword() {
		return this.SSLPassword;
	}

	/**
	 * Setter for the sSLPassword
	 * 
	 * @param sSLPassword
	 *            the sSLPassword to set
	 */
	public void setSSLPassword(String sSLPassword) {
		this.SSLPassword = sSLPassword;
	}

	/**
	 * Getter for sSLCipherSuite
	 * 
	 * @return the sSLCipherSuite
	 */
	public String getSSLCipherSuite() {
		return this.SSLCipherSuite;
	}

	/**
	 * Setter for the sSLCipherSuite
	 * 
	 * @param sSLCipherSuite
	 *            the sSLCipherSuite to set
	 */
	public void setSSLCipherSuite(String sSLCipherSuite) {
		this.SSLCipherSuite = sSLCipherSuite;
	}

	/**
	 * Getter for sSLCertificateFile
	 * 
	 * @return the sSLCertificateFile
	 */
	public String getSSLCertificateFile() {
		return this.SSLCertificateFile;
	}

	/**
	 * Setter for the sSLCertificateFile
	 * 
	 * @param sSLCertificateFile
	 *            the sSLCertificateFile to set
	 */
	public void setSSLCertificateFile(String sSLCertificateFile) {
		this.SSLCertificateFile = sSLCertificateFile;
	}

	/**
	 * Getter for sSLCertificateChainFile
	 * 
	 * @return the sSLCertificateChainFile
	 */
	public String getSSLCertificateChainFile() {
		return this.SSLCertificateChainFile;
	}

	/**
	 * Setter for the sSLCertificateChainFile
	 * 
	 * @param sSLCertificateChainFile
	 *            the sSLCertificateChainFile to set
	 */
	public void setSSLCertificateChainFile(String sSLCertificateChainFile) {
		this.SSLCertificateChainFile = sSLCertificateChainFile;
	}

	/**
	 * Getter for sSLCACertificatePath
	 * 
	 * @return the sSLCACertificatePath
	 */
	public String getSSLCACertificatePath() {
		return this.SSLCACertificatePath;
	}

	/**
	 * Setter for the sSLCACertificatePath
	 * 
	 * @param sSLCACertificatePath
	 *            the sSLCACertificatePath to set
	 */
	public void setSSLCACertificatePath(String sSLCACertificatePath) {
		this.SSLCACertificatePath = sSLCACertificatePath;
	}

	/**
	 * Getter for sSLCACertificateFile
	 * 
	 * @return the sSLCACertificateFile
	 */
	public String getSSLCACertificateFile() {
		return this.SSLCACertificateFile;
	}

	/**
	 * Setter for the sSLCACertificateFile
	 * 
	 * @param sSLCACertificateFile
	 *            the sSLCACertificateFile to set
	 */
	public void setSSLCACertificateFile(String sSLCACertificateFile) {
		this.SSLCACertificateFile = sSLCACertificateFile;
	}

	/**
	 * Getter for sSLCARevocationPath
	 * 
	 * @return the sSLCARevocationPath
	 */
	public String getSSLCARevocationPath() {
		return this.SSLCARevocationPath;
	}

	/**
	 * Setter for the sSLCARevocationPath
	 * 
	 * @param sSLCARevocationPath
	 *            the sSLCARevocationPath to set
	 */
	public void setSSLCARevocationPath(String sSLCARevocationPath) {
		this.SSLCARevocationPath = sSLCARevocationPath;
	}

	/**
	 * Getter for sSLCARevocationFile
	 * 
	 * @return the sSLCARevocationFile
	 */
	public String getSSLCARevocationFile() {
		return this.SSLCARevocationFile;
	}

	/**
	 * Setter for the sSLCARevocationFile
	 * 
	 * @param sSLCARevocationFile
	 *            the sSLCARevocationFile to set
	 */
	public void setSSLCARevocationFile(String sSLCARevocationFile) {
		this.SSLCARevocationFile = sSLCARevocationFile;
	}

	/**
	 * Getter for sSLVerifyClient
	 * 
	 * @return the sSLVerifyClient
	 */
	public String getSSLVerifyClient() {
		return this.SSLVerifyClient;
	}

	/**
	 * Setter for the sSLVerifyClient
	 * 
	 * @param sSLVerifyClient
	 *            the sSLVerifyClient to set
	 */
	public void setSSLVerifyClient(String sSLVerifyClient) {
		this.SSLVerifyClient = sSLVerifyClient;
	}

	/**
	 * Getter for sSLVerifyDepth
	 * 
	 * @return the sSLVerifyDepth
	 */
	public int getSSLVerifyDepth() {
		return this.SSLVerifyDepth;
	}

	/**
	 * Setter for the sSLVerifyDepth
	 * 
	 * @param sSLVerifyDepth
	 *            the sSLVerifyDepth to set
	 */
	public void setSSLVerifyDepth(int sSLVerifyDepth) {
		this.SSLVerifyDepth = sSLVerifyDepth;
	}

	/**
	 * Getter for sSLInsecureRenegotiation
	 * 
	 * @return the sSLInsecureRenegotiation
	 */
	public boolean getSSLInsecureRenegotiation() {
		return this.SSLInsecureRenegotiation;
	}

	/**
	 * Setter for the sSLInsecureRenegotiation
	 * 
	 * @param sSLInsecureRenegotiation
	 *            the sSLInsecureRenegotiation to set
	 */
	public void setSSLInsecureRenegotiation(boolean sSLInsecureRenegotiation) {
		this.SSLInsecureRenegotiation = sSLInsecureRenegotiation;
	}

	/**
	 * Getter for sSLCertificateKeyFile
	 * 
	 * @return the sSLCertificateKeyFile
	 */
	public String getSSLCertificateKeyFile() {
		return this.SSLCertificateKeyFile;
	}

	/**
	 * Setter for the sSLCertificateKeyFile
	 * 
	 * @param sSLCertificateKeyFile
	 *            the sSLCertificateKeyFile to set
	 */
	public void setSSLCertificateKeyFile(String sSLCertificateKeyFile) {
		this.SSLCertificateKeyFile = sSLCertificateKeyFile;
	}

	/**
	 * Getter for reuseAddress
	 * 
	 * @return the reuseAddress
	 */
	public boolean isReuseAddress() {
		return this.reuseAddress;
	}

	/**
	 * Setter for the reuseAddress
	 * 
	 * @param reuseAddress
	 *            the reuseAddress to set
	 */
	public void setReuseAddress(boolean reuseAddress) {
		this.reuseAddress = reuseAddress;
	}

	/**
	 * Getter for acceptorThreadCount
	 * 
	 * @return the acceptorThreadCount
	 */
	public int getAcceptorThreadCount() {
		return this.acceptorThreadCount;
	}

	/**
	 * Setter for the acceptorThreadCount
	 * 
	 * @param acceptorThreadCount
	 *            the acceptorThreadCount to set
	 */
	public void setAcceptorThreadCount(int acceptorThreadCount) {
		this.acceptorThreadCount = acceptorThreadCount;
	}

}
