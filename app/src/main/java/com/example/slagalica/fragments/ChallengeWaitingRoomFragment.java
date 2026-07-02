package com.example.slagalica.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.slagalica.R;
import com.example.slagalica.models.Challenge;
import com.example.slagalica.repository.ChallengeRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;
import java.util.Map;

public class ChallengeWaitingRoomFragment extends Fragment {

    private static final int[] AVATAR_RES = {
            android.R.drawable.ic_menu_myplaces,
            android.R.drawable.ic_menu_compass,
            android.R.drawable.ic_menu_gallery,
            android.R.drawable.ic_menu_camera,
            android.R.drawable.ic_menu_manage,
            android.R.drawable.ic_menu_help
    };

    private String challengeId;
    private String myUid;
    private final ChallengeRepository repository = new ChallengeRepository();
    private ListenerRegistration challengeListener;

    private TextView tvStake, tvStatus;
    private LinearLayout llParticipants;
    private Button btnStart, btnLeave;

    private boolean navigated = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_challenge_waiting_room, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        if (getArguments() != null) {
            challengeId = getArguments().getString("CHALLENGE_ID");
        }

        tvStake = view.findViewById(R.id.tvWaitingStake);
        tvStatus = view.findViewById(R.id.tvWaitingStatus);
        llParticipants = view.findViewById(R.id.llParticipants);
        btnStart = view.findViewById(R.id.btnStartChallenge);
        btnLeave = view.findViewById(R.id.btnLeaveChallenge);

        btnStart.setOnClickListener(v -> repository.startChallengeManually(challengeId,
                new ChallengeRepository.OnChallengeActionListener() {
                    @Override public void onSuccess() {}
                    @Override public void onFailure(String message) {
                        if (getContext() != null) {
                            Toast.makeText(getContext(), "Greška: " + message, Toast.LENGTH_LONG).show();
                        }
                    }
                }));

        btnLeave.setOnClickListener(v -> repository.cancelJoin(challengeId,
                new ChallengeRepository.OnChallengeActionListener() {
                    @Override
                    public void onSuccess() {
                        if (getView() != null) {
                            Navigation.findNavController(getView()).navigateUp();
                        }
                    }
                    @Override public void onFailure(String message) {
                        if (getContext() != null) {
                            Toast.makeText(getContext(), "Greška: " + message, Toast.LENGTH_LONG).show();
                        }
                    }
                }));

        listenToChallenge();
    }

    private void listenToChallenge() {
        if (challengeId == null) return;

        challengeListener = FirebaseFirestore.getInstance()
                .collection("challenges").document(challengeId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null || !snapshot.exists() || !isAdded()) return;
                    renderChallenge(snapshot);
                });
    }

    @SuppressWarnings("unchecked")
    private void renderChallenge(DocumentSnapshot snapshot) {
        Challenge challenge = Challenge.fromSnapshot(snapshot);

        tvStake.setText("Ulog: ⭐ " + challenge.stakeStars + "   🎟 " + challenge.stakeTokens);

        if ("open".equals(challenge.status)) {
            tvStatus.setText("Čeka se pokretanje (" + challenge.participants.size() + "/4 učesnika)...");
            renderParticipants(challenge.participants);

            boolean isCreator = myUid.equals(challenge.creatorId);
            btnStart.setVisibility(isCreator && challenge.participants.size() >= 2 ? View.VISIBLE : View.GONE);
            btnLeave.setVisibility(View.VISIBLE);

            // Pasivna provera: ako je u međuvremenu stiglo 4 učesnika, pokušaj auto-start
            // (tiho ne radi ništa ako uslov nije ispunjen ili je neko drugi već pokrenuo).
            repository.autoStartIfReady(challengeId);

        } else if ("starting".equals(challenge.status)) {
            tvStatus.setText("Pokretanje izazova u toku...");
            btnStart.setVisibility(View.GONE);
            btnLeave.setVisibility(View.GONE);

        } else if ("in_progress".equals(challenge.status)) {
            tvStatus.setText("Izazov je počeo!");
            btnStart.setVisibility(View.GONE);
            btnLeave.setVisibility(View.GONE);
            navigateToMyMatch(snapshot);

        } else if ("finished".equals(challenge.status)) {
            tvStatus.setText("Izazov je završen.");
            navigateToResult();
        }
    }

    private void renderParticipants(List<String> participants) {
        llParticipants.removeAllViews();
        for (String uid : participants) {
            View row = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_challenge_participant, llParticipants, false);
            ImageView ivAvatar = row.findViewById(R.id.ivParticipantAvatar);
            TextView tvUsername = row.findViewById(R.id.tvParticipantUsername);

            FirebaseFirestore.getInstance().collection("users").document(uid).get()
                    .addOnSuccessListener(doc -> {
                        if (!doc.exists() || !isAdded()) return;
                        String username = doc.getString("username");
                        tvUsername.setText(username != null ? username : uid);

                        Long avatarId = doc.getLong("avatarId");
                        if (avatarId != null) {
                            int idx = avatarId.intValue();
                            if (idx >= 0 && idx < AVATAR_RES.length) {
                                ivAvatar.setImageResource(AVATAR_RES[idx]);
                            }
                        }
                    });

            llParticipants.addView(row);
        }
    }

    @SuppressWarnings("unchecked")
    private void navigateToMyMatch(DocumentSnapshot snapshot) {
        if (navigated || getView() == null) return;

        Map<String, Object> matchIds = (Map<String, Object>) snapshot.get("matchIds");
        if (matchIds == null) return;
        Object matchIdObj = matchIds.get(myUid);
        if (!(matchIdObj instanceof String)) return;

        navigated = true;

        Bundle args = new Bundle();
        args.putString("MATCH_ID", (String) matchIdObj);
        args.putString("PLAYER_ROLE", "player1");
        args.putBoolean("IS_CHALLENGE", true);
        args.putString("CHALLENGE_ID", challengeId);

        Navigation.findNavController(getView()).navigate(R.id.nav_game, args);
    }

    private void navigateToResult() {
        if (navigated || getView() == null) return;
        navigated = true;

        Bundle args = new Bundle();
        args.putString("CHALLENGE_ID", challengeId);
        Navigation.findNavController(getView()).navigate(R.id.nav_challenge_result, args);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (challengeListener != null) challengeListener.remove();
    }
}
