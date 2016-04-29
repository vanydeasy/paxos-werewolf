/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package client;

import game.Player;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.simple.JSONArray;
import server.Server;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 *
 * @author vanyadeasy
 */
public class Client extends Thread {
    // FOR TCP CONNECTION
    private Socket socket;
    public String SERVER_HOSTNAME;
    public int COMM_PORT;  // socket port for client comms
    
    private JSONArray players = new JSONArray();
    private Player player;
    
    public Client() {
        SERVER_HOSTNAME = "192.168.0.104";
        COMM_PORT = 8181;
    }
    
    public Client(String host, int port) {
        SERVER_HOSTNAME = host;
        COMM_PORT = port;
    }
    
    public static void main(String[] args) {
        Scanner keyboard = new Scanner(System.in);
        System.out.print("Server HOSTNAME: ");
        String s_host = keyboard.nextLine();
        System.out.print("Server PORT: ");
        int s_port = Integer.parseInt(keyboard.nextLine());
        
        Client client = new Client(s_host, s_port);
        client.connectToServer();
        System.out.println(client.socket);
        
        // Get username from user
        client.joinGame();

        // GAME PLAY HERE
        client.start();
        // while belum menang
        
        if(client.players.size() > 2) {
            if(client.player.getID() == (Integer)((JSONObject)client.players.get(client.players.size()-2)).get("player_id")
                || client.player.getID() == (Integer)((JSONObject)client.players.get(client.players.size()-1)).get("player_id")) {
                // PROPOSER pid dua terbesar (player ke n dan n­1) 
                client.player.setProposer(true);

                System.out.println("You can propose");

                // PAXOS PREPARE PROPOSAL
                JSONObject sent = new JSONObject();
                sent.put("method", "prepare_proposal");
                sent.put("proposal_id", "("+client.player.getProposalID()+","+client.player.getID()+")"); // (local clock, local identifier)
                byte[] sendData = sent.toJSONString().getBytes();

                for(int i=0;i<client.players.size();i++) {
                    String ipAddress = (String)((JSONObject)client.players.get(i)).get("address");
                    int port = (Integer)((JSONObject)client.players.get(i)).get("port");
                    client.sendToUDP(ipAddress, port, sendData);
                }

                // PAXOS ACCEPT PROPOSAL
                sent.clear();
                sent.put("method", "accept_proposal");
                sent.put("proposal_id", "("+client.player.getProposalID()+","+client.player.getID()+")"); // (local clock, local identifier)
                sendData = sent.toJSONString().getBytes();

                for(int i=0;i<client.players.size();i++) {
                    String ipAddress = (String)((JSONObject)client.players.get(i)).get("address");
                    int port = (Integer)((JSONObject)client.players.get(i)).get("port");
                    client.sendToUDP(ipAddress, port, sendData);
                }
                
            } else {
                // ACCEPTOR
                client.player.setProposer(false);
                System.out.println("You cannot propose");

            }
        }
        
        client.leaveGame();
    }
    
    public void connectToServer() { // Connect client to server (binding)
        System.out.println("Connecting to server...");
        try {
            this.socket = new Socket(SERVER_HOSTNAME, COMM_PORT);
            System.out.println("Connected!");
        } catch (IOException ex) {
            Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
        }
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
    
    public String listenToUDP() {
        try {
            byte[] receiveData = new byte[1024];
            DatagramSocket serverSocket = new DatagramSocket(player.getUDPPort());
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            serverSocket.receive(receivePacket);
            String sentence = new String(receivePacket.getData(), 0, receivePacket.getLength());
            System.out.println("String received: "+sentence);
            return sentence;
        } catch (IOException ex) {
            Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
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
    
    public void sendToUDP(String ipAddress, int port, byte[] sendData) {
        try {
            DatagramSocket datagramSocket = new DatagramSocket();
            UnreliableSender unreliableSender = new UnreliableSender(datagramSocket);
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName(ipAddress.substring(1)), port);
            unreliableSender.send(sendPacket, 1.00);
        } catch (SocketException ex) {
            Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
        }
        catch (IOException ex) {
            Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public void joinGame() {
        JSONObject obj = new JSONObject();
        String username = null; int udp_port = 0;
        do {
            try {
                Scanner keyboard = new Scanner(System.in);
                System.out.print("Username: ");
                username = keyboard.nextLine();
                System.out.print("UDP Port: ");
                udp_port = keyboard.nextInt();
                
                // Mengirim data user ke server
                obj.put("username", username);
                obj.put("address",Inet4Address.getLocalHost().getHostAddress());
                obj.put("port",udp_port);
                obj.put("method","join");
                sendToServer(obj);
                
                obj = (JSONObject)listenToServer();
                if(obj.get("status")==null) break;
            } catch (UnknownHostException ex) {
                Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
            }
        } while(obj.get("status").equals("fail"));

        // Mendapatkan player id dari server
        int player_id = (Integer)obj.get("player_id");
        this.player = new Player(player_id, username, udp_port);
        obj.clear();
        
        // Mengirim ready ke user
        obj.put("method","ready");
        sendToServer(obj);
        
        // Mendapatkan status dari server = ok
        obj = (JSONObject)listenToServer();
        if(obj.get("status").equals("ok")) {
            // Mendapatkan role dari server saat player sudah cukup
            String role = (String)((JSONObject)listenToServer()).get("role");
            System.out.println("YOUR ROLE IS "+role);
            this.player.setRole(role);
        }
        else if(obj.get("description") != null) {
            System.out.println(obj.get("description"));
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
    
    @Override
    public void run() {
        // Used for listening to socket
        System.out.println ("New Communication Thread Started");
        
        JSONObject proposal_1 = null;
        int num_proposal = 0;
        do {
            String recv = listenToUDP();
            
            JSONParser parser = new JSONParser();
            JSONObject jsonRecv = new JSONObject();
            try {
                jsonRecv = (JSONObject) parser.parse(recv);
            } catch (ParseException ex) {
                Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
            }
            
            JSONObject temp = new JSONObject();
            if(jsonRecv.get("method").equals("prepare_proposal")){
                temp.put("status", "ok");
                temp.put("description", "accepted");
                temp.put("previous_accepted", this.player.getKPUID()); // gue ga ngerti previous kpu_id maksudnya apa
                byte[] sendData = temp.toJSONString().getBytes();
                this.sendToUDP(this.getPlayerAddress(Integer.parseInt((String)jsonRecv.get("player_id"))), this.getPlayerPort(Integer.parseInt((String)jsonRecv.get("player_id"))), sendData);
            } else if (jsonRecv.get("method").equals("accept_proposal")) {
                if(num_proposal < 2) {
                    num_proposal++;

                    if(num_proposal == 2 && !this.player.isProposer()) {
                        String prop_1 = (String)proposal_1.get("proposal_id");
                        String prop_2 = (String)jsonRecv.get("proposal_id");

                        int proposal_id_1 = Integer.parseInt(prop_1.substring(prop_1.indexOf('(')+1, prop_1.indexOf(',')-1));
                        int proposal_id_2 = Integer.parseInt(prop_2.substring(prop_2.indexOf('(')+1, prop_2.indexOf(',')-1));
                        int player_id_1 = Integer.parseInt(prop_1.substring(prop_1.indexOf(',')+2, prop_1.indexOf(')')-1));
                        int player_id_2 = Integer.parseInt(prop_2.substring(prop_2.indexOf(',')+2, prop_2.indexOf(')')-1));
                        
                        if(proposal_id_1 > proposal_id_2) {
                            temp.put("status", "ok");
                            temp.put("description", "accepted");
                            byte[] sendData = temp.toJSONString().getBytes();
                            this.sendToUDP(this.getPlayerAddress(player_id_1), this.getPlayerPort(player_id_1), sendData);
                            temp.clear();
                            temp.put("status", "fail");
                            temp.put("description", "rejected");
                            sendData = temp.toJSONString().getBytes();
                            this.sendToUDP(this.getPlayerAddress(player_id_2), this.getPlayerPort(player_id_2), sendData);
                        }
                        else if(proposal_id_1 < proposal_id_2) {
                            temp.put("status", "ok");
                            temp.put("description", "accepted");
                            byte[] sendData = temp.toJSONString().getBytes();
                            this.sendToUDP(this.getPlayerAddress(player_id_2), this.getPlayerPort(player_id_2), sendData);
                            temp.clear();
                            temp.put("status", "fail");
                            temp.put("description", "rejected");
                            sendData = temp.toJSONString().getBytes();
                            this.sendToUDP(this.getPlayerAddress(player_id_1), this.getPlayerPort(player_id_1), sendData);
                        }
                        else { //proposal_id_1 == proposal_id_2
                            if(player_id_1 < player_id_2) {
                                int temp_id = player_id_1;
                                player_id_1 = player_id_2;
                                player_id_2 = temp_id;
                            }
                            temp.put("status", "ok");
                            temp.put("description", "accepted");
                            byte[] sendData = temp.toJSONString().getBytes();
                            this.sendToUDP(this.getPlayerAddress(player_id_1), this.getPlayerPort(player_id_1), sendData);
                            temp.clear();
                            temp.put("status", "fail");
                            temp.put("description", "rejected");
                            sendData = temp.toJSONString().getBytes();
                            this.sendToUDP(this.getPlayerAddress(player_id_2), this.getPlayerPort(player_id_2), sendData);
                        }
                        proposal_1 = null;

                    }
                    else if(num_proposal == 1 && !this.player.isProposer()) {
                        proposal_1 = jsonRecv;
                    }
                    else if(num_proposal == 1 && this.player.isProposer()) {
                        String proposal = (String)jsonRecv.get("proposal_id");
                        int proposal_id = Integer.parseInt(proposal.substring(proposal.indexOf(',')+2, proposal.indexOf(')')-1));
                        temp.put("status", "ok");
                        temp.put("description", "accepted");
                        byte[] sendData = temp.toJSONString().getBytes();
                        this.sendToUDP(this.getPlayerAddress(proposal_id), this.getPlayerPort(proposal_id), sendData);
                        temp.clear();
                        temp.put("status", "fail");
                        temp.put("description", "rejected");
                        sendData = temp.toJSONString().getBytes();
                        this.sendToUDP(this.getPlayerAddress(proposal_id), this.getPlayerPort(proposal_id), sendData);
                    }
                }

            }
            
        } while (true);
        
    }
    
    public String getPlayerAddress(int id) {
        for(int i = 0; i < players.size(); i++) {
            JSONObject temp = (JSONObject)this.players.get(this.players.size()-1);
            if(Integer.parseInt((String)temp.get("player_id")) == id) {
                return (String)temp.get("address");
            }
        }
        return null;
    }
    
    public int getPlayerPort(int id) {
        for(int i = 0; i < players.size(); i++) {
            JSONObject temp = (JSONObject)this.players.get(this.players.size()-1);
            if(Integer.parseInt((String)temp.get("player_id")) == id) {
                return Integer.parseInt((String)temp.get("port"));
            }
        }
        return -1;
    }

}
