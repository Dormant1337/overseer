package server;

import java.io.*;
import java.net.*;
import java.util.Scanner;

public class TcpClient {

    private final String host;
    private final int port;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    public TcpClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void connect() {
        int attempts = 0;
        for (;;) {
            try {
                socket = new Socket(host, port);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                System.out.println("[CLIENT] Connected to server " + host + ":" + port);
                break;
            } catch (IOException e) {
                System.err.println("[CLIENT] Connection failed (" + (attempts + 1) + "): " + e.getMessage());
                attempts++;
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public void sendMessage(String message, boolean log) {
        if (out != null) {
            out.println(message);
            if (log) {
                System.out.println("[CLIENT] Sent: " + message);
            }
        }
    }

    public String receiveMessage(boolean log) {
        if (in != null) {
            try {
                String msg = in.readLine();
                if (log && msg != null) {
                    System.out.println("[CLIENT] Received: " + msg);
                }
                return msg;
            } catch (IOException e) {
                System.err.println("[CLIENT] Failed to read message: " + e.getMessage());
            }
        }
        return null;
    }

    public void disconnect() {
        try {
            if (socket != null) socket.close();
            System.out.println("[CLIENT] Disconnected.");
        } catch (IOException e) {
            System.err.println("[CLIENT] Error closing connection: " + e.getMessage());
        }
    }

    public void checkPassword(Scanner scanner) {
        while (true) {
            String password = scanner.nextLine();
            sendMessage(password, false);

            String response = receiveMessage(false);
            if (response == null) {
                System.out.println("[CLIENT] No response from server");
                break;
            }

            response = response.trim().toUpperCase();

            switch (response) {
                case "PASSED":
                    System.out.println("[CLIENT] Password Accepted. You passed.");
                    break;
                case "FAILED":
                    System.out.println("[CLIENT] Wrong password, try again.");
                    break;
                default:
                    System.out.println("[CLIENT] Unknown response: " + response);
                    break;
            }
        }
    }

    public void runSession() {
        receiveMessage(false);

        Scanner scanner = new Scanner(System.in);
        System.out.println("[CLIENT] Type messages (or 'exit' to quit):");

        while (true) {
            String input = scanner.nextLine();
            sendMessage(input, true);

            if (input.equalsIgnoreCase("exit")) {
                break;
            }

            receiveMessage(false);
        }
    }

    // beacon thread
    private void startBeaconThread() {
        Thread beaconThread = new Thread(() -> {
            try (DatagramSocket udpSocket = new DatagramSocket()) {
                udpSocket.setBroadcast(true);

                String beaconMsg = "OVERSEER_BEACON|" + host + "|online";
                byte[] buffer = beaconMsg.getBytes();

                InetAddress broadcastAddress = InetAddress.getByName("255.255.255.255");
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, broadcastAddress, 8888);

                while (!Thread.currentThread().isInterrupted()) {
                    udpSocket.send(packet);
                    System.out.println("[BEACON] Sent: " + beaconMsg);
                    Thread.sleep(3000);
                }

            } catch (Exception e) {
                System.err.println("[BEACON] Error: " + e.getMessage());
            }
        });

        beaconThread.setDaemon(true);
        beaconThread.start();
    }

    public static void main(String[] args) {
        TcpClient client = new TcpClient("127.0.0.1", 8080);
        client.connect();

        client.startBeaconThread();

        try (Scanner scanner = new Scanner(System.in)) {
            client.receiveMessage(false);

            String msg = client.receiveMessage(false);
            if (msg != null && msg.equals("REQUEST_PASSWORD")) {
                System.out.println("[CLIENT] Server requested password. Please enter:");
            }

            client.checkPassword(scanner);
            client.runSession();

            client.disconnect();
        }
    }
}
