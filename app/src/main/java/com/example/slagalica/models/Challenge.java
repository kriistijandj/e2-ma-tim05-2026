package com.example.slagalica.models;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class Challenge {
    public String challengeId = "";
    public String creatorId = "";
    public String status = "open"; // "open" | "in_progress" | "finished"
    public long stakeStars = 0;
    public long stakeTokens = 0;
    public List<String> participants = new ArrayList<>();
    public long createdAt = 0;

    public Challenge() {}

    @SuppressWarnings("unchecked")
    public static Challenge fromSnapshot(DocumentSnapshot doc) {
        Challenge c = new Challenge();
        c.challengeId = doc.getId();
        String creator = doc.getString("creatorId");
        c.creatorId = creator != null ? creator : "";
        String status = doc.getString("status");
        c.status = status != null ? status : "open";
        Long stars = doc.getLong("stakeStars");
        c.stakeStars = stars != null ? stars : 0;
        Long tokens = doc.getLong("stakeTokens");
        c.stakeTokens = tokens != null ? tokens : 0;
        Object participantsObj = doc.get("participants");
        if (participantsObj instanceof List) {
            c.participants = new ArrayList<>((List<String>) participantsObj);
        }
        com.google.firebase.Timestamp createdAt = doc.getTimestamp("createdAt");
        c.createdAt = createdAt != null ? createdAt.toDate().getTime() : 0;
        return c;
    }
}
