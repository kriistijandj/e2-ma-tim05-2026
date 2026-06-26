package com.example.slagalica.repository;

import androidx.annotation.NonNull;

import com.example.slagalica.models.asocijacije.AsocijacijeGameState;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class AsocijacijeRepository {

    private final DatabaseReference gameRef;
    private ValueEventListener gameStateListener;

    public interface AsocijacijeCallback {
        void onStateChanged(AsocijacijeGameState state);
    }

    public AsocijacijeRepository(String matchId) {
        // Putanja je identična MojBroju – games/{matchId}/asocijacije
        this.gameRef = FirebaseDatabase.getInstance()
                .getReference("games")
                .child(matchId)
                .child("asocijacije");
    }

    public void listenToGameState(AsocijacijeCallback callback) {
        if (gameStateListener != null) {
            gameRef.removeEventListener(gameStateListener);
        }
        gameStateListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                AsocijacijeGameState state = snapshot.getValue(AsocijacijeGameState.class);
                if (state != null) callback.onStateChanged(state);
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        gameRef.addValueEventListener(gameStateListener);
    }

    public void updateGameState(AsocijacijeGameState state) {
        gameRef.setValue(state);
    }

    public void removeListener() {
        if (gameStateListener != null) {
            gameRef.removeEventListener(gameStateListener);
            gameStateListener = null;
        }
    }

    /**
     * Isti ready mehanizam kao u MojBrojRepository.
     * Svaki igrač poziva setReady(), i tek kad su oba ready,
     * izvršava se bothReadyCallback (samo jednom, kod player1).
     */
    public void setReady(String role, Runnable bothReadyCallback) {
        gameRef.child("ready").child(role).setValue(true);
        gameRef.child("ready").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean p1 = Boolean.TRUE.equals(snapshot.child("player1").getValue(Boolean.class));
                boolean p2 = Boolean.TRUE.equals(snapshot.child("player2").getValue(Boolean.class));
                if (p1 && p2) {
                    gameRef.child("ready").removeEventListener(this);
                    bothReadyCallback.run();
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError e) {}
        });
    }
}