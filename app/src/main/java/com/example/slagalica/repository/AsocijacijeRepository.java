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

    public AsocijacijeRepository(String gameId) {
        this.gameRef = FirebaseDatabase.getInstance().getReference("games").child(gameId).child("asocijacije");
    }

    public void listenToGameState(AsocijacijeCallback callback) {
        if (gameStateListener != null) {
            gameRef.removeEventListener(gameStateListener);
        }

        gameStateListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                AsocijacijeGameState state = snapshot.getValue(AsocijacijeGameState.class);
                if (state != null) {
                    callback.onStateChanged(state);
                }
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
        }
    }
}