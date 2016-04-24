/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package client;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
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
    
    public Client() {
        SERVER_HOSTNAME = "127.0.1.1";
        COMM_PORT = 8181;
    }
    
    public Client(String host, int port) {
        SERVER_HOSTNAME = host;
        COMM_PORT = port;
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
    
    public void joinGame(String username) {
        JSONObject obj = new JSONObject();
        obj.put("username", username);
        obj.put("method","join");
        sendToServer(obj);
        
        obj = (JSONObject)listenToServer();
        player_id = (Integer)obj.get("player_id");
        obj = new JSONObject();
        obj.put("method","ready");
        sendToServer(obj);
        obj = (JSONObject)listenToServer();
        if(obj.get("status").equals("ok")) {
            System.out.println(obj.get("description"));
        }
        else {
            
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
    
    public static void main(String[] args) {
        Client client = new Client();
        client.connectToServer();
        System.out.println(client.socket);
        
        // Get username from user
        Scanner keyboard = new Scanner(System.in);
        System.out.print("Username: ");
        String username = keyboard.nextLine();
        client.joinGame(username);
        
        
        
        client.leaveGame();
    }
}
