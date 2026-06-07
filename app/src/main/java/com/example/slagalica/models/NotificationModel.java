package com.example.slagalica.models;

import com.google.firebase.firestore.DocumentId;

public class NotificationModel {
    @DocumentId // Firebase će automatski upisati ID dokumenta u ovo polje
    private String id;
    private String title;
    private String message;
    private long timestamp;
    private boolean isRead; // U bazi neka se zove isRead
    private String type;    // Najbezbednije je držati kao String ili mapirati iz Enuma

    public NotificationModel() {}

    public NotificationModel(String id, String title, String message, long timestamp, boolean isRead, String type) {
        this.id = id;
        this.title = title;
        this.message = message;
        this.timestamp = timestamp;
        this.isRead = isRead;
        this.type = type;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    // Veoma važno za Firebase mapiranje:
    public boolean getIsRead() { return isRead; }
    public void setIsRead(boolean read) { isRead = read; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}