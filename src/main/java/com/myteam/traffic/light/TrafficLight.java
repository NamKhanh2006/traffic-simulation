package com.myteam.traffic.light;

public class TrafficLight {
	public int redTime; // The duration of a red light (in seconds)
	public int greenTime; // The duration of a green light (in seconds)
	public int yellowTime; // The duration of a yellow light (in seconds)
	public TrafficLightState state;  // The traffic light's current color
	
	public TrafficLight(int redTime, int greenTime, int yellowTime) {
		super();
		this.redTime = redTime;
		this.greenTime = greenTime;
		this.yellowTime = yellowTime;
	}
}
