package com.example.slagalica.models;

public class NotificationModel {

        private String title;
        private String message;
        private String time;
        private boolean isRead;
        private NotificationType type;

        public NotificationModel(String title, String message, String time, boolean isRead, NotificationType type) {
            this.title = title;
            this.message = message;
            this.time = time;
            this.isRead = isRead;
            this.type = type;
        }

        public String getTitle() {
            return title;
        }

        public String getMessage() {
            return message;
        }

        public String getTime() {
            return time;
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


