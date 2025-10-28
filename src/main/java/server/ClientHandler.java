package server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

/**
 * ClientHandler.java
 *
 * A task responsible for handling all I/O operations for a single client.
 *
 * An instance of this class is created for each readable event and is
 * executed by a worker thread from the server's thread pool. This isolates
 * blocking I/O from the main event loop thread.
 */
public class ClientHandler implements Runnable {

    private final SocketChannel clientChannel;
    private final SelectionKey selectionKey;

    public ClientHandler(SelectionKey selectionKey) {
        this.selectionKey = selectionKey;
        this.clientChannel = (SocketChannel) selectionKey.channel();
    }

    @Override
    public void run() {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        try {
            int bytesRead = clientChannel.read(buffer);

            if (bytesRead == -1) {
                // The client has closed the connection cleanly (end-of-stream).
                handleDisconnect();
                return;
            }

            if (bytesRead > 0) {
                buffer.flip(); // Prepare buffer for reading.
                String message = new String(buffer.array(), 0, buffer.limit()).trim();
                System.out.println("Received from " + getClientAddressSafe() + ": " + message);

                /*
                 * <<< FIX FOR CLIENT TERMINAL DISPLAY >>>
                 * Append a newline character ('\n') to the response. This acts as
                 * a "carriage return" for most terminal clients (like netcat),
                 * causing the cursor to move to the next line after printing the echo.
                 */
                String response = "Echo: " + message + "\n";
                ByteBuffer responseBuffer = ByteBuffer.wrap(response.getBytes());
                clientChannel.write(responseBuffer);
            }

        } catch (IOException e) {
            // An exception here typically means the client connection was forcibly closed.
            System.err.println("Connection error with " + getClientAddressSafe() + ": " + e.getMessage());
            handleDisconnect();
        }
    }

    /**
     * Centralized resource cleanup for a disconnected client.
     */
    private void handleDisconnect() {
    try {
        System.out.println("Client disconnected: " + getClientAddressSafe());
        
        if (selectionKey != null && selectionKey.isValid()) {
            selectionKey.cancel();
        }
        
        if (clientChannel != null && clientChannel.isOpen()) {
            clientChannel.close();
        }
    } catch (IOException e) {
        System.err.println("Error while closing client connection: " + e.getMessage());
    }
}

    /**
     * Safely retrieves the client's remote address as a string.
     * @return The remote address or a placeholder string if an error occurs.
     */
    private String getClientAddressSafe() {
        try {
            return clientChannel.getRemoteAddress().toString();
        } catch (IOException e) {
            return "[address unavailable]";
        }
    }
}