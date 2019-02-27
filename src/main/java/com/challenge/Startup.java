package com.challenge;

import java.util.logging.*;

/**
 * Program startup with main method, for running Server.
 * @author vvia
 */
public class Startup {
    private static Logger LOG = Logger.getLogger(Startup.class.getName());

    /**
     * Start the server controller and allow client connections.
     */
    public void start() throws Exception {
        LOG.fine("Start called, creating server controller");
        ServerController sc = new ServerController() {
            @Override
            public void stop() {
                super.stop();
                System.exit(0);
            }
        };
        LOG.fine("Starting server controller");
        sc.start();
        LOG.fine("Start completed");
    }
    
    /**
     * Main entry application startup.
     * Note: main thread is not "kept".  This will call start, which will create a new thread.
     */
    public static void main(String[] args) throws Exception {
        
        // start java logging.  Note: logging is not in any code path that needs to be performant.
        Logger log = Logger.getLogger("");
        log.setLevel(Level.FINE);
        ConsoleHandler ch = new ConsoleHandler();
        ch.setLevel(Level.FINE);
        log.addHandler(ch);
        
        Startup startup = new Startup();
        startup.start();
        
        // hold and sleep main thread
        for (;;) {
            try {
                Thread.sleep(30 * 1000);
            }
            catch (Exception e) {
            }
        }
    }
    
}
