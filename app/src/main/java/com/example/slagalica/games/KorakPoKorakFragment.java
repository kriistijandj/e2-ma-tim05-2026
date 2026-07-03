package com.example.slagalica.games;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.example.slagalica.R;
import com.example.slagalica.helper.MatchPresenceHelper;
import com.example.slagalica.models.korak.KorakGameState;
import com.example.slagalica.viewmodel.KorakViewModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.util.Arrays;
import java.util.List;

public class KorakPoKorakFragment extends Fragment {

    private TextView tvLeftName, tvRightName, tvLeftScore, tvRightScore;
    private TextView tvTimer, tvHintTitle, tvStatusMessage, tvSolution;

    private List<TextView> hintViews;

    private EditText etAnswer;
    private Button btnPotvrdi;

    private KorakViewModel viewModel;

    private String matchId;
    private String playerRole;
    private boolean isTournament;
    private String tournamentId;
    private boolean isChallenge;
    private String challengeId;

    private ValueEventListener gameAdvanceListener;

    private MatchPresenceHelper presenceHelper;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_korak_po_korak, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bindViews(view);

        if (getArguments() != null) {
            matchId = getArguments().getString("MATCH_ID");
            playerRole = getArguments().getString("PLAYER_ROLE");
            isTournament = getArguments().getBoolean("IS_TOURNAMENT", false);
            tournamentId = getArguments().getString("TOURNAMENT_ID");
            isChallenge = getArguments().getBoolean("IS_CHALLENGE", false);
            challengeId = getArguments().getString("CHALLENGE_ID");
        }

        if (matchId == null || playerRole == null) {
            Toast.makeText(getContext(), "Greška: nema MATCH podataka", Toast.LENGTH_SHORT).show();
            return;
        }

        viewModel = new ViewModelProvider(this).get(KorakViewModel.class);
        viewModel.init(matchId, playerRole);
        viewModel.signalReadyAndInit(isChallenge);

        viewModel.getTimerText().observe(getViewLifecycleOwner(),
                t -> tvTimer.setText(t));

        viewModel.getGameState().observe(getViewLifecycleOwner(),
                this::renderUi);

        viewModel.getGameState().observe(getViewLifecycleOwner(), state -> {
            if (state != null && "finished".equals(state.status)) {
                tvStatusMessage.setText("Igra završena!");
                tvStatusMessage.setVisibility(View.VISIBLE);

                if (gameAdvanceListener == null) {
                    listenForNextGame();
                }
            }
        });

