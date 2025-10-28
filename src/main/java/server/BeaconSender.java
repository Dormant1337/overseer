package server;
import java.net.*;

public class BeaconSender {
    public static void main(String[] args) throws Exception {
        DatagramSocket socket = new DatagramSocket();
        socket.setBroadcast(true);

        String message = "OVERSEER_BEACON|192.168.0.42|online";
        byte[] buffer = message.getBytes();

        InetAddress broadcastAddress = InetAddress.getByName("255.255.255.255");
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, broadcastAddress, 8888);

        while (true) {
            socket.send(packet);
            System.out.println("Beacon sent");
            Thread.sleep(3000);
        }
    }
}
