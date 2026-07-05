package com.example.slagalica.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.slagalica.R;
import com.example.slagalica.models.DailyMissionsModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class DailyMissionsFragment extends Fragment {

    private CheckBox cbWonGame, cbSentMessage, cbPlayedFriendly, cbWonTournament;
    private Button btnClaimBonus, btnSimulateMission;

    private FirebaseFirestore db;
    private String currentUid;
    private ListenerRegistration missionsListener;
    private DailyMissionsModel currentMissionsState;

    public DailyMissionsFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_daily_missions, container, false);

        db = FirebaseFirestore.getInstance();
        currentUid = FirebaseAuth.getInstance().getCurrentUser() != null ?
                FirebaseAuth.getInstance().getCurrentUser().getUid() : "test_user_id";

        initViews(view);
        listenForDailyMissions();

        // Akcija za preuzimanje glavnog bonusa (Zahtev b)
        btnClaimBonus.setOnClickListener(v -> claimUltimateBonus());
        btnSimulateMission.setOnClickListener(v -> simulateColleagueAction());

        return view;
    }

    private void initViews(View v) {
        cbWonGame = v.findViewById(R.id.cbWonGame);
        cbSentMessage = v.findViewById(R.id.cbSentMessage);
        cbPlayedFriendly = v.findViewById(R.id.cbPlayedFriendly);
        cbWonTournament = v.findViewById(R.id.cbWonTournament);
        btnClaimBonus = v.findViewById(R.id.btnClaimBonus);
        btnSimulateMission = v.findViewById(R.id.btnSimulateMission);
    }

    private void listenForDailyMissions() {
        DocumentReference userRef = db.collection("users").document(currentUid);

        missionsListener = userRef.addSnapshotListener((snapshot, error) -> {
            if (error != null || snapshot == null || !snapshot.exists()) return;

            // Izvlačimo mapu iz dokumenta i pretvaramo je u naš objekat
            Map<String, Object> missionsMap = (Map<String, Object>) snapshot.get("dailyMissions");

            if (missionsMap == null) {
                // Ako polje uopšte ne postoji u bazi kod korisnika, kreiramo ga inicijalno
                resetMissionsInDatabase(userRef);
                return;
            }

            // Mapiranje ručno ili preko objekta
            currentMissionsState = snapshot.get("dailyMissions", DailyMissionsModel.class);

            if (currentMissionsState != null) {
                // 1. Provera da li je dan prošao (Reset logika)
                if (isNewDay(currentMissionsState.getLastResetTimestamp())) {
                    resetMissionsInDatabase(userRef);
                    return;
                }

                // 2. Ažuriranje CheckBox-ova na UI
                updateUI(currentMissionsState);
            }
        });
    }

    private void updateUI(DailyMissionsModel state) {
        cbWonGame.setChecked(state.isWonGame());
        cbSentMessage.setChecked(state.isSentChatMessage());
        cbPlayedFriendly.setChecked(state.isPlayedFriendly());
        cbWonTournament.setChecked(state.isWonTournamentGame());

        // Ako je rešio sve 4, a NIJE još pokupio nagradu, otključaj dugme
        if (state.areAllMissionsCompleted() && !state.isClaimedReward()) {
            btnClaimBonus.setEnabled(true);
            btnClaimBonus.setText("POKUPI GLAVNI BONUS!");
        } else if (state.isClaimedReward()) {
            btnClaimBonus.setEnabled(false);
            btnClaimBonus.setText("Bonus je već preuzet za danas!");
        } else {
            btnClaimBonus.setEnabled(false);
            btnClaimBonus.setText("Reši sve misije za glavni bonus");
        }
    }

    private boolean isNewDay(long lastResetTime) {
        Calendar today = Calendar.getInstance();
        Calendar lastReset = Calendar.getInstance();
        lastReset.setTimeInMillis(lastResetTime);

        return today.get(Calendar.DAY_OF_YEAR) != lastReset.get(Calendar.DAY_OF_YEAR)
                || today.get(Calendar.YEAR) != lastReset.get(Calendar.YEAR);
    }

    private void resetMissionsInDatabase(DocumentReference userRef) {
        Map<String, Object> initialMissions = new HashMap<>();
        initialMissions.put("wonGame", false);
        initialMissions.put("sentChatMessage", false);
        initialMissions.put("playedFriendly", false);
        initialMissions.put("wonTournamentGame", false);
        initialMissions.put("claimedReward", false);
        initialMissions.put("lastResetTimestamp", System.currentTimeMillis());

        userRef.update("dailyMissions", initialMissions);
    }

    private void claimUltimateBonus() {
        if (currentMissionsState == null) return;

        DocumentReference userRef = db.collection("users").document(currentUid);

        db.runTransaction(transaction -> {
            // Dodajemo +2 tokena i +3 dodatne zvezde direktno na korisnički račun
            transaction.update(userRef, "tokens", FieldValue.increment(2));
            transaction.update(userRef, "stars", FieldValue.increment(3));

            // Označavamo da je nagrada preuzeta za taj dan
            transaction.update(userRef, "dailyMissions.claimedReward", true);

            return null;
        }).addOnSuccessListener(aVoid -> {
            Toast.makeText(getContext(), "Uspešno preuzet bonus! Stigla su 2 tokena i 3 zvezde! 🎉", Toast.LENGTH_LONG).show();
            com.example.slagalica.services.LeagueManager.checkAndUpdateLeague(
                    currentUid, requireContext(), getChildFragmentManager());
        }).addOnFailureListener(e -> {
            Toast.makeText(getContext(), "Greška pri preuzimanju: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void simulateColleagueAction() {
        DocumentReference userRef = db.collection("users").document(currentUid);

        if (currentMissionsState == null) return;

        // Naizmenično rešavamo misije klikom na test dugme radi lakšeg testiranja
        String targetField;
        if (!currentMissionsState.isWonGame()) {
            targetField = "dailyMissions.wonGame";
        } else if (!currentMissionsState.isSentChatMessage()) {
            targetField = "dailyMissions.sentChatMessage";
        } else if (!currentMissionsState.isPlayedFriendly()) {
            targetField = "dailyMissions.playedFriendly";
        } else {
            targetField = "dailyMissions.wonTournamentGame";
        }

        db.runTransaction(transaction -> {
            // Postavi misiju na true
            transaction.update(userRef, targetField, true);
            // Pošto je uspešno rešio pojedinačnu misiju, odmah mu dajemo pripadajuće 3 zvezde (Zahtev b)
            transaction.update(userRef, "stars", FieldValue.increment(3));
            return null;
        }).addOnSuccessListener(aVoid -> {
            Toast.makeText(getContext(), "Simulacija uspešna! +3 zvezde upisane.", Toast.LENGTH_SHORT).show();
            com.example.slagalica.services.LeagueManager.checkAndUpdateLeague(
                    currentUid, requireContext(), getChildFragmentManager());
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (missionsListener != null) {
            missionsListener.remove();
        }
    }
}