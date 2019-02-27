package com.challenge;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.BitSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.*;

/**
 * Main controller for server app, that manages threads and other controller objects.
 * 
 * This will create a ServerSocketController and add the client data to a queue that is then
 * serviced by a thread that calculates stats and determines if the received values are unique,
 * and if so will, add to another queue for output. Another thread uses this output queue to 
 * write to an instance file "numbers.log"
 * 
 */
public class ServerController {
    private static Logger LOG = Logger.getLogger(ServerController.class.getName());
    
    private ServerSocketController controlServerSocket;
    
    /* best performance
    private final ArrayBlockingQueue<String> queNumber = new ArrayBlockingQueue<>(80000);
    private final ArrayBlockingQueue<String> queLog = new ArrayBlockingQueue<>(80000);
    */

    /**
     * Queue used for storing new numbers read from client sockets.
     */
    private final LinkedBlockingQueue<String> queNumber = new LinkedBlockingQueue<>(100000);
    /**
     * Queue used for storing new unique numbers that need to be written to numbers.log
     */
    private final LinkedBlockingQueue<String> queLog = new LinkedBlockingQueue<>(100000);
    
    /**
     * Used to determine if a nine-digit number has already been used.
     */
    private BitSet bsNumber;
    
    /**
     * Number of unique numbers received by client connnections.
     */
    private final AtomicInteger aiNewCount = new AtomicInteger();
    /**
     * Number of duplicate numbers received by client connnections.
     */
    private final AtomicInteger aiDupCount = new AtomicInteger();
    
    /**
     * buffered writer to write unique numbers to a file.
     */
    private PrintWriter writerLog;
    
    /**
     * Manages state of this controller, for start/stop.
     */
    private final AtomicBoolean abStart = new AtomicBoolean();
    
    /**
     * hardcoded number of digits required for client numbers, also needed to determin size of bitset.
     */
    public static final int requiredDigits = 9;
    
    /**
     * Max value of digits that can be received from client input.
     */
    public static final int MaxValue = ((int) Math.pow(10, requiredDigits)) - 1;

    
    /**
     * These track the number of times that a thread had to wait to add to a queue.
     */
    private AtomicInteger aiInputQueueWait = new AtomicInteger();
    private AtomicInteger aiOutputQueueWait = new AtomicInteger();

    /**
     * used to gracefully shutdown
     */
    private CountDownLatch countDownLatch;

    
    
