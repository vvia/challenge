package com.challenge;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Test client to connect to server and send data.
 * 
 * Includes options to run many or one test client.
 * 
 * @author vvia
 */
public class TestClient {
    private static Logger LOG = Logger.getLogger(TestClient.class.getName());
    
    public TestClient() {
        
    }
    
    public void testMany(final int cnt, final int numberOfSeconds) throws Exception {
    
        final CyclicBarrier barrier = new CyclicBarrier(cnt);
        final CountDownLatch countDownLatch = new CountDownLatch(cnt);
        
        for (int i=0; i<cnt; i++) {
            final int id = i;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        LOG.fine(id+" is created");
                        barrier.await();
                        LOG.fine(id+" is starting");
                        testOne(numberOfSeconds);
                    }
                    catch (Exception e) {
                    }
                    finally {
                        countDownLatch.countDown();
                    }
                    LOG.fine(id+" is DONE");
                }
            }).start();
        }
        
        LOG.fine("MAIN THREAD waiting on countDownLatch, cnt="+countDownLatch.getCount());
        // countDownLatch.await(numberOfSeconds, TimeUnit.MILLISECONDS);
        countDownLatch.await();
        
        String s = "MAIN THREAD send terminate message, countDownLatch.cnt="+countDownLatch.getCount();
        LOG.fine(s);
        Thread.sleep(2000);

        Socket socket = new Socket("localhost", 4000);
        
        OutputStream os = socket.getOutputStream();
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(os));
  
        pw.println("terminate");
        
        pw.flush();
        pw.close();
        socket.close();
        LOG.fine("MAIN THREAD is DONE");
    }    
    
    public void testOne(int numberOfSeconds) throws Exception {
        Socket socket;
        socket = new Socket("localhost", 4000);
        // socket.setTcpNoDelay(true);

        boolean b = socket.isClosed();

        OutputStream os = socket.getOutputStream();
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(os));

        final int max = (int) Math.pow(10, 9);
        long ms = System.currentTimeMillis();
        for (int i=0; ; i++) {
            if ( (System.currentTimeMillis() - ms) > (numberOfSeconds * 1000)) break;

            double d = Math.random() * max;
            String s = String.format("%09d", (int)d);
            pw.println(s);
            if (i % 1000 == 0 && pw.checkError()) { // causes decrease in performance
                break;   
            }
            // Thread.sleep(1 * 1000);
        }
        
        // tcp/ip does not have a real-time way to determine disconnect, so sending invalid data to trigger server side socket close
        pw.println("END");  
        
        pw.flush();
        pw.close();
        socket.close();
    }
    
    public static void main(String[] args) throws Exception {
        Logger log = Logger.getLogger("");
        log.setLevel(Level.FINE);
        ConsoleHandler ch = new ConsoleHandler();
        ch.setLevel(Level.FINE);
        log.addHandler(ch);
        
        LOG.fine("TestClient is starting, will create 7 clients");
        TestClient tc = new TestClient();
        tc.testMany(7, 120);
        // tc.testOne();
        LOG.fine("TestClient is DONE");
    }
    
}
