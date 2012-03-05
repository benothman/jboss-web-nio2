/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.apache.tomcat.util.net;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Executor;

import org.apache.tomcat.util.res.StringManager;
import org.jboss.logging.Logger;

/**
 * Handle incoming TCP connections.
 *
 * This class implement a simple server model: one listener thread accepts on a socket and
 * creates a new worker thread for each incoming connection.
 *
 * More advanced Endpoints will reuse the threads, use queues, etc.
 *
 * @author James Duncan Davidson
 * @author Jason Hunter
 * @author James Todd
 * @author Costin Manolache
 * @author Gal Shachor
 * @author Yoav Shapira
 * @author Remy Maucherat
 */
public class JIoEndpoint {


    // -------------------------------------------------------------- Constants


    protected static Logger log = Logger.getLogger(JIoEndpoint.class);

    protected StringManager sm = 
        StringManager.getManager("org.apache.tomcat.util.net.res");


    // ----------------------------------------------------------------- Fields


    /**
     * Available workers.
     */
    protected WorkerStack workers = null;


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
     * Associated server socket.
     */
    protected ServerSocket serverSocket = null;


    // ------------------------------------------------------------- Properties


    /**
     * Acceptor thread count.
     */
    protected int acceptorThreadCount = 0;
    public void setAcceptorThreadCount(int acceptorThreadCount) { this.acceptorThreadCount = acceptorThreadCount; }
    public int getAcceptorThreadCount() { return acceptorThreadCount; }


    /**
     * External Executor based thread pool.
     */
    protected Executor executor = null;
    public void setExecutor(Executor executor) { this.executor = executor; }
    public Executor getExecutor() { return executor; }


    /**
     * Maximum amount of worker threads.
     */
    protected int maxThreads = (org.apache.tomcat.util.Constants.LOW_MEMORY) ? 64 : 512 * Runtime.getRuntime().availableProcessors();
    public void setMaxThreads(int maxThreads) { this.maxThreads = maxThreads; }
    public int getMaxThreads() { return maxThreads;}


    /**
     * Priority of the acceptor and poller threads.
     */
    protected int threadPriority = Thread.NORM_PRIORITY;
    public void setThreadPriority(int threadPriority) { this.threadPriority = threadPriority; }
    public int getThreadPriority() { return threadPriority; }

    
    /**
     * Size of the socket poller.
     */
    protected int pollerSize = (org.apache.tomcat.util.Constants.LOW_MEMORY) ? 128 : (32 * 1024);
    public void setPollerSize(int pollerSize) { this.pollerSize = pollerSize; }
    public int getPollerSize() { return pollerSize; }


    /**
     * Keep-Alive timeout.
     */
    protected int keepAliveTimeout = -1;
    public int getKeepAliveTimeout() { return keepAliveTimeout; }
    public void setKeepAliveTimeout(int keepAliveTimeout) { this.keepAliveTimeout = keepAliveTimeout; }


    /**
     * Server socket port.
     */
    protected int port;
    public int getPort() { return port; }
    public void setPort(int port ) { this.port=port; }


    /**
     * Address for the server socket.
     */
    protected InetAddress address;
    public InetAddress getAddress() { return address; }
    public void setAddress(InetAddress address) { this.address = address; }


    /**
     * Handling of accepted sockets.
     */
    protected Handler handler = null;
    public void setHandler(Handler handler ) { this.handler = handler; }
    public Handler getHandler() { return handler; }


    /**
     * Allows the server developer to specify the backlog that
     * should be used for server sockets. By default, this value
     * is 100.
     */
    protected int backlog = 100;
    public void setBacklog(int backlog) { if (backlog > 0) this.backlog = backlog; }
    public int getBacklog() { return backlog; }


    /**
     * Socket TCP no delay.
     */
    protected boolean tcpNoDelay = false;
    public boolean getTcpNoDelay() { return tcpNoDelay; }
    public void setTcpNoDelay(boolean tcpNoDelay) { this.tcpNoDelay = tcpNoDelay; }


    /**
     * Socket linger.
     */
    protected int soLinger = 100;
    public int getSoLinger() { return soLinger; }
    public void setSoLinger(int soLinger) { this.soLinger = soLinger; }


