package com.myteam.traffic.sign;

public class AdditionalPanel {
    private PanelType type;
    private float value;
    
    public AdditionalPanel(PanelType type, float value) {
        this.type = type;
        this.value = value;
    }

    public PanelType getType() {
        return type;
    }
    public float getValue() {
        return value;
    }
}
