package com.example.slagalica.repository;

import android.util.Log;

import com.example.slagalica.models.Match;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class MatchmakingRepository {

    private final DatabaseReference db = FirebaseDatabase.getInstance().getReference();
    private final String currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();

    private ValueEventListener player1MatchListener;

    // Poziva se kada igrač klikne "Igraj"
    public void startMatchmaking(OnMatchFoundListener listener) {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();

        firestore.collection("users")
                .document(currentUid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        Log.e("Matchmaking", "Korisnik ne postoji u Firestore-u");
                        listener.onNoTokens();
                        return;
                    }

                    Long tokensLong = documentSnapshot.getLong("tokens");
                    int tokens = tokensLong != null ? tokensLong.intValue() : 0;

                    Log.d("Matchmaking", "Tokens from Firestore = " + tokens);

                    if (tokens <= 0) {
                        listener.onNoTokens();
                        return;
                    }

                    findOrCreateMatch(listener);
                })
                .addOnFailureListener(e -> {
                    Log.e("Matchmaking", "Greška pri čitanju iz Firestorea", e);
                });
    }

    private void findOrCreateMatch(OnMatchFoundListener listener) {
        DatabaseReference waitingRef = db.child("matchmaking").child("waiting");

        waitingRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String waitingPlayerId = snapshot.child("playerId").getValue(String.class);

                    if (waitingPlayerId.equals(currentUid)) {
                        // Isti igrač čeka, ne radi ništa
                        return;
                    }

                    // Nađen protivnik – kreiraj meč
                    String matchId = db.child("matches").push().getKey();
                    Match match = new Match();
                    match.player1Id = waitingPlayerId;
                    match.player2Id = currentUid;
                    match.status = "in_progress";
                    match.currentGame = 0;
                    match.scores = new HashMap<>();
                    match.scores.put(waitingPlayerId, 0);
                    match.scores.put(currentUid, 0);
                    match.player1Id = waitingPlayerId;
                    match.player2Id = currentUid;
                    match.createdAt = System.currentTimeMillis();

                    // Atomičan upis: meč + brisanje lobija + troši token oba igrača
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("/matches/" + matchId, match);
                    updates.put("/matchmaking/waiting", null); // obriši čekaoca
                    updates.put("/players/" + waitingPlayerId + "/inMatch", true);
                    updates.put("/players/" + currentUid + "/inMatch", true);
                    //updates.put("/players/" + waitingPlayerId + "/tokens", ServerValue.increment(-1));
                    //updates.put("/players/" + currentUid + "/tokens", ServerValue.increment(-1));

                    db.updateChildren(updates).addOnSuccessListener(unused -> {
                        FirebaseFirestore firestore = FirebaseFirestore.getInstance();

                        firestore.collection("users").document(currentUid)
                                .update("tokens", com.google.firebase.firestore.FieldValue.increment(-1));
                        firestore.collection("users").document(waitingPlayerId)
                                .update("tokens", com.google.firebase.firestore.FieldValue.increment(-1));

                        listener.onMatchFound(matchId, "player2");
                    });

                } else {
                    // Niko ne čeka – dodaj sebe u lobi
                    Map<String, Object> waiting = new HashMap<>();
                    waiting.put("playerId", currentUid);
                    waiting.put("timestamp", System.currentTimeMillis());
                    waitingRef.setValue(waiting);
                    listener.onWaitingForOpponent();

                    // Slušaj kad se meč kreira
                    listenForMatchAsPlayer1(listener);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e("Matchmaking", "Greška pri čitanju tokena", error.toException());
            }
        });
    }

    private void listenForMatchAsPlayer1(OnMatchFoundListener listener) {
        Query matchesQuery = db.child("matches")
                .orderByChild("player1Id")
                .equalTo(currentUid);

        player1MatchListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Log.d("Matchmaking", "Player1 listener fired, children = " + snapshot.getChildrenCount());
                for (DataSnapshot matchSnap : snapshot.getChildren()) {
                    Log.d("Matchmaking", "Match found: " + matchSnap.getKey());
                    Match m = matchSnap.getValue(Match.class);

                    if (m != null && "in_progress".equals(m.status)) {
                        matchesQuery.removeEventListener(player1MatchListener);
                        listener.onMatchFound(matchSnap.getKey(), "player1");
                        return;
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError e) {}
        };

        matchesQuery.addValueEventListener(player1MatchListener);
    }

    // Pozvati kad igrač odustane od čekanja
    public void cancelMatchmaking() {
        db.child("matchmaking").child("waiting")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            String waitingId = snapshot.child("playerId").getValue(String.class);
                            if (currentUid.equals(waitingId)) {
                                db.child("matchmaking").child("waiting").removeValue();
                            }
                        }
                    }
                    @Override public void onCancelled(DatabaseError e) {}
                });
    }

    public interface OnMatchFoundListener {
        void onMatchFound(String matchId, String role);
        void onWaitingForOpponent();
        void onNoTokens();
    }
}