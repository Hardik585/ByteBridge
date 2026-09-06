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

    private static class MultipartParser {
        private final byte[] data;
        private final String boundary;

        public MultipartParser(byte[] data, String boundary) {
            this.data = data;
            this.boundary = boundary;
        }

        public ParseResult parse() {
            try {
                // 1. Find the end of headers (\r\n\r\n) strictly operating on bytes/ASCII
                byte[] headerEndMarker = "\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
                int headerEnd = findSequence(data, headerEndMarker, 0);
                if (headerEnd == -1) {
                    return null;
                }

                // Decode ONLY the header section to string (safe because HTTP headers are ASCII)
                String headersSection = new String(data, 0, headerEnd, StandardCharsets.US_ASCII);

                // 2. Parse Filename safely
                String fileNameMarker = "filename=\"";
                int fileNameStart = headersSection.indexOf(fileNameMarker);
                if (fileNameStart == -1) {
                    return null;
                }
                fileNameStart += fileNameMarker.length();
                int fileNameEnd = headersSection.indexOf("\"", fileNameStart);
                if (fileNameEnd == -1) {
                    return null;
                }
                String fileName = headersSection.substring(fileNameStart, fileNameEnd);

                // 3. Parse Content-Type safely
                String contentTypeMarker = "Content-Type: ";
                int contentTypeStart = headersSection.indexOf(contentTypeMarker);
                String contentType = "application/octet-stream";

                if (contentTypeStart != -1) {
                    contentTypeStart += contentTypeMarker.length();
                    int contentTypeEnd = headersSection.indexOf("\r\n", contentTypeStart);
                    if (contentTypeEnd != -1) {
                        contentType = headersSection.substring(contentTypeStart, contentTypeEnd).trim();
                    }
                }

                // 4. Binary payload starts right after \r\n\r\n
                int contentStart = headerEnd + headerEndMarker.length;

                // 5. Find end boundary in raw byte array
                byte[] endBoundaryBytes = ("\r\n--" + boundary + "--").getBytes(StandardCharsets.US_ASCII);
                int contentEnd = findSequence(data, endBoundaryBytes, contentStart);

                if (contentEnd == -1) {
                    byte[] boundaryBytes = ("\r\n--" + boundary).getBytes(StandardCharsets.US_ASCII);
                    contentEnd = findSequence(data, boundaryBytes, contentStart);
                }

                if (contentEnd == -1 || contentEnd <= contentStart) {
                    return null;
                }

                // Extract exact raw byte range without charset encoding corruption
                byte[] fileContent = new byte[contentEnd - contentStart];
                System.arraycopy(data, contentStart, fileContent, 0, fileContent.length);

                return new ParseResult(fileName, contentType, fileContent);

            } catch (Exception e) {
                System.err.println("Error parsing multipart payload: " + e.getMessage());
                return null;
            }
        }

        private int findSequence(byte[] data, byte[] sequence, int startPos) {
            outer:
            for (int i = startPos; i <= data.length - sequence.length; i++) {
                for (int j = 0; j < sequence.length; j++) {
                    if (data[i + j] != sequence[j]) {
                        continue outer;
                    }
                }
                return i;
            }
            return -1;
        }

        public static class ParseResult {
            private final String fileName;
            private final String contentType;
            private final byte[] fileContent;

            public ParseResult(String fileName, String contentType, byte[] fileContent) {
                this.fileName = fileName;
                this.contentType = contentType;
                this.fileContent = fileContent;
            }

            public String getFileName() { return fileName; }
            public String getContentType() { return contentType; }
            public byte[] getFileContent() { return fileContent; }
        }
    }
}
