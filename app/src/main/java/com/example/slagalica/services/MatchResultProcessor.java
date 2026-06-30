package com.example.slagalica.services;

import android.util.Log;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.Transaction;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class MatchResultProcessor {

    public static void processMatchEnd(String matchId, String winnerId, String loserId,
                                       int winnerScore, int loserScore) {
        DatabaseReference db = FirebaseDatabase.getInstance().getReference();

        // Zvezde za pobednika: +10 + floor(score/40)
        int winnerStars = 10 + (winnerScore / 40);
        // Zvezde za gubitnika: -10 + floor(score/40), min 0
        int loserStarsBonus = loserScore / 40;

        Map<String, Object> updates = new HashMap<>();
        updates.put("/matches/" + matchId + "/status", "finished");
        updates.put("/players/" + winnerId + "/inMatch", false);
        updates.put("/players/" + loserId + "/inMatch", false);
        updates.put("/players/" + winnerId + "/stars", ServerValue.increment(winnerStars));
        // Za gubitnika: dodaj bonus, oduzmi 10
        // Firebase ne podržava direktno "oduzmi 10 ali dodaj X", pa uradi u transakciji:

        db.child("players").child(loserId).child("stars").runTransaction(new Transaction.Handler() {
            @Override
            public Transaction.Result doTransaction(MutableData currentData) {
                int current = currentData.getValue(Integer.class) != null
                        ? currentData.getValue(Integer.class) : 0;
                int newStars = Math.max(0, current - 10 + loserStarsBonus);
                currentData.setValue(newStars);
                return Transaction.success(currentData);
            }
            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
                if (error != null) {
                    Log.e("MatchResult", "Greška pri ažuriranju zvezdica", error.toException());
                }
            }
        });

        db.updateChildren(updates);

        // Sync stars to Firestore and update leagues
        FirebaseFirestore fs = FirebaseFirestore.getInstance();
        fs.collection("users").document(winnerId)
                .update("stars", FieldValue.increment(winnerStars))
                .addOnSuccessListener(v -> LeagueManager.checkAndUpdateLeague(winnerId, null, null));

        fs.collection("users").document(loserId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    long current = doc.getLong("stars") != null ? doc.getLong("stars") : 0;
                    long newStars = Math.max(0, current - 10 + loserStarsBonus);
                    fs.collection("users").document(loserId)
                            .update("stars", newStars)
                            .addOnSuccessListener(v ->
                                    LeagueManager.checkAndUpdateLeague(loserId, null, null));
                });
    }
}
