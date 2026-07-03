package com.example.slagalica.services;

import android.util.Log;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class TokenService {

    public static void claimDailyTokensIfEligible(String uid) {
        DatabaseReference playerRef = FirebaseDatabase.getInstance()
                .getReference("players").child(uid);

        playerRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                long lastClaim = snapshot.child("lastTokenClaimTimestamp")
                        .getValue(Long.class);
                long now = System.currentTimeMillis();
                long oneDayMs = 24 * 60 * 60 * 1000L;

                if (now - lastClaim >= oneDayMs) {
                    int league = snapshot.child("league").getValue(Integer.class); // 0-5
                    int bonusTokens = league; // liga 0 = 0 bonus, liga 3 = 3 bonus

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("tokens", ServerValue.increment(5 + bonusTokens));
                    updates.put("lastTokenClaimTimestamp", now);
                    playerRef.updateChildren(updates);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e("Matchmaking", "Greška pri čitanju tokena", error.toException());
            }
        });
    }
}