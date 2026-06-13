package com.example.slagalica.repository;

import com.example.slagalica.models.korak_po_korak.KorakPoKorakGameState;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class KorakPoKorakRepository {

    private final DatabaseReference gameRef;

    public KorakPoKorakRepository(String gameId) {

        gameRef =
                FirebaseDatabase
                        .getInstance()
                        .getReference("games")
                        .child(gameId)
                        .child("korakPoKorak");
    }

    // 1. UPDATE STATE (PIŠE U FIREBASE)
    public void updateGameState(KorakPoKorakGameState state) {
        gameRef.setValue(state);
    }

    // 2. LISTENER (ČITA PROMENE U REAL TIME)
    public void listenToGameState(GameStateCallback callback) {

        gameRef.addValueEventListener(new ValueEventListener() {

            @Override
            public void onDataChange(DataSnapshot snapshot) {

                KorakPoKorakGameState state =
                        snapshot.getValue(KorakPoKorakGameState.class);

                if (state != null) {
                    callback.onStateChanged(state);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    // 3. CALLBACK INTERFEJS
    public interface GameStateCallback {

        void onStateChanged(KorakPoKorakGameState state);

        void onError(String error);
    }
}