    /**
     * Socket timeout.
     */
    protected int soTimeout = -1;
    public int getSoTimeout() { return soTimeout; }
    public void setSoTimeout(int soTimeout) { this.soTimeout = soTimeout; }


    /**
     * The default is true - the created threads will be
     *  in daemon mode. If set to false, the control thread
     *  will not be daemon - and will keep the process alive.
     */
    protected boolean daemon = true;
    public void setDaemon(boolean b) { daemon = b; }
    public boolean getDaemon() { return daemon; }


    /**
     * Name of the thread pool, which will be used for naming child threads.
     */
    protected String name = "TP";
    public void setName(String name) { this.name = name; }
    public String getName() { return name; }


    /**
     * Server socket factory.
     */
    protected ServerSocketFactory serverSocketFactory = null;
    public void setServerSocketFactory(ServerSocketFactory factory) { this.serverSocketFactory = factory; }
    public ServerSocketFactory getServerSocketFactory() { return serverSocketFactory; }


    /**
     * The socket poller used for event support.
     */
    protected Poller eventPoller = null;
    public Poller getEventPoller() {
        return eventPoller;
    }


    public boolean isRunning() {
        return running;
    }
    
    public boolean isPaused() {
        return paused;
    }
    
    public int getCurrentThreadCount() {
        return curThreads;
    }
    
    public int getCurrentThreadsBusy() {
        return workers!=null?curThreads - workers.size():0;
    }
    

    // ------------------------------------------------ Handler Inner Interface


    /**
     * Bare bones interface used for socket processing. Per thread data is to be
     * stored in the ThreadWithAttributes extra folders, or alternately in
     * thread local fields.
     */
    public interface Handler {
        public enum SocketState {
            OPEN, CLOSED, LONG
        }
        public SocketState process(Socket socket);
        public SocketState event(Socket socket, SocketStatus status);
    }


    // --------------------------------------------------- Acceptor Inner Class


    /**
     * Server socket acceptor thread.
     */
    protected class Acceptor implements Runnable {


