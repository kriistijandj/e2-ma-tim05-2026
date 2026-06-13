package com.example.slagalica.repository;

import androidx.annotation.NonNull;

import com.example.slagalica.models.korak.KorakGameState;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class KorakRepository {

    private final DatabaseReference gameRef;
    private ValueEventListener listener;

    public interface GameStateCallback {
        void onStateChanged(KorakGameState state);
    }

    public KorakRepository(String gameId) {
        this.gameRef = FirebaseDatabase.getInstance()
                .getReference("games")
                .child(gameId)
                .child("korak");
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
}
