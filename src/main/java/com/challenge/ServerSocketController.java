package com.challenge;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller used to manage client connections using tcp/ip on port 4000.
 * 
 * This will allow a max of 5 connections at one time.
 * A new thread will be created to service each connection.
 * 
 * Input is expected to be 9 digit string with NL termination.
 * If input is not correct, then socket will be closed.  If the client sends
 * the input "terminate", then an internal method will be called and then the socket will be close. 
 * 
 * Each input is verified and then used to call an internal method.
 *
 * This class is abstract, with methods to be implemented for number input and termination.
 *
 */
public abstract class ServerSocketController {

    private static Logger LOG = Logger.getLogger(ServerSocketController.class.getName());
    
    /**
     * single server socket for allow client connetions.
     */
    private ServerSocket serverSocket;

    /** max number of connections at one time */
    public static final int MaxConnections = 5;
    
    /** number of required digits for client data */
    private final int requiredDigits;
    
    /** manages active client socket connections */
    private final ArrayList<SocketController> alClientController = new ArrayList<>();
    
    /** flag to know if state is started or stopped */
    private final AtomicBoolean abStart = new AtomicBoolean();
    
    /** lock used to manage connections */
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    
    /** used to assign sequential id value to each client connection */
    private int idNext;
    
    /** string value that can be used by client to stop the server */
    private static final String TerminateText = "teminate";

    
    /**
     * Create connection that manages client connections.
     * @param requiredDigits number of digits required for valid data from clients.
     */
    public ServerSocketController(int requiredDigits) {
        this.requiredDigits = requiredDigits;
        LOG.log(Level.FINE, "requiredDigits="+requiredDigits);
    }
    

    /**
     * Start the server socket using the current thread.
     * @throws IOException
     */
    public void start() throws IOException {
        LOG.log(Level.FINE, "start called, isAlreadyStarted="+abStart);
        if (!abStart.compareAndSet(false, true)) return;
        
        this.serverSocket = new ServerSocket(4000);
        LOG.log(Level.FINE, "new server socket on port 4000");

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                long msLastError = 0;
                int cntError = 0;
                for ( ;abStart.get(); ) {
                    try {
                        Socket socket = serverSocket.accept();
                        onNewSocket(socket);
                    }
                    catch (Exception e) {
                        cntError++;
                        long ms = System.currentTimeMillis();
                        if (ms > msLastError + 5000) {
                            LOG.log(Level.WARNING, "exception while accepting socket, total errors="+cntError+", will continue", e);
                            msLastError = ms;
                        }
                    }
                }
            }
        }, "ServerSocketController");
        thread.setDaemon(true);
        thread.start();
        
        LOG.log(Level.FINE, "start completed");
    }

    /**
     * Used to close the server socket, and all currect client connections.
     */
    public void stop() throws Exception {
        LOG.log(Level.FINE, "stop called, isAlreadyStarted="+abStart);
        if (!abStart.compareAndSet(true, false)) return;

        try {
            rwLock.writeLock().lock();
            this.serverSocket.close();
            
            for (SocketController sc : alClientController) {
                try {
                    sc.stop();
                }
                catch (Exception e) {
                    LOG.log(Level.WARNING, "exception while stopping client socket, will continue to stop", e);
                }
            }
            alClientController.clear();
        }
        finally {
            rwLock.writeLock().unlock();
        }
        LOG.log(Level.FINE, "stop completed");
    }


    /**
     * Called when server socket receives a new client connection.
     * This will create a thread to manage the client socket, expecting to read numbers as 9 digit strings
     * or the word "terminate". All valid numbers will be "past on" to be processed.  All other
     * input will cause the connection to be closed.
     *  
     * @param socket new client socket 
     */
    protected void onNewSocket(final Socket socket) throws IOException{
        LOG.log(Level.FINE, "new client socket="+socket);
        if (socket == null) return;
        
        SocketController clientController = null;
        try {
            rwLock.writeLock().lock();
            LOG.fine("isStarted="+abStart.get()+", current connect count="+alClientController.size());
            boolean bValid = abStart.get() && (alClientController.size() < MaxConnections);

            if (!bValid) {
                LOG.fine("connection is not allowed, will close the socket");
                socket.close();
                return;
            }
            
            final int id = idNext++;
            clientController = new SocketController(socket, id) {
                @Override
                protected void onReadLine(String text) {
                    ServerSocketController.this.onReadLine(this, text);
                }

                @Override
                protected void onException(IOException ex) {
                    ServerSocketController.this.onException(this, ex);
                }
            };

            alClientController.add(clientController);
        }
        finally {
            rwLock.writeLock().unlock();
        }
        LOG.fine("client connection is allowed, creating thread to read from it");

        
        final SocketController ccx = clientController;
        Thread thread = new Thread(new Runnable() {
            public void run() {
                try {
                    ccx.start();
                }
                catch (IOException e) {
                    ServerSocketController.this.onException(ccx, e);
                }
            }
        }, "ClientSocket."+ccx.getId());
        thread.start();
        LOG.log(Level.FINE, "client connection completed, new thread="+thread.getName());
    }

    /**
     * Close a client connection.  This is called when stop is called or when a client sends invalid data.
     */
    protected void close(SocketController cc) {
        if (cc == null) return;
        try {
            rwLock.writeLock().lock();
            boolean b = alClientController.remove(cc);
        }
        finally {
            rwLock.writeLock().unlock();
        }
    }
    

    /**
     * Called when new data is read from a client connection.
     * This will verify the data.  If invalid then the socket will be closed, 
     * otherwise the data is used for calling onReadValidText(..)
     * @param cc connection that data is from
     * @param text input data
     */
    protected void onReadLine(SocketController cc, String text) {
        if (!isValidText(text)) {
            if (TerminateText.equals(text)) onTerminateCalled();
            close(cc);
        }
        else {
            onReadValidText(text);
        }
    }

    /**
     * Called when a client connection has an exception.  
     * This will call close, and remove it from the live list and close the socket.
     */
    protected void onException(SocketController cc, IOException e) {
        cc.stop();
        synchronized (alClientController) {
            alClientController.remove(cc);
        }
    }

    /**
     * Used to validate client socket input.
     */
    protected boolean isValidText(String text) {
        if (text == null) return false;
        if (text.length() != requiredDigits) return false;
        for (int i=0; i<requiredDigits; i++) {
            if (!Character.isDigit(text.charAt(i))) return false;
        }
        return true;
    }
    

    /** called whenever a client connection send the terminate string. */
    protected abstract void onTerminateCalled();
    /** called whenever a client connection sends valid data. */
    protected abstract void onReadValidText(String text);
}

