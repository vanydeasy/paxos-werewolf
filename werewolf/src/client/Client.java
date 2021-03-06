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
import java.net.SocketTimeoutException;
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
    
    private List<JSONObject> proposal = new ArrayList<>();
            
    private static int timeout = 10000;
    
    private static double lowerBound = 0.85;
    
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
                if(client.player.getID() == Integer.parseInt(((JSONObject)Client.players.get(Client.players.size()-2)).get("player_id").toString())
                    || client.player.getID() == Integer.parseInt(((JSONObject)Client.players.get(Client.players.size()-1)).get("player_id").toString())) {
                    // PROPOSER pid dua terbesar (player ke n dan n­1)
                    client.player.setProposer(true);

                    System.out.println("\nYou can propose");

                    do {
                        try {
                            // PAXOS PREPARE PROPOSAL
                            JSONObject sent = new JSONObject();
                            sent.put("method", "prepare_proposal");
                            client.player.setProposalID(client.player.getProposalID()+1);
                            sent.put("proposal_id", "("+client.player.getProposalID()+","+client.player.getID()+")"); // (local clock, local identifier)
                            String sendData = sent.toJSONString();

                            Thread t1 = new Thread(client);

                            try {
                                client.udpSocket.setSoTimeout(timeout);
                            } catch (SocketException ex) {
                                
                            }

                            t1.start();

                            for(int i=0;i<Client.players.size();i++) {
                                String ipAddress = ((JSONObject)Client.players.get(i)).get("address").toString();
                                int port = Integer.parseInt(((JSONObject)Client.players.get(i)).get("port").toString());
                                if ( port != client.player.getUDPPort()){
                                    client.sendToUDP(ipAddress, port, sendData, client.lowerBound);
                                }
                            }

                            t1.join();

                            Thread t2 = new Thread(client);

                            try {
                                client.udpSocket.setSoTimeout(timeout);
                            } catch (SocketException ex) {
                                
                            }

                            t2.start();

                            sent.clear();
                            sent.put("method", "accept_proposal");
                            sent.put("proposal_id", "("+client.player.getProposalID()+","+client.player.getID()+")"); // (local clock, local identifier)
                            sendData = sent.toJSONString();
                            for(int i=0;i<Client.players.size();i++) {
                                // PAXOS ACCEPT PROPOSAL
                                String ipAddress = ((JSONObject)Client.players.get(i)).get("address").toString();
                                int port = Integer.parseInt(((JSONObject)Client.players.get(i)).get("port").toString());
                                if ( port != client.player.getUDPPort()){
                                    client.sendToUDP(ipAddress, port, sendData, client.lowerBound);
                                }
                            }


                            t2.join();

                            client.acceptedProposal();

                        } catch (InterruptedException ex) {
                            Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
                        }

                        if(client.player.getKPUID() == -1) System.out.println("Ulang proposal");
                    } while(client.player.getKPUID() == -1);
                }
                else {
                    // ACCEPTOR
                    System.out.println("\nYou cannot propose");
                    do {
                        try {
                            Thread t1 = new Thread(client);

                            try {
                                client.udpSocket.setSoTimeout(timeout);
                            } catch (SocketException ex) {
                                
                            }

                            t1.start();
                            t1.join();

                            Thread t2 = new Thread(client);

                            try {
                                client.udpSocket.setSoTimeout(timeout);
                            } catch (SocketException ex) {
                                
                            }

                            t2.start();
                            t2.join();

                            client.acceptedProposal();

                        } catch (InterruptedException ex) {
                            Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
                        }
                        
                        if(client.player.getKPUID() == -1) System.out.println("Ulang proposal");
                    } while(client.player.getKPUID() == -1);
                }
            }
            
            JSONObject method;
            JSONObject data = (JSONObject)client.listenToServer();
            if(data.get("method") != null) {
                if(data.get("method").equals("vote_now")) {
                    client.isDay = data.get("phase").equals("day");
                    do {
                        client.vote();
                        method = client.changePhase();
                    } while(method.get("method").equals("vote_now"));

                    if(method.get("method").equals("game_over")) {
                        System.out.println("GAME OVER!");
                        break;
                    }
                }
            }
            
            // Menerima address semua klien dari server
            client.requestListOfClients();
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
                System.out.println("Server :: Received "+((JSONObject) object).toJSONString());
            }
            else {
                System.out.println("Unknown object received: "+object.toString());
            }
        } catch (IOException | ClassNotFoundException ex) {
            Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
        }
        return object;
    }
    
    public String listenToUDP() throws SocketTimeoutException {
        try {
            byte[] receiveData = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            udpSocket.receive(receivePacket);
            String sentence = new String(receivePacket.getData(), 0, receivePacket.getLength());
            System.out.println("UDP :: Received " + receivePacket.getAddress() + "|" + receivePacket.getPort() +" : "+sentence);
            return sentence;
        } catch (IOException ex) {
            
        }
        return null;
    }
    
    // Send object to server
    public void sendToServer(Object object) {
        try {
            OutputStream oStream = socket.getOutputStream();
            ObjectOutputStream ooStream = new ObjectOutputStream(oStream);
            ooStream.writeObject(object);  // send seriliazed
            System.out.println("Server :: Sent "+object.toString());
        } catch (IOException ex) {
            Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public void sendToUDP(String ipAddress, int port, String send, double bound) {
        byte[] sendData = send.getBytes();
        try {
            UnreliableSender unreliableSender = new UnreliableSender(udpSocket);
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName(ipAddress), port);
            unreliableSender.send(sendPacket, bound);
            System.out.println("UDP :: Sent "+ipAddress+"|"+port+" : "+send);
        } catch (SocketException ex) {
            
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
                    do {
                        System.out.print("Username: ");
                        username = keyboard.nextLine();
                        if(username.equals("")) System.out.println("You cannot have blank username");
                    } while(username.equals(""));
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
            
            try {
                udpSocket = new DatagramSocket(udp_port);
            } catch (SocketException ex) {
                
            }

            // Mendapatkan player id dari server
            int player_id = (Integer)obj.get("player_id");
            this.player = new Player(player_id, username, Inet4Address.getLocalHost().getHostAddress(), udp_port);
            obj.clear();
            
            System.out.print("Ready to play?(Y/N) ");
            Scanner keyboard = new Scanner(System.in);
            String play_game = keyboard.nextLine();
            
            if(play_game.equalsIgnoreCase("Y")) {
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
            }
            else {
                System.out.println("Leaving the game...");
                this.leaveGame();
                System.exit(0);
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
            System.out.println("YOUR ROLE IS " + player.getRole());
            if(recv.get("friends")!=null) {
                friends = (ArrayList)recv.get("friends");
                System.out.println("YOUR FRIEND IS ");
                for(String friend : friends)
                    System.out.print(friend+" ");
                System.out.println("");
            }
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
        else if(recv.get("method").equals("game_over")) {
            System.out.println("THE GAME IS OVER");
            System.out.println("The winner is "+recv.get("winner"));
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
        
        for(int i = 0; i < players.size(); i++) {
            JSONObject player = (JSONObject)players.get(i);
            if(Integer.parseInt(player.get("player_id").toString()) == this.player.getID()) {
                this.player.setState(Integer.parseInt(player.get("is_alive").toString()) == 1);
            }
        }

        if(!recv.get("status").equals("ok")) {
            System.out.println(recv.toJSONString());
        }
    }
    
    public void leaveGame() {
        JSONObject recv;
        JSONObject obj = new JSONObject();
        obj.put("method","leave");
        sendToServer(obj);
        recv = (JSONObject)listenToServer();
        if(recv.get("status") != null) {
            if(!recv.get("status").equals("ok")) {
                System.out.println(recv.toJSONString());
            }
        }
    }
    
    @Override
    public void run() {
        // Used for listening to socket
        int num_proposal = 0;
        int size;
        
        if(this.player.isProposer()) size = this.getAlivePlayers();
        else size = 2;
        
        int vote_civilian = 0, vote_werewolf = 0, prepare = 0, accept = 0;
        while(true) {
            String recv = null;
            try {
                recv = listenToUDP();
            } catch (SocketTimeoutException e) {
                System.out.println("Time out reached!");
                break;
            }
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
                    data.put("status", "ok");
                    data.put("description", "accepted");
                    data.put("previous_accepted", this.player.getKPUID()); // gue ga ngerti previous kpu_id maksudnya apa
                    String sendData = data.toJSONString();
                    String prop = (String)jsonRecv.get("proposal_id");
                    int proposal_id = Integer.parseInt(prop.substring(prop.indexOf('(')+1, prop.indexOf(',')));
                    int player_id = Integer.parseInt(prop.substring(prop.indexOf(',')+1, prop.indexOf(')')));
                    this.sendToUDP(this.getPlayerAddress(player_id), this.getPlayerPort(player_id), sendData, this.lowerBound);
                    if(this.player.isProposer() && prepare == 4) break;
                    else if(!this.player.isProposer() && prepare == 1) break;
                    prepare++;
                } else if (jsonRecv.get("method").equals("accept_proposal")) {
                    if(++num_proposal <= 2) {
                        proposal.add(jsonRecv);
                        if(num_proposal == 1 && this.player.isProposer()) break;
                        else if(num_proposal == 2 && !this.player.isProposer()) break;
                    }
                    if(this.player.isProposer() && accept == 4) break;
                    else if(!this.player.isProposer() && accept == 1) break;
                    accept++;
                }
                else if(jsonRecv.get("method").equals("vote_civilian")) {
                    this.votes.set(Integer.parseInt(jsonRecv.get("player_id").toString()), votes.get(Integer.parseInt(jsonRecv.get("player_id").toString()))+1);
                    if(this.getAlivePlayers()-2 == vote_civilian) break;
                    vote_civilian++;
                }
                else if(jsonRecv.get("method").equals("vote_werewolf")) {
                    this.votes.set(Integer.parseInt(jsonRecv.get("player_id").toString()), votes.get(Integer.parseInt(jsonRecv.get("player_id").toString()))+1);
                    if(this.player.getRole().equals("werewolf")) {
                        if(1-this.getDeadWerewolf() == vote_werewolf +1) break;
                        vote_werewolf++;
                    }
                    else {
                        if(2-this.getDeadWerewolf() == vote_werewolf +1) break;
                        vote_werewolf++;
                    }
                }
            }
        }
        try {
            this.udpSocket.setSoTimeout(0);
        } catch (SocketException ex) {
            
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
    
    public void acceptedProposal() {
        int player_won = -1, player_lose = -1;
        System.out.println(this.proposal.toString());
        
        if(!this.player.isProposer()) {
            // ACCEPTOR
            if(proposal.size() == 2) {
                String prop_1 = (String)proposal.get(0).get("proposal_id");
                String prop_2 = (String)proposal.get(1).get("proposal_id");

                int proposal_id_1 = Integer.parseInt(prop_1.substring(prop_1.indexOf('(')+1, prop_1.indexOf(',')));
                int proposal_id_2 = Integer.parseInt(prop_2.substring(prop_2.indexOf('(')+1, prop_2.indexOf(',')));
                int player_id_1 = Integer.parseInt(prop_1.substring(prop_1.indexOf(',')+1, prop_1.indexOf(')')));
                int player_id_2 = Integer.parseInt(prop_2.substring(prop_2.indexOf(',')+1, prop_2.indexOf(')')));

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
            }
            else if(proposal.size() == 1) {
                // Jika proposal yang diterima hanya satu berarti proposal itu yang menang
                String prop = (String)proposal.get(0).get("proposal_id");
                player_won = Integer.parseInt(prop.substring(prop.indexOf(',')+1, prop.indexOf(')')));
            }
            else if(this.proposal.size() == 0) {
                // Jika proposal tidak diterima
                System.out.println("Got nothing!");
            }

            
            JSONObject data = new JSONObject();
            
            if(player_won != -1) {
                data.put("status", "ok");
                data.put("description", "accepted");
                String sendData = data.toJSONString();
                this.sendToUDP(this.getPlayerAddress(player_won), this.getPlayerPort(player_won), sendData, this.lowerBound);
                data.clear();
            }
            
            if(player_lose != -1) {
                data.put("status", "fail");
                data.put("description", "rejected");
                String sendData = data.toJSONString();
                this.sendToUDP(this.getPlayerAddress(player_lose), this.getPlayerPort(player_lose), sendData, this.lowerBound);
                data.clear();
            }
            
            data.put("method","accepted_proposal");
            data.put("kpu_id", player_won);
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
        else {
            if(this.proposal.size() > 0) {
                String prop = (String)proposal.get(0).get("proposal_id");
                int proposal_id = Integer.parseInt(prop.substring(prop.indexOf(',')+1, prop.indexOf(')')));

                JSONObject data = new JSONObject();
                data.put("status", "fail");
                data.put("description", "rejected");
                String sendData = data.toJSONString();
                this.sendToUDP(this.getPlayerAddress(proposal_id), this.getPlayerPort(proposal_id), sendData, this.lowerBound);

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
            else {
                System.out.println("Got nothing!");
                JSONObject data = new JSONObject();
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
        proposal.clear();
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
        JSONObject recv;
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
            
            recv = (JSONObject)listenToServer();
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
        
        Scanner keyboard = new Scanner(System.in);
        JSONObject data = new JSONObject();
        // SEND STATUS OK
        data.put("status", "ok");
        this.sendToServer(data);
        data.clear();
        
        // List of players
        System.out.println("Players");
        for(Object player : players) {
            JSONObject temp = (JSONObject)player;
            System.out.print(temp.get("username")+"\t\t");
            System.out.println(temp.get("is_alive").equals(1) ? "ALIVE" : "DEAD");
        }

        if(this.player.getKPUID() == this.player.getID()) {
            System.out.println("I AM THE KPU FOR TODAY");
            
            Thread t1 = new Thread(this);
            try {
                this.udpSocket.setSoTimeout(60000);
            } catch (SocketException ex) {
                
            }
            t1.start();
            
            String voted_player;
            if(isDay && this.player.isAlive()) {
                while(true) {
                    if(this.player.getRole().equals("civilian")) {
                        System.out.print("\nWHO IS THE WEREWOLF? ");
                    }
                    else {
                        System.out.println("\nDO NOT FORGET! YOUR FRIEND IS "+friends.get(0));
                        System.out.print("AS THE WEREWOLF, WHO WILL YOU KILL? ");
                    }
                    voted_player = keyboard.nextLine();
                    
                    if (this.getIDFromUsername(voted_player) != -1){    
                        if(this.isPlayerAlive(this.getIDFromUsername(voted_player))) 
                            break;
                        else
                            System.out.println(voted_player+" already died. Choose another player!");
                    } else {
                        System.out.println(voted_player+" doesn't exist");
                    }
                }
                this.votes.set(this.getIDFromUsername(voted_player), votes.get(this.getIDFromUsername(voted_player))+1);
            }
            else if(!isDay && this.player.isAlive()) {
                if(this.player.getRole().equals("civilian")) {
                    System.out.println("You cannot vote. You are sleeping...");
                }
                else {
                    while(true) {
                        System.out.print("\nWHO WILL YOU KILL TONIGHT? ");
                        voted_player = keyboard.nextLine();
                        if (this.getIDFromUsername(voted_player) != -1){    
                            if(this.isPlayerAlive(this.getIDFromUsername(voted_player))) 
                                break;
                            else
                                System.out.println(voted_player+" already died. Choose another player!");
                        } else {
                            System.out.println(voted_player+" doesn't exist");
                        }
                    }
                    this.votes.set(this.getIDFromUsername(voted_player), votes.get(this.getIDFromUsername(voted_player))+1);
                }
            }
            else if(!this.player.isAlive()) {
                System.out.println("\nYou already died. Just watch!");
            }

            try {
                t1.join();
            } catch (InterruptedException ex) {
                Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
            }

            JSONArray json_array = new JSONArray();
            JSONArray final_array = new JSONArray();

            for (int j=0; j<votes.size(); j++) {
                json_array.add(j); json_array.add(votes.get(j));

                final_array.add(json_array.toJSONString());
                json_array.clear();
            }

            if (isDay) {
                civilianVoteInfo(final_array);
            } else {
                werewolfVoteInfo(final_array);
            }

        }
        else {
            if(isDay && this.player.isAlive()) {
                String voted_player = null;
                while(true) {
                    if(this.player.getRole().equals("civilian")) {
                        System.out.print("\nWHO IS THE WEREWOLF? ");
                    }
                    else {
                        System.out.println("\nDO NOT FORGET! YOUR FRIEND IS "+friends.get(0));
                        System.out.print("AS THE WEREWOLF, WHO WILL YOU KILL? ");
                    }
                    voted_player = keyboard.nextLine();
                    if (this.getIDFromUsername(voted_player) != -1){    
                        if(this.isPlayerAlive(this.getIDFromUsername(voted_player))) 
                            break;
                        else
                            System.out.println(voted_player+" already died. Choose another player!");
                    } else {
                        System.out.println(voted_player+" doesn't exist");
                    }
                }
                data.put("method","vote_civilian");
                data.put("player_id", this.getIDFromUsername(voted_player));
                this.sendToUDP(this.getPlayerAddress(this.player.getKPUID()), this.getPlayerPort(this.player.getKPUID()), data.toJSONString(), 1.00);
            }
            else if(!isDay && this.player.isAlive()) {
                if(this.player.getRole().equals("civilian")) {
                    System.out.print("You cannot vote. You are sleeping...");
                }
                else {
                    String voted_player = null;
                    while(true) {
                        System.out.print("\nWHO WILL YOU KILL TONIGHT? ");
                        voted_player = keyboard.nextLine();
                        if (this.getIDFromUsername(voted_player) != -1){    
                            if(this.isPlayerAlive(this.getIDFromUsername(voted_player))) 
                                break;
                            else
                                System.out.println(voted_player+" already died. Choose another player!");
                        } else {
                            System.out.println(voted_player+" doesn't exist");
                        }
                    }
                    data.put("method","vote_werewolf");
                    data.put("player_id", this.getIDFromUsername(voted_player));
                    this.sendToUDP(this.getPlayerAddress(this.player.getKPUID()), this.getPlayerPort(this.player.getKPUID()), data.toJSONString(), 1.00);
                }
            }
            else if(!this.player.isAlive()) {
                System.out.println("You already died. Just watch!");
            }
        }
        try {
            this.udpSocket.setSoTimeout(0);
        } catch (SocketException ex) {
            
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
    
    public int getDeadWerewolf() {
        int counter = 0;
        for (int i=0; i<players.size(); i++) {
            if (Integer.parseInt(((JSONObject)players.get(i)).get("is_alive").toString())==0) {
                JSONObject player = (JSONObject)players.get(i);
                if(player != null) {
                    Object role = player.get("role");
                    if(role != null) {
                        if (role.toString().equals("werewolf")) {
                            counter++;
                        }
                    }
                }
            }
        }
        
        return counter;
    }
    
    public boolean isPlayerAlive(int id) {
        for(int i=0; i<players.size(); i++) {
            if (Integer.parseInt(((JSONObject)players.get(i)).get("player_id").toString())==id) {
                return Integer.parseInt(((JSONObject)players.get(i)).get("is_alive").toString())==1;
            }
        }
        return true;
    }
}
