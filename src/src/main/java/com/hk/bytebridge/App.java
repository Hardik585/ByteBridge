package com.hk.bytebridge;

import com.hk.bytebridge.controller.FileController;

import java.io.IOException;

/**
 * P2P sharing application
 *
 */

public class App {
    public static void main(String[] args) {
        int apiPort = 8080;

        try {
            // Instantiate the API server
            FileController fileController = new FileController(apiPort);

            // Shutdown hook for Ctrl+C or SIGTERM signals
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nShutting down ByteBridge server...");
                fileController.stop();
            }));

            // Start the HTTP server listener
            fileController.start();

            System.out.println("==================================================");
            System.out.println(" ByteBridge Server Started Successfully");
            System.out.println(" REST API Port: " + apiPort);
            System.out.println(" React Frontend URL: http://localhost:3000");
            System.out.println(" Press Enter or Ctrl+C to stop the server");
            System.out.println("==================================================");

            // Wait for user keypress in terminal
            int read = System.in.read();

            // Graceful manual stop on Enter keypress
            fileController.stop();

        } catch (IOException e) {
            System.err.println("Failed to start ByteBridge server: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
