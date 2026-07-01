package com.example.slagalica.repository;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ChallengeRepository {

    private static final long MAX_STAKE_STARS = 10;
    private static final long MAX_STAKE_TOKENS = 2;
    private static final double WINNER_SHARE = 0.75;
    private static final int MAX_PARTICIPANTS = 4;

    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private final DatabaseReference rtdb = FirebaseDatabase.getInstance().getReference();
    private final String currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();

    public interface OnChallengeCreateListener {
        void onCreated(String challengeId);
        void onInsufficientFunds();
        void onFailure(String message);
    }

    public interface OnChallengeActionListener {
        void onSuccess();
        void onFailure(String message);
    }

    // -------------------------------------------------------------------------
    // KREIRANJE IZAZOVA
    // -------------------------------------------------------------------------

    public void createChallenge(long stakeStars, long stakeTokens, OnChallengeCreateListener listener) {
        final long stars = clamp(stakeStars, 0, MAX_STAKE_STARS);
        final long tokens = clamp(stakeTokens, 0, MAX_STAKE_TOKENS);

        DocumentReference userRef = firestore.collection("users").document(currentUid);

        userRef.get().addOnSuccessListener(snap -> {
            long curStars = longOrZero(snap.getLong("stars"));
            long curTokens = longOrZero(snap.getLong("tokens"));

            if (curStars < stars || curTokens < tokens) {
                listener.onInsufficientFunds();
                return;
            }

            String challengeId = firestore.collection("challenges").document().getId();
            DocumentReference challengeRef = firestore.collection("challenges").document(challengeId);

            Map<String, Object> data = new HashMap<>();
            data.put("challengeId", challengeId);
            data.put("creatorId", currentUid);
            data.put("status", "open");
            data.put("stakeStars", stars);
            data.put("stakeTokens", tokens);
            data.put("participants", Collections.singletonList(currentUid));
            data.put("createdAt", FieldValue.serverTimestamp());

            WriteBatch batch = firestore.batch();
            batch.update(userRef, "stars", FieldValue.increment(-stars));
            batch.update(userRef, "tokens", FieldValue.increment(-tokens));
            batch.set(challengeRef, data);

            batch.commit()
                    .addOnSuccessListener(v -> listener.onCreated(challengeId))
                    .addOnFailureListener(e -> listener.onFailure(e.getMessage()));
        }).addOnFailureListener(e -> listener.onFailure(e.getMessage()));
    }

    // -------------------------------------------------------------------------
    // PRIDRUŽIVANJE / NAPUŠTANJE ČEKAONICE
    // -------------------------------------------------------------------------

    public void joinChallenge(String challengeId, OnChallengeActionListener listener) {
        DocumentReference challengeRef = firestore.collection("challenges").document(challengeId);
        DocumentReference userRef = firestore.collection("users").document(currentUid);

        firestore.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot challengeSnap = transaction.get(challengeRef);
            DocumentSnapshot userSnap = transaction.get(userRef);

            String status = challengeSnap.getString("status");
            List<String> participants = stringList(challengeSnap.get("participants"));

            if (!"open".equals(status)) {
                throw new RuntimeException("Izazov više nije otvoren za pridruživanje.");
            }
            if (participants.contains(currentUid)) {
                throw new RuntimeException("Već ste deo ovog izazova.");
            }
            if (participants.size() >= MAX_PARTICIPANTS) {
                throw new RuntimeException("Izazov je već popunjen.");
            }

            long stakeStars = longOrZero(challengeSnap.getLong("stakeStars"));
            long stakeTokens = longOrZero(challengeSnap.getLong("stakeTokens"));
            long curStars = longOrZero(userSnap.getLong("stars"));
            long curTokens = longOrZero(userSnap.getLong("tokens"));

            if (curStars < stakeStars || curTokens < stakeTokens) {
                throw new RuntimeException("Nemate dovoljno zvezda/tokena za ovaj izazov.");
            }

            transaction.update(challengeRef, "participants", FieldValue.arrayUnion(currentUid));
            transaction.update(userRef, "stars", FieldValue.increment(-stakeStars));
            transaction.update(userRef, "tokens", FieldValue.increment(-stakeTokens));

            return null;
        }).addOnSuccessListener(v -> listener.onSuccess())
          .addOnFailureListener(e -> listener.onFailure(messageOf(e)));
    }

    public void cancelJoin(String challengeId, OnChallengeActionListener listener) {
        DocumentReference challengeRef = firestore.collection("challenges").document(challengeId);
        DocumentReference userRef = firestore.collection("users").document(currentUid);

        firestore.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot snap = transaction.get(challengeRef);
            String status = snap.getString("status");

            if (!"open".equals(status)) {
                throw new RuntimeException("Izazov je već počeo, ne možete napustiti čekaonicu.");
            }

            long stakeStars = longOrZero(snap.getLong("stakeStars"));
            long stakeTokens = longOrZero(snap.getLong("stakeTokens"));

            transaction.update(challengeRef, "participants", FieldValue.arrayRemove(currentUid));
            transaction.update(userRef, "stars", FieldValue.increment(stakeStars));
            transaction.update(userRef, "tokens", FieldValue.increment(stakeTokens));

            return null;
        }).addOnSuccessListener(v -> listener.onSuccess())
          .addOnFailureListener(e -> listener.onFailure(messageOf(e)));
    }

    // -------------------------------------------------------------------------
    // POKRETANJE IZAZOVA
    // -------------------------------------------------------------------------

    /** Poziva domaćin (kreator) klikom na dugme, kada ima bar 2 učesnika. */
    public void startChallengeManually(String challengeId, OnChallengeActionListener listener) {
        claimAndStart(challengeId, 2, listener);
    }

    /** Pasivna provera pozvana na svaku promenu čekaonice - tiho ne radi ništa
     * ako uslov (4 učesnika) još nije ispunjen ili je izazov već pokrenut. */
    public void autoStartIfReady(String challengeId) {
        claimAndStart(challengeId, MAX_PARTICIPANTS, new OnChallengeActionListener() {
            @Override public void onSuccess() {}
            @Override public void onFailure(String message) {}
        });
    }

    private void claimAndStart(String challengeId, int minParticipants, OnChallengeActionListener listener) {
        DocumentReference challengeRef = firestore.collection("challenges").document(challengeId);

        firestore.runTransaction((Transaction.Function<List<String>>) transaction -> {
            DocumentSnapshot snap = transaction.get(challengeRef);
            String status = snap.getString("status");
            List<String> participants = stringList(snap.get("participants"));

            if (!"open".equals(status) || participants.size() < minParticipants) {
                // Ili je neko drugi već pokrenuo izazov, ili uslov još nije ispunjen -
                // u oba slučaja tiho ne radimo ništa (nije greška).
                return null;
            }

            transaction.update(challengeRef, "status", "starting");
            return participants;
        }).addOnSuccessListener(participants -> {
            if (participants == null) return;
            startMatchesForParticipants(challengeId, participants, listener);
        }).addOnFailureListener(e -> listener.onFailure(messageOf(e)));
    }

    private void startMatchesForParticipants(String challengeId, List<String> participants,
                                              OnChallengeActionListener listener) {
        Map<String, Object> matchIds = new HashMap<>();
        Map<String, Object> rtdbUpdates = new HashMap<>();

        for (String uid : participants) {
            String matchId = rtdb.child("matches").push().getKey();
            matchIds.put(uid, matchId);

            Map<String, Object> scores = new HashMap<>();
            scores.put(uid, 0);

            Map<String, Object> presence = new HashMap<>();
            presence.put("SOLO", false);

            Map<String, Object> matchData = new HashMap<>();
            matchData.put("player1Id", uid);
            matchData.put("player2Id", "SOLO");
            matchData.put("status", "in_progress");
            matchData.put("currentGame", 0);
            matchData.put("scores", scores);
            matchData.put("createdAt", System.currentTimeMillis());
            matchData.put("isChallenge", true);
            matchData.put("challengeId", challengeId);
            matchData.put("presence", presence);

            rtdbUpdates.put("/matches/" + matchId, matchData);
            rtdbUpdates.put("/players/" + uid + "/inMatch", true);
        }

        rtdb.updateChildren(rtdbUpdates, (error, ref) -> {
            if (error != null) {
                listener.onFailure("Greška pri kreiranju partija: " + error.getMessage());
                return;
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put("status", "in_progress");
            updates.put("matchIds", matchIds);
            updates.put("startedAt", FieldValue.serverTimestamp());

            firestore.collection("challenges").document(challengeId)
                    .update(updates)
                    .addOnSuccessListener(v -> listener.onSuccess())
                    .addOnFailureListener(e -> listener.onFailure(e.getMessage()));
        });
    }

    // -------------------------------------------------------------------------
    // PREDAJA REZULTATA + OBRAČUN NAGRADA
    // -------------------------------------------------------------------------

    public void submitChallengeResult(String challengeId, String uid, int score, OnChallengeActionListener listener) {
        submitResult(challengeId, uid, score, false, listener);
    }

    /** Poziva se kada učesnik napusti solo partiju pre kraja - tretira se kao
     * neuspešno završena partija (0 poena) i isključuje se iz rangiranja/nagrada. */
    public void submitDnfResult(String challengeId, String uid, OnChallengeActionListener listener) {
        submitResult(challengeId, uid, 0, true, listener);
    }

    @SuppressWarnings("unchecked")
    private void submitResult(String challengeId, String uid, int score, boolean dnf, OnChallengeActionListener listener) {
        DocumentReference challengeRef = firestore.collection("challenges").document(challengeId);

        firestore.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot snap = transaction.get(challengeRef);

            Map<String, Object> results = (Map<String, Object>) snap.get("results");
            if (results == null) results = new HashMap<>();

            if (results.containsKey(uid)) {
                return null; // već predato - idempotentno
            }

            List<String> participants = stringList(snap.get("participants"));

            Map<String, Object> myResult = new HashMap<>();
            myResult.put("score", score);
            myResult.put("dnf", dnf);
            myResult.put("finishedAt", FieldValue.serverTimestamp());
            transaction.update(challengeRef, "results." + uid, myResult);

            Set<String> doneUids = new HashSet<>(results.keySet());
            doneUids.add(uid);

            boolean allDone = !participants.isEmpty() && doneUids.containsAll(participants);

            Object payoutComputedObj = snap.get("payout.computed");
            boolean payoutComputed = Boolean.TRUE.equals(payoutComputedObj);

            if (allDone && !payoutComputed) {
                computeAndApplyPayout(transaction, challengeRef, snap, participants, results, uid, score, dnf);
            }

            return null;
        }).addOnSuccessListener(v -> listener.onSuccess())
          .addOnFailureListener(e -> listener.onFailure(messageOf(e)));
    }

    @SuppressWarnings("unchecked")
    private void computeAndApplyPayout(Transaction transaction, DocumentReference challengeRef,
                                        DocumentSnapshot snap, List<String> participants,
                                        Map<String, Object> priorResults, String myUid, int myScore,
                                        boolean myDnf) {
        long stakeStars = longOrZero(snap.getLong("stakeStars"));
        long stakeTokens = longOrZero(snap.getLong("stakeTokens"));

        Map<String, Integer> scores = new HashMap<>();
        Set<String> dnfUids = new HashSet<>();
        if (myDnf) dnfUids.add(myUid);

        for (String p : participants) {
            if (p.equals(myUid)) {
                scores.put(p, myScore);
                continue;
            }
            Object rObj = priorResults.get(p);
            int s = 0;
            if (rObj instanceof Map) {
                Map<?, ?> rMap = (Map<?, ?>) rObj;
                Object scoreObj = rMap.get("score");
                if (scoreObj instanceof Long) s = ((Long) scoreObj).intValue();
                else if (scoreObj instanceof Integer) s = (Integer) scoreObj;

                if (Boolean.TRUE.equals(rMap.get("dnf"))) dnfUids.add(p);
            }
            scores.put(p, s);
        }

        // Rangiranje po broju bodova opadajuće (učesnici koji nisu završili partiju
        // - "dnf" - isključeni su iz rangiranja i ne dobijaju ništa nazad). Nerešeno
        // se razrešava redosledom pridruživanja (stabilno sortiranje) - precizno
        // poređenje po vremenu završetka nije moguće unutar iste transakcije, pošto
        // se FieldValue.serverTimestamp() za MOJ rezultat razrešava tek posle commit-a.
        List<String> ranking = new ArrayList<>();
        for (String p : participants) {
            if (!dnfUids.contains(p)) ranking.add(p);
        }
        Collections.sort(ranking, (a, b) -> scores.get(b) - scores.get(a));

        long totalStars = stakeStars * participants.size();
        long totalTokens = stakeTokens * participants.size();
        long winnerStars = (long) (WINNER_SHARE * totalStars);
        long winnerTokens = (long) (WINNER_SHARE * totalTokens);

        Map<String, Object> starsDelta = new HashMap<>();
        Map<String, Object> tokensDelta = new HashMap<>();

        for (int i = 0; i < ranking.size(); i++) {
            String p = ranking.get(i);
            long sDelta;
            long tDelta;
            if (i == 0) {
                sDelta = winnerStars;
                tDelta = winnerTokens;
            } else if (i == 1) {
                sDelta = stakeStars;
                tDelta = stakeTokens;
            } else {
                sDelta = 0;
                tDelta = 0;
            }
            starsDelta.put(p, sDelta);
            tokensDelta.put(p, tDelta);

            if (sDelta != 0) {
                transaction.update(firestore.collection("users").document(p), "stars", FieldValue.increment(sDelta));
            }
            if (tDelta != 0) {
                transaction.update(firestore.collection("users").document(p), "tokens", FieldValue.increment(tDelta));
            }
        }

        Map<String, Object> payout = new HashMap<>();
        payout.put("computed", true);
        payout.put("ranking", ranking);
        payout.put("starsDelta", starsDelta);
        payout.put("tokensDelta", tokensDelta);

        transaction.update(challengeRef, "payout", payout);
        transaction.update(challengeRef, "status", "finished");
        transaction.update(challengeRef, "finishedAt", FieldValue.serverTimestamp());
    }

    // -------------------------------------------------------------------------
    // HELPERI
    // -------------------------------------------------------------------------

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long longOrZero(Long v) {
        return v != null ? v : 0;
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object value) {
        if (value instanceof List) {
            return new ArrayList<>((List<String>) value);
        }
        return new ArrayList<>();
    }

    private static String messageOf(Exception e) {
        return e.getMessage() != null ? e.getMessage() : "Došlo je do greške.";
    }
}
