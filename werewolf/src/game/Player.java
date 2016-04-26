/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package game;

import java.io.Serializable;
import java.net.Socket;

/**
 *
 * @author Acer
 */
public class Player implements Serializable{
    private int player_id = 0;
    private String player_name;
    private String player_role;
    
    private int udp_port;
    
    private boolean state; //alive=true; dead=false
    private boolean isReady;
    
    public Player(int id, String name, int port) {
        player_id = id;
        player_name = name;
        player_role = new String ("undefined");
        udp_port = port;
        state = true;
        isReady = false;    //ini gatau true pas kapan
    }
    
    public int getID() {
        return player_id;
    }
    
    public String getName() {
        return player_name;
    }
    
    public String getRole() {
        return player_role;
    }
    
    public boolean isAlive() {
        return state;
    }
    
    public boolean isReady() {
        return isReady;
    }
    
    public int getUDPPOrt() {
        return udp_port; 
    }
    
    public void setRole(String new_role) {
        player_role = new_role;
    }
    
    public void setState(boolean new_state)  {
        state = new_state;
    }
    
    public void setReady (boolean value) { //nama fungsinya jelek wkwkwk
        isReady = value;
    }
}
