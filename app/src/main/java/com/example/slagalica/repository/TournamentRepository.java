package com.example.slagalica.repository;

import android.util.Log;

import com.example.slagalica.models.Match;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TournamentRepository {

    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private final DatabaseReference rtdb = FirebaseDatabase.getInstance().getReference();
    private final String currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
    private ListenerRegistration queueListener;
    private static final int TOURNAMENT_COST = 3;

    public void joinTournament(OnTournamentStatusListener listener) {
        DocumentReference userRef = firestore.collection("users").document(currentUid);
        DocumentReference queueRef = firestore.collection("tournament_matchmaking").document("waiting");

        firestore.runTransaction((Transaction.Function<Void>) transaction -> {
            // ==========================================
            // 1. SVA ČITANJA MORAJU IĆI NA POČETAK!
            // ==========================================
            DocumentSnapshot userSnapshot = transaction.get(userRef);
            DocumentSnapshot queueSnapshot = transaction.get(queueRef);

            // ==========================================
            // 2. LOGIKA I PROVERE (Nakon što je sve pročitano)
            // ==========================================
            Long tokens = userSnapshot.getLong("tokens");
            if (tokens == null || tokens < TOURNAMENT_COST) {
                throw new IllegalStateException("INSUFFICIENT_TOKENS");
            }

            List<String> waitingPlayers = (List<String>) queueSnapshot.get("players");
            if (waitingPlayers == null) {
                waitingPlayers = new ArrayList<>();
            }

            // Ako je igrač već u redu, nemoj ga ponovo dodavati niti mu skidati tokene
            if (waitingPlayers.contains(currentUid)) {
                return null;
            }

            // Dodaj trenutnog igrača u lokalnu listu
            waitingPlayers.add(currentUid);

            // ==========================================
            // 3. SVI UPISI IDU NA KRAJ!
            // ==========================================

            // Skini tokene (Zahtev c)
            transaction.update(userRef, "tokens", FieldValue.increment(-TOURNAMENT_COST));

            // Da li imamo 4 igrača?
            if (waitingPlayers.size() == 4) {
                // KREIRAJ TURNIR!
                String tournamentId = firestore.collection("tournaments").document().getId();

                // Promešaj igrače nasumično (Zahtev a)
                Collections.shuffle(waitingPlayers);

                String p1 = waitingPlayers.get(0);
                String p2 = waitingPlayers.get(1);
                String p3 = waitingPlayers.get(2);
                String p4 = waitingPlayers.get(3);

                // Kreiraj mečeve u Realtime Database-u
                String match1Id = rtdb.child("matches").push().getKey();
                String match2Id = rtdb.child("matches").push().getKey();

                createTournamentMatchInRTDB(match1Id, p1, p2, tournamentId, "semi_finals");
                createTournamentMatchInRTDB(match2Id, p3, p4, tournamentId, "semi_finals");

                // Snimi strukturu turnira u Firestore
                Map<String, Object> tournamentData = new HashMap<>();
                tournamentData.put("tournamentId", tournamentId);
                tournamentData.put("status", "semi_finals");
                tournamentData.put("players", waitingPlayers);

                Map<String, String> semiFinals = new HashMap<>();
                semiFinals.put("match1Id", match1Id);
                semiFinals.put("match2Id", match2Id);
                tournamentData.put("semiFinals", semiFinals);

                Map<String, String> finals = new HashMap<>();
                finals.put("matchId", "");
                finals.put("player1Id", "");
                finals.put("player2Id", "");
                finals.put("winnerId", "");
                tournamentData.put("finals", finals);

                transaction.set(firestore.collection("tournaments").document(tournamentId), tournamentData);

                // Isprazni red za čekanje za sledeći turnir
                transaction.update(queueRef, "players", new ArrayList<>());

            } else {
                // Nema 4 igrača, samo ažuriraj red za čekanje
                transaction.update(queueRef, "players", waitingPlayers);
            }

            return null;
        }).addOnSuccessListener(unused -> {
            listener.onJoinedQueue();
            listenForTournamentStart(listener);
        }).addOnFailureListener(e -> {
            if (e instanceof IllegalStateException && e.getMessage().equals("INSUFFICIENT_TOKENS")) {
                listener.onNoTokens();
            } else {
                Log.e("Tournament", "Greška pri ulasku u turnir", e);
            }
        });
    }
    public void createTournamentMatchInRTDB(String matchId, String p1, String p2, String tourId, String phase) {
        Match match = new Match();
        match.player1Id = p1;
        match.player2Id = p2;
        match.status = "in_progress";
        match.currentGame = 0;
        match.scores = new HashMap<>();
        match.scores.put(p1, 0);
        match.scores.put(p2, 0);
        match.createdAt = System.currentTimeMillis();

        Map<String, Object> updates = new HashMap<>();
        updates.put("/matches/" + matchId, match);
        updates.put("/matches/" + matchId + "/isTournament", true);
        updates.put("/matches/" + matchId + "/tournamentId", tourId);
        updates.put("/matches/" + matchId + "/tournamentPhase", phase);

        updates.put("/players/" + p1 + "/inMatch", true);
        updates.put("/players/" + p2 + "/inMatch", true);

        rtdb.updateChildren(updates);
    }

    public void listenForTournamentStart(OnTournamentStatusListener listener) {
        if (queueListener != null) queueListener.remove();

        queueListener = firestore.collection("tournaments")
                .whereArrayContains("players", currentUid)
                .whereEqualTo("status", "semi_finals")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null || snapshots.isEmpty()) return;

                    DocumentSnapshot doc = snapshots.getDocuments().get(0);
                    String tournamentId = doc.getId();

                    List<String> players = (List<String>) doc.get("players");
                    Map<String, String> semiFinals = (Map<String, String>) doc.get("semiFinals");

                    String myMatchId = "";
                    String myRole = "";

                    if (players != null && semiFinals != null) {
                        if (currentUid.equals(players.get(0)) || currentUid.equals(players.get(1))) {
                            myMatchId = semiFinals.get("match1Id");
                            myRole = currentUid.equals(players.get(0)) ? "player1" : "player2";
                        } else if (currentUid.equals(players.get(2)) || currentUid.equals(players.get(3))) {
                            myMatchId = semiFinals.get("match2Id");
                            myRole = currentUid.equals(players.get(2)) ? "player1" : "player2";
                        }
                    }

                    if (!myMatchId.isEmpty()) {
                        removeQueueListener(); // Gasimo slušalac jer meč počinje
                        listener.onTournamentStarted(tournamentId, myMatchId, myRole);
                    }
                });
    }

    // Atomska operacija: Izbaci igrača iz niza i VRATI mu 3 žetona nazad
    public void cancelTournamentMatchmaking(String uid, OnCancelListener listener) {
        // Prvo ugasimo snapshot listener da nam fragment ne reaguje na promenu u redu
        removeQueueListener();

        DocumentReference queueRef = firestore.collection("tournament_matchmaking").document("waiting");
        DocumentReference userRef = firestore.collection("users").document(uid);

        WriteBatch batch = firestore.batch();

        // 1. Ukloni korisnika iz niza čekanja
        batch.update(queueRef, "players", FieldValue.arrayRemove(uid));

        // 2. Vrati žetone (Refundacija pošto meč zapravo nije počeo)
        batch.update(userRef, "tokens", FieldValue.increment(TOURNAMENT_COST));

        // Izvrši batch operaciju na serveru
        batch.commit()
                .addOnSuccessListener(aVoid -> listener.onCancelSuccess())
                .addOnFailureListener(e -> {
                    Log.e("Tournament", "Greška pri otkazivanju reda", e);
                    listener.onCancelFailure(e);
                });
    }

    private void removeQueueListener() {
        if (queueListener != null) {
            queueListener.remove();
            queueListener = null;
        }
    }

    public interface OnCancelListener {
        void onCancelSuccess();
        void onCancelFailure(Exception e);
    }

    public interface OnTournamentStatusListener {
        void onJoinedQueue();
        void onNoTokens();
        void onTournamentStarted(String tournamentId, String myMatchId, String myRole);
    }
}