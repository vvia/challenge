package com.challenge;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Used to manage a single client socket connection.
 * 
 * This is crated by ServerSocketController.
 */
public abstract class SocketController {
    private static Logger LOG = Logger.getLogger(SocketController.class.getName());
    
    private final Socket socket;
    private final int id;
    private final AtomicBoolean abStart = new AtomicBoolean(); 
 
    /**
     * Create a controller that manages a client connection/socket.
     * @param socket actual socket.
     * @param id identifier that can be assigned to this connection. 
     */
    public SocketController(Socket socket, int id) {
        if (socket == null) throw new IllegalArgumentException("socket can not be null");
        this.socket = socket;
        this.id = id;
    }
    
    public int getId() {
        return this.id;
    }
    
    /**
     * Reads from the input stream and calls onReadLine or onExcpetion.
     * This expects data to be NL terminated strings.
     * 
     * Note: this does not creat a new thread.  The calling code will need 
     * to create a new that then calls this method.
     * 
     * Note: this is not protected against DOS attacks and other types of "hacks".
     */
    public void start() throws IOException {
        if (!abStart.compareAndSet(false, true)) return;

        final InputStream is = socket.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        
        // read input from client
        for (; abStart.get() ;) {
            try {
                String text = reader.readLine();
                if (text == null) break;
                onReadLine(text);
            }
            catch (IOException e) {
                onException(e);
            }
        }
    }
    
    /**
     * Called to close the socket.
     */
    public void stop() {
        if (!abStart.compareAndSet(true, false)) return;
        if (socket == null) return;
        try {
            if (!socket.isClosed()) {
                socket.close();
            }
        }
        catch (Exception e) {
            // no-op
        }
    }
    
    /** method called if the socket has an exception */
    protected abstract void onException(IOException e);
    
    /** called when the socket inputstream receives a new String */
    protected abstract void onReadLine(String text);

}
