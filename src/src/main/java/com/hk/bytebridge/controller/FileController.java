package com.hk.bytebridge.controller;

import com.hk.bytebridge.service.FileSharer;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileController {
    private final HttpServer server;
    private final FileSharer fileSharer;
    private final ExecutorService executorService;
    private final String uploadDir;

    public FileController(int port) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.executorService = Executors.newFixedThreadPool(10);
        this.fileSharer = new FileSharer();
        this.uploadDir = System.getProperty("java.io.tmpdir") + File.separator + "ByteBridge-uploads";

        File uploadDirFile = new File(uploadDir);
        if (!uploadDirFile.exists()) {
            uploadDirFile.mkdirs();
        }
        server.createContext("/upload", new UploadHandler());
        server.createContext("/download", new DownloadHandler());
        server.createContext("/", new CorsHandler());
        server.setExecutor(executorService);
    }

    public void start() {
        server.start();
        System.out.println("Server started on port : " + server.getAddress().getPort());
    }


    public void stop() {
        server.stop(0);
        executorService.shutdown();
        System.out.println("Server stopped on port : " + server.getAddress().getPort());
    }

    private static class CorsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            Headers headers = httpExchange.getResponseHeaders();
            headers.add("Access-Control-Allow-Origin", "*");
            headers.add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, HEAD");
            headers.add("Access-Control-Allow-Headers", "Content-Type, Authorization");

            if ("OPTIONS".equals(httpExchange.getRequestMethod())) {
                httpExchange.sendResponseHeaders(204, -1);
                return;
            }

            String response = "Not Found";
            httpExchange.sendResponseHeaders(404, response.getBytes().length);
            try (OutputStream os = httpExchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }
}
