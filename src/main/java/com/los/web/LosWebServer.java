package com.los.web;

import com.los.config.AppConfig;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Embedded HTTP REST & Web Application Server using JDK built-in HttpServer.
 */
public class LosWebServer {

    private final int port;
    private HttpServer server;

    public LosWebServer() {
        this.port = AppConfig.getInstance().getServerPort();
    }

    public LosWebServer(int port) {
        this.port = port;
    }

    public synchronized void start() throws IOException {
        if (server != null) {
            return;
        }
        int actualPort = this.port;
        IOException lastEx = null;
        for (int p : new int[]{actualPort, 8085, 8081, 8888, 9090}) {
            try {
                server = HttpServer.create(new InetSocketAddress(p), 0);
                actualPort = p;
                lastEx = null;
                break;
            } catch (IOException e) {
                lastEx = e;
            }
        }
        if (server == null && lastEx != null) {
            throw lastEx;
        }

        LosHttpHandler handler = new LosHttpHandler();
        server.createContext("/", handler);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor()); // Java 21 Virtual Threads!
        server.start();
        System.out.println("================================================================================");
        System.out.printf("  Mini LOS Web Dashboard & REST Server running on http://localhost:%d%n", actualPort);
        System.out.println("================================================================================");
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(1);
            server = null;
            System.out.println("[LosWebServer] Web server stopped.");
        }
    }

    public static void main(String[] args) throws IOException {
        LosWebServer webServer = new LosWebServer();
        webServer.start();
    }
}
