package com.example.slagalica.repository;

import androidx.annotation.NonNull;

import com.example.slagalica.models.mojbroj.MojBrojGameState;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class MojBrojRepository {

    private final DatabaseReference gameRef;
    private ValueEventListener listener;
    private final String gameId;

    public interface GameStateCallback {
        void onStateChanged(MojBrojGameState state);
    }

    public MojBrojRepository(String gameId) {
        this.gameId = gameId;
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
        if (state == null) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("round", state.round);
        updates.put("status", state.status);
        updates.put("stopPlayer", state.stopPlayer);
        updates.put("targetNumber", state.targetNumber);
        updates.put("targetRevealed", state.targetRevealed);
        updates.put("numbersRevealed", state.numbersRevealed);
        updates.put("showingRoundResult", state.showingRoundResult);


        updates.put("p1Score", state.p1Score);
        updates.put("p2Score", state.p2Score);


        updates.put("p1Submitted", state.p1Submitted);
        updates.put("p2Submitted", state.p2Submitted);


        updates.put("p1Result", state.p1Result);
        updates.put("p2Result", state.p2Result);
        updates.put("p1Expression", state.p1Expression);
        updates.put("p2Expression", state.p2Expression);


        if (state.availableNumbers != null) {
            updates.put("availableNumbers", state.availableNumbers);
        }


        gameRef.updateChildren(updates);
    }

    public void removeListener() {
        if (listener != null) {
            gameRef.removeEventListener(listener);
        }
    }

    public void setReady(String role, Runnable bothReadyCallback) {

        gameRef.child("ready").child(role).setValue(true);
        gameRef.child("ready").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean p1 = snapshot.child("player1").getValue(Boolean.class);
                Boolean p2 = snapshot.child("player2").getValue(Boolean.class);

                if (p1 != null && p2 != null && p1 && p2) {
                    gameRef.child("ready").removeEventListener(this);
                    bothReadyCallback.run();
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });
    }
}