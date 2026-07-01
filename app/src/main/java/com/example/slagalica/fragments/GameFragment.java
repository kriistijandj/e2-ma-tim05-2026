package com.example.slagalica.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.example.slagalica.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class GameFragment extends Fragment {

    private DatabaseReference db;

    private String matchId;
    private String playerRole;
    private boolean isTournament;
    private String tournamentId;
    private boolean isChallenge;
    private String challengeId;

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState) {

        View view = inflater.inflate(
                R.layout.fragment_game,
                container,
                false);

        db = FirebaseDatabase.getInstance().getReference();

        if (getArguments() != null) {
            matchId = getArguments().getString("MATCH_ID");
            playerRole = getArguments().getString("PLAYER_ROLE");
            isTournament = getArguments().getBoolean("IS_TOURNAMENT", false);
            tournamentId = getArguments().getString("TOURNAMENT_ID");
            isChallenge = getArguments().getBoolean("IS_CHALLENGE", false);
            challengeId = getArguments().getString("CHALLENGE_ID");
        }

        loadCurrentGame();

        return view;
    }

    private void loadCurrentGame() {

        db.child("matches")
                .child(matchId)
                .child("currentGame")
                .addListenerForSingleValueEvent(new ValueEventListener() {

                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {

                        Integer idx = snapshot.getValue(Integer.class);

                        if (idx == null) {
                            idx = 0;
                        }

                        navigateToGame(idx);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                    }
                });
    }

    private void navigateToGame(int idx) {

        if (!isAdded() || getView() == null) {
            return;
        }

        Bundle args = new Bundle();
        args.putString("MATCH_ID", matchId);
        args.putString("PLAYER_ROLE", playerRole);
        args.putBoolean("IS_TOURNAMENT", isTournament);
        args.putString("TOURNAMENT_ID", tournamentId);
        args.putBoolean("IS_CHALLENGE", isChallenge);
        args.putString("CHALLENGE_ID", challengeId);

        NavController nav =
                Navigation.findNavController(requireView());

        switch (idx) {

            case 0:
                nav.navigate(R.id.nav_korak, args);
                break;

            case 1:
                nav.navigate(R.id.nav_mojbroj, args);
                break;

            case 2:
                nav.navigate(R.id.nav_gameAsocijacije, args);
                break;

            case 3:
                nav.navigate(R.id.nav_gameSkocko, args);
                break;

            case 4:
                nav.navigate(R.id.nav_gameKoZnaZna, args);
                break;

            case 5:
                nav.navigate(R.id.nav_gameSpojnice, args);
                break;

            default:
                Toast.makeText(
                        getContext(),
                        "Partija završena",
                        Toast.LENGTH_SHORT
                ).show();
        }
    }
}