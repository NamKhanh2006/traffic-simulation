package com.myteam.traffic.light;

public abstract class TrafficLight {
    protected final int redTime;
    protected final int greenTime;
    protected final int yellowTime;
    protected TrafficLightState currentState;
    protected int secondsRemaining;

    public TrafficLight(int redTime, int greenTime, int yellowTime) {
        this.redTime = redTime;
        this.greenTime = greenTime;
        this.yellowTime = yellowTime;
        this.currentState = TrafficLightState.RED;
        this.secondsRemaining = redTime;
    }

    public TrafficLightState getCurrentState() {
        return currentState;
    }

    public int getSecondsRemaining() {
        return secondsRemaining;
    }

    public abstract void changeState();

    protected void switchTo(TrafficLightState nextState) {
        this.currentState = nextState;
        this.secondsRemaining = getDurationForState(nextState);
    }

    protected int getDurationForState(TrafficLightState state) {
        switch (state) {
            case RED:
                return redTime;
            case GREEN:
                return greenTime;
            case YELLOW:
                return yellowTime;
            default:
                throw new IllegalArgumentException("Unknown light state: " + state);
        }
    }

    protected TrafficLightState nextState(TrafficLightState state) {
        switch (state) {
            case RED:
                return TrafficLightState.GREEN;
            case GREEN:
                return TrafficLightState.YELLOW;
            case YELLOW:
                return TrafficLightState.RED;
            default:
                throw new IllegalArgumentException("Unknown light state: " + state);
        }
    }
}
