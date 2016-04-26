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
import java.util.Random;
import java.util.Scanner;
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
    private static ServerSocket serverSocket; // server socket
    private static ArrayList<JSONObject> players = new ArrayList<>();
    private static ArrayList<String> roles = new ArrayList<>();
    private final Socket clientSocket;
    private static int clientCount = 0;
    private static int playerCount = 0;
    private static int PLAYER_TO_PLAY;
    
    private int player_id;
    
    public Server(Socket clientSocket) {
        this.clientSocket = clientSocket;
        start();
    }
    
    public static void main(String[] args) {
        try {
            System.out.println(Inet4Address.getLocalHost().getHostAddress());
        } catch (UnknownHostException ex) {
            Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        do {
            Scanner keyboard = new Scanner(System.in);
            System.out.print("Players amount(>= 6) : ");
            PLAYER_TO_PLAY = keyboard.nextInt();
        } while(PLAYER_TO_PLAY < 0); /** @TODO Jangan lupa diganti */
        
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
        do {
            Object recv = listen(clientSocket);
            jsonRecv = (JSONObject)recv;
            
            JSONObject temp = new JSONObject();
            if(jsonRecv.get("method").equals("join")) {
                if(PLAYER_TO_PLAY > playerCount) {
                    if(!isUsernameExist((String)jsonRecv.get("username"))) {
                        temp.put("username", jsonRecv.get("username"));
                        player_id = playerCount;
                        temp.put("player_id", player_id);
                        send(clientSocket, temp);
                        temp.put("is_alive", 1);
                        temp.put("address",jsonRecv.get("address"));
                        temp.put("port",(Integer)jsonRecv.get("port"));
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
                    System.out.println("\nClient Counter: "+ ++clientCount);
                }
            }
            else if(jsonRecv.get("method").equals("ready")) {
                temp.put("status","ok");
                if(PLAYER_TO_PLAY > clientCount) {
                    temp.put("play", 0);
                    temp.put("player",clientCount);
                    temp.put("description","waiting for other player to start");
                }
                else {
                    temp.put("play", 1);
                    temp.put("player",clientCount);
                    temp.put("description","ready to start");
                    
                }
                send(clientSocket, temp);
                
                while(PLAYER_TO_PLAY > clientCount){
                    System.out.print("");
                }
                
                temp.clear();
                temp.put("method","start");
                temp.put("time", "day");
                temp.put("description", "game is started");
                temp.put("role",roles.get(player_id));
                send(clientSocket, temp);
            }
            else if(jsonRecv.get("method").equals("client_address")) {
                while(PLAYER_TO_PLAY > clientCount); // Tunggu sampai siap main
                temp.put("status", "ok");
                JSONArray playerJSON = new JSONArray();
                playerJSON.addAll(players);
                temp.put("clients", playerJSON);
                temp.put("description", "list of clients retrieved");
                send(clientSocket, temp);
            }
        } while(!jsonRecv.get("method").equals("leave"));
        
        JSONObject leave = new JSONObject();
        leave.put("status", "ok");
        send(clientSocket, leave);
        
        players.stream().filter((p) -> ((Integer)p.get("player_id") == playerCount)).forEach((p) -> {
            p = null;
        });
        System.out.println("\nCommunication Thread Stopped. Client leave!");
        System.out.println("Client Counter: "+ --clientCount);
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
    
    public Boolean isUsernameExist(String username) {
        return players.stream().anyMatch((temp) -> (temp.get("username").equals(username)));
    }
}
