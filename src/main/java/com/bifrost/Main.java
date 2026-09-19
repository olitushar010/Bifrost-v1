package com.bifrost;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
  private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
  private static final int PORT = 8080;
  private static final int MAX_PAYLOAD_SIZE = 1048576;
  private static final int MAX_HEADER_LINE_LENGTH = 8192;
  private static final int MAX_TOTAL_HEADER_SIZE = 32 * 1024;

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
    try (clientSocket) {
      clientSocket.setSoTimeout(5000);
      out = clientSocket.getOutputStream();
      // Buffered Reader is not good here because it stores and decodes it while reading (it won't
      // handle emoji's)
      //      BufferedReader reader = new BufferedReader(new
      // InputStreamReader(clientSocket.getInputStream()));
      InputStream in = new BufferedInputStream(clientSocket.getInputStream());
      String line;
      int contentLength = 0;
      int headerSize=0;
      String contentType = "application/json";
      boolean contentTypeFound = false;
      boolean firstLine = true;
      while ((line = readLine(in)) != null && !line.isEmpty()) {
        headerSize += line.getBytes(StandardCharsets.UTF_8).length + 2; // +2 for \r\n
        if (headerSize > MAX_TOTAL_HEADER_SIZE) {
          sendHttpResponse(out, "431 Request Header Fields Too Large", "Header too large");
          return;
        }
        System.out.println(line);
        if (firstLine) {
          String[] parts = line.split(" ");

          if (parts.length < 2) {
            sendHttpResponse(out, "400 Bad Request", "Invalid request line");
            return;
          }
          if (!parts[0].equals("POST")) {
            sendHttpResponse(out, "405 Method Not Allowed", "Use POST only");
            return;
          }
          if (!parts[1].equalsIgnoreCase("/events")) {
            sendHttpResponse(out, "404 Not Found", "NO such endpoint");
            return;
          }
          firstLine = false;
        }
        if (line.toLowerCase().startsWith("content-length:")) {
          String[] parts = line.split(":",2);
          if (parts.length >= 2) {
            try {
              contentLength = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException e) {
              sendHttpResponse(out, "400 Bad Request", "Invalid content length");
              return;
            }
          }
        }

        if (line.toLowerCase().startsWith("content-type:")) {
          contentTypeFound = true;
          String[] parts = line.split(":",2);
          String type = parts[1].trim();
          if (!type.toLowerCase().startsWith(contentType)) {
            sendHttpResponse(out, "415 Unsupported Media Type", "Invalid content type");
            return;
          }
        }
      }
      if (!contentTypeFound) {
        sendHttpResponse(out, "400 Bad Request", "Content-Type header is missing");
        return;
      }
      if (contentLength > MAX_PAYLOAD_SIZE) {
        sendHttpResponse(out, "413 Payload Too Large", "Payload too large");
        return;
      }
      if(contentLength <= 0) {
        sendHttpResponse(out, "411 Length Required", "Invalid content length");
        return;
      }

        // READ the body as raw bytes and convert it after reading
        byte[] bodyBuffer = new byte[contentLength];
        int totalBytesRead = 0;
        while (totalBytesRead < contentLength) {
          int bytesRead = in.read(bodyBuffer, totalBytesRead, contentLength - totalBytesRead);
          if (bytesRead == -1) {
            System.out.println("Client dropped connection prematurely.");
            return;
          }
          totalBytesRead += bytesRead;
        }
        // convert to text
        String requestBody = new String(bodyBuffer, StandardCharsets.UTF_8);
        System.out.println(requestBody);
        System.out.println("----------------------------\n");

      sendHttpResponse(out, "200 OK", "Event tracked successfully");
    } catch (SocketTimeoutException e) {
      System.out.println("Socket timeout occurred: " + e.getMessage());
      try {
        if (out != null) sendHttpResponse(out, "408 Request Time Out", "Request Timed Out");
      } catch (IOException ignored) {
      }
    } catch (SocketException e) {
      System.out.println("Socket error occurred: " + e.getMessage());
    }catch(HeaderTooLargeException e){
      System.out.println("Header too large: " + e.getMessage());
      try{
        if(out != null) sendHttpResponse(out, "431 Request Header Fields Too Large", e.getMessage());
      }catch(IOException ignored){}
    } catch (IOException e) {
      System.out.println("Error handling the client: " + e.getMessage());
    }
  }

  private static String readLine(InputStream in) throws IOException {
    ByteArrayOutputStream lineBytes = new ByteArrayOutputStream();
    int b;
    int length = 0;
    while ((b = in.read()) != -1) {
      if (b == '\n') break;
      if (b != '\r') {
        lineBytes.write(b);
        length++;
        if (length > MAX_HEADER_LINE_LENGTH) {
          throw new HeaderTooLargeException(
              "Header line exceeds maximum limit of " + MAX_HEADER_LINE_LENGTH + " bytes");
        }
      }
    }
    if (b == -1 && lineBytes.size() == 0) return null;
    return lineBytes.toString(StandardCharsets.UTF_8);
  }

  private static void sendHttpResponse(OutputStream out, String status, String body)
      throws IOException {
    String response =
        "HTTP/1.1 "
            + status
            + "\r\n"
            + "Content-Length: "
            + body.getBytes(StandardCharsets.UTF_8).length
            + "\r\n"
            + "Content-Type: text/plain\r\n"
            + "Connection: close\r\n\r\n"
            + body;
    out.write(response.getBytes(StandardCharsets.UTF_8));
    out.flush();
  }
}
class HeaderTooLargeException extends IOException {
  public HeaderTooLargeException(String message){
    super(message);
  }
}
