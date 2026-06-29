package com.example.slagalica.repository;

import com.example.slagalica.models.ChatMessage;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

public class ChatRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private static final String COLLECTION = "messages";

    public void sendMessage(String region, String senderId, String senderUsername, String text) {
        ChatMessage message = new ChatMessage(senderId, senderUsername, text, Timestamp.now());
        db.collection(COLLECTION)
                .document(region)
                .collection("chats")
                .add(message);
    }

    public Query getMessagesQuery(String region) {
        return db.collection(COLLECTION)
                .document(region)
                .collection("chats")
                .orderBy("timestamp", Query.Direction.ASCENDING);
    }
}