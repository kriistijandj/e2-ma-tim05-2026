package com.example.slagalica.repository;

import com.example.slagalica.models.ChatMessage;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.HashMap;
import java.util.Map;

public class ChatRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private static final String COLLECTION = "messages";

    public void sendMessage(String region, String senderId, String senderUsername, String text) {
        // 1. Pošalji poruku u čet (postojeći kod)
        ChatMessage message = new ChatMessage(senderId, senderUsername, text, Timestamp.now());
        db.collection(COLLECTION)
                .document(region)
                .collection("chats")
                .add(message);

        // 2. Upiši notifikaciju svim korisnicima u regionu osim pošiljaoca
        db.collection("users")
                .whereEqualTo("region", region)
                .whereEqualTo("online", false)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    querySnapshot.getDocuments().forEach(doc -> {
                        String uid = doc.getId();
                        if (uid.equals(senderId)) return; // preskoci posaljaoca

                        Map<String, Object> notif = new HashMap<>();
                        notif.put("title", senderUsername + " (čet)");
                        notif.put("message", text);
                        notif.put("timestamp", System.currentTimeMillis());
                        notif.put("isRead", false);
                        notif.put("type", "CHAT");

                        db.collection("users")
                                .document(uid)
                                .collection("notifications")
                                .add(notif);
                    });
                });
    }


    public Query getMessagesQuery(String region) {
        return db.collection(COLLECTION)
                .document(region)
                .collection("chats")
                .orderBy("timestamp", Query.Direction.ASCENDING);
    }
}