        /**
         * The background thread that listens for incoming TCP/IP connections and
         * hands them off to an appropriate processor.
         */
        public void run() {

            // Loop until we receive a shutdown command
            while (running) {

                // Loop if endpoint is paused
                while (paused) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                }

                // Accept the next incoming connection from the server socket
                try {
                    Socket socket = serverSocketFactory.acceptSocket(serverSocket);
                    serverSocketFactory.initSocket(socket);
                    // Hand this socket off to an appropriate processor
                    if (!processSocket(socket)) {
                        // Close socket right away
                        try { socket.close(); } catch (IOException e) { }
                    }
                }catch ( IOException x ) {
                    if ( running ) log.error(sm.getString("endpoint.accept.fail"), x);
                } catch (Throwable t) {
                    log.error(sm.getString("endpoint.accept.fail"), t);
                }

                // The processor will recycle itself when it finishes

            }

        }

    }


    // ------------------------------------------------- SocketInfo Inner Class


    /**
     * Socket list class, used to avoid using a possibly large amount of objects
     * with very little actual use.
     */
    public static class SocketInfo {
        public static final int RESUME = 4;
        public static final int WAKEUP = 8;
        public Socket socket;
        public int timeout;
        public int flags;
        public boolean resume() {
            return (flags & RESUME) == RESUME;
        }
        public boolean wakeup() {
            return (flags & WAKEUP) == WAKEUP;
        }
        public static int merge(int flag1, int flag2) {
            return ((flag1 & RESUME) | (flag2 & RESUME)) 
                | ((flag1 & WAKEUP) & (flag2 & WAKEUP));
        }
    }
    
    
    // --------------------------------------------- SocketTimeouts Inner Class


    /**
     * Socket list class, used to avoid using a possibly large amount of objects
     * with very little actual use.
     */
    public class SocketTimeouts {
        protected int size;

        protected Socket[] sockets;
        protected long[] timeouts;
        protected int pos = 0;

        public SocketTimeouts(int size) {
            this.size = 0;
            sockets = new Socket[size];
            timeouts = new long[size];
        }

        public void add(Socket socket, long timeout) {
            sockets[size] = socket;
            timeouts[size] = timeout;
            size++;
        }
        
        public boolean remove(Socket socket) {
            for (int i = 0; i < size; i++) {
                if (sockets[i] == socket) {
                    sockets[i] = sockets[size - 1];
                    timeouts[i] = timeouts[size - 1];
                    size--;
                    return true;
                }
            }
            return false;
        }
        
        public Socket check(long date) {
            while (pos < size) {
                if (date >= timeouts[pos]) {
                    Socket result = sockets[pos];
                    sockets[pos] = sockets[size - 1];
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
    
    
    // ------------------------------------------------- SocketList Inner Class


    /**
     * Socket list class, used to avoid using a possibly large amount of objects
     * with very little actual use.
     */
    public class SocketList {
        protected int size;
        protected int pos;

        protected Socket[] sockets;
        protected int[] timeouts;
        protected int[] flags;
        
        protected SocketInfo info = new SocketInfo();
        
        public SocketList(int size) {
            this.size = 0;
            pos = 0;
            sockets = new Socket[size];
            timeouts = new int[size];
            flags = new int[size];
        }
        
        public int size() {
            return this.size;
        }
        
        public SocketInfo get() {
            if (pos == size) {
                return null;
            } else {
                info.socket = sockets[pos];
                info.timeout = timeouts[pos];
                info.flags = flags[pos];
                pos++;
                return info;
            }
        }
        
        public void clear() {
            size = 0;
            pos = 0;
        }
        
        public boolean add(Socket socket, int timeout, int flag) {
            if (size == sockets.length) {
                return false;
            } else {
                for (int i = 0; i < size; i++) {
                    if (sockets[i] == socket) {
                        flags[i] = SocketInfo.merge(flags[i], flag);
                        return true;
                    }
                }
                sockets[size] = socket;
                timeouts[size] = timeout;
                flags[size] = flag;
                size++;
                return true;
            }
        }
        
        public void duplicate(SocketList copy) {
            copy.size = size;
            copy.pos = pos;
            System.arraycopy(sockets, 0, copy.sockets, 0, size);
            System.arraycopy(timeouts, 0, copy.timeouts, 0, size);
            System.arraycopy(flags, 0, copy.flags, 0, size);
        }
        
    }
    
    
    // ------------------------------------------- SocketProcessor Inner Class


    /**
     * This class is the equivalent of the Worker, but will simply use in an
     * external Executor thread pool.
     */
    protected class SocketProcessor implements Runnable {
        
        protected Socket socket = null;
        
        public SocketProcessor(Socket socket) {
            this.socket = socket;
        }

        public void run() {

            // Process the request from this socket
            if (!setSocketOptions(socket) || (handler.process(socket) == Handler.SocketState.CLOSED)) {
                // Close socket
                try { socket.close(); } catch (IOException e) { }
            }

            // Finish up this request
            socket = null;

        }
        
    }
    
    
    // --------------------------------------- SocketEventProcessor Inner Class


    /**
     * This class is the equivalent of the Worker, but will simply use in an
     * external Executor thread pool.
     */
    protected class SocketEventProcessor implements Runnable {
        
        protected Socket socket = null;
        protected SocketStatus status = null; 
        
        public SocketEventProcessor(Socket socket, SocketStatus status) {
            this.socket = socket;
            this.status = status;
        }

        public void run() {

            Handler.SocketState socketState = handler.event(socket, status);
            if (socketState == Handler.SocketState.CLOSED) {
                // Close socket
                try { socket.close(); } catch (IOException e) { }
            } else if (socketState == Handler.SocketState.OPEN) {
                // Process the keepalive after the event processing
                // This is the main behavior difference with endpoint with pollers, which
                // will add the socket to the poller
                if (handler.process(socket) == Handler.SocketState.CLOSED) {
                    // Close socket
                    try { socket.close(); } catch (IOException e) { }
                }
            }
            socket = null;

        }
        
    }
    
    
    // ----------------------------------------------------- Poller Inner Class


    /**
     * Poller class.
     */
    public class Poller implements Runnable {

        /**
         * List of sockets to be added to the poller.
         */
        protected SocketList addList = null;

        /**
         * List of sockets to be added to the poller.
         */
        protected SocketList localAddList = null;

        /**
         * Structure used for storing timeouts.
         */
        protected SocketTimeouts timeouts = null;
        
        
        /**
         * Last run of maintain. Maintain will run usually every 5s.
         */
        protected long lastMaintain = System.currentTimeMillis();
        
        
        /**
         * Amount of connections inside this poller.
         */
        protected int connectionCount = 0;
        public int getConnectionCount() { return connectionCount; }

        public Poller() {
        }
        
        /**
         * Create the poller. The java.io poller only deals with timeouts.
         */
        protected void init() {

            timeouts = new SocketTimeouts(pollerSize);
            
            connectionCount = 0;
            addList = new SocketList(pollerSize);
            localAddList = new SocketList(pollerSize);

        }

        /**
         * Destroy the poller.
         */
        protected void destroy() {
            // Wait for pollerTime before doing anything, so that the poller threads
            // exit, otherwise parallel destruction of sockets which are still
            // in the poller can cause problems
            try {
                synchronized (this) {
                    this.wait(2);
                }
            } catch (InterruptedException e) {
                // Ignore
            }
            // Close all sockets in the add queue
            SocketInfo info = addList.get();
            while (info != null) {
                if (!processSocket(info.socket, SocketStatus.STOP)) {
                    try { info.socket.close(); } catch (IOException e) { }
                }
                info = addList.get();
            }
            addList.clear();
            // Close all sockets still in the poller
            long future = System.currentTimeMillis() + Integer.MAX_VALUE;
            Socket socket = timeouts.check(future);
            while (socket != null) {
                if (!processSocket(socket, SocketStatus.TIMEOUT)) {
                    try { socket.close(); } catch (IOException e) { }
                }
                socket = timeouts.check(future);
            }
            connectionCount = 0;
        }

        /**
         * Add specified socket and associated pool to the poller. The socket will
         * be added to a temporary array, and polled first after a maximum amount
         * of time equal to pollTime (in most cases, latency will be much lower,
         * however).
         *
         * @param socket to add to the poller
         */
        public void add(Socket socket, int timeout, boolean resume, boolean wakeup) {
            if (timeout < 0) {
                timeout = keepAliveTimeout;
            }
            if (timeout < 0) {
                timeout = soTimeout;
            }
            if (timeout <= 0) {
                // Always put a timeout in
                timeout = Integer.MAX_VALUE;
            }
            boolean ok = false;
            synchronized (this) {
                // Add socket to the list. Newly added sockets will wait
                // at most for pollTime before being polled
                if (addList.add(socket, timeout, (resume ? SocketInfo.RESUME : 0)
                        | (wakeup ? SocketInfo.WAKEUP : 0))) {
                    ok = true;
                    this.notify();
                }
            }
            if (!ok) {
                // Can't do anything: close the socket right away
                if (!processSocket(socket, SocketStatus.ERROR)) {
                    try { socket.close(); } catch (IOException e) { }
                }
            }
        }

        /**
         * Timeout checks.
         */
        protected void maintain() {

            long date = System.currentTimeMillis();
            // Maintain runs at most once every 5s, although it will likely get called more
            if ((date - lastMaintain) < 5000L) {
                return;
            } else {
                lastMaintain = date;
            }
            Socket socket = timeouts.check(date);
            while (socket != null) {
                if (!processSocket(socket, SocketStatus.TIMEOUT)) {
                    try { socket.close(); } catch (IOException e) { }
                }
                socket = timeouts.check(date);
            }

        }
        
        /**
         * The background thread that listens for incoming TCP/IP connections and
         * hands them off to an appropriate processor.
         */
        public void run() {

            int maintain = 0;
            // Loop until we receive a shutdown command
            while (running) {

                // Loop if endpoint is paused
                while (paused) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                }
                // Check timeouts for suspended connections if the poller is empty
                while (connectionCount < 1 && addList.size() < 1) {
                    // Reset maintain time.
                    try {
                        if (soTimeout > 0 && running) {
                            maintain();
                        }
                        synchronized (this) {
                            this.wait(10000);
                        }
                    } catch (InterruptedException e) {
                        // Ignore
                    } catch (Throwable t) {
                        log.error(sm.getString("endpoint.maintain.error"), t);
                    }
                }

                try {
                    
                    // Add sockets which are waiting to the poller
                    if (addList.size() > 0) {
                        synchronized (this) {
                            // Duplicate to another list, so that the syncing is minimal
                            addList.duplicate(localAddList);
                            addList.clear();
                        }
                        SocketInfo info = localAddList.get();
                        while (info != null) {
                            if (info.wakeup()) {
                                // Resume event if socket is present in the poller
                                if (timeouts.remove(info.socket)) {
                                    if (info.resume()) {
                                        if (!processSocket(info.socket, SocketStatus.OPEN_CALLBACK)) {
                                            try { info.socket.close(); } catch (IOException e) { }
                                        }
                                    } else {
                                        timeouts.add(info.socket, System.currentTimeMillis() + info.timeout);
                                    }
                                }
                            } else {
                                if (info.resume()) {
                                    timeouts.remove(info.socket);
                                    if (!processSocket(info.socket, SocketStatus.OPEN_CALLBACK)) {
                                        try { info.socket.close(); } catch (IOException e) { }
                                    }
                                } else {
                                    timeouts.add(info.socket, System.currentTimeMillis() + info.timeout);
                                }
                            }
                            info = localAddList.get();
                        }
                    }

                    try {
                        Thread.sleep(2);
                    } catch (InterruptedException e) {
                        // Ignore
                    }

                    // Process socket timeouts
                    if (soTimeout > 0 && maintain++ > 1000 && running) {
                        maintain = 0;
                        maintain();
                    }

                } catch (Throwable t) {
                    if (maintain == 0) {
                        log.error(sm.getString("endpoint.maintain.error"), t);
                    } else {
                        log.error(sm.getString("endpoint.poll.error"), t);
                    }
                }

            }

            synchronized (this) {
                this.notifyAll();
            }

        }
        
    }


    // ----------------------------------------------------- Worker Inner Class


    protected class Worker implements Runnable {

        protected Thread thread = null;
        protected boolean available = false;
        protected Socket socket = null;
        protected SocketStatus status = null;

        
        /**
         * Process an incoming TCP/IP connection on the specified socket.  Any
         * exception that occurs during processing must be logged and swallowed.
         * <b>NOTE</b>:  This method is called from our Connector's thread.  We
         * must assign it to our own thread so that multiple simultaneous
         * requests can be handled.
         *
         * @param socket TCP socket to process
         */
        protected synchronized void assign(Socket socket) {

            // Wait for the Processor to get the previous Socket
            while (available) {
                try {
                    wait();
                } catch (InterruptedException e) {
                }
            }

            // Store the newly available Socket and notify our thread
            this.socket = socket;
            this.status = null;
            available = true;
            notifyAll();

        }

        
        protected synchronized void assign(Socket socket, SocketStatus status) {

            // Wait for the Processor to get the previous Socket
            while (available) {
                try {
                    wait();
                } catch (InterruptedException e) {
                }
            }

            // Store the newly available Socket and notify our thread
            this.socket = socket;
            this.status = status;
            available = true;
            notifyAll();

        }


        /**
         * Await a newly assigned Socket from our Connector, or <code>null</code>
         * if we are supposed to shut down.
         */
        private synchronized Socket await() {

            // Wait for the Connector to provide a new Socket
            while (!available) {
                try {
                    wait();
                } catch (InterruptedException e) {
                }
            }

            // Notify the Connector that we have received this Socket
            Socket socket = this.socket;
            available = false;
            notifyAll();

            return (socket);

        }



        /**
         * The background thread that listens for incoming TCP/IP connections and
         * hands them off to an appropriate processor.
         */
        public void run() {

            // Process requests until we receive a shutdown signal
            while (running) {

                // Wait for the next socket to be assigned
                Socket socket = await();
                if (socket == null)
                    continue;

                // Process the request from this socket
                if (status != null){
                    Handler.SocketState socketState = handler.event(socket, status);
                    if (socketState == Handler.SocketState.CLOSED) {
                        // Close socket
                        try { socket.close(); } catch (IOException e) { }
                    } else if (socketState == Handler.SocketState.OPEN) {
                        // Process the keepalive after the event processing
                        // This is the main behavior difference with endpoint with pollers, which
                        // will add the socket to the poller
                        if (handler.process(socket) == Handler.SocketState.CLOSED) {
                            // Close socket
                            try { socket.close(); } catch (IOException e) { }
                        }
                    }
                } else if ((status == null) && (!setSocketOptions(socket) || (handler.process(socket) == Handler.SocketState.CLOSED))) {
                    // Close socket
                    try { socket.close(); } catch (IOException e) { }
                }

                // Finish up this request
                recycleWorkerThread(this);

            }

        }


        /**
         * Start the background processing thread.
         */
        public void start() {
            thread = new Thread(this);
            thread.setName(getName() + "-" + (++curThreads));
            thread.setDaemon(true);
            thread.start();
        }


    }


    // -------------------- Public methods --------------------

    public void init()
        throws Exception {

        if (initialized)
            return;
        
        // Initialize thread count defaults for acceptor
        if (acceptorThreadCount == 0) {
            acceptorThreadCount = 1;
        }
        if (serverSocketFactory == null) {
            serverSocketFactory = ServerSocketFactory.getDefault();
        }
        if (serverSocket == null) {
            try {
                if (address == null) {
                    serverSocket = serverSocketFactory.createSocket(port, backlog);
                } else {
                    serverSocket = serverSocketFactory.createSocket(port, backlog, address);
                }
            } catch (BindException be) {
                if (address == null) {
                    throw new BindException(be.getMessage() + "<null>:" + port);
                } else {
                    throw new BindException(be.getMessage() + " " +
                            address.toString() + ":" + port);
                }
            }
        }
        //if( serverTimeout >= 0 )
        //    serverSocket.setSoTimeout( serverTimeout );
        
        initialized = true;
        
    }
    
    public void start()
        throws Exception {
        // Initialize socket if not done before
        if (!initialized) {
            init();
        }
        if (!running) {
            running = true;
            paused = false;

            // Create worker collection
            if (executor == null) {
                workers = new WorkerStack(maxThreads);
            }

            // Start event poller thread
            eventPoller = new Poller();
            eventPoller.init();
            Thread pollerThread = new Thread(eventPoller, getName() + "-Poller");
            pollerThread.setPriority(threadPriority);
            pollerThread.setDaemon(true);
            pollerThread.start();

            // Start acceptor threads
            for (int i = 0; i < acceptorThreadCount; i++) {
                Thread acceptorThread = new Thread(new Acceptor(), getName() + "-Acceptor-" + i);
                acceptorThread.setPriority(threadPriority);
                acceptorThread.setDaemon(daemon);
                acceptorThread.start();
            }
        }
    }

    public void pause() {
        if (running && !paused) {
            paused = true;
            unlockAccept();
        }
    }

    public void resume() {
        if (running) {
            paused = false;
        }
    }

    public void stop() {
        if (running) {
            running = false;
            unlockAccept();
            eventPoller.destroy();
            eventPoller = null;
        }
    }

    /**
     * Deallocate APR memory pools, and close server socket.
     */
    public void destroy() throws Exception {
        if (running) {
            stop();
        }
        if (serverSocket != null) {
            try {
                if (serverSocket != null)
                    serverSocket.close();
            } catch (Exception e) {
                log.error(sm.getString("endpoint.err.close"), e);
            }
            serverSocket = null;
        }
        initialized = false ;
    }

    
    /**
     * Unlock the accept by using a local connection.
     */
    protected void unlockAccept() {
        Socket s = null;
        try {
            // Need to create a connection to unlock the accept();
            if (address == null) {
                s = new Socket("localhost", port);
            } else {
                s = new Socket(address, port);
                    // setting soLinger to a small value will help shutdown the
                    // connection quicker
                s.setSoLinger(true, 0);
            }
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("endpoint.debug.unlock", "" + port), e);
            }
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
     * Set the options for the current socket.
     */
    protected boolean setSocketOptions(Socket socket) {
        // Process the connection
        int step = 1;
        try {

            // 1: Set socket options: timeout, linger, etc
            if (soLinger >= 0) { 
                socket.setSoLinger(true, soLinger);
            }
            if (tcpNoDelay) {
                socket.setTcpNoDelay(tcpNoDelay);
            }
            if (soTimeout > 0) {
                socket.setSoTimeout(soTimeout);
            }

            // 2: SSL handshake
            step = 2;
            serverSocketFactory.handshake(socket);

        } catch (Throwable t) {
            if (log.isDebugEnabled()) {
                if (step == 2) {
                    log.debug(sm.getString("endpoint.err.handshake"), t);
                } else {
                    log.debug(sm.getString("endpoint.err.unexpected"), t);
                }
            }
            // Tell to close the socket
            return false;
        }
        return true;
    }

    
    /**
     * Create (or allocate) and return an available processor for use in
     * processing a specific HTTP request, if possible.  If the maximum
     * allowed processors have already been created and are in use, return
     * <code>null</code> instead.
     */
    protected Worker createWorkerThread() {

        synchronized (workers) {
            if (workers.size() > 0) {
                curThreadsBusy++;
                return workers.pop();
            }
            if ((maxThreads > 0) && (curThreads < maxThreads)) {
                curThreadsBusy++;
                if (curThreadsBusy == maxThreads) {
                    log.info(sm.getString("endpoint.info.maxThreads",
                            Integer.toString(maxThreads), address,
                            Integer.toString(port)));
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
     * Create and return a new processor suitable for processing HTTP
     * requests and returning the corresponding responses.
     */
    protected Worker newWorkerThread() {

        Worker workerThread = new Worker();
        workerThread.start();
        return (workerThread);

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
                    synchronized (workers) {
                        workers.wait();
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
     * Recycle the specified Processor so that it can be used again.
     *
     * @param workerThread The processor to be recycled
     */
    protected void recycleWorkerThread(Worker workerThread) {
        synchronized (workers) {
            workers.push(workerThread);
            curThreadsBusy--;
            workers.notify();
        }
    }


    /**
     * Process given socket.
     */
    protected boolean processSocket(Socket socket) {
        try {
            if (executor == null) {
                Worker worker = getWorkerThread();
                if (worker != null) {
                    worker.assign(socket);
                } else {
                    return false;
                }
            } else {
                executor.execute(new SocketProcessor(socket));
            }
        } catch (Throwable t) {
            // This means we got an OOM or similar creating a thread, or that
            // the pool and its queue are full
            log.error(sm.getString("endpoint.process.fail"), t);
            return false;
        }
        return true;
    }
    

    /**
     * Process given socket for an event.
     */
    protected boolean processSocket(Socket socket, SocketStatus status) {
        try {
            if (executor == null) {
                Worker worker = getWorkerThread();
                if (worker != null) {
                    worker.assign(socket, status);
                } else {
                    return false;
                }
            } else {
                executor.execute(new SocketEventProcessor(socket, status));
            }
        } catch (Throwable t) {
            // This means we got an OOM or similar creating a thread, or that
            // the pool and its queue are full
            log.error(sm.getString("endpoint.process.fail"), t);
            return false;
        }
        return true;
    }
    

    // ------------------------------------------------- WorkerStack Inner Class


    public class WorkerStack {
        
        protected Worker[] workers = null;
        protected int end = 0;
        
        public WorkerStack(int size) {
            workers = new Worker[size];
        }
        
        /** 
         * Put the object into the queue.
         * 
         * @param   object      the object to be appended to the queue (first element). 
         */
        public void push(Worker worker) {
            workers[end++] = worker;
        }
        
        /**
         * Get the first object out of the queue. Return null if the queue
         * is empty. 
         */
        public Worker pop() {
            if (end > 0) {
                return workers[--end];
            }
            return null;
        }
        
        /**
         * Get the first object out of the queue, Return null if the queue
         * is empty.
         */
        public Worker peek() {
            return workers[end];
        }
        
        /**
         * Is the queue empty?
         */
        public boolean isEmpty() {
            return (end == 0);
        }
        
        /**
         * How many elements are there in this queue?
         */
        public int size() {
            return (end);
        }
    }

}
