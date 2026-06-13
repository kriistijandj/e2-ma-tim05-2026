package com.example.slagalica.repository;

import androidx.annotation.NonNull;

import com.example.slagalica.models.mojbroj.MojBrojGameState;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MojBrojRepository {

    private final DatabaseReference gameRef;
    private ValueEventListener listener;

    public interface GameStateCallback {
        void onStateChanged(MojBrojGameState state);
    }

    public MojBrojRepository(String gameId) {
        this.gameRef = FirebaseDatabase.getInstance()
                .getReference("games")
                .child(gameId)
                .child("mojbroj");
    }

    public void listenToGameState(GameStateCallback callback) {
        if (listener != null) {
            gameRef.removeEventListener(listener);
        }
        listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                MojBrojGameState state = snapshot.getValue(MojBrojGameState.class);
                if (state != null) {
                    callback.onStateChanged(state);
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        gameRef.addValueEventListener(listener);
    }

    public void updateGameState(MojBrojGameState state) {
        gameRef.setValue(state);
    }

    public void removeListener() {
        if (listener != null) {
            gameRef.removeEventListener(listener);
        }
    }
}
