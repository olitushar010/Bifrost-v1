package com.bifrost;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.OutputStream;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
  private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
  private static final int PORT = 8080;
  private static final int MAX_PAYLOAD_SIZE = 1048576;

  public static void main(String[] args) {
    try (ServerSocket serverSocket = new ServerSocket(PORT)) {
      System.out.println("Bifrost Server started on port " + PORT);
      while (true) {
        Socket clientSocket = serverSocket.accept();
        EXECUTOR.submit(() -> handleClient(clientSocket));
      }
    } catch (IOException e) {
      System.err.println("Could not start server on port 8080:  " + e.getMessage());
    }
  }

  public static void handleClient(Socket clientSocket) {
    OutputStream out = null;
    try {
      clientSocket.setSoTimeout(5000);
      out = clientSocket.getOutputStream();
      BufferedReader reader =
              new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

      String line;
      int contentLength = 0;
      while ((line = reader.readLine()) != null && !line.isEmpty()) {
        System.out.println(line);
        if (line.toLowerCase().startsWith("content-length:")) {
          String[] parts = line.split(":");
          contentLength = Integer.parseInt(parts[1].trim());
        }
      }

      if (contentLength > MAX_PAYLOAD_SIZE) {
        sendHttpResponse(out, "413 Payload Too Large", "Payload too large");
        return;
      }

      if (contentLength > 0) {
        char[] bodyBuffer = new char[contentLength];
        int totalCharsRead = 0;
        while (totalCharsRead < contentLength) {
          int charRead = reader.read(bodyBuffer, totalCharsRead, contentLength - totalCharsRead);
          if (charRead == -1) {
            System.out.println("Client dropped connection prematurely.");
            return;
          }
          totalCharsRead += charRead;
        }
        String requestBody = new String(bodyBuffer);
        System.out.println(requestBody);
        System.out.println("----------------------------\n");
      }

      sendHttpResponse(out, "200 OK", "Event tracked successfully");
    } catch (SocketTimeoutException e) {
      System.out.println("Socket timeout occurred: " + e.getMessage());
      try {
        if (out != null) sendHttpResponse(out, "408 Request Time Out", "Request Timed Out");
      } catch (IOException ignored) {
      }
    } catch (SocketException e) {
      System.out.println("Socket error occurred: " + e.getMessage());
    } catch (IOException e) {
      System.out.println("Error handling the client: " + e.getMessage());
    } finally {
      try {
        clientSocket.close();
      } catch (IOException ignored) {
      }
    }
  }

  private static void sendHttpResponse(OutputStream out, String status, String body)
          throws IOException {
    String response =
            "HTTP/1.1 "
                    + status
                    + "\r\n"
                    + "Content-Length: "
                    + body.length()
                    + "\r\n"
                    + "Content-Type: text/plain\r\n\r\n"
                    + body;
    out.write(response.getBytes(StandardCharsets.UTF_8));
    out.flush();
  }
}