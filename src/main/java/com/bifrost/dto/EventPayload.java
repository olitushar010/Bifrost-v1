package com.bifrost.dto;

import java.util.Map;

public class EventPayload {
    private String eventId;
    private String eventType;
    private Map<String, Object> payload;
    
    public String getEventId(){
        return eventId;
    }
    public void setEventId(String eventId){
        this.eventId = eventId;
    }
    public String getEventType(){
        return eventType;
    }   
    public void setEventType(String eventType){
        this.eventType= eventType;
    }
    public Map<String, Object> getPayload(){
        return payload;
    }
    public void setPayload(Map<String, Object> payload){
        this.payload = payload;
    }

}
