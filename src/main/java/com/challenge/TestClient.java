package com.challenge;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Test client to connect to server and send data.
 * @author vvia
 */
public class TestClient {
    private static Logger LOG = Logger.getLogger(TestClient.class.getName());
    
    public TestClient() {
        
    }
    
    public void testMany(int cnt) throws Exception {
    
        final CyclicBarrier barrier = new CyclicBarrier(cnt);
        final CountDownLatch countDownLatch = new CountDownLatch(cnt);
        
        for (int i=0; i<7; i++) {
            final int id = i;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        LOG.fine(id+" is created");
                        barrier.await();
                        LOG.fine(id+" is starting");
                        testOne();
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
        countDownLatch.await(35, TimeUnit.SECONDS);
        
        LOG.fine("MAIN THREAD send terminate message, countDownLatch.cnt="+countDownLatch.getCount());

        Socket socket = new Socket("localhost", 4000);
        
        OutputStream os = socket.getOutputStream();
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(os));
        
        pw.println("terminate");
        
        pw.flush();
        os.close();
        LOG.fine("MAIN THREAD is DONE");
    }    
    
    public void testOne() throws Exception {
        Socket socket;
        socket = new Socket("localhost", 4000);
        socket.setTcpNoDelay(true);
        
        OutputStream os = socket.getOutputStream();
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(os));
        

        final int max = (int) Math.pow(10, 9);
        for (int i=0; i<30 ; i++) {
            double d = Math.random() * max;
            String s = String.format("%09d", (int)d);
            pw.println(s);
            if (pw.checkError()) break;
Thread.sleep(1 * 1000);            
        }
        
        pw.flush();
        os.close();
    }
    
    
    
    
    public static void main(String[] args) throws Exception {
        Logger log = Logger.getLogger("");
        log.setLevel(Level.FINE);
        ConsoleHandler ch = new ConsoleHandler();
        ch.setLevel(Level.FINE);
        log.addHandler(ch);
        
        LOG.fine("TestClient is starting, will create 7 clients");
        TestClient tc = new TestClient();
        tc.testMany(7);
        LOG.fine("TestClient is DONE");
    }
    
}
