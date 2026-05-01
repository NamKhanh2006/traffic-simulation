package com.myteam.traffic.light;

public class CountdownLight extends TrafficLight {
    public CountdownLight(int redTime, int greenTime, int yellowTime) {
        super(redTime, greenTime, yellowTime);
    }

    @Override
    public void changeState() {
        if (secondsRemaining > 1) {
            secondsRemaining--;
            return;
        }

        LightState next = nextState(currentState);
        switchTo(next);
    }
}
