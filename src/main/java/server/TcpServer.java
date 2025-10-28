package server;

import java.io.*;
import java.net.*;
import java.util.List;

public class TcpServer {
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;
    private final String correctPassword = "admin123";
    private final int port;
    private final FileManager fileManager = new FileManager("config/settings.conf", "logs/server.log");

    // udp port for beacon listening
    private static final int BEACON_PORT = 8888;
    private volatile boolean running = true;

    public TcpServer(int port) {
        this.port = port;
    }

    public void start() {
        // init beacon listener
        startBeaconListener();

        try {
            List<String> config = fileManager.readConfig();
            System.out.println("--- Configuration Loaded ---");
            config.forEach(System.out::println);
            System.out.println("-----------------------------");
        } catch (IOException e) {
            System.err.println("FATAL: Could not read configuration file. Server cannot start.");
            e.printStackTrace();
            return;
        }

        fileManager.log("Server starting up...");

        try {
            serverSocket = new ServerSocket(port);
            System.out.println("[SERVER] Listening on port " + port);
            fileManager.log("Server started. Listening on port " + port);

            clientSocket = serverSocket.accept();
            System.out.println("[SERVER] Client connected: " + clientSocket.getInetAddress());
            fileManager.log("Client connected: " + clientSocket.getInetAddress());

            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            sendToClient("Hello, client!", false);
            sendToClient("REQUEST_PASSWORD", false);

            handlePasswordCheck();

        } catch (IOException e) {
            System.err.println("[SERVER] Error: " + e.getMessage());
            fileManager.log("ERROR: " + e.getMessage());
        } finally {
            stop();
        }
    }

    private void sendToClient(String message, boolean log) {
        if (out != null) {
            out.println(message);
            if (log) System.out.println("[SERVER] Sent: " + message);
            fileManager.log("Sent: " + message);
        }
    }

    private String receiveFromClient() {
        try {
            if (in != null) {
                String msg = in.readLine();
                if (msg != null) {
                    System.out.println("[SERVER] Received: " + msg);
                    fileManager.log("Received: " + msg);
                }
                return msg;
            }
        } catch (IOException e) {
            System.err.println("[SERVER] Error reading message: " + e.getMessage());
            fileManager.log("ERROR: " + e.getMessage());
        }
        return null;
    }

    private void handlePasswordCheck() {
        while (true) {
            String clientPassword = receiveFromClient();

            if (clientPassword == null) {
                System.out.println("[SERVER] Client disconnected");
                fileManager.log("Client disconnected during password check");
                break;
            }

            if (clientPassword.equals(correctPassword)) {
                sendToClient("PASSED", false);
                System.out.println("[SERVER] Password accepted!");
                fileManager.log("Password accepted");
                handleClientSession();
                break;
            } else {
                sendToClient("FAILED", false);
                System.out.println("[SERVER] Wrong password attempt");
                fileManager.log("Wrong password attempt");
            }
        }
    }

    private void handleClientSession() {
        sendToClient("Welcome! You are now authenticated.", false);

        while (true) {
            String message = receiveFromClient();
            if (message == null || message.equalsIgnoreCase("exit")) {
                fileManager.log("Client session ended");
                break;
            }
            sendToClient("Echo: " + message, false);
        }
    }

    // thread for beacon listening
    private void startBeaconListener() {
        Thread listenerThread = new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(BEACON_PORT)) {
                socket.setBroadcast(true);
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                System.out.println("[BEACON] Listening for beacons on UDP port " + BEACON_PORT);

                while (running) {
                    socket.receive(packet);
                    String message = new String(packet.getData(), 0, packet.getLength());

                    // filter and log only overseer beacons
                    if (message.startsWith("OVERSEER_BEACON")) {
                        System.out.println("[BEACON] Received: " + message + " from " + packet.getAddress());
                        fileManager.log("Beacon received: " + message + " from " + packet.getAddress());
                    }
                }

            } catch (IOException e) {
                if (running) {
                    System.err.println("[BEACON] Error: " + e.getMessage());
                    fileManager.log("ERROR in Beacon Listener: " + e.getMessage());
                }
            }
        });

        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public void stop() {
        running = false;
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (clientSocket != null) clientSocket.close();
            if (serverSocket != null) serverSocket.close();
            System.out.println("[SERVER] Server stopped");
            fileManager.log("Server has shut down");
        } catch (IOException e) {
            System.err.println("[SERVER] Error stopping server: " + e.getMessage());
            fileManager.log("ERROR: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        TcpServer server = new TcpServer(8080);
        server.start();
        server.sendToClient("Server is shutting down. Goodbye!", false);
    }
}