        setupPresence();

        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                .setTitle("Napusti partiju")
                                .setMessage("Ako izađeš, gubiš partiju i ne dobijaš zvezde. Nastaviti?")
                                .setPositiveButton("Napusti", (d, w) -> {
                                    if (presenceHelper != null) presenceHelper.leaveMatch();
                                    Navigation.findNavController(requireView())
                                            .navigate(R.id.nav_home);
                                })
                                .setNegativeButton("Otkaži", null)
                                .show();
                    }
                }
        );

        btnPotvrdi.setOnClickListener(v -> {
            String guess = etAnswer.getText().toString().trim();

            if (guess.isEmpty()) {
                Toast.makeText(getContext(), "Unesi odgovor!", Toast.LENGTH_SHORT).show();
                return;
            }

            boolean correct = viewModel.submitGuess(guess);

            Toast.makeText(getContext(),
                    correct ? "Tačno!" : "Netačno!",
                    Toast.LENGTH_SHORT).show();

            etAnswer.setText("");
        });
    }

    private void setupPresence() {
        String myUid = FirebaseAuth.getInstance().getUid();
        presenceHelper = new com.example.slagalica.helper.MatchPresenceHelper(matchId, myUid);
        if (isChallenge && challengeId != null) presenceHelper.setChallengeContext(challengeId);
        presenceHelper.markPresent();

        // Pročitaj player1Id/player2Id direktno iz meča (ne iz KorakGameState)
        FirebaseDatabase.getInstance()
                .getReference("matches").child(matchId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String p1 = snapshot.child("player1Id").getValue(String.class);
                        String p2 = snapshot.child("player2Id").getValue(String.class);
                        String opponentUid = "player1".equals(playerRole) ? p2 : p1;

                        if (opponentUid != null && presenceHelper != null) {
                            presenceHelper.listenForOpponentLeft(opponentUid, () -> {
                                if (viewModel != null) viewModel.onOpponentLeft();
                            });
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void listenForNextGame() {
        DatabaseReference ref = FirebaseDatabase.getInstance()
                .getReference("matches")
                .child(matchId)
                .child("currentGame");

        gameAdvanceListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Integer game = snapshot.getValue(Integer.class);

                if (game != null && game == 1) {
                    ref.removeEventListener(this);

                    if (!isAdded() || getView() == null) return;

                    Bundle args = new Bundle();
                    args.putString("MATCH_ID", matchId);
                    args.putString("PLAYER_ROLE", playerRole);
                    args.putBoolean("IS_TOURNAMENT", isTournament);
                    args.putString("TOURNAMENT_ID", tournamentId);
                    args.putBoolean("IS_CHALLENGE", isChallenge);
                    args.putString("CHALLENGE_ID", challengeId);

                    Navigation.findNavController(requireView())
                            .navigate(R.id.nav_mojbroj, args);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };

        ref.addValueEventListener(gameAdvanceListener);
    }

    private void renderUi(KorakGameState state) {
        if (state == null) return;


        if (state.hints == null || state.hints.isEmpty()) return;

        String uid = FirebaseAuth.getInstance().getUid();


        int myScore = (state.scores != null && state.scores.get(uid) != null)
                ? state.scores.get(uid)
                : 0;

        String opponentUid = null;
        if (state.scores != null) {
            for (String key : state.scores.keySet()) {
                if (!key.equals(uid)) {
                    opponentUid = key;
                    break;
                }
            }
        }

        int opponentScore = (opponentUid != null && state.scores != null && state.scores.get(opponentUid) != null)
                ? state.scores.get(opponentUid)
                : 0;

        if ("player1".equals(playerRole)) {
            tvLeftScore.setText("Bodovi: " + myScore);
            tvRightScore.setText("Bodovi: " + opponentScore);
        } else {
            tvLeftScore.setText("Bodovi: " + opponentScore);
            tvRightScore.setText("Bodovi: " + myScore);
        }


        boolean iAmPlayer1 = "player1".equals(playerRole);
        boolean iAmActive = (state.activePlayer == 1 && iAmPlayer1)
                || (state.activePlayer == 2 && !iAmPlayer1);

        tvLeftName.setText("Igrač 1" + (state.activePlayer == 1 ? " ✎" : ""));
        tvRightName.setText("Igrač 2" + (state.activePlayer == 2 ? " ✎" : ""));


        tvHintTitle.setText("Korak " + state.revealedHints + "/7");

        for (int i = 0; i < hintViews.size(); i++) {
            TextView tv = hintViews.get(i);

            if (i < state.revealedHints && i < state.hints.size()) {
                tv.setText(state.hints.get(i));
                tv.setVisibility(View.VISIBLE);
            } else {
                tv.setVisibility(View.INVISIBLE);
            }
        }


        if (state.revealedAnswer != null && !state.revealedAnswer.isEmpty()) {
            tvSolution.setText("Rešenje: " + state.revealedAnswer);
            tvSolution.setVisibility(View.VISIBLE);
        } else {
            tvSolution.setVisibility(View.GONE);
        }


        boolean canAnswer =
                iAmActive &&
                        "active".equals(state.status) &&
                        (state.revealedAnswer == null || state.revealedAnswer.isEmpty());

        etAnswer.setEnabled(canAnswer);
        btnPotvrdi.setEnabled(canAnswer);


        if ("finished".equals(state.status)) {

            int p1 = (state.scores != null && state.player1Id != null && state.scores.get(state.player1Id) != null)
                    ? state.scores.get(state.player1Id)
                    : 0;

            int p2 = (state.scores != null && state.player2Id != null && state.scores.get(state.player2Id) != null)
                    ? state.scores.get(state.player2Id)
                    : 0;

            String winner;
            if (p1 > p2) winner = "Pobedio Igrač 1";
            else if (p2 > p1) winner = "Pobedio Igrač 2";
            else winner = "Nerešeno";

            tvStatusMessage.setText(
                    "Igra završena! " + winner + " (" + p1 + " - " + p2 + ")"
            );
            tvStatusMessage.setVisibility(View.VISIBLE);

        } else if (state.isOpponentChance && iAmActive) {

            tvStatusMessage.setText("Protivnik nije pogodio – tvoja šansa!");
            tvStatusMessage.setVisibility(View.VISIBLE);

        } else if (!iAmActive && "active".equals(state.status)) {

            tvStatusMessage.setText("Protivnik igra...");
            tvStatusMessage.setVisibility(View.VISIBLE);

        } else {
            tvStatusMessage.setVisibility(View.GONE);
        }
    }

    private void bindViews(View view) {

        tvLeftName = view.findViewById(R.id.tvLeftName);
        tvRightName = view.findViewById(R.id.tvRightName);

        tvLeftScore = view.findViewById(R.id.tvLeftScore);
        tvRightScore = view.findViewById(R.id.tvRightScore);

        tvTimer = view.findViewById(R.id.tvTimer);
        tvHintTitle = view.findViewById(R.id.tvHintTitle);

        tvStatusMessage = view.findViewById(R.id.tvStatusMessage);
        tvSolution = view.findViewById(R.id.tvSolution);

        etAnswer = view.findViewById(R.id.etAnswer);
        btnPotvrdi = view.findViewById(R.id.btnStop);

        hintViews = Arrays.asList(
                view.findViewById(R.id.tvHint1),
                view.findViewById(R.id.tvHint2),
                view.findViewById(R.id.tvHint3),
                view.findViewById(R.id.tvHint4),
                view.findViewById(R.id.tvHint5),
                view.findViewById(R.id.tvHint6),
                view.findViewById(R.id.tvHint7)
        );
    }
}