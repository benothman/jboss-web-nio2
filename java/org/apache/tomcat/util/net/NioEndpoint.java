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

import java.io.IOException;
import java.net.BindException;
import java.net.StandardSocketOptions;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;

import org.apache.tomcat.jni.File;
import org.apache.tomcat.util.net.AprEndpoint.Sendfile;
import org.apache.tomcat.util.net.jsse.NioJSSESocketChannelFactory;
import org.jboss.logging.Logger;

/**
 * {@code NioEndpoint} NIO2 endpoint, providing the following services:
 * <ul>
 * <li>Socket channel acceptor thread</li>
 * <li>Socket poller thread</li>
 * <li>Sendfile thread</li>
 * <li>Simple Worker thread pool, with possible use of executors</li>
 * </ul>
 * 
 * Created on Dec 13, 2011 at 9:41:53 AM
 * 
 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
 */
public class NioEndpoint extends AbstractEndpoint {

	protected static Logger logger = Logger.getLogger(NioEndpoint.class);

	protected AsynchronousServerSocketChannel listener;
	private ThreadFactory threadFactory;
	protected Map<String, Object> attributes = new HashMap<String, Object>();

	/**
	 * Available workers.
	 */
	protected WorkerStack workerStack = null;

	/**
	 * Handling of accepted sockets.
	 */
	protected Handler handler = null;

	protected ListChannel[] listChannel = null;

	protected ChannelList channelList;

	protected NioServerSocketChannelFactory serverSocketChannelFactory = null;

	/**
	 * The static file sender.
	 */
	protected Sendfile sendfile = null;

	/**
	 * SSL context.
	 */
	protected SSLContext sslContext;

	/**
	 * Create a new instance of {@code NioEndpoint}
	 */
	public NioEndpoint() {
		super();
	}

	// ----------------------- Getters and Setters -----------------------

	/**
	 * @param handler
	 */
	public void setHandler(Handler handler) {
		this.handler = handler;
	}

	/**
	 * @return the handler
	 */
	public Handler getHandler() {
		return handler;
	}

	// --------------------------------------------------------- Public Methods

	/**
	 * Number of keep-alive channels.
	 * 
	 * @return the number of connection
	 */
	public int getKeepAliveCount() {
		return channelList.size();
	}

	/**
	 * Return the amount of threads that are managed by the pool.
	 * 
	 * @return the amount of threads that are managed by the pool
	 */
	public int getCurrentThreadCount() {
		return curThreads;
	}

	/**
	 * Return the amount of threads currently busy.
	 * 
	 * @return the amount of threads currently busy
	 */
	public int getCurrentThreadsBusy() {
		return curThreadsBusy;
	}

	// ----------------------------------------------- Public Lifecycle Methods

	/**
	 * Getter for sslContext
	 * 
	 * @return the sslContext
	 */
	public SSLContext getSslContext() {
		return this.sslContext;
	}

	/**
	 * Setter for the sslContext
	 * 
	 * @param sslContext
	 *            the sslContext to set
	 */
	public void setSslContext(SSLContext sslContext) {
		this.sslContext = sslContext;
	}

	/**
	 * Getter for sendfile
	 * 
	 * @return the sendfile
	 */
	public Sendfile getSendfile() {
		return this.sendfile;
	}

	/**
	 * Setter for the sendfile
	 * 
	 * @param sendfile
	 *            the sendfile to set
	 */
	public void setSendfile(Sendfile sendfile) {
		this.sendfile = sendfile;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.tomcat.util.net.AbstractEndpoint#init()
	 */
	@Override
	public void init() throws Exception {
		if (initialized) {
			return;
		}

		// Initialize thread count defaults for acceptor
		if (acceptorThreadCount <= 0) {
			acceptorThreadCount = 1;
		}

		// Create the thread factory
		if (this.threadFactory == null) {
			this.threadFactory = new DefaultThreadFactory(getName() + "-", threadPriority);
		}

		this.channelList = new ChannelList(getMaxThreads());
		ExecutorService executorService;
		if (this.executor != null) {
			executorService = (ExecutorService) this.executor;
		} else {
			// Create the executor service
			executorService = Executors.newFixedThreadPool(getMaxThreads(), this.threadFactory);
			// initialize the endpoint executor
			setExecutor(executorService);
		}
		AsynchronousChannelGroup threadGroup = AsynchronousChannelGroup
				.withThreadPool(executorService);

		if (this.serverSocketChannelFactory == null) {
			this.serverSocketChannelFactory = NioServerSocketChannelFactory
					.createServerSocketChannelFactory(threadGroup, SSLEnabled);

			// Initialize the SSL context if the SSL mode is enabled
			if (SSLEnabled) {
				NioJSSESocketChannelFactory factory = (NioJSSESocketChannelFactory) this.serverSocketChannelFactory;
				for (String key : this.attributes.keySet()) {
					factory.setAttribute(key, this.attributes.get(key));
				}
				if (sslContext == null) {
					sslContext = factory.getSslContext();
				} else {
					factory.setSslContext(sslContext);
				}
			}
			// Initialize the channel factory
			this.serverSocketChannelFactory.init();
		}

		if (listener == null) {
			try {
				if (address == null) {
					listener = this.serverSocketChannelFactory.createServerChannel(port, backlog);
				} else {
					listener = this.serverSocketChannelFactory.createServerChannel(port, backlog,
							address);
				}

				listener.setOption(StandardSocketOptions.SO_REUSEADDR, this.reuseAddress);
			} catch (BindException be) {
				logger.fatal(be.getMessage(), be);
				if (address == null) {
					throw new BindException(be.getMessage() + "<null>:" + port);
				} else {
					throw new BindException(be.getMessage() + " " + address.toString() + ":" + port);
				}
			}
		}

		initialized = true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.tomcat.util.net.AbstractEndpoint#start()
	 */
	@Override
	public void start() throws Exception {
		// Initialize channel if not done before
		if (!initialized) {
			init();
		}
		if (!running) {
			running = true;
			paused = false;

			// Create worker collection
			if (executor == null) {
				workerStack = new WorkerStack(maxThreads);
			}

			// Start acceptor threads
			for (int i = 0; i < acceptorThreadCount; i++) {
				Thread acceptorThread = this.threadFactory.newThread(new Acceptor());
				acceptorThread.setDaemon(daemon);
				acceptorThread.start();
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.tomcat.util.net.AbstractEndpoint#stop()
	 */
	@Override
	public void stop() {
		if (running) {
			running = false;
			unlockAccept();
			// TODO complete implementation
		}
	}

	/**
	 * 
	 * @param attributes
	 */
	public void setSSLAttributes(Map<String, Object> attributes) {
		if (this.attributes == null) {
			this.attributes = attributes;
		} else {
			this.attributes.putAll(attributes);
		}
	}

	/**
	 * 
	 * @param name
	 * @param value
	 */
	public void setSSLAttribute(String name, Object value) {
		if (name != null && value != null) {
			this.attributes.put(name, value);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.tomcat.util.net.AbstractEndpoint#destroy()
	 */
	@Override
	public void destroy() throws Exception {
		if (running) {
			stop();
		}
		if (listener != null) {
			try {
				listener.close();
			} catch (IOException e) {
				logger.error(sm.getString("endpoint.err.close"), e);
			} finally {
				listener = null;
			}
		}

		if (workerStack != null) {
			// All workers will stop automatically since they already received
			// the interruption signal
			for (Worker worker : workerStack.workers) {
				if (worker != null && worker.channel != null) {
					worker.channel.close(true);
				}
			}
		}

		this.serverSocketChannelFactory = null;
		sslContext = null;
		initialized = false;
	}

	/**
	 * Configure the channel options
	 */
	protected boolean setChannelOptions(NioChannel channel) {
		// Process the connection
		int step = 1;
		try {
			// 1: Set socket options: timeout, linger, etc
			if (keepAliveTimeout > 0) {
				channel.setOption(StandardSocketOptions.SO_KEEPALIVE, Boolean.TRUE);
			}
			if (soLinger >= 0) {
				channel.setOption(StandardSocketOptions.SO_LINGER, soLinger);
			}
			if (tcpNoDelay) {
				channel.setOption(StandardSocketOptions.TCP_NODELAY, tcpNoDelay);
			}

			// 2: SSL handshake
			serverSocketChannelFactory.initChannel(channel);
			serverSocketChannelFactory.handshake(channel);

			step = 2;
		} catch (Throwable t) {
			if (logger.isDebugEnabled()) {
				if (step == 2) {
					logger.debug(sm.getString("endpoint.err.handshake"), t);
				} else {
					logger.debug(sm.getString("endpoint.err.unexpected"), t);
				}
			}
			// Tell to close the socket
			return false;
		}
		return true;
	}

	/**
	 * 
	 * @param channel
	 * @param timeout
	 * @param flag
	 */
	public void addChannel(NioChannel channel, int timeout, int flag) {
		this.channelList.add(channel, timeout, flag);
	}

	/**
	 * Create (or allocate) and return an available processor for use in
	 * processing a specific HTTP request, if possible. If the maximum allowed
	 * processors have already been created and are in use, return
	 * <code>null</code> instead.
	 */
	protected Worker createWorkerThread() {

		synchronized (workerStack) {
			if (workerStack.size() > 0) {
				curThreadsBusy++;
				return (workerStack.pop());
			}
			if ((maxThreads > 0) && (curThreads < maxThreads)) {
				curThreadsBusy++;
				if (curThreadsBusy == maxThreads) {
					logger.info(sm.getString("endpoint.info.maxThreads",
							Integer.toString(maxThreads), address, Integer.toString(port)));
				}
				return (newWorkerThread());
			} else {
				if (maxThreads < 0) {
					curThreadsBusy++;
					return (newWorkerThread());
				} else {
					return (null);
				}
			}
		}

	}

	/**
	 * Create and return a new processor suitable for processing HTTP requests
	 * and returning the corresponding responses.
	 */
	protected Worker newWorkerThread() {
		return new Worker();
	}

	/**
	 * Recycle the specified Processor so that it can be used again.
	 * 
	 * @param workerThread
	 *            The processor to be recycled
	 */
	protected void recycleWorkerThread(Worker workerThread) {
		synchronized (workerStack) {
			workerStack.push(workerThread);
			curThreadsBusy--;
			workerStack.notify();
		}
	}

	/**
	 * Return a new worker thread, and block while to worker is available.
	 */
	protected Worker getWorkerThread() {
		// Allocate a new worker thread
		Worker workerThread = createWorkerThread();
		if (org.apache.tomcat.util.net.Constants.WAIT_FOR_THREAD
				|| org.apache.tomcat.util.Constants.LOW_MEMORY) {
			while (workerThread == null) {
				try {
					synchronized (workerStack) {
						workerStack.wait();
					}
				} catch (InterruptedException e) {
					// Ignore
				}
				workerThread = createWorkerThread();
			}
		}
		return workerThread;
	}

	/**
	 * Process given channel.
	 */
	protected boolean processChannelWithOptions(NioChannel channel) {
		try {
			if (executor == null) {
				Worker worker = getWorkerThread();
				if (worker != null) {
					worker.assignWithOptions(channel);
				} else {
					return false;
				}
			} else {
				executor.execute(new ChannelWithOptionsProcessor(channel));
			}
		} catch (Throwable t) {
			// This means we got an OOM or similar creating a thread, or that
			// the pool and its queue are full
			logger.error(sm.getString("endpoint.process.fail"), t);
			return false;
		}
		return true;
	}

	/**
	 * Process given channel.
	 * 
	 * @param channel
	 * @return <tt>true</tt> if the processing of the channel finish
	 *         successfully else <tt>false</tt>
	 */
	public boolean processChannel(NioChannel channel) {
		try {
			if (executor == null) {
				Worker worker = getWorkerThread();
				if (worker != null) {
					worker.assign(channel);
				} else {
					return false;
				}
			} else {
				executor.execute(new ChannelProcessor(channel));
			}
		} catch (Throwable t) {
			// This means we got an OOM or similar creating a thread, or that
			// the pool and its queue are full
			logger.error(sm.getString("endpoint.process.fail"), t);
			return false;
		}
		return true;
	}

	/**
	 * Process given channel for an event.
	 * 
	 * @param channel
	 * @param status
	 * @return <tt>true</tt> if the processing of the channel finish
	 *         successfully else <tt>false</tt>
	 */
	public boolean processChannel(NioChannel channel, SocketStatus status) {
		try {
			if (executor == null) {
				Worker worker = getWorkerThread();
				if (worker != null) {
					worker.assign(channel, status);
				} else {
					return false;
				}
			} else {
				executor.execute(new ChannelEventProcessor(channel, status));
			}
		} catch (Throwable t) {
			// This means we got an OOM or similar creating a thread, or that
			// the pool and its queue are full
			logger.error(sm.getString("endpoint.process.fail"), t);
			return false;
		}
		return true;
	}

	/**
	 * Getter for serverSocketChannelFactory
	 * 
	 * @return the serverSocketChannelFactory
	 */
	public NioServerSocketChannelFactory getServerSocketChannelFactory() {
		return this.serverSocketChannelFactory;
	}

	/**
	 * Setter for the serverSocketChannelFactory
	 * 
	 * @param serverSocketChannelFactory
	 *            the serverSocketChannelFactory to set
	 */
	public void setServerSocketChannelFactory(
			NioServerSocketChannelFactory serverSocketChannelFactory) {
		this.serverSocketChannelFactory = serverSocketChannelFactory;
	}

	/**
	 * List of channel counters.
	 */
	class ListChannel {
		int count;
		int port;
	}

	// --------------------------------------------------- Acceptor Inner Class
	/**
	 * Server socket acceptor thread.
	 */
	protected class Acceptor implements Runnable {

		/**
		 * The background thread that listens for incoming TCP/IP connections
		 * and hands them off to an appropriate processor.
		 */
		public void run() {

			// Loop until we receive a shutdown command
			while (running) {
				// Loop if end point is paused
				while (paused) {
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						// Ignore
					}
				}

				// Accept the next incoming connection from the server channel
				try {
					final NioChannel channel = serverSocketChannelFactory.acceptChannel(listener);
					// set the channel options
					if (!setChannelOptions(channel)) {
						channel.close();
					}
					if (channel.isOpen()) {
						// Hand this channel off to an appropriate processor
						if (!processChannel(channel)) {
							logger.info("Fail processing the channel");
							// Close channel right away
							close(channel);
						}
					}
				} catch (Exception x) {
					if (running) {
						logger.error(sm.getString("endpoint.accept.fail"), x);
					}
				} catch (Throwable t) {
					logger.error(sm.getString("endpoint.accept.fail"), t);
				}
			}
		}

		/**
		 * 
		 * @param channel
		 */
		private void close(NioChannel channel) {
			try {
				channel.close();
			} catch (IOException e) {
				logger.error(e.getMessage(), e);
			}
		}
	}

	// ------------------------------------------------- SocketInfo Inner Class

	/**
	 * Channel list class, used to avoid using a possibly large amount of
	 * objects with very little actual use.
	 */
	public static class ChannelInfo {
		/**
		 * 
		 */
		public static final int READ = 1;
		/**
		 * 
		 */
		public static final int WRITE = 2;
		/**
		 * 
		 */
		public static final int RESUME = 4;
		/**
		 * 
		 */
		public static final int WAKEUP = 8;

		protected NioChannel channel;
		protected int timeout;
		protected int flags;

		/**
		 * @return the read flag
		 */
		public boolean read() {
			return (flags & READ) == READ;
		}

		/**
		 * @return the write flag
		 */
		public boolean write() {
			return (flags & WRITE) == WRITE;
		}

		/**
		 * @return the resume flag
		 */
		public boolean resume() {
			return (flags & RESUME) == RESUME;
		}

		/**
		 * @return the wakeup flag
		 */
		public boolean wakeup() {
			return (flags & WAKEUP) == WAKEUP;
		}

		/**
		 * @param flag1
		 * @param flag2
		 * @return
		 */
		public static int merge(int flag1, int flag2) {
			return ((flag1 & READ) | (flag2 & READ)) | ((flag1 & WRITE) | (flag2 & WRITE))
					| ((flag1 & RESUME) | (flag2 & RESUME)) | ((flag1 & WAKEUP) & (flag2 & WAKEUP));
		}
	}

	// --------------------------------------------- SocketTimeouts Inner Class

	/**
	 * Channel list class, used to avoid using a possibly large amount of
	 * objects with very little actual use.
	 */
	public class ChannelTimeouts {
		protected int size;

		protected NioChannel[] channels;
		protected long[] timeouts;
		protected TimeUnit[] units;
		protected int pos = 0;

		/**
		 * Create a new instance of {@code ChannelTimeouts}
		 * 
		 * @param size
		 */
		public ChannelTimeouts(int size) {
			this.size = 0;
			channels = new NioChannel[size];
			timeouts = new long[size];
		}

		/**
		 * @param channel
		 * @param timeout
		 * @param unit
		 */
		public void add(NioChannel channel, long timeout, TimeUnit unit) {
			channels[size] = channel;
			timeouts[size] = timeout;
			units[size] = unit;
			size++;
		}

		/**
		 * @param channel
		 * @return true of the channel has been removed successfully else false
		 */
		public boolean remove(NioChannel channel) {
			for (int i = 0; i < size; i++) {
				if (channels[i] == channel) {
					channels[i] = channels[size - 1];
					timeouts[i] = timeouts[size - 1];
					units[i] = units[size - 1];
					size--;
					return true;
				}
			}
			return false;
		}

		/**
		 * @param date
		 * @return the channel having a timeout less than the given date
		 */
		public NioChannel check(long date) {
			while (pos < size) {
				if (date >= timeouts[pos]) {
					NioChannel result = channels[pos];
					channels[pos] = channels[size - 1];
					timeouts[pos] = timeouts[size - 1];
					size--;
					return result;
				}
				pos++;
			}
			pos = 0;
			return null;
		}

	}

	// ------------------------------------------------ ChannelList Inner Class

	/**
	 * Channel list class, used to avoid using a possibly large amount of
	 * objects with very little actual use.
	 */
	public class ChannelList {
		protected int size;
		protected int pos;

		protected NioChannel[] channels;
		protected int[] timeouts;
		protected int[] flags;

		protected ChannelInfo info = new ChannelInfo();

		/**
		 * Create a new instance of {@code ChannelList}
		 * 
		 * @param size
		 */
		public ChannelList(int size) {
			this.size = 0;
			pos = 0;
			channels = new NioChannel[size];
			timeouts = new int[size];
			flags = new int[size];
		}

		/**
		 * @return the channel list size
		 */
		public int size() {
			return this.size;
		}

		/**
		 * @return the current ChannelInfo
		 */
		public ChannelInfo get() {
			if (pos == size) {
				return null;
			} else {
				info.channel = channels[pos];
				info.timeout = timeouts[pos];
				info.flags = flags[pos];
				pos++;
				return info;
			}
		}

		/**
		 * Clear the channel list
		 */
		public void clear() {
			size = 0;
			pos = 0;
		}

		/**
		 * Add the channel to the list of channels
		 * 
		 * @param channel
		 * @param timeout
		 * @param flag
		 * @return <tt>true</tt> if the channel is added successfully else
		 *         <tt>false</tt>
		 */
		public boolean add(NioChannel channel, int timeout, int flag) {
			if (size == channels.length) {
				return false;
			} else {
				for (int i = 0; i < size; i++) {
					if (channels[i] == channel) {
						flags[i] = ChannelInfo.merge(flags[i], flag);
						return true;
					}
				}
				channels[size] = channel;
				timeouts[size] = timeout;
				flags[size] = flag;
				size++;
				return true;
			}
		}

		/**
		 * @param copy
		 */
		public void duplicate(ChannelList copy) {
			copy.size = size;
			copy.pos = pos;
			System.arraycopy(channels, 0, copy.channels, 0, size);
			System.arraycopy(timeouts, 0, copy.timeouts, 0, size);
			System.arraycopy(flags, 0, copy.flags, 0, size);
		}
	}

	// ----------------------------------------------------- Worker Inner Class

	/**
	 * Server processor class.
	 */
	protected class Worker implements Runnable {

		protected boolean available = false;
		protected NioChannel channel;
		protected SocketStatus status = null;
		protected boolean options = false;

		/**
		 * Process an incoming TCP/IP connection on the specified socket. Any
		 * exception that occurs during processing must be logged and swallowed.
		 * <b>NOTE</b>: This method is called from our Connector's thread. We
		 * must assign it to our own thread so that multiple simultaneous
		 * requests can be handled.
		 * 
		 * @param channel
		 *            The channel to process
		 */
		protected synchronized void assignWithOptions(NioChannel channel) {
			doAssign(channel, null, true);
		}

		/**
		 * 
		 * @param channel
		 * @param status
		 * @param options
		 */
		protected synchronized void doAssign(NioChannel channel, SocketStatus status,
				boolean options) {
			// Wait for the Processor to get the previous Socket
			while (available) {
				try {
					wait();
				} catch (InterruptedException e) {
				}
			}

			// Store the newly available Socket and notify our thread
			this.channel = channel;
			this.status = status;
			this.options = options;
			available = true;
			notifyAll();
		}

		/**
		 * Process an incoming TCP/IP connection on the specified channel. Any
		 * exception that occurs during processing must be logged and swallowed.
		 * <b>NOTE</b>: This method is called from our Connector's thread. We
		 * must assign it to our own thread so that multiple simultaneous
		 * requests can be handled.
		 * 
		 * @param channel
		 *            the channel to process
		 */
		protected synchronized void assign(NioChannel channel) {
			doAssign(channel, null, false);
		}

		/**
		 * 
		 * @param channel
		 * @param status
		 */
		protected synchronized void assign(NioChannel channel, SocketStatus status) {
			doAssign(channel, status, false);
		}

		/**
		 * Await a newly assigned Channel from our Connector, or
		 * <code>null</code> if we are supposed to shut down.
		 */
		protected synchronized NioChannel await() {

			// Wait for the Connector to provide a new channel
			while (!available) {
				try {
					wait();
				} catch (InterruptedException e) {
				}
			}

			// Notify the Connector that we have received this channel
			NioChannel channel = this.channel;
			available = false;
			notifyAll();

			return channel;

		}

		/**
		 * The background thread that listens for incoming TCP/IP connections
		 * and hands them off to an appropriate processor.
		 */
		public void run() {

			// Process requests until we receive a shutdown signal
			while (running) {
				try {
					// Wait for the next socket to be assigned
					// long socket = await();
					NioChannel channel = await();
					if (channel == null)
						continue;

					if (!deferAccept && options) {
						if (setChannelOptions(channel)) {
							// getPoller().add(socket);
						} else {
							// Close socket and pool only if it wasn't closed
							// already
							channel.close();
						}
					} else {
						// Process the request from this socket
						if ((status != null)
								&& (handler.event(channel, status) == Handler.SocketState.CLOSED)) {
							// Close socket and pool only if it wasn't closed
							// already by the parent pool
							channel.close();
						} else if ((status == null)
								&& ((options && !setChannelOptions(channel)) || handler
										.process(channel) == Handler.SocketState.CLOSED)) {
							// Close socket and pool only if it wasn't closed
							// already by the parent pool
							channel.close();
						}
					}
				} catch (Exception exp) {
					// NOTHING
				} finally {
					// Finish up this request
					// We do not need to recycle threads manually since this
					// will be done by the Executor
					// recycleWorkerThread(this);
				}
			}

		}
	}

	// ----------------------------------------------- SendfileData Inner Class

	/**
	 * SendfileData class.
	 */
	public static class SendfileData {
		// File
		protected String fileName;
		protected File file;
		// Range information
		protected long start;
		protected long end;
		// The channel
		protected NioChannel channel;
		// Position
		protected long pos;
		// KeepAlive flag
		protected boolean keepAlive;

		/**
		 * Getter for fileName
		 * 
		 * @return the fileName
		 */
		public String getFileName() {
			return this.fileName;
		}

		/**
		 * Setter for the fileName
		 * 
		 * @param fileName
		 *            the fileName to set
		 */
		public void setFileName(String fileName) {
			this.fileName = fileName;
		}

		/**
		 * Getter for file
		 * 
		 * @return the file
		 */
		public File getFile() {
			return this.file;
		}

		/**
		 * Setter for the file
		 * 
		 * @param fd
		 *            the file to set
		 */
		public void setFile(File fd) {
			this.file = fd;
		}

		/**
		 * Getter for start
		 * 
		 * @return the start
		 */
		public long getStart() {
			return this.start;
		}

		/**
		 * Setter for the start
		 * 
		 * @param start
		 *            the start to set
		 */
		public void setStart(long start) {
			this.start = start;
		}

		/**
		 * Getter for end
		 * 
		 * @return the end
		 */
		public long getEnd() {
			return this.end;
		}

		/**
		 * Setter for the end
		 * 
		 * @param end
		 *            the end to set
		 */
		public void setEnd(long end) {
			this.end = end;
		}

		/**
		 * Getter for channel
		 * 
		 * @return the channel
		 */
		public NioChannel getChannel() {
			return this.channel;
		}

		/**
		 * Setter for the channel
		 * 
		 * @param channel
		 *            the channel to set
		 */
		public void setChannel(NioChannel channel) {
			this.channel = channel;
		}

		/**
		 * Getter for pos
		 * 
		 * @return the pos
		 */
		public long getPos() {
			return this.pos;
		}

		/**
		 * Setter for the pos
		 * 
		 * @param pos
		 *            the pos to set
		 */
		public void setPos(long pos) {
			this.pos = pos;
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
	}

	// ------------------------------------------------ Handler Inner Interface

	/**
	 * Bare bones interface used for socket processing. Per thread data is to be
	 * stored in the ThreadWithAttributes extra folders, or alternately in
	 * thread local fields.
	 */
	public interface Handler {
		/**
		 * {@code ChannelState}
		 * 
		 * Created on Dec 12, 2011 at 9:41:06 AM
		 * 
		 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
		 */
		public enum SocketState {
			/**
			 * 
			 */
			OPEN,
			/**
			 * 
			 */
			CLOSED,
			/**
			 * 
			 */
			LONG
		}

		/**
		 * Process the specified {@code org.apache.tomcat.util.net.NioChannel}
		 * 
		 * @param asyncChannel
		 *            the {@code org.apache.tomcat.util.net.NioChannel}
		 * @return a channel state
		 */
		public SocketState process(NioChannel asyncChannel);

		/**
		 * Process the specified {@code org.apache.tomcat.util.net.NioChannel}
		 * 
		 * @param asyncChannel
		 * @param status
		 * @return a channel state
		 */
		public SocketState event(NioChannel asyncChannel, SocketStatus status);

	}

	// ------------------------------------------------- WorkerStack Inner Class

	/**
	 * {@code WorkerStack}
	 * 
	 * Created on Dec 13, 2011 at 10:36:38 AM
	 * 
	 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
	 */
	public class WorkerStack {

		protected Worker[] workers = null;
		protected int end = 0;

		/**
		 * Create a new instance of {@code WorkerStack}
		 * 
		 * @param size
		 */
		public WorkerStack(int size) {
			workers = new Worker[size];
		}

		/**
		 * Put the object into the queue.
		 * 
		 * @param worker
		 *            the worker to be appended to the queue (first element).
		 */
		public void push(Worker worker) {
			workers[end++] = worker;
		}

		/**
		 * Get the first object out of the queue. Return null if the queue is
		 * empty.
		 * 
		 * @return the worker on the top of the stack
		 */
		public Worker pop() {
			if (end > 0) {
				return workers[--end];
			}
			return null;
		}

		/**
		 * Get the first object out of the queue, Return null if the queue is
		 * empty.
		 * 
		 * @return the worker on the top of the stack
		 */
		public Worker peek() {
			return workers[end];
		}

		/**
		 * Is the queue empty?
		 * 
		 * @return true if the stack is empty
		 */
		public boolean isEmpty() {
			return (end == 0);
		}

		/**
		 * How many elements are there in this queue?
		 * 
		 * @return the number of elements in the stack
		 */
		public int size() {
			return (end);
		}
	}

	// -------------------------------- ChannelWithOptionsProcessor Inner Class

	/**
	 * This class is the equivalent of the Worker, but will simply use in an
	 * external Executor thread pool. This will also set the channel options and
	 * do the handshake.
	 */
	protected class ChannelWithOptionsProcessor extends ChannelProcessor {

		/**
		 * Create a new instance of {@code ChannelWithOptionsProcessor}
		 * 
		 * @param channel
		 */
		public ChannelWithOptionsProcessor(NioChannel channel) {
			super(channel);
		}

		@Override
		public void run() {

			if (!deferAccept) {
				if (!setChannelOptions(channel)) {
					// Close channel
					close();
				}
			} else {
				// Process the request from this channel
				if (!setChannelOptions(channel)
						|| handler.process(channel) == Handler.SocketState.CLOSED) {
					// Close the channel
					close();
				}
			}
			channel = null;
		}
	}

	// ------------------------------------------- ChannelProcessor Inner Class

	/**
	 * This class is the equivalent of the Worker, but will simply use in an
	 * external Executor thread pool.
	 */
	protected class ChannelProcessor implements Runnable {

		protected NioChannel channel;

		/**
		 * Create a new instance of {@code ChannelProcessor}
		 * 
		 * @param channel
		 */
		public ChannelProcessor(NioChannel channel) {
			this.channel = channel;
		}

		@Override
		public void run() {

			// Process the request from this socket
			if (handler.process(this.channel) == Handler.SocketState.CLOSED) {
				// Close channel
				close();
			}
			this.channel = null;
		}

		/**
		 * Close the channel
		 */
		protected void close() {
			try {
				this.channel.close();
			} catch (IOException e) {
				// NOP
				logger.error("Error when closing the channel : " + e.getMessage(), e);
				e.printStackTrace();
			}
		}

		/**
		 * @param channel
		 */
		public void setChannel(NioChannel channel) {
			this.channel = channel;
		}
	}

	// --------------------------------------- ChannelEventProcessor Inner Class

	/**
	 * This class is the equivalent of the Worker, but will simply use in an
	 * external Executor thread pool.
	 */
	protected class ChannelEventProcessor extends ChannelProcessor {

		protected SocketStatus status = null;

		/**
		 * Create a new instance of {@code ChannelEventProcessor}
		 * 
		 * @param channel
		 * @param status
		 */
		public ChannelEventProcessor(NioChannel channel, SocketStatus status) {
			super(channel);
			this.status = status;
		}

		@Override
		public void run() {
			// Process the request from this channel
			if (handler.event(channel, status) == Handler.SocketState.CLOSED) {
				// Close channel
				close();
			}
			channel = null;
		}
	}

	/**
	 * The default thread factory
	 */
	protected static class DefaultThreadFactory implements ThreadFactory {
		private static final AtomicInteger poolNumber = new AtomicInteger(1);
		private final ThreadGroup group;
		private final AtomicInteger threadNumber = new AtomicInteger(1);
		private final String namePrefix;
		private final int threadPriority;

		/**
		 * Create a new instance of {@code DefaultThreadFactory}
		 * 
		 * @param namePrefix
		 * @param threadPriority
		 */
		public DefaultThreadFactory(String namePrefix, int threadPriority) {
			SecurityManager s = System.getSecurityManager();
			group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
			this.namePrefix = namePrefix;
			this.threadPriority = threadPriority;
		}

		/**
		 * 
		 * Create a new instance of {@code DefaultThreadFactory}
		 * 
		 * @param threadPriority
		 */
		public DefaultThreadFactory(int threadPriority) {
			this("pool-" + poolNumber.getAndIncrement() + "-thread-", threadPriority);
		}

		/**
		 * Create and return a new thread
		 */
		public Thread newThread(Runnable r) {
			Thread thread = new Thread(group, r, namePrefix + threadNumber.getAndIncrement(), 0);
			if (thread.isDaemon())
				thread.setDaemon(false);

			if (thread.getPriority() != this.threadPriority)
				thread.setPriority(this.threadPriority);
			return thread;
		}
	}

}
