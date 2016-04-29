package client;

import java.io.File;
import java.io.FileInputStream;
//import java.io.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Random;

/**
 * Kelas pembungkus DatagramSocket yang mensimulaiskan paket yang hilang
 * dalam pengiriman.
 */
public class UnreliableSender
{
    private final DatagramSocket datagramSocket;
    private InetAddress address;
    private int port;
    private Random random;

    public UnreliableSender(DatagramSocket datagramSocket, String hostname, int port) throws SocketException, UnknownHostException {
        this.datagramSocket = datagramSocket;
        this.address = InetAddress.getByName(hostname);
        this.port = port;
        random = new Random();
    }

    public void send(DatagramPacket packet, double fault) throws IOException {
        double rand = random.nextDouble();

        System.out.println("Sending the package");

        if (rand < fault) {
                datagramSocket.send(packet);
        }

        datagramSocket.close();
    }

    public void send(String filename) throws IOException {

    	File file= new File(filename);
 
        InputStream inFromFile = new FileInputStream(file);
        byte[] fileByteArray = new byte[(int)file.length()];
        inFromFile.read(fileByteArray);

    	System.out.println("Sending the package");

    	int seqNumber = 0;
    	boolean lastMessageFlag = false;

    	//create message yang dikirim
    	for (int i=0; i<fileByteArray.length; i++) {
            seqNumber++;

            byte[] message = new byte[1024];
            message[0] = (byte) (seqNumber >> 8);
            message[1] = (byte) (seqNumber);

            //kalo data paket terakhir
            if ((i+1021) >= fileByteArray.length) {
                lastMessageFlag = true;
                message[2] = (byte)(1);
            } else {
                lastMessageFlag = false;
                message[2] = (byte)(0);
            }

            // Copy bytes ke array
            if (lastMessageFlag == false) {
                for (int j=0; j <= 1020; j++) {
                    message [j+3] = fileByteArray[i+j];
                }
            } else if (lastMessageFlag == true) {
                for (int j=0;  j < (fileByteArray.length - i)  ;j++) {
                    message[j+3] = fileByteArray[i+j];                      
                }
            }

            //kirim
            DatagramPacket sendPacket = new DatagramPacket(message, message.length, address, port);
            datagramSocket.send(sendPacket);
            System.out.println("Sent: Sequence number = " + seqNumber + ", Flag = " + lastMessageFlag);

            // Sleep for 20 ms
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
    	}

    	datagramSocket.close();
    }
}
