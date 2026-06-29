package com.example.slagalica.fragments;

import android.app.AlertDialog;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import com.example.slagalica.R;
import com.example.slagalica.repository.MatchmakingRepository;

public class HomeFragment extends Fragment {


    public HomeFragment() { }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        // Povezivanje dugmeta za navigaciju ka GameFragment (izboru igara)
        Button btnStartGame = view.findViewById(R.id.btnStartGame);

        btnStartGame.setOnClickListener(v -> {
            // Prikaži loading/čekanje UI
            showWaitingDialog();

            matchmakingRepo = new MatchmakingRepository();

            matchmakingRepo.startMatchmaking(new MatchmakingRepository.OnMatchFoundListener() {
                @Override
                public void onMatchFound(String matchId, String role) {
                    dismissWaitingDialog();

                    if (!isAdded() || getView() == null) return;

                    Bundle args = new Bundle();
                    args.putString("MATCH_ID", matchId);
                    args.putString("PLAYER_ROLE", role);
                    Navigation.findNavController(requireView()).navigate(R.id.nav_game, args);
                }

                @Override
                public void onWaitingForOpponent() {
                    // Prikaži "Tražim protivnika..." sa X dugmetom
                }

                @Override
                public void onNoTokens() {
                    dismissWaitingDialog();

                    if (!isAdded() || getContext() == null) return;

                    Toast.makeText(getContext(), "Nemaš tokena!", Toast.LENGTH_SHORT).show();
                }
            });
        });


        androidx.cardview.widget.CardView rangList = view.findViewById(R.id.cardRankings);

        rangList.setOnClickListener(v -> {
            if (isAdded() && getView() != null) {
                Navigation.findNavController(view).navigate(R.id.action_home_to_leaderboard);
            }
        });

        return view;
    }

    private AlertDialog waitingDialog;
    private MatchmakingRepository matchmakingRepo;

    private void showWaitingDialog() {
        waitingDialog = new AlertDialog.Builder(getContext())
                .setTitle("Tražim protivnika...")
                .setMessage("Molimo sačekajte")
                .setNegativeButton("Otkaži", (d, w) -> {
                    matchmakingRepo.cancelMatchmaking();
                    d.dismiss();
                })
                .setCancelable(false)
                .show();
    }

    private void dismissWaitingDialog() {
        if (waitingDialog != null && waitingDialog.isShowing()) {
            waitingDialog.dismiss();
        }
    }
}