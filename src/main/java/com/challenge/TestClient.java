package com.challenge;

import java.io.*;
import java.net.Socket;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;



public class TestClient {
    private static Logger LOG = Logger.getLogger(TestClient.class.getName());
    
    public TestClient() {
        
    }
    
    public void test() throws Exception {
        Socket socket;
        socket = new Socket("localhost", 4000);
        socket.setTcpNoDelay(true);
        
        OutputStream os = socket.getOutputStream();
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(os));
        
        
        for (int i=0; i<5000000 ; i++) {
            double d = Math.random() * (Math.pow(10, 9));
            String s = String.format("%09d", (int)d);
            pw.println(s);
//            if (pw.checkError()) break;
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
        
        TestClient tc = new TestClient();
        tc.test();
    }
    
}
