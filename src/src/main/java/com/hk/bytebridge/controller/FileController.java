package com.hk.bytebridge.controller;

import com.hk.bytebridge.service.FileSharer;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
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

    private class UploadHandler implements HttpHandler {
        // 1. Define maximum allowed upload size (e.g., 50 MB)
        private static final long MAX_UPLOAD_SIZE = 50 * 1024 * 1024; // 50 Megabytes

        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            Headers headers = httpExchange.getResponseHeaders();
            headers.add("Access-Control-Allow-Origin", "*");

            // 2. Validate HTTP Method
            if (!httpExchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendTextResponse(httpExchange, 405, "Method Not Allowed");
                return;
            }

            // 3. Validate Content-Type
            Headers requestHeader = httpExchange.getRequestHeaders();
            String contentType = requestHeader.getFirst("Content-Type");
            if (contentType == null || !contentType.startsWith("multipart/form-data")) {
                sendTextResponse(httpExchange, 400, "Bad Request. Content-Type must be multipart/form-data");
                return;
            }

            // 4. Validate Content-Length header (Early exit if client declares large size upfront)
            String contentLengthHeader = requestHeader.getFirst("Content-Length");
            if (contentLengthHeader != null) {
                try {
                    long declaredLength = Long.parseLong(contentLengthHeader);
                    if (declaredLength > MAX_UPLOAD_SIZE) {
                        sendTextResponse(httpExchange, 413, "Payload Too Large. Max allowed size is " + (MAX_UPLOAD_SIZE / (1024 * 1024)) + " MB");
                        return;
                    }
                } catch (NumberFormatException ignored) {
                    // Ignore invalid header format and fall back to stream-length checking
                }
            }

            // 5. Extract Boundary
            int boundaryIdx = contentType.indexOf("boundary=");
            if (boundaryIdx == -1) {
                sendTextResponse(httpExchange, 400, "Bad Request: Missing boundary in Content-Type header");
                return;
            }
            String boundary = contentType.substring(boundaryIdx + 9);

            // 6. Read Request Body with Enforced Byte Limit
            byte[] requestData;
            try {
                requestData = readRequestBodyWithLimit(httpExchange.getRequestBody(), MAX_UPLOAD_SIZE);
            } catch (MaxUploadSizeExceededException e) {
                sendTextResponse(httpExchange, 413, "Payload Too Large. Max allowed size is " + (MAX_UPLOAD_SIZE / (1024 * 1024)) + " MB");
                return;
            }

            // 7. Parse Payload & Process File
            try {
                MultipartParser multipartParser = new MultipartParser(requestData, boundary);
                MultipartParser.ParseResult result = multipartParser.parse();

                if (result == null) {
                    sendTextResponse(httpExchange, 400, "Bad Request: Could not parse File content");
                    return;
                }

                String fileName = result.fileName;
                if (fileName == null || fileName.trim().isEmpty()) {
                    fileName = "unnamed-file";
                }

                String uniqueFileName = UUID.randomUUID() + "-" + new File(fileName).getName();
                String filePath = uploadDir + File.separator + uniqueFileName;

                try (FileOutputStream fileOutputStream = new FileOutputStream(filePath)) {
                    fileOutputStream.write(result.fileContent);
                }

                int port = fileSharer.offerPort(filePath);
                // Spawn background thread to start FileServer socket listener
                new Thread(() -> fileSharer.startFileServer(port)).start();

                // Return port in JSON response
                String jsonResponse = "{\"port\":" + port + "}";
                headers.add("Content-Type", "application/json");
                sendTextResponse(httpExchange, 200, jsonResponse);

            } catch (Exception e) {
                System.err.println("Error processing file upload: " + e.getMessage());
                if (httpExchange.getResponseCode() == -1) {
                    headers.add("Content-Type", "application/json");
                    sendTextResponse(httpExchange, 500, "{\"error\": \"Server error: " + e.getMessage() + "\"}");
                }
            }
        }
        /**
         * Reads bytes from the InputStream into a byte array, throwing an exception
         * if the total bytes read exceeds maxBytes.
         */
        private byte[] readRequestBodyWithLimit(InputStream input, long maxBytes) throws IOException, MaxUploadSizeExceededException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            long totalBytesRead = 0;
            int bytesRead;

            while ((bytesRead = input.read(buffer)) != -1) {
                totalBytesRead += bytesRead;
                if (totalBytesRead > maxBytes) {
                    throw new MaxUploadSizeExceededException("Upload size limit exceeded");
                }
                baos.write(buffer, 0, bytesRead);
            }
            return baos.toByteArray();
        }

        /**
         * Helper to send plain HTTP response.
         */
        private void sendTextResponse(HttpExchange exchange, int statusCode, String responseText) throws IOException {
            byte[] bytes = responseText.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        /**
         * Custom exception thrown when stream size exceeds configured limit.
         */
        private static class MaxUploadSizeExceededException extends Exception {
            public MaxUploadSizeExceededException(String message) {
                super(message);
            }
        }
    }

}
