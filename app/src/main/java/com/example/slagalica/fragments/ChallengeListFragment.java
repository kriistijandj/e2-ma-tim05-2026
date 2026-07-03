package com.example.slagalica.fragments;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.adapters.ChallengeAdapter;
import com.example.slagalica.models.Challenge;
import com.example.slagalica.repository.ChallengeRepository;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class ChallengeListFragment extends Fragment {

    private final ChallengeRepository repository = new ChallengeRepository();
    private ChallengeAdapter adapter;
    private final List<Challenge> challenges = new ArrayList<>();
    private ListenerRegistration listenerRegistration;
    private TextView tvNoChallenges;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_challenge_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvNoChallenges = view.findViewById(R.id.tvNoChallenges);

        RecyclerView rv = view.findViewById(R.id.rvChallenges);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ChallengeAdapter(challenges, this::onJoinClicked);
        rv.setAdapter(adapter);

        view.findViewById(R.id.btnPostaviIzazov).setOnClickListener(v -> showStakeDialog());

        listenOpenChallenges();
    }

    private void listenOpenChallenges() {
        Query query = FirebaseFirestore.getInstance()
                .collection("challenges")
                .whereEqualTo("status", "open")
                .orderBy("createdAt", Query.Direction.DESCENDING);

        listenerRegistration = query.addSnapshotListener((snapshots, error) -> {
            if (error != null || snapshots == null || !isAdded()) return;

            List<Challenge> parsed = new ArrayList<>();
            snapshots.forEach(doc -> {
                Challenge c = Challenge.fromSnapshot(doc);
                if (c.participants.size() < 4) parsed.add(c);
            });

            adapter.setData(parsed);
            if (tvNoChallenges != null) {
                tvNoChallenges.setVisibility(parsed.isEmpty() ? View.VISIBLE : View.GONE);
            }
        });
    }

    private void showStakeDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_challenge_stake, null);
        EditText etStars = dialogView.findViewById(R.id.etStakeStars);
        EditText etTokens = dialogView.findViewById(R.id.etStakeTokens);

        new AlertDialog.Builder(requireContext())
                .setTitle("Postavi izazov")
                .setView(dialogView)
                .setPositiveButton("Postavi", (dialog, which) -> {
                    long stars = parseClamped(etStars.getText().toString(), 0, 10);
                    long tokens = parseClamped(etTokens.getText().toString(), 0, 2);
                    createChallenge(stars, tokens);
                })
                .setNegativeButton("Otkaži", null)
                .show();
    }

    private long parseClamped(String text, long min, long max) {
        long value;
        try {
            value = Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            value = 0;
        }
        return Math.max(min, Math.min(max, value));
    }

    private void createChallenge(long stars, long tokens) {
        repository.createChallenge(stars, tokens, new ChallengeRepository.OnChallengeCreateListener() {
            @Override
            public void onCreated(String challengeId) {
                if (getContext() == null) return;
                Toast.makeText(getContext(), "Izazov je postavljen!", Toast.LENGTH_SHORT).show();
                navigateToWaitingRoom(challengeId);
            }

            @Override
            public void onInsufficientFunds() {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Nemate dovoljno zvezda/tokena za ovo ulaganje.",
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(String message) {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Greška: " + message, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void onJoinClicked(Challenge challenge) {
        repository.joinChallenge(challenge.challengeId, new ChallengeRepository.OnChallengeActionListener() {
            @Override
            public void onSuccess() {
                if (getContext() == null) return;
                Toast.makeText(getContext(), "Pridružili ste se izazovu!", Toast.LENGTH_SHORT).show();
                navigateToWaitingRoom(challenge.challengeId);
            }

            @Override
            public void onFailure(String message) {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Greška: " + message, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void navigateToWaitingRoom(String challengeId) {
        if (getView() == null) return;
        Bundle args = new Bundle();
        args.putString("CHALLENGE_ID", challengeId);
        Navigation.findNavController(getView()).navigate(R.id.nav_challenge_waiting_room, args);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (listenerRegistration != null) listenerRegistration.remove();
    }
}
