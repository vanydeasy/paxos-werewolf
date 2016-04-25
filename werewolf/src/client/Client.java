/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.simple.JSONArray;
import server.Server;
import org.json.simple.JSONObject;

/**
 *
 * @author vanyadeasy
 */
public class Client {
    public String SERVER_HOSTNAME;
    public int COMM_PORT;  // socket port for client comms
    
    private Socket socket;
    
    private int player_id;
    private String role;
    private JSONArray players = new JSONArray();
    
    public Client() {
        SERVER_HOSTNAME = "127.0.1.1";
        COMM_PORT = 8181;
    }
    
    public Client(String host, int port) {
        SERVER_HOSTNAME = host;
        COMM_PORT = port;
    }
    
    public static void main(String[] args) {
        Client client = new Client();
        client.connectToServer();
        System.out.println(client.socket);
        
        // Get username from user
        client.joinGame();

        // GAME PLAY HERE
        if(client.players.size() > 2) {
            int listenPort = 9876;
            
            if(client.player_id == (Integer)((JSONObject)client.players.get(client.players.size()-2)).get("player_id")
                || client.player_id == (Integer)((JSONObject)client.players.get(client.players.size()-1)).get("player_id")) {
                // PROPOSER pid dua terbesar (player ke n dan n­1) 
                try {
                    System.out.println("You can propose");
                    BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
                    
                    DatagramSocket datagramSocket = new DatagramSocket();
                    UnreliableSender unreliableSender = new UnreliableSender(datagramSocket);
                    JSONObject sent = new JSONObject();
                    sent.put("method", "prepare_proposal");
                    sent.put("proposal_id", "(1,"+client.player_id+")");
                    byte[] sendData = sent.toJSONString().getBytes();
                    for(int i=0;i<client.players.size();i++) {
                        String ipAddress = (String)((JSONObject)client.players.get(i)).get("address");
                        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName(ipAddress.substring(1)), listenPort);
                        unreliableSender.send(sendPacket, 1.00);
                    }
                } catch (SocketException ex) {
                    Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
                } catch (UnknownHostException ex) {
                    Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
                } catch (IOException ex) {
                    Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
                }
                
            } else {
                // ACCEPTOR
                try {
                    System.out.println("You cannot propose");
                    byte[] receiveData = new byte[1024];
                    
                    DatagramSocket serverSocket = new DatagramSocket(listenPort);
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                    for(int i=0;i<2;i++) {
                        serverSocket.receive(receivePacket);
                        String sentence = new String(receivePacket.getData(), 0, receivePacket.getLength());
                        System.out.println(sentence);
                    }
                } catch (SocketException ex) {
                    Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
                } catch (IOException ex) {
                    Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        
        client.leaveGame();
    }
    
    public void connectToServer() { // Connect client to server (binding)
        System.out.println("Connecting to server...");
        try {
            this.socket = new Socket(SERVER_HOSTNAME, COMM_PORT);
        } catch (IOException ex) {
            Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
        }
        System.out.println("Connected!");
    }
    
    public Object listenToServer() {
        Object object = null;
        try {
            InputStream iStream = socket.getInputStream();
            ObjectInputStream oiStream = new ObjectInputStream(iStream);
            object = (Object) oiStream.readObject();
            if(object instanceof JSONObject) {
                System.out.println("JSON received: "+((JSONObject) object).toJSONString());
            }
            else {
                System.out.println("Unknown object received: "+object.toString());
            }
        } catch (IOException | ClassNotFoundException ex) {
            Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
        }
        return object;
    }
    
    // Send object to server
    public void sendToServer(Object object) {
        System.out.println("Send to server: "+object.toString());
        OutputStream oStream = null;
        try {
            oStream = socket.getOutputStream();
            ObjectOutputStream ooStream = new ObjectOutputStream(oStream);
            ooStream.writeObject(object);  // send seriliazed
        } catch (IOException ex) {
            Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public void joinGame() {
        JSONObject obj = new JSONObject();
        do {
            Scanner keyboard = new Scanner(System.in);
            System.out.print("Username: ");
            String username = keyboard.nextLine();
            // Mengirim username ke server
            obj.put("username", username);
            obj.put("method","join");
            sendToServer(obj);

            obj = (JSONObject)listenToServer();
            if(obj.get("status")==null) break;
        } while(obj.get("status").equals("fail"));

         // Mendapatkan player id dari server
        player_id = (Integer)obj.get("player_id");
        obj.clear();
        
        // Mengirim ready ke user
        obj.put("method","ready");
        sendToServer(obj);
        
        // Mendapatkan status dari server = ok
        obj = (JSONObject)listenToServer();
        if(obj.get("status").equals("ok")) {
            // Mendapatkan role dari server saat player sudah cukup
            role = (String)((JSONObject)listenToServer()).get("role");
            System.out.println("YOUR ROLE IS "+role);
        }
        
        // Meminta address semua klien dari server
        obj.clear();
        obj.put("method", "client_address");
        sendToServer(obj);
        
        // Menerima address semua klien dari server
        players = (JSONArray)((JSONObject)listenToServer()).get("clients");
    }
    
    public void leaveGame() {
        JSONObject recv;
        do { // send method = leave until client may leave
            JSONObject obj = new JSONObject();
            obj.put("method","leave");
            sendToServer(obj);
            recv = (JSONObject)listenToServer();
            if(!recv.get("status").equals("ok")) {
                System.out.println(recv.toJSONString());
            }
        } while(!recv.get("status").equals("ok"));
    }
    
}
