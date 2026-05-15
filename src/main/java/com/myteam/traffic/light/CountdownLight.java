package com.myteam.traffic.light;

public class CountdownLight extends TrafficLight{
	private int timeLeft; // The time left until the next state
	
	
	
	public CountdownLight(int redTime, int greenTime, int yellowTime) {
		super(redTime, greenTime, yellowTime);
	}
	
	
}
