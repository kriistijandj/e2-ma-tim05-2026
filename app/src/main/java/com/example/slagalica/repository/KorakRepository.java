package com.example.slagalica.repository;

import androidx.annotation.NonNull;

import com.example.slagalica.models.korak.KorakGameState;
import com.google.firebase.database.*;

public class KorakRepository {

    private final DatabaseReference gameRef;
    private ValueEventListener listener;

    public interface GameStateCallback {
        void onStateChanged(KorakGameState state);
    }

    public KorakRepository(String matchId) {
        this.gameRef = FirebaseDatabase.getInstance()
                .getReference("games")
                .child(matchId)
                .child("korak");
    }

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
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });
    }

    public void listenToGameState(GameStateCallback callback) {

        if (listener != null) {
            gameRef.removeEventListener(listener);
        }

        listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                KorakGameState state = snapshot.getValue(KorakGameState.class);
                if (state != null) {
                    callback.onStateChanged(state);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };

        gameRef.addValueEventListener(listener);
    }

    public void updateGameState(KorakGameState state) {
        gameRef.setValue(state);
    }

    public void removeListener() {
        if (listener != null) {
            gameRef.removeEventListener(listener);
        }
    }

    public void setReadySolo(Runnable readyCallback) {
        gameRef.child("ready").child("player1").setValue(true);
        gameRef.child("ready").child("player2").setValue(true);
        readyCallback.run();
    }
}