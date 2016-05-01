/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package server;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 *
 * @author vanyadeasy
 */
public class Server extends Thread {
    public final static int COMM_PORT = 8181;  // socket port for client comms
    private final static int PLAYER_TO_PLAY = 3; // minimum number of clients
    
    private static ServerSocket serverSocket; // server socket
    private static ArrayList<JSONObject> players = new ArrayList<>(); // list of all players
    private static ArrayList<String> roles = new ArrayList<>(); // list of roles where index equals player_id
    private static Map<Integer, Integer> proposed_kpu_id = new HashMap<>();

    private static int clientCount = 0; // number of clients
    private static int readyCount = 0; // number of ready clients
    private static int playerCount = 0; // number of players currently alive
    private int day = 0; // number of days
    private boolean isDay = false;
    private boolean isPlaying = false;
    
    private final Socket clientSocket;
    private int player_id;
    
    public Server(Socket clientSocket) {
        this.clientSocket = clientSocket;
        start();
    }
    
    public static void main(String[] args) {
        // Create server socket
        try {
            serverSocket = new ServerSocket(COMM_PORT, 0, InetAddress.getLocalHost());
            System.out.println(serverSocket);
        } catch (IOException ex) {
            Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
        }
        System.out.println("Listening...");
        
        while(true) {
            Socket socket = null;
            try {
                socket = serverSocket.accept();
                new Server(socket);
            } catch (IOException ex) {
                Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    @Override
    public void run() {
        System.out.println ("New Communication Thread Started");
        JSONObject jsonRecv;
        boolean isLeave = false;
        do {
            JSONObject temp = new JSONObject();
                        
            Object recv = listen(clientSocket);
            jsonRecv = (JSONObject)recv;
            temp.clear();
            String method = (String)jsonRecv.get("method");
            if(method != null) {
                if(jsonRecv.get("method").equals("join")) {
                    if (!isPlaying){
                        if(!isUsernameExist((String)jsonRecv.get("username"))) {
                            temp.put("username", jsonRecv.get("username"));
                            player_id = playerCount;
                            temp.put("player_id", player_id);
                            send(clientSocket, temp);
                            temp.put("is_alive", 1);
                            temp.put("address",jsonRecv.get("udp_address"));
                            temp.put("port",(Integer)jsonRecv.get("udp_port"));
                            players.add(temp);
                            randomizeRole(player_id);
                            System.out.println("\nClient Counter: "+ ++clientCount);
                            ++playerCount;
                        }
                        else {
                            temp.put("status", "fail");
                            temp.put("description", "user exists");
                            send(clientSocket, temp);
                        }
                    }
                    else {
                        temp.put("status","fail");
                        temp.put("description", "please wait, game is currently running");
                    }
                }
                else if(jsonRecv.get("method").equals("ready")) {
                    temp.put("status","ok");
                    temp.put("description","waiting for other player to start");
                    send(clientSocket, temp);
                    System.out.println("\nReady Counter: "+ ++readyCount);

                    while (readyCount < clientCount || clientCount < PLAYER_TO_PLAY){
                        // keep on waiting
                        System.out.print("");
                    }

                    // when everyone is ready and the game hasn't started yet
                    if (readyCount == clientCount && !isPlaying && clientCount >= PLAYER_TO_PLAY) {   
                        // START GAME
                        isPlaying = true;
                        day++;
                        temp.put("method","start");
                        temp.put("time", "night");
                        temp.put("description", "game is started");
                        temp.put("role",roles.get(player_id));
                        if (roles.get(player_id).equals("werewolf")){
                            ArrayList<String> friends = new ArrayList<>();
                            for (int i=0; i<clientCount; i++){
                                if (roles.get(i).equals("werewolf") && i!=player_id){
                                    String p = (String) players.get(i).get("username");
                                    friends.add(p);
                                }
                            }
                            temp.put("friends", friends);
                        }
                        send(clientSocket, temp);

                        Object recv_status_start = listen(clientSocket);
                        jsonRecv = (JSONObject)recv_status_start;
                        if(jsonRecv.get("status").equals("ok")) { // successfully start the game
                            // CHANGE PHASE
                            isDay = true;
                            temp.clear();
                            temp.put("method", "change_phase");
                            temp.put("time", "day");
                            temp.put("days", ++day);
                            temp.put("description", "PUT NARRATION HERE");
                            send(clientSocket, temp);
                            JSONObject status = (JSONObject)listen(clientSocket);
                            if(status.get("status").equals("ok")) { 
                                //SUCCESS
                            }
                        } else {
                            // start game unseccesful (mau diapain yah enaknya)
                        }
                    }
                }
                else if(jsonRecv.get("method").equals("client_address")) {
                    if (isPlaying) {
                        temp.put("status", "ok");
                        JSONArray playerJSON = new JSONArray();
                        playerJSON.addAll(players);
                        temp.put("clients", playerJSON);
                        temp.put("description", "list of clients retrieved");
                    } else {
                        temp.put("status", "fail");
                        temp.put("description", "the game hasn't started yet");
                    }
                    send(clientSocket, temp);
                }
                else if (jsonRecv.get("method").equals("accepted_proposal")){
                    if (!isPlaying){
                        temp.put("status", "fail");
                        temp.put("description", "the game hasn't started yet");
                    } else if ((Integer)jsonRecv.get("kpu_id") > players.size()-1) {
                        temp.put("status", "fail");
                        temp.put("description", "player_id doesn't exist");
                    } else {
                        temp.put("status", "ok");
                        temp.put("description", "proposal recieved");
                        proposed_kpu_id.put(player_id, (Integer)jsonRecv.get("kpu_id"));
                    }
                    send(clientSocket, temp);

                    while (proposed_kpu_id.size() < playerCount){
                        // keep on waiting
                        System.out.print("");
                    }

                    // every active palyer has proposed a leader
                    // INCLUDING the proposers themselves
                    if (proposed_kpu_id.size() == playerCount){ 
                        int kpu_id = electedKPU();
                        System.out.println("Elected KPU: " + kpu_id);
                        temp.clear();
                        temp.put("status", "ok");
                        temp.put("description", "your KPU proposal has been accepted");
                        send(clientSocket, temp);
                        temp.clear();
                        temp.put("method", "kpu_selected");
                        temp.put("kpu_id", kpu_id);
                    }

                } else if (jsonRecv.get("method").equals("vote_result_werewolf")) {
                    if (!isPlaying){
                        temp.put("status", "fail");
                        temp.put("description", "the game hasn't started yet");
                    } else {
                        temp.put("status", "ok");
                        temp.put("description", "vote result for werewolf recieved");
                    }
                } else if (jsonRecv.get("method").equals("vote_result_civillian")) {
                    if (!isPlaying){
                        temp.put("status", "fail");
                        temp.put("description", "the game hasn't started yet");
                    } else {
                        temp.put("status", "ok");
                        temp.put("description", "vote result for civillian recieved");
                    }
                } else if (jsonRecv.get("method").equals("vote_result")) {
                    if (!isPlaying){
                        temp.put("status", "fail");
                        temp.put("description", "the game hasn't started yet");
                    } else {
                        temp.put("status", "fail");
                        temp.put("description", "no one is killed");
                    }
                }
                isLeave = method.equals("leave");
            }
        } while(!isLeave);
        
        JSONObject leave = new JSONObject();
        leave.put("status", "ok");
        send(clientSocket, leave);
        
        players.stream().filter((p) -> ((Integer)p.get("player_id") == playerCount)).forEach((p) -> {
            p = null;
        });
        System.out.println("\nCommunication Thread Stopped. Client leave!");
        System.out.println("Client Counter: "+ --clientCount);
        --playerCount;
    }
    
    public static Object listen(Socket socket) {
        Object object = null;
        try {
            InputStream iStream = socket.getInputStream();
            ObjectInputStream oiStream = new ObjectInputStream(iStream);
            object = (Object) oiStream.readObject();
            System.out.println("\nServer received: "+object.toString()+" from: "+socket);
        } catch (IOException | ClassNotFoundException ex) {
            Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
        }
        return object;
    }
    
    // send object to socket
    public static boolean send(Socket socket, Object object) {
        OutputStream oStream;
        try {
            oStream = socket.getOutputStream();
            ObjectOutputStream ooStream = new ObjectOutputStream(oStream);
            ooStream.writeObject(object);  // send serialized
        } catch (IOException ex) {
            Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
            System.out.println("Client "+socket+" has disconnected");
            return false;
        }
        System.out.println("\nServer send: "+object.toString());
        return true;
    }
        
    public void randomizeRole(int player_id) {
        if(Collections.frequency(roles, "werewolf") == 0) {
            roles.add(player_id, "werewolf");
        }
        else if((float)Collections.frequency(roles,"werewolf")/(float)PLAYER_TO_PLAY >= 1.00/3.00) {
            roles.add(player_id, "civilian");
        }
        else {
            Random rand = new Random();
            roles.add(player_id, rand.nextBoolean() ? "werewolf":"civilian");
        }
    }
    
    public boolean isUsernameExist(String username) {
        return players.stream().anyMatch((temp) -> (temp.get("username").equals(username)));
    }
    
    public void voteNow(boolean isDay) {
        JSONObject recv;
        do { // send method
            JSONObject obj = new JSONObject();
            obj.put("method","vote_now");
            if (isDay) {
                obj.put("phase",isDay);
            } else {
                obj.put("phase","night");
            }
            
            send(clientSocket, obj);
            
            recv = (JSONObject) listen(clientSocket);
         
            if(!recv.get("status").equals("ok")) {
                System.out.println(recv.toJSONString());
            }
        } while(!recv.get("status").equals("ok"));
    }
    
    public void gameOver (String winner) {
        JSONObject recv;
        do { // send method
            JSONObject obj = new JSONObject();
            obj.put("method","game_over");
            obj.put("phase",winner);
            obj.put("description","HAHA"); //@TODO ubah deskripsi
            
            send(clientSocket, obj);   
            recv = (JSONObject) listen(clientSocket);
         
            if(!recv.get("status").equals("ok")) {
                System.out.println(recv.toJSONString());
            }
        } while(!recv.get("status").equals("ok"));
    }
    
    public int getDeadPlayers() {
        int counter = 0;
        
        for  (int i=0; i<players.size(); i++) {
            if (Integer.parseInt(players.get(i).get("is_alive").toString())==0) {
                counter++;
            }
        }
        
        return counter;
    }
    
    public int getDeadWerewolfNumber() {
        int counter = 0;
        
        for  (int i=0; i<players.size(); i++) {
            if (Integer.parseInt(players.get(i).get("is_alive").toString())==0) {
                if (players.get(i).get("role").toString().equals("werewolf")) {
                    counter++;
                }
            }
        }
        
        return counter;
    }
    
    public int electedKPU(){
        int smallest_id = 0, candidate1_id = 0, candidate2_id = 0;
        int candidate1_count = 0, candidate2_count = 0;
        for (int i=0; i<players.size(); i++){
            if ((Integer)players.get(i).get("is_alive") == 1){
                candidate1_id = proposed_kpu_id.get(i);
                candidate1_count++;
                smallest_id = i;
                break;
            }
        }
        
        for (int i = smallest_id+1; i<players.size(); i++){
            if ((Integer)players.get(i).get("is_alive") == 1){
                if (proposed_kpu_id.get(i) == candidate1_id){
                    candidate1_count++;
                } else {
                    candidate2_id = proposed_kpu_id.get(i);
                    candidate2_count++;
                }
            }
        }
        
        if (candidate2_count > candidate1_count){
            return candidate2_id;
        } else { // jika jumlah vote sama, otomatis kandidat pertama terpilih
            return candidate1_id;
        }
    }
    
}
