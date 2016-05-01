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
public class Client implements Runnable {
    // FOR TCP CONNECTION
    private Socket socket;
    public String SERVER_HOSTNAME;
    public int COMM_PORT;  // socket port for client comms
    
    private DatagramSocket udpSocket;
    
    private static JSONArray players = new JSONArray(); // lists of players
    private Player player; // client as player
    private ArrayList<String> friends = new ArrayList<>(); // player's friends (werewolf only)
    
    private boolean isDay; // return true when day
    private int day = 0; // number of days
    
    private List<Integer> votes = new ArrayList<>();
    
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
        
        //siang
        client.changePhase();
        client.requestListOfClients();
        
        // GAME PLAY HERE
        while(true) {
            System.out.println("YOUR ROLE IS "+client.player.getRole());
            if(client.isDay) {
                if(client.player.getID() == (Integer)((JSONObject)Client.players.get(Client.players.size()-2)).get("player_id")
                    || client.player.getID() == (Integer)((JSONObject)Client.players.get(Client.players.size()-1)).get("player_id")) {
                    // PROPOSER pid dua terbesar (player ke n dan n­1) 
                    client.player.setProposer(true);

                    System.out.println("\nYou can propose");

                    // PAXOS PREPARE PROPOSAL
                    JSONObject sent = new JSONObject();
                    sent.put("method", "prepare_proposal");
                    client.player.setProposalID(client.player.getProposalID()+1);
                    sent.put("proposal_id", "("+client.player.getProposalID()+","+client.player.getID()+")"); // (local clock, local identifier)
                    String sendData = sent.toJSONString();

                    Thread t1 = new Thread(client);
                    t1.start();

                    for(int i=0;i<Client.players.size();i++) {
                        String ipAddress = (String)((JSONObject)Client.players.get(i)).get("address");
                        int port = (Integer)((JSONObject)Client.players.get(i)).get("port");
                        if ( port != client.player.getUDPPort()){
                            client.sendToUDP(ipAddress, port, sendData);
                        }
                    }

                    try {
                        t1.join();
                    } catch (InterruptedException ex) {
                        Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
                    }

                    Thread t2 = new Thread(client);
                    t2.start();

                    sent.clear();
                    sent.put("method", "accept_proposal");
                    sent.put("proposal_id", "("+client.player.getProposalID()+","+client.player.getID()+")"); // (local clock, local identifier)
                    sendData = sent.toJSONString();
                    for(int i=0;i<Client.players.size();i++) {
                        // PAXOS ACCEPT PROPOSAL
                        String ipAddress = (String)((JSONObject)Client.players.get(i)).get("address");
                        int port = (Integer)((JSONObject)Client.players.get(i)).get("port");
                        if ( port != client.player.getUDPPort()){
                            client.sendToUDP(ipAddress, port, sendData);
                        }
                    }

                    try {
                        t2.join();
                    } catch (InterruptedException ex) {
                        Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
                    }

                }
                else {
                    try {
                        // ACCEPTOR
                        System.out.println("\nYou cannot propose");
                        Thread t1 = new Thread(client);
                        t1.start();
                        t1.join();

                        Thread t2 = new Thread(client);
                        t2.start();
                        t2.join();
                    } catch (InterruptedException ex) {
                        Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
            JSONObject method;
            do {
                client.vote();
                method = client.changePhase();
            } while(method.get("method").equals("vote_now"));
            
            // Menerima address semua klien dari server
            client.requestListOfClients();
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
                System.out.println("JSON received from server: "+((JSONObject) object).toJSONString());
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
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            udpSocket.receive(receivePacket);
            String sentence = new String(receivePacket.getData(), 0, receivePacket.getLength());
            System.out.println("Received from UDP: "+sentence);
            return sentence;
        } catch (IOException ex) {
            Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    // Send object to server
    public void sendToServer(Object object) {
        System.out.println("Send to server: "+object.toString());
        try {
            OutputStream oStream = socket.getOutputStream();
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
            UnreliableSender unreliableSender = new UnreliableSender(udpSocket);
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName(ipAddress), port);
            unreliableSender.send(sendPacket, 1.00);
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
                    
                    try {
                        udpSocket = new DatagramSocket(udp_port);
                    } catch (SocketException ex) {
                        Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    
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
            if(obj.get("status") != null) {
                if(obj.get("status").equals("ok")) {
                }
            }
        } catch (UnknownHostException ex) {
            Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }
    
    public void startGame() {
        JSONObject recv = (JSONObject)listenToServer();
        if(recv.get("method").equals("start")) {
            isDay = recv.get("time").equals("day");
            player.setRole(recv.get("role").toString());
            if(recv.get("friends")!=null)
                friends = (ArrayList)recv.get("friends");

            JSONObject obj = new JSONObject();
            obj.put("status","ok");
            sendToServer(obj);
        }
        
        day++;
        isDay = true;
    }
    
    public JSONObject changePhase() {
        JSONObject recv = (JSONObject)listenToServer();
        if(recv.get("method").equals("change_phase")) {
            day = (int) recv.get("days");
            isDay = recv.get("time").equals("day");
            
            JSONObject obj = new JSONObject();
            obj.put("status","ok");
            sendToServer(obj);
        }
        return recv;
    }
    
    public void requestListOfClients() {
        JSONObject recv;
        JSONObject obj = new JSONObject();
        obj.put("method","client_address");
        sendToServer(obj);

        recv = (JSONObject)listenToServer();
        players = (JSONArray)recv.get("clients");

        if(!recv.get("status").equals("ok")) {
            System.out.println(recv.toJSONString());
        }
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
        System.out.println ("Running the listening thread");
        
        int num_proposal = 0;
        String str_prop_1 = "{}"; 
        int size;
        
        if(this.player.isProposer()) size = players.size();
        else size = 2;
        
        for(int i = 0; i < size; i++) {
            JSONObject proposal_1 = null;
            String recv = listenToUDP();

            JSONParser parser = new JSONParser();
            JSONObject jsonRecv = new JSONObject();
            try {
                jsonRecv = (JSONObject) parser.parse(recv);
            } catch (ParseException ex) {
                Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
            }

            JSONObject data = new JSONObject();
            if(jsonRecv.get("method") != null) {
                if(jsonRecv.get("method").equals("prepare_proposal")){
                    data.put("from", this.player.getUDPPort());
                    data.put("status", "ok");
                    data.put("description", "accepted");
                    data.put("previous_accepted", this.player.getKPUID()); // gue ga ngerti previous kpu_id maksudnya apa
                    String sendData = data.toJSONString();
                    String prop = (String)jsonRecv.get("proposal_id");
                    int proposal_id = Integer.parseInt(prop.substring(prop.indexOf('(')+1, prop.indexOf(',')));
                    int player_id = Integer.parseInt(prop.substring(prop.indexOf(',')+1, prop.indexOf(')')));
                    this.sendToUDP(this.getPlayerAddress(player_id), this.getPlayerPort(player_id), sendData);
                } else if (jsonRecv.get("method").equals("accept_proposal")) {
                    if(num_proposal < 2) {
                        num_proposal++;

                        if(num_proposal == 2 && !this.player.isProposer()) {
                            try {
                                proposal_1 = (JSONObject)parser.parse(str_prop_1);
                            } catch (ParseException ex) {
                                Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
                            }
                            String prop_1 = (String)proposal_1.get("proposal_id");
                            String prop_2 = (String)jsonRecv.get("proposal_id");

                            int proposal_id_1 = Integer.parseInt(prop_1.substring(prop_1.indexOf('(')+1, prop_1.indexOf(',')));
                            int proposal_id_2 = Integer.parseInt(prop_2.substring(prop_2.indexOf('(')+1, prop_2.indexOf(',')));
                            int player_id_1 = Integer.parseInt(prop_1.substring(prop_1.indexOf(',')+1, prop_1.indexOf(')')));
                            int player_id_2 = Integer.parseInt(prop_2.substring(prop_2.indexOf(',')+1, prop_2.indexOf(')')));
                            
                            int player_won, player_lose;
                            if(proposal_id_1 > proposal_id_2) {
                                player_won = player_id_1;
                                player_lose = player_id_2;
                            }
                            else if(proposal_id_1 < proposal_id_2) {
                                player_won = player_id_2;
                                player_lose = player_id_1;
                            }
                            else { //proposal_id_1 == proposal_id_2
                                if(player_id_1 < player_id_2) {
                                    player_won = player_id_2;
                                    player_lose = player_id_1;
                                }
                                else {
                                    player_won = player_id_1;
                                    player_lose = player_id_2;
                                }
                                
                            }
                            
                            data.put("status", "ok");
                            data.put("description", "accepted");
                            String sendData = data.toJSONString();
                            this.sendToUDP(this.getPlayerAddress(player_won), this.getPlayerPort(player_won), sendData);
                            data.clear();
                            data.put("status", "fail");
                            data.put("description", "rejected");
                            sendData = data.toJSONString();
                            this.sendToUDP(this.getPlayerAddress(player_lose), this.getPlayerPort(player_lose), sendData);

                            data.clear();
                            data.put("method","accepted_proposal");
                            data.put("kpu_id", player_won);
                            data.put("description","kpu is selected");
                            this.sendToServer(data);
                            
                            proposal_1 = null;
                            
                            JSONObject status = (JSONObject)this.listenToServer();
                            if(status.get("status").equals("ok")) {
                                JSONObject kpu = (JSONObject)this.listenToServer();
                                if(kpu.get("kpu_id") != null) {
                                    this.player.setKPU(Integer.parseInt(kpu.get("kpu_id").toString()));
                                }
                                data.clear();
                                data.put("status","ok");
                                this.sendToServer(data);
                            }
                        }
                        else if(num_proposal == 1 && !this.player.isProposer()) {
                            str_prop_1 = jsonRecv.toJSONString();
                        }
                        else if(num_proposal == 1 && this.player.isProposer()) {
                            String proposal = (String)jsonRecv.get("proposal_id");
                            int proposal_id = Integer.parseInt(proposal.substring(proposal.indexOf(',')+1, proposal.indexOf(')')));
                            data.put("status", "fail");
                            data.put("description", "rejected");
                            String sendData = data.toJSONString();
                            this.sendToUDP(this.getPlayerAddress(proposal_id), this.getPlayerPort(proposal_id), sendData);
                            
                            data.clear();
                            data.put("method","accepted_proposal");
                            data.put("kpu_id", this.player.getID());
                            data.put("description","kpu is selected");
                            this.sendToServer(data);
                            
                            JSONObject status = (JSONObject)this.listenToServer();
                            if(status.get("status").equals("ok")) {
                                JSONObject kpu = (JSONObject)this.listenToServer();
                                if(kpu.get("kpu_id") != null) {
                                    this.player.setKPU(Integer.parseInt(kpu.get("kpu_id").toString()));
                                }
                                data.clear();
                                data.put("status","ok");
                                this.sendToServer(data);
                            }
                        }
                    }
                }
                else if(jsonRecv.get("method").equals("vote_civilian")) {
                    this.votes.set(Integer.parseInt(jsonRecv.get("player_id").toString()), votes.get(Integer.parseInt(jsonRecv.get("player_id").toString()))+1);
                    if(this.getAlivePlayers()-2 == i) break;
                }
                else if(jsonRecv.get("method").equals("vote_werewolf")) {
                    this.votes.set(Integer.parseInt(jsonRecv.get("player_id").toString()), votes.get(Integer.parseInt(jsonRecv.get("player_id").toString()))+1);
                    if(this.getAliveWerewolves()-2 == i) break;
                }
            }
        }
    }
    
    public String getPlayerAddress(int id) {
        for(int i = 0; i < players.size(); i++) {
            JSONObject temp = (JSONObject)Client.players.get(i);
            if(Integer.parseInt(temp.get("player_id").toString()) == id) {
                return temp.get("address").toString();
            }
        }
        return null;
    }
    
    public int getPlayerPort(int id) {
        for(int i = 0; i < Client.players.size(); i++) {
            JSONObject temp = (JSONObject)Client.players.get(i);
            if(Integer.parseInt(temp.get("player_id").toString()) == id) {
                return Integer.parseInt(temp.get("port").toString());
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
    
    public void werewolfVoteInfo(JSONArray final_array) {
        JSONObject recv = (JSONObject)listenToServer();
        do { // send method
            JSONObject obj = new JSONObject();
            int player_id = getVoteResult();
            if (player_id != -1) {
                obj.put("method", "vote_result_werewolf");
                obj.put("vote_status",1);
                obj.put("player_killed",player_id);
            } else {
                obj.put("method", "vote_result");
                obj.put("vote_status",-1);
            }
            obj.put("vote_result",final_array); ////ini belom ditangani yg final_arraynya
            
            sendToServer(obj);
            if(!recv.get("status").equals("ok")) {
                System.out.println(recv.toJSONString());
            }
        } while(!recv.get("status").equals("ok"));
    }   
    
    public void civilianVoteInfo(JSONArray final_array) {
        JSONObject recv;
        do { // send method
            JSONObject obj = new JSONObject();
            int player_id = getVoteResult();
            if (player_id != -1) {
                obj.put("method", "vote_result_civilian");
                obj.put("vote_status",1);
                obj.put("player_killed",player_id);
            } else {
                obj.put("method", "vote_result");
                obj.put("vote_status",-1);
            }
            obj.put("vote_result",final_array); //ini belom ditangani yg final_arraynya
            
            System.out.println(obj.toJSONString());
            
            sendToServer(obj);
            
            recv = (JSONObject)listenToServer();
            if(!recv.get("status").equals("ok")) {
                System.out.println(recv.toJSONString());
            }
        } while(!recv.get("status").equals("ok"));
    }
    
    public int getIDFromUsername(String username) {
        for(int i=0; i<players.size(); i++) {
            try {
                JSONParser parser = new JSONParser();
                
                JSONObject data = (JSONObject)parser.parse(players.get(i).toString());
                if(data.get("username").equals(username)) {
                    return Integer.parseInt(data.get("player_id").toString());
                }
            
            } catch (ParseException ex) {
                Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return -1;
    }
    
    public String getUsernameFromID(int id) {
        for(int i=0; i<players.size(); i++) {
            try {
                JSONParser parser = new JSONParser();
                
                JSONObject data = (JSONObject)parser.parse(players.get(i).toString());
                if(Integer.parseInt(data.get("player_id").toString()) == id) {
                    return data.get("username").toString();
                }
            
            } catch (ParseException ex) {
                Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return "not found";
    }
    
    public void vote() {
        votes.clear();
        for (int i=0; i<6; i++) {
            votes.add(0);
        }
        
        JSONObject data = (JSONObject)this.listenToServer();
        Scanner keyboard = new Scanner(System.in);
        
        if(data.get("method") != null) {
            if(data.get("method").equals("vote_now")) {
                
                System.out.println("Time to vote as "+this.player.getRole());
                isDay = data.get("phase").equals("day");

                // SEND STATUS OK
                data.clear();
                data.put("status", "ok");
                this.sendToServer(data);
                data.clear();

                System.out.println("KPU ID "+this.player.getKPUID());

<<<<<<< HEAD
                        try {
                            t1.join();
                        } catch (InterruptedException ex) {
                            Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
                        }
                        
                        System.out.println(votes.toString());
                        System.out.println("ONE KILLED "+ this.getVoteResult() + "|" + this.getUsernameFromID(this.getVoteResult())); //ini masi bisa -1 loh haha
                        
                        JSONArray json_array = new JSONArray();
                        JSONArray final_array = new JSONArray();
                       
                        for (int j=0; j<votes.size(); j++) {
                            json_array.add(0, j);
                            json_array.add(1, votes.get(j));
                            System.out.println(votes.toArray());
                            final_array.add(json_array);
                            json_array.clear();
=======
                if(this.player.getKPUID() == this.player.getID()) {
                    // KPU
                    Thread t1 = new Thread(this);
                    t1.start();
                    String voted_player;
                    if(isDay) {
                        if(this.player.getRole().equals("civilian")) {
                            System.out.print("Siapa werewolf nya? ");
>>>>>>> add night shift
                        }
                        else {
                            System.out.print("Siapa yang ingin kamu bunuh?");
                        }
                        voted_player = keyboard.nextLine();
                        this.votes.set(this.getIDFromUsername(voted_player), votes.get(this.getIDFromUsername(voted_player))+1);
                    }
                    else {
                        if(this.player.getRole().equals("civilian")) {
                            System.out.println("You are sleeping...");
                        }
                        else {
                            System.out.print("Civilian mana yang ingin kamu bunuh? ");
                            voted_player = keyboard.nextLine();
                            this.votes.set(this.getIDFromUsername(voted_player), votes.get(this.getIDFromUsername(voted_player))+1);
                        }
                    }
                    
                    try {
                        t1.join();
                    } catch (InterruptedException ex) {
                        Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
                    }

                    System.out.println(votes.toString());
                    
                    JSONArray json_array = new JSONArray();
                    JSONArray final_array = new JSONArray();

                    for (int j=0; j<votes.size(); j++) {
                        json_array.add(0, j);
                        json_array.add(1, votes.get(j));
                        final_array.add(j, json_array);
                        json_array.remove(0);
                        json_array.remove(0);
                    }
                    System.out.println(final_array.toJSONString());

                    if (isDay) {
                        System.out.println("DAY");
                        civilianVoteInfo(final_array);
                    } else {
                        System.out.println("NIGHT");
                        werewolfVoteInfo(final_array);
                    }

                }
                else {
                    System.out.print("Siapa werewolf nya? ");
                    String voted_player = keyboard.nextLine();
                    data.put("method","vote_civilian");
                    data.put("player_id", this.getIDFromUsername(voted_player));
                    this.sendToUDP(this.getPlayerAddress(this.player.getKPUID()), this.getPlayerPort(this.player.getKPUID()), data.toJSONString());
                }
            }
            else {
                System.out.println("Server does not send to vote");
            }
        }
    }
    
    
    public int getAlivePlayers() {
        int counter = 0;
        
        for  (int i=0; i<players.size(); i++) {
            if (Integer.parseInt(((JSONObject)players.get(i)).get("is_alive").toString())==1) {
                counter++;
            }
        }
        
        return counter;
    }
    
    public int getAliveWerewolves() {
        int counter = 0;
        
        for  (int i=0; i<players.size(); i++) {
            if (Integer.parseInt(((JSONObject)players.get(i)).get("is_alive").toString())==1) {
                if (((JSONObject)players.get(i)).get("role").toString().equals("werewolf")) {
                    counter++;
                }
            }
        }
        
        return counter;
    }
}
