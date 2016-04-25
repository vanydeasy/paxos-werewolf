package client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Random;

/**
 * Kelas pembungkus DatagramSocket yang mensimulaiskan paket yang hilang
 * dalam pengiriman.
 */
public class UnreliableSender
{
    private final DatagramSocket datagramSocket;
    private final Random random;

    public UnreliableSender(DatagramSocket datagramSocket) throws SocketException {
        this.datagramSocket = datagramSocket;
        random = new Random();
    }

    public void send(DatagramPacket packet, double fault) throws IOException {
        double rand = random.nextDouble();
        if (rand < fault) {
                datagramSocket.send(packet);
        }
    }
}
