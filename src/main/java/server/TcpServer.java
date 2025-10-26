package server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TcpServer.java
 *
 * A non-blocking TCP server implementation using Java NIO.
 *
 * This class is responsible for the server's lifecycle. It binds to a port,
 * listens for incoming connections, and dispatches I/O events to worker
 * threads. The core of its operation is a single-threaded event loop
 * managed by a Selector.
 */
public class TcpServer {

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 8080;
    private static final int THREAD_POOL_SIZE = 10;

    private final ExecutorService workerPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

    /**
     * Initializes and starts the server's main event loop.
     */
    public void start() {
        try (ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
             Selector selector = Selector.open()) {

            serverSocketChannel.bind(new InetSocketAddress(HOST, PORT));
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            System.out.println("Server started. Listening on " + HOST + ":" + PORT);

            while (true) {
                selector.select(); // Blocks until at least one channel is ready for I/O.

                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();

                    /*
                     * <<< CRITICAL FIX FOR RACE CONDITION >>>
                     * Before processing, we must ensure the key is still valid.
                     * It's possible for a worker thread in ClientHandler to have
                     * cancelled this key (e.g., on client disconnect) right after
                     * the selector returned it. This check prevents a
                     * CancelledKeyException.
                     */
                    if (!key.isValid()) {
                        keyIterator.remove();
                        continue;
                    }

                    if (key.isAcceptable()) {
                        acceptConnection(key, selector);
                    } else if (key.isReadable()) {
                        readData(key);
                    }

                    keyIterator.remove();
                }
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        } finally {
            workerPool.shutdown();
        }
    }

    /**
     * Handles new incoming client connections.
     *
     * @param key The selection key representing the acceptance event.
     * @param selector The main selector to register the new client channel with.
     * @throws IOException If an I/O error occurs when accepting the connection.
     */
    private void acceptConnection(SelectionKey key, Selector selector) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();
        clientChannel.configureBlocking(false);
        clientChannel.register(selector, SelectionKey.OP_READ);
        System.out.println("Accepted new connection from: " + clientChannel.getRemoteAddress());
    }

    /**
     * Dispatches a readable channel to a worker thread for processing.
     *
     * @param key The selection key representing the readable event.
     */
    private void readData(SelectionKey key) {
        workerPool.submit(new ClientHandler(key));
    }

    /**
     * Main entry point to create and run the server.
     */
    public static void main(String[] args) {
        new TcpServer().start();
    }
}