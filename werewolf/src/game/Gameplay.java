/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package game;

/**
 *
 * @author Acer
 */
public class Gameplay {
    private boolean isStart;
    private boolean daytime;
    
    public Gameplay() {
        isStart = false;
        daytime = false;
    }
    
    public boolean isStarted() {
        return isStart;
    }
    
    public boolean isDaytime() {
        return daytime;
    }
    
    public void start() {
        isStart = true;
    }
    
    public void stop() {
        isStart = false;
    }
    
    public void setNight() {
        daytime = false;
    }
    
    public void setDay() {
        daytime = true;
    }
}
