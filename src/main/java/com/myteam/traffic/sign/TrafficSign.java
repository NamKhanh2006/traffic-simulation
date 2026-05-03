package com.myteam.traffic.sign;

public class TrafficSign {
    private int xPos;
    private int yPos;
    private SignType signType;

    public TrafficSign(int xPos, int yPos, SignType signType) {
        this.xPos = xPos;
        this.yPos = yPos;
        this.signType = signType;
    }

    // Getters and setters
    public int getXPos() {
        return xPos;
    }
    public void setXPos(int xPos) {
        this.xPos = xPos;
    }
    public int getYPos() {
        return yPos;
    }
    public void setYPos(int yPos) {
        this.yPos = yPos;
    }
    public SignType getSignType() {
        return signType;
    }
}
