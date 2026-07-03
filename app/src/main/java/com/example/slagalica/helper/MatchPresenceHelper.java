package com.example.slagalica.helper;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

public class MatchPresenceHelper {

    private final DatabaseReference matchRef;
    private final String matchId;
    private final String myUid;

    private DatabaseReference myPresenceRef;
    private DatabaseReference opponentPresenceRef;
    private ValueEventListener opponentListener;
    private ValueEventListener leftByListener;

    // Postavlja se samo ako je ovo solo partija u okviru Izazova - vidi setChallengeContext().
    private String challengeId;

    public MatchPresenceHelper(String matchId, String myUid) {
        this.matchId = matchId;
        this.myUid = myUid;
        this.matchRef = FirebaseDatabase.getInstance().getReference("matches").child(matchId);
    }

    /**
     * Poziva se iz igara kada je partija deo Izazova. Ako igrač napusti partiju
     * (leaveMatch) pre nego što je stigao da je završi, njegov rezultat se nikad
     * ne bi predao u challenges/{challengeId}, pa bi obračun nagrada zauvek čekao
     * njega - zato ovde odmah upisujemo "nije završio" (dnf) rezultat.
     */
    public void setChallengeContext(String challengeId) {
        this.challengeId = challengeId;
    }

    /** Pozvati jednom kad igrač uđe u partiju (npr. u GameFragment/GameActivity). */
    public void markPresent() {
        myPresenceRef = matchRef.child("presence").child(myUid);
        myPresenceRef.setValue(true);
        // Ako konekcija padne (crash, gašenje app-a, gubitak neta) - automatski se piše false
        myPresenceRef.onDisconnect().setValue(false);
    }

    /** Pozvati na eksplicitan izlazak (dugme "Napusti partiju" ili potvrđen back). */
    public void leaveMatch() {
        if (myPresenceRef != null) {
            myPresenceRef.onDisconnect().cancel();
            myPresenceRef.setValue(false);
        }
        matchRef.child("leftBy").runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                if (currentData.getValue() == null) {
                    currentData.setValue(myUid);
                }
                return Transaction.success(currentData);
            }
            @Override
            public void onComplete(DatabaseError e, boolean committed, DataSnapshot d) {}
        });

        // Izazov: pošto igrač napušta partiju pre kraja, njegov rezultat nikad ne bi
        // stigao do challenges/{challengeId} (to se inače predaje tek na kraju poslednje
        // igre) - upisujemo "nije završio" odmah, da obračun nagrada ne čeka zauvek.
        if (challengeId != null && !challengeId.isEmpty()) {
            new com.example.slagalica.repository.ChallengeRepository().submitDnfResult(
                    challengeId, myUid,
                    new com.example.slagalica.repository.ChallengeRepository.OnChallengeActionListener() {
                        @Override public void onSuccess() {}
                        @Override public void onFailure(String message) {}
                    });
        }

        // ── Tačka 3.f: napuštanjem gubim partiju i NE dobijam/gubim zvezde ──────
        // (zvezde se namerno ne diraju - samo statistika partija/poraza)
        java.util.Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("stats.global.totalGames", com.google.firebase.firestore.FieldValue.increment(1));
        updates.put("stats.global.losses",     com.google.firebase.firestore.FieldValue.increment(1));

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        FirebaseDatabase.getInstance()
                .getReference("players")
                .child(uid)
                .child("inMatch")
                .setValue(false);

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users")
                .document(myUid)
                .update(updates);

        matchRef.child("presence")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {

                        boolean anyonePresent = false;

                        for (DataSnapshot child : snapshot.getChildren()) {
                            Boolean present = child.getValue(Boolean.class);
                            if (Boolean.TRUE.equals(present)) {
                                anyonePresent = true;
                                break;
                            }
                        }

                        if (!anyonePresent) {
                            matchRef.child("status").setValue("finished");
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                    }
                });
    }

    /** Slušaj da li je protivnik napustio (prisustvo=false ILI leftBy==opponentUid). */
    public void listenForOpponentLeft(String opponentUid, Runnable onOpponentLeft) {
        opponentPresenceRef = matchRef.child("presence").child(opponentUid);
        opponentListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean present = snapshot.getValue(Boolean.class);
                if (present != null && !present) {
                    onOpponentLeft.run();
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        opponentPresenceRef.addValueEventListener(opponentListener);

        leftByListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                String leftBy = snapshot.getValue(String.class);
                if (opponentUid.equals(leftBy)) onOpponentLeft.run();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        matchRef.child("leftBy").addValueEventListener(leftByListener);
    }

    public void detach() {
        if (opponentPresenceRef != null && opponentListener != null)
            opponentPresenceRef.removeEventListener(opponentListener);
        if (leftByListener != null)
            matchRef.child("leftBy").removeEventListener(leftByListener);
    }
}