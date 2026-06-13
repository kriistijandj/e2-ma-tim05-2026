package com.example.slagalica.repository;

import androidx.annotation.NonNull;
import com.example.slagalica.models.skocko.SkockoGameState;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class SkockoRepository {

    private final DatabaseReference gameRef;
    private ValueEventListener gameStateListener;

    public interface GameStateCallback {
        void onStateChanged(SkockoGameState state);
    }

    public SkockoRepository(String gameId) {
        this.gameRef = FirebaseDatabase.getInstance().getReference("games").child(gameId).child("skocko");
    }

    public void listenToGameState(GameStateCallback callback) {
        if (gameStateListener != null) {
            gameRef.removeEventListener(gameStateListener);
        }

        gameStateListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                SkockoGameState state = snapshot.getValue(SkockoGameState.class);
                if (state != null) {
                    callback.onStateChanged(state);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        gameRef.addValueEventListener(gameStateListener);
    }

    public void updateGameState(SkockoGameState state) {
        gameRef.setValue(state);
    }

    public void removeListener() {
        if (gameStateListener != null) {
            gameRef.removeEventListener(gameStateListener);
        }
    }
}