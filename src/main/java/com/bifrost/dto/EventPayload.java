package com.bifrost.dto;

import java.util.Map;
import java.util.UUID;

public class EventPayload {
    private UUID eventId;
    private String eventType;
    private Map<String, Object> payload;
    
    public  UUID getEventId(){
        return eventId;
    }
    public void setEventId(UUID eventId){
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