    /**
     * construtor without any required params.
     */
    public ServerController() {
    }
    
    
    /**
     * Start the server controller, which will start the Server Socket Controller, allowing client socket connections.
     * This will start two other threads to perform a "pipeline" processing of client data.
     * All client data will be added to a queue, allowing the "reader" thread to continue.
     * 
     * The first thread thread reads from the input queue and determines if the number is unique (using bitset). This 
     * thread also increments counters.  If number is unique, then it will be added to a second queue for output.
     * 
     * The second thread that is created here will read from the output queue and write the numbers to a single
     * file named "numbers.log".  This file is created new (and overwritten) each time start is called.
     *  
     * @throws IOException if output file can not created.
     */
    public void start() throws IOException {
        LOG.log(Level.FINE, "start called, isAlreadyStarted="+abStart);
        if (!abStart.compareAndSet(false, true)) return;

        bsNumber = new BitSet(MaxValue+1);
        
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                long msLastError = 0;
                for ( ;abStart.get(); ) {
                    try {
                        String text = queNumber.take();
                        
                        int x = (new Integer(text)).intValue();
                        boolean b = bsNumber.get(x);
                        if (b) {
                            aiDupCount.incrementAndGet();
                        }
                        else {
                            aiNewCount.incrementAndGet();
                            bsNumber.set(x);
                            if (!queLog.offer(text)) {
                                aiOutputQueueWait.incrementAndGet();
                                queLog.put(text);
                            }
                        }
                    }
                    catch (Exception e) {
                        long ms = System.currentTimeMillis();
                        if (ms > msLastError + 5000) {
                            LOG.log(Level.WARNING, "exception while processing numbers from clients", e);
                            msLastError = ms;
                        }
                    }
                }
            }
        }, "ServerController.ProcessNumbers");
        // thread.setPriority(Thread.MAX_PRIORITY);
        thread.setDaemon(true);
        thread.start();
        LOG.log(Level.FINE, "started thread "+thread.getName());
        
        // thread to report every 10 seconds
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                int iLastNewCount = 0;
                int iLastDupCount = 0;
                
                for ( ;abStart.get(); ) {
                    try {
                        Thread.sleep(10 * 1000);
                        int newCount = aiNewCount.get();
                        int dupCount = aiDupCount.get();
                        
                        String s = String.format("Received %,d unique numbers, %,d duplicates, Unique total: %,d, total read: %,d", 
                            (newCount-iLastNewCount), (dupCount-iLastDupCount), aiNewCount.get(), (newCount+dupCount));
                        
                        /*
                        s += ", InputQueWait="+aiInputQueueWait.get();
                        s += ", OutputQueWait="+aiOutputQueueWait.get();
                        */
                        
                        System.out.println(s);
                        
                        iLastNewCount = newCount;
                        iLastDupCount = dupCount;
                    }
                    catch (Exception e) {
                        LOG.log(Level.WARNING, "exception while formating 10second report", e);
                    }
                }
            }
        }, "ServerController.Reporter");
        thread.setDaemon(true);
        thread.start();
        LOG.log(Level.FINE, "started thread "+thread.getName());

        
        // thread to log new numbers
        File file = new File("numbers.log");
        writerLog = new PrintWriter(file);  // uses 8192 buffer by default, does not incude auto flushing on newline
        
        countDownLatch = new CountDownLatch(1);
        
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                long msLastError = 0;
                for ( ;abStart.get(); ) {
                    try {
                        if (queLog.isEmpty()) writerLog.flush();
                        String text = queLog.take();
                        if (text.length() == 0) continue;
                        writerLog.println(text);
                    }
                    catch (Exception e) {
                        long ms = System.currentTimeMillis();
                        if (ms > msLastError + 5000 && abStart.get()) {
                            LOG.log(Level.WARNING, "exception while writing unique numbers to log", e);
                            msLastError = ms;
                        }
                    }
                }
                try {
                    writerLog.flush();
                    writerLog.close();
                }
                catch (Exception e) {
                    LOG.log(Level.WARNING, "exception while closing log", e);
                }
                finally {
                    countDownLatch.countDown();
                }
            }
        }, "ServerController.Logger");
        thread.setDaemon(true);
        thread.start();
        LOG.log(Level.FINE, "started thread "+thread.getName());

        // start server socket
        getServerSocketController().start();
        LOG.log(Level.FINE, "start process completed");
    }

    
    /**
     * Used to 'close' the log file, close client sockets and the server socket.
     * Note: any numbers in the output queue will not be written to the log file.
     */
    public void stop() {
        if (!abStart.compareAndSet(true, false)) return;
        try {
            getServerSocketController().stop();
            // make sure logger thread cleans up log file.
            queLog.offer(""); 
            countDownLatch.await(10, TimeUnit.SECONDS);
        }
        catch (Exception e) {
            LOG.log(Level.WARNING, "exception while stopping", e);
        }
    }
    
    
    /**
     * Creates server socket controller that receives client connects and 
     * @return
     */
    public ServerSocketController getServerSocketController() {
        if (controlServerSocket != null) return controlServerSocket;
        controlServerSocket = new ServerSocketController(requiredDigits) {
            
            @Override
            protected void onTerminateCalled() {
                ServerController.this.stop();
            }
            
            @Override
            protected void onReadValidText(String text) {
                for (;;) {
                    try {
                        if (!queNumber.offer(text)) {
                            aiInputQueueWait.incrementAndGet();
                            queNumber.put(text);
                        }
                        break;
                    }
                    catch (Exception e) {
                        LOG.log(Level.WARNING, "exception adding to number queue", e);
                    }
                }
            }
        };
        return controlServerSocket;
    }
    
    
}


