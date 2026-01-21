package com.example.meshtastic.data.model;

import java.util.Date;

/**
 * Модель текстового сообщения в mesh-сети.
 */
public class Message {
    private String id;
    private String text;
    private String senderId; // ID узла отправителя
    private long timestamp;
    private boolean isOwnMessage; // true если сообщение отправлено с этого устройства
    
    public Message() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public Message(String text, String senderId, boolean isOwnMessage) {
        this.text = text;
        this.senderId = senderId;
        this.isOwnMessage = isOwnMessage;
        this.timestamp = System.currentTimeMillis();
        this.id = generateId();
    }
    
    private String generateId() {
        return senderId + "_" + timestamp;
    }
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getText() {
        return text;
    }
    
    public void setText(String text) {
        this.text = text;
    }
    
    public String getSenderId() {
        return senderId;
    }
    
    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public boolean isOwnMessage() {
        return isOwnMessage;
    }
    
    public void setOwnMessage(boolean ownMessage) {
        isOwnMessage = ownMessage;
    }
    
    public String getFormattedTime() {
        return new Date(timestamp).toString();
    }
}
