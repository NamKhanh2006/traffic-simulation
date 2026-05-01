package com.myteam.traffic.light;

public abstract class TrafficLight {
    protected final int redTime;
    protected final int greenTime;
    protected final int yellowTime;
    protected LightState currentState;
    protected int secondsRemaining;

    public TrafficLight(int redTime, int greenTime, int yellowTime) {
        this.redTime = redTime;
        this.greenTime = greenTime;
        this.yellowTime = yellowTime;
        this.currentState = LightState.RED;
        this.secondsRemaining = redTime;
    }

    public LightState getCurrentState() {
        return currentState;
    }

    public int getSecondsRemaining() {
        return secondsRemaining;
    }

    public abstract void changeState();

    protected void switchTo(LightState nextState) {
        this.currentState = nextState;
        this.secondsRemaining = getDurationForState(nextState);
    }

    protected int getDurationForState(LightState state) {
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

    protected LightState nextState(LightState state) {
        switch (state) {
            case RED:
                return LightState.GREEN;
            case GREEN:
                return LightState.YELLOW;
            case YELLOW:
                return LightState.RED;
            default:
                throw new IllegalArgumentException("Unknown light state: " + state);
        }
    }
}
