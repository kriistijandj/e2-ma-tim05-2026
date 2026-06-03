package com.example.slagalica.models;

public class NotificationModel {

        private String id;
        private String title;
        private String message;
        private long timestamp;
        private boolean isRead;
        private NotificationType type;
        public NotificationModel() {
        }

        public NotificationModel(String id,String title, String message, long timestamp, boolean isRead, NotificationType type) {
            this.id=id;
            this.title = title;
            this.message = message;
            this.timestamp = timestamp;
            this.isRead = isRead;
            this.type = type;
        }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    public String getTitle() {
            return title;
        }

        public String getMessage() {
            return message;
        }



        public boolean isRead() {
            return isRead;
        }

        public NotificationType getType() {
            return type;
        }

        public void setRead(boolean read) {
            isRead = read;
        }
    }


