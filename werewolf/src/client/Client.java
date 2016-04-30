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
import java.util.ArrayList;
import java.util.List;
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
    
    private static JSONArray players = new JSONArray();
    private Player player;
    private ArrayList<String> friends = new ArrayList<String>();
    
    private boolean isDay;
    private int day = 0;
    
    private List<Integer> votes = new ArrayList<Integer>();
    
    public Client() {
        SERVER_HOSTNAME = "127.0.0.1";
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

        //mulai game malam, werewolf saling kenal
        client.startGame();
        System.out.println("START GAME");
        client.start();
        
        //siang
        client.changePhase();
        
        // GAME PLAY HERE
        while(true) {                      
            if(client.player.getID() == (Integer)((JSONObject)client.players.get(client.players.size()-2)).get("player_id")
                || client.player.getID() == (Integer)((JSONObject)client.players.get(client.players.size()-1)).get("player_id")) {
                // PROPOSER pid dua terbesar (player ke n dan n­1) 
                client.player.setProposer(true);

                System.out.println("You can propose");

                // PAXOS PREPARE PROPOSAL
                JSONObject sent = new JSONObject();
                sent.put("method", "prepare_proposal");
                client.player.setProposalID(client.player.getProposalID()+1);
                sent.put("proposal_id", "("+client.player.getProposalID()+","+client.player.getID()+")"); // (local clock, local identifier)
                String sendData = sent.toJSONString();

                for(int i=0;i<client.players.size();i++) {
                    String ipAddress = (String)((JSONObject)client.players.get(i)).get("address");
                    int port = (Integer)((JSONObject)client.players.get(i)).get("port");
                    if ( port != client.player.getUDPPort()){
                        client.sendToUDP(ipAddress, port, sendData);
                    }
                    
                    // NUNGGU JAWABAN "OK" DARI CLIENT LAINNYA
                    try {
                        String recv = client.listenToUDP();   
                        JSONParser parser = new JSONParser();
                        JSONObject jsonRecv = new JSONObject();
                        jsonRecv = (JSONObject) parser.parse(recv);
                        if (jsonRecv.get("status").equals("ok")){
                            // PAXOS ACCEPT PROPOSAL
                            sent.clear();
                            sent.put("method", "accept_proposal");
                            sent.put("proposal_id", "("+client.player.getProposalID()+","+client.player.getID()+")"); // (local clock, local identifier)
                            sendData = sent.toJSONString();

                            if ( port != client.player.getUDPPort()){
                                client.sendToUDP(ipAddress, port, sendData);
                            }
                        }
                    } catch (ParseException ex) {
                        Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
                    }
                } 
            }
            else { // ACCEPTOR
                
            }
        }
        
        //client.leaveGame();
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
        System.out.println("Listen on port "+player.getUDPPort());
        try {
            byte[] receiveData = new byte[1024];
            DatagramSocket serverSocket = new DatagramSocket(player.getUDPPort());
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            serverSocket.receive(receivePacket);
            String sentence = new String(receivePacket.getData(), 0, receivePacket.getLength());
            System.out.println("Received from UDP: "+sentence);
            serverSocket.close();
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
    
    public void sendToUDP(String ipAddress, int port, String send) {
        System.out.println("Send to "+ipAddress+"|"+port+": "+send);
        byte[] sendData = send.getBytes();
        try {
            DatagramSocket datagramSocket = new DatagramSocket();
            UnreliableSender unreliableSender = new UnreliableSender(datagramSocket);
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName(ipAddress), port);
            unreliableSender.send(sendPacket, 1.00);
            datagramSocket.close();
        } catch (SocketException ex) {
            Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
        }
        catch (IOException ex) {
            Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public void joinGame() {
        try {
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
                    obj.put("udp_address",Inet4Address.getLocalHost().getHostAddress());
                    obj.put("udp_port",udp_port);
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
            this.player = new Player(player_id, username, Inet4Address.getLocalHost().getHostAddress(), udp_port);
            obj.clear();
            
            // Mengirim ready ke user
            obj.put("method","ready");
            player.setReady(true);
            sendToServer(obj);
            
            // Mendapatkan status dari server = ok
            obj = (JSONObject)listenToServer();
        } catch (UnknownHostException ex) {
            Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }
    
    public void startGame() {
        JSONObject recv = (JSONObject)listenToServer();
        if(recv.get("method").equals("start")) {
            isDay = recv.get("time").equals("day");
            player.setRole((String) recv.get("role"));
            if(recv.get("friends")!=null)
                friends = (ArrayList)recv.get("friends");

            JSONObject obj = new JSONObject();
            obj.put("status","ok");
            sendToServer(obj);
        }
        
        day++;
        isDay = true;
    }
    
    public void changePhase() {
        JSONObject recv = (JSONObject)listenToServer();
        if(recv.get("method").equals("change_phase")) {
            day = (int) recv.get("days");
            if (recv.get("time").equals("day")) {
                isDay = true;
            } else {
                isDay = false;
            }
            
            JSONObject obj = new JSONObject();
            obj.put("status","ok");
            sendToServer(obj);
            obj.clear();
            obj.put("method","client_address");
            sendToServer(obj);

            // Menerima address semua klien dari server
            players = (JSONArray)((JSONObject)listenToServer()).get("clients");
        }
        
    }
    
    public void requestListOfClients() {
        JSONObject recv;
        do { // send method
            JSONObject obj = new JSONObject();
            obj.put("method","client_address");
            sendToServer(obj);
            
            recv = (JSONObject)listenToServer();
            players = (JSONArray)recv.get("clients");

            if(!recv.get("status").equals("ok")) {
                System.out.println(recv.toJSONString());
            }
        } while(!recv.get("status").equals("ok"));
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
        System.out.println ("ON START");
        
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
                String sendData = temp.toJSONString();
                String prop = (String)jsonRecv.get("proposal_id");
                int proposal_id = Integer.parseInt(prop.substring(prop.indexOf('(')+1, prop.indexOf(',')));
                int player_id = Integer.parseInt(prop.substring(prop.indexOf(',')+1, prop.indexOf(')')));
                this.sendToUDP(this.getPlayerAddress(player_id), this.getPlayerPort(player_id), sendData);
            } else if (jsonRecv.get("method").equals("accept_proposal")) {
                if(num_proposal < 2) {
                    num_proposal++;

                    if(num_proposal == 2 && !this.player.isProposer()) {
                        String prop_1 = (String)proposal_1.get("proposal_id");
                        String prop_2 = (String)jsonRecv.get("proposal_id");

                        int proposal_id_1 = Integer.parseInt(prop_1.substring(prop_1.indexOf('(')+1, prop_1.indexOf(',')));
                        int proposal_id_2 = Integer.parseInt(prop_2.substring(prop_2.indexOf('(')+1, prop_2.indexOf(',')));
                        int player_id_1 = Integer.parseInt(prop_1.substring(prop_1.indexOf(',')+1, prop_1.indexOf(')')));
                        int player_id_2 = Integer.parseInt(prop_2.substring(prop_2.indexOf(',')+1, prop_2.indexOf(')')));
                        
                        if(proposal_id_1 > proposal_id_2) {
                            temp.put("status", "ok");
                            temp.put("description", "accepted");
                            String sendData = temp.toJSONString();
                            this.sendToUDP(this.getPlayerAddress(player_id_1), this.getPlayerPort(player_id_1), sendData);
                            temp.clear();
                            temp.put("status", "fail");
                            temp.put("description", "rejected");
                            sendData = temp.toJSONString();
                            this.sendToUDP(this.getPlayerAddress(player_id_2), this.getPlayerPort(player_id_2), sendData);
                        }
                        else if(proposal_id_1 < proposal_id_2) {
                            temp.put("status", "ok");
                            temp.put("description", "accepted");
                            String sendData = temp.toJSONString();
                            this.sendToUDP(this.getPlayerAddress(player_id_2), this.getPlayerPort(player_id_2), sendData);
                            temp.clear();
                            temp.put("status", "fail");
                            temp.put("description", "rejected");
                            sendData = temp.toJSONString();
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
                            String sendData = temp.toJSONString();
                            this.sendToUDP(this.getPlayerAddress(player_id_1), this.getPlayerPort(player_id_1), sendData);
                            temp.clear();
                            temp.put("status", "fail");
                            temp.put("description", "rejected");
                            sendData = temp.toJSONString();
                            this.sendToUDP(this.getPlayerAddress(player_id_2), this.getPlayerPort(player_id_2), sendData);
                        }
                        proposal_1 = null;

                    }
                    else if(num_proposal == 1 && !this.player.isProposer()) {
                        proposal_1 = jsonRecv;
                    }
                    else if(num_proposal == 1 && this.player.isProposer()) {
                        String proposal = (String)jsonRecv.get("proposal_id");
                        int proposal_id = Integer.parseInt(proposal.substring(proposal.indexOf(',')+1, proposal.indexOf(')')));
                        temp.put("status", "ok");
                        temp.put("description", "accepted");
                        String sendData = temp.toJSONString();
                        this.sendToUDP(this.getPlayerAddress(proposal_id), this.getPlayerPort(proposal_id), sendData);
                        temp.clear();
                        temp.put("status", "fail");
                        temp.put("description", "rejected");
                        sendData = temp.toJSONString();
                        this.sendToUDP(this.getPlayerAddress(proposal_id), this.getPlayerPort(proposal_id), sendData);
                    }
                }

            }
            
        } while (true);
        
    }
    
    public String getPlayerAddress(int id) {
        for(int i = 0; i < players.size(); i++) {
            JSONObject temp = (JSONObject)this.players.get(i);
            if(Integer.parseInt(temp.get("player_id").toString()) == id) {
                return (String)temp.get("address");
            }
        }
        return null;
    }
    
    public int getPlayerPort(int id) {
        for(int i = 0; i < players.size(); i++) {
            JSONObject temp = (JSONObject)this.players.get(i);
            if(Integer.parseInt(temp.get("player_id").toString()) == id) {
                return (Integer)temp.get("port");
            }
        }
        return -1;
    }
    
    public void acceptedProposal(int kpu_id) {
        JSONObject recv = (JSONObject)listenToServer();
        do { // send method
            JSONObject obj = new JSONObject();
            obj.put("method", "accepted_proposal");
            obj.put("kpu_id",kpu_id);
            obj.put("description", "Kpu is selected");
            
            sendToServer(obj);
            if(!recv.get("status").equals("ok")) {
                System.out.println(recv.toJSONString());
            }
        } while(!recv.get("status").equals("ok"));
    }
    
    public int getVoteResult() {
        
        int killed_player = -1;
        int voteCounter = 0;
        boolean valid = true;
       
        for (int i=0; i<votes.size(); i++) {
            if (votes.get(i) == voteCounter) {
                valid = false;
            } else if (votes.get(i) > voteCounter) {
                killed_player = i;
                voteCounter = votes.get(i);
                valid = true;
            }
        }
        
        if (!valid) {
            killed_player = -1;
        }
        
        return killed_player;            
    }
    
    public void werewolfKilled() {
        //ini uda pake udp-an
    }
    
    public void playerKilledInfo(int vote_status, int player_killed) {
        JSONObject recv = (JSONObject)listenToServer();
        if (this.player.getID() == this.player.getKPUID()) {
            do { // send method
                JSONObject obj = new JSONObject();
                obj.put("method","vote_result_werewolf");
                obj.put("vote_status",vote_status);
                if (vote_status == 1) {
                        obj.put("player_killed", player_killed); //Gatau ini buat apaa	
                }

                for (int i=0; i<player_killed; i++) {

                }

            //    obj.put("player_killed", ); Gatau ini buat apaa

                sendToServer(obj);
                if(!recv.get("status").equals("ok")) {
                    System.out.println(recv.toJSONString());
                }
            } while(!recv.get("status").equals("ok"));
        }
    }

    public void civilianKilled() {
        //pake udp
    }

    public void civilianKilledInfo(int vote_status, int player_killed) {
        JSONObject recv = (JSONObject)listenToServer();
        if (this.player.getID() == this.player.getKPUID()) {
            do { // send method
                JSONObject obj = new JSONObject();
                obj.put("method","vote_result_civilian");
                obj.put("vote_status",vote_status);
                if (vote_status == 1) {
                        obj.put("player_killed", player_killed); //Gatau ini buat apaa	
                }

                for (int i=0; i<player_killed; i++) {

                }

            //    obj.put("player_killed", ); Gatau ini buat apaa

                sendToServer(obj);
                if(!recv.get("status").equals("ok")) {
                    System.out.println(recv.toJSONString());
                }
            } while(!recv.get("status").equals("ok"));
        }
    }   
    

}
