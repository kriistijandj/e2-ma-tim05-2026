package com.example.slagalica.models;

import com.google.firebase.Timestamp;

public class ChatMessage {
    private String id;
    private String senderId;
    private String senderUsername;
    private String text;
    private Timestamp timestamp;

    public ChatMessage() {} // obavezno za Firebase

    public ChatMessage(String senderId, String senderUsername, String text, Timestamp timestamp) {
        this.senderId = senderId;
        this.senderUsername = senderUsername;
        this.text = text;
        this.timestamp = timestamp;
    }

    // getteri i setteri za sve fieldove
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }
    public String getSenderUsername() { return senderUsername; }
    public void setSenderUsername(String senderUsername) { this.senderUsername = senderUsername; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public Timestamp getTimestamp() { return timestamp; }
    public void setTimestamp(Timestamp timestamp) { this.timestamp = timestamp; }
}