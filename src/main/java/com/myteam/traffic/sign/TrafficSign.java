package com.myteam.traffic.sign;
import com.myteam.traffic.model.infrastructure.*;
import com.myteam.traffic.common.*;
import com.myteam.traffic.rule.*;

public class TrafficSign {
	private float xPos;	// The x position of the sign
	private float yPos;	// The y position of the sign
	private String code; // Use float to handle multiple signs with the same integer code
	/*
	Eg: code = 110.0 => Sign P.110
	 	code = 120.1 => Sign P.120a
	 	code = 120.2 => Sign P.120b
		code = 120.3 => Sign P.120c
		code = 120.4 => Sign P.120d
		(Note that these codes are only examples to illustrate the pattern and do not necessarily belong to real 
		traffic signs)
	*/
	private SignType type;
	private TrafficRule rule;
	
	RoadSegment road; // The road segment on which the sign is put

	public TrafficSign(float xPos, float yPos, String code, TrafficRule rule, RoadSegment road) {
		super();
		this.xPos = xPos;
		this.yPos = yPos;
		this.code = code;
		this.rule = rule;
		this.road = road;
	}

	// Getters and setters
	
	
	
	
}
