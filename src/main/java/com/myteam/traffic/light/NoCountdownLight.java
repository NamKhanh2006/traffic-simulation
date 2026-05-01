package com.myteam.traffic.light;

public class NoCountdownLight extends TrafficLight {
    public NoCountdownLight(int redTime, int greenTime, int yellowTime) {
        super(redTime, greenTime, yellowTime);
    }

    @Override
    public void changeState() {
        LightState next = nextState(currentState);
        switchTo(next);
    }
}
