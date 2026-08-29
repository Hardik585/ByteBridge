package com.hk.bytebridge.service;

import com.hk.bytebridge.utils.UploadUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;

public class FileSharer {

    Map<Integer, String> availableFiles;

    public int offerPort(String filePath){
        int port;
        while (true) {
            port = UploadUtils.generateCode();
            if (!availableFiles.containsKey(port)) {
                availableFiles.put(port, filePath);
                return port;
            }
        }
    }

    public void startFileServer(int port) {
        String filePath = availableFiles.get(port);
        if (filePath == null) {
            System.err.println("No file associated with port: " + port);
            return;
        }
        // Running the WHOLE server operation (accepting + sending) inside the background thread
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                System.out.println("Serving file '" + new File(filePath).getName() + "' on port " + port);

                // Wait for 1 peer to connect
                try (Socket clientSocket = serverSocket.accept()) {
                    System.out.println("Client connected: " + clientSocket.getInetAddress());

                    // Execute transfer directly inside this background thread
                    new FileSenderHandler(clientSocket, filePath).run();
                }
                System.out.println("Finished serving on port " + port + ". Port closed.");
            } catch (IOException e) {
                System.err.println("Error on file server port " + port + ": " + e.getMessage());
            }
        }).start();
    }


    private static class FileSenderHandler implements Runnable {
        private final Socket clientSocket;
        private final String filePath;

        public FileSenderHandler(Socket clientSocket, String filePath) {
            this.clientSocket = clientSocket;
            this.filePath = filePath;
        }

        @Override
        public void run() {
            // checking file is present or not
            File file = new File(filePath);
            if (!file.exists()) {
                System.err.println("File not found: " + filePath);
                return;
            }

            try (FileInputStream fileInputStream = new FileInputStream(filePath);
                 OutputStream oss = clientSocket.getOutputStream()) {

                //Sending the file name into the header
                String fileName = new File(filePath).getName();
                long fileSize = file.length();

                // Write clear HTTP-like protocol headers
                String header = "FileName: " + fileName + "\n" +
                        "FileSize: " + fileSize + "\n\n"; // Double newline marks END OF HEADERS
                oss.write(header.getBytes(java.nio.charset.StandardCharsets.UTF_8));

                //creating a bytes array to reduce the disc call
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                    oss.write(buffer, 0, bytesRead);
                }
                oss.flush();
                System.out.println("File " + fileName + " sent to : " + clientSocket.getInetAddress());
            } catch (Exception e) {
                System.err.println("Error int file Sender Handler : " + e.getMessage());
            }
        }
    }
}
