package com.myteam.traffic.light;

public class TenSecLight extends TrafficLight {
    public TenSecLight(int redTime, int greenTime, int yellowTime) {
        super(redTime, greenTime, yellowTime);
    }

    @Override
    public void changeState() {
        if (secondsRemaining > 10) {
            secondsRemaining = 10;
            return;
        }

        if (secondsRemaining > 1) {
            secondsRemaining--;
            return;
        }

        LightState next = nextState(currentState);
        switchTo(next);
    }
}
