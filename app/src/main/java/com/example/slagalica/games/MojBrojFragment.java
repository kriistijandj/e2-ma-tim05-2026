package com.example.slagalica.games;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.example.slagalica.R;
import com.example.slagalica.helper.MatchPresenceHelper;
import com.example.slagalica.models.mojbroj.MojBrojGameState;
import com.example.slagalica.viewmodel.MojBrojViewModel;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import static android.content.Context.SENSOR_SERVICE;

import java.util.Arrays;
import java.util.List;

public class MojBrojFragment extends Fragment implements SensorEventListener {

    private TextView tvLeftName, tvRightName, tvLeftScore, tvRightScore;
    private TextView tvLeftNumber, tvRightNumber;
    private TextView tvTimer, tvTargetNumber, tvStatusMessage;
    private Button btnNum1, btnNum2, btnNum3, btnNum4, btnNum5, btnNum6;
    private Button btnStop, btnDelete, btnPlus, btnMinus, btnMul, btnDiv, btnOpen, btnClose;
    private EditText etExpression;

    private MojBrojViewModel viewModel;

    private String matchId;
    private String myRole;
    private boolean isTournament;
    private String tournamentId;
    private boolean isChallenge;
    private String challengeId;

    private ValueEventListener gameAdvanceListener;
    private boolean navigationScheduled = false;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private long lastShakeTime = 0;
    private static final float SHAKE_THRESHOLD = 12f;
    private static final int SHAKE_SLOP_TIME_MS = 500;

    private MatchPresenceHelper presenceHelper;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_moj_broj, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bindViews(view);
        setupKeyboard();
        setupSensor();

        viewModel = new ViewModelProvider(this).get(MojBrojViewModel.class);

        matchId = "fallback_id";
        myRole  = "player1";
        if (getArguments() != null) {
            matchId = getArguments().getString("MATCH_ID", matchId);
            myRole  = getArguments().getString("PLAYER_ROLE", myRole);
            isTournament = getArguments().getBoolean("IS_TOURNAMENT", false);
            tournamentId = getArguments().getString("TOURNAMENT_ID");
            isChallenge = getArguments().getBoolean("IS_CHALLENGE", false);
            challengeId = getArguments().getString("CHALLENGE_ID");
        }

        viewModel.init(matchId, myRole);
        //viewModel.signalReadyAndInit();

        viewModel.getTimerText().observe(getViewLifecycleOwner(),
                t -> tvTimer.setText(t));

        viewModel.getGameState().observe(getViewLifecycleOwner(),
                this::renderUiFromState);

        viewModel.getGameState().observe(getViewLifecycleOwner(), state -> {
            if (state != null && "finished".equals(state.status) && !navigationScheduled) {
                navigationScheduled = true;
                listenForNextGame();
            }
        });


        setupPresence();

        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                new androidx.activity.OnBackPressedCallback(true) {
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
    }

    private void setupPresence() {
        String myUid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
        presenceHelper = new com.example.slagalica.helper.MatchPresenceHelper(matchId, myUid);
        if (isChallenge && challengeId != null) presenceHelper.setChallengeContext(challengeId);
        presenceHelper.markPresent();

        FirebaseDatabase.getInstance()
                .getReference("matches").child(matchId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String p1 = snapshot.child("player1Id").getValue(String.class);
                        String p2 = snapshot.child("player2Id").getValue(String.class);
                        String opponentUid = "player1".equals(myRole) ? p2 : p1;

                        if (opponentUid != null && presenceHelper != null) {
                            presenceHelper.listenForOpponentLeft(opponentUid, () -> {
                                if (viewModel != null) viewModel.onOpponentLeft();

                                // SIGURNOST: Ako igra još nije inicijalizovana, a preuzeli smo host ulogu
                                if (viewModel != null) {
                                    viewModel.signalReadyAndInit();
                                }
                            });
                        }

                        // Pokrećemo inicijalizaciju tek nakon što su se postavili prisustvo i listeneri
                        if (viewModel != null) {
                            viewModel.signalReadyAndInit();
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


                if (game != null && game >= 2) {
                    ref.removeEventListener(this);

                    if (!isAdded() || getView() == null) return;

                    Bundle args = new Bundle();
                    args.putString("MATCH_ID", matchId);
                    args.putString("PLAYER_ROLE", myRole);
                    args.putBoolean("IS_TOURNAMENT", isTournament);
                    args.putString("TOURNAMENT_ID", tournamentId);
                    args.putBoolean("IS_CHALLENGE", isChallenge);
                    args.putString("CHALLENGE_ID", challengeId);

                    Navigation.findNavController(requireView())
                            .navigate(R.id.nav_game, args);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };

        ref.addValueEventListener(gameAdvanceListener);
    }



    private void renderUiFromState(MojBrojGameState state) {
        if (state == null) return;

        String myId = viewModel.getMyPlayerId();
        boolean iAmPlayer1   = "player1".equals(myId);
        boolean iAmStopPlayer = (state.stopPlayer == 1 && iAmPlayer1)
                || (state.stopPlayer == 2 && !iAmPlayer1);

        tvLeftName.setText("Igrač 1"  + (state.stopPlayer == 1 ? " ✎" : ""));
        tvRightName.setText("Igrač 2" + (state.stopPlayer == 2 ? " ✎" : ""));

        int displayScoreP1 = viewModel.getMatchStartingScoreP1() + state.p1Score;
        int displayScoreP2 = viewModel.getMatchStartingScoreP2() + state.p2Score;
        tvLeftScore.setText("Bodovi: "  + displayScoreP1);
        tvRightScore.setText("Bodovi: " + displayScoreP2);

        if (state.targetRevealed) {
            tvTargetNumber.setText("Traženi broj: " + state.targetNumber);
        } else {
            tvTargetNumber.setText("Traženi broj: ???");
        }

        if (state.numbersRevealed && state.availableNumbers != null
                && state.availableNumbers.size() == 6) {
            btnNum1.setText(String.valueOf(state.availableNumbers.get(0)));
            btnNum2.setText(String.valueOf(state.availableNumbers.get(1)));
            btnNum3.setText(String.valueOf(state.availableNumbers.get(2)));
            btnNum4.setText(String.valueOf(state.availableNumbers.get(3)));
            btnNum5.setText(String.valueOf(state.availableNumbers.get(4)));
            btnNum6.setText(String.valueOf(state.availableNumbers.get(5)));
        } else {
            btnNum1.setText("?"); btnNum2.setText("?");
            btnNum3.setText("?"); btnNum4.setText("?");
            btnNum5.setText("?"); btnNum6.setText("?");
        }

        boolean mySubmitted = iAmPlayer1 ? state.p1Submitted : state.p2Submitted;
        boolean bothSubmitted = state.p1Submitted && state.p2Submitted;

        boolean canInput = state.numbersRevealed && !mySubmitted
                && "active".equals(state.status);
        setInputEnabled(canInput);

        // STOP dugme logika
        if (!"active".equals(state.status)) {
            btnStop.setText("---");
            btnStop.setEnabled(false);
        } else if (!state.targetRevealed) {
            btnStop.setText("STOP (otkrij broj)");
            btnStop.setEnabled(iAmStopPlayer);
        } else if (!state.numbersRevealed) {
            btnStop.setText("STOP (otkrij brojeve)");
            btnStop.setEnabled(iAmStopPlayer);
        } else if (!mySubmitted) {
            btnStop.setText("Predaj izraz");
            btnStop.setEnabled(true);
        } else {
            btnStop.setText("Predato");
            btnStop.setEnabled(false);
        }

        if (bothSubmitted) {
            tvLeftNumber.setText(formatResult(state.p1Result));
            tvRightNumber.setText(formatResult(state.p2Result));
        } else {
            tvLeftNumber.setText(state.p1Submitted  ? formatResult(state.p1Result)  : "???");
            tvRightNumber.setText(state.p2Submitted ? formatResult(state.p2Result) : "???");
        }




        if (state.showingRoundResult) {
            tvStatusMessage.setText("Runda 1 završena!\nPrelazak na rundu 2...");
            tvStatusMessage.setVisibility(View.VISIBLE);
            setInputEnabled(false);
            btnStop.setEnabled(false);
            return;
        }


        if ("finished".equals(state.status)) {
            int finalScoreP1 = viewModel.getMatchStartingScoreP1() + state.p1Score;
            int finalScoreP2 = viewModel.getMatchStartingScoreP2() + state.p2Score;

            String winner;
            if (finalScoreP1 > finalScoreP2)      winner = "Pobedio Igrač 1!";
            else if (finalScoreP2 > finalScoreP1) winner = "Pobedio Igrač 2!";
            else                                   winner = "Nerešeno!";

            tvStatusMessage.setText("Igra završena! " + winner
                    + " (P1: " + finalScoreP1 + " – P2: " + finalScoreP2 + ")");
            tvStatusMessage.setVisibility(View.VISIBLE);
            setInputEnabled(false);
            btnStop.setEnabled(false);
            return;
        }


        if (mySubmitted && !bothSubmitted) {
            tvStatusMessage.setText("Čeka se protivnik...");
            tvStatusMessage.setVisibility(View.VISIBLE);
            return;
        }


        if (!state.numbersRevealed) {
            if (iAmStopPlayer) {
                tvStatusMessage.setText("Pritisni STOP da otkriješ "
                        + (!state.targetRevealed ? "traženi broj" : "dostupne brojeve") + "!");
            } else {
                tvStatusMessage.setText("Čeka se da protivnik stopira...");
            }
            tvStatusMessage.setVisibility(View.VISIBLE);
            return;
        }

        tvStatusMessage.setVisibility(View.GONE);
    }

    private String formatResult(int result) {
        if (result == -1 || result == Integer.MIN_VALUE) return "—";
        return String.valueOf(result);
    }

    private void setInputEnabled(boolean enabled) {
        etExpression.setEnabled(enabled);
        btnPlus.setEnabled(enabled);
        btnMinus.setEnabled(enabled);
        btnMul.setEnabled(enabled);
        btnDiv.setEnabled(enabled);
        btnOpen.setEnabled(enabled);
        btnClose.setEnabled(enabled);
        btnDelete.setEnabled(enabled);
        btnNum1.setEnabled(enabled);
        btnNum2.setEnabled(enabled);
        btnNum3.setEnabled(enabled);
        btnNum4.setEnabled(enabled);
        btnNum5.setEnabled(enabled);
        btnNum6.setEnabled(enabled);
    }



    private static class TokenEntry {
        final String text;
        final Button button; // null ako je operator (+, -, *, /, (, ))
        TokenEntry(String text, Button button) {
            this.text = text;
            this.button = button;
        }
    }

    private final List<TokenEntry> insertedTokens = new java.util.ArrayList<>();

    private void setupKeyboard() {
        List<Button> numButtons = Arrays.asList(
                btnNum1, btnNum2, btnNum3, btnNum4, btnNum5, btnNum6);

        View.OnClickListener insertOp = v -> {
            String text = ((Button) v).getText().toString();
            etExpression.append(text);
            insertedTokens.add(new TokenEntry(text, null));
        };
        btnPlus.setOnClickListener(insertOp);
        btnMinus.setOnClickListener(insertOp);
        btnMul.setOnClickListener(insertOp);
        btnDiv.setOnClickListener(insertOp);
        btnOpen.setOnClickListener(insertOp);
        btnClose.setOnClickListener(insertOp);

        for (Button btn : numButtons) {
            btn.setOnClickListener(v -> {
                String text = btn.getText().toString();
                etExpression.append(text);
                btn.setEnabled(false);
                btn.setAlpha(0.4f);
                insertedTokens.add(new TokenEntry(text, btn));
            });
        }

        btnDelete.setOnClickListener(v -> {
            if (insertedTokens.isEmpty()) return;

            TokenEntry last = insertedTokens.remove(insertedTokens.size() - 1);

            // Ukloni CEO taj token sa kraja teksta, ne samo jedan karakter
            String current = etExpression.getText().toString();
            if (current.endsWith(last.text)) {
                etExpression.setText(current.substring(0, current.length() - last.text.length()));
                etExpression.setSelection(etExpression.getText().length());
            }

            // Vrati samo TO dugme (ako je token bio broj), ne svu dugmad
            if (last.button != null) {
                last.button.setEnabled(true);
                last.button.setAlpha(1.0f);
            }
        });

        btnStop.setOnClickListener(v -> {
            MojBrojGameState state = viewModel.getGameState().getValue();
            if (state == null) return;

            if (!state.targetRevealed || !state.numbersRevealed) {
                viewModel.onStopPressed();
            } else {
                String expr = etExpression.getText().toString().trim();
                viewModel.submitExpression(expr);
                etExpression.setText("");
                insertedTokens.clear();
                for (Button btn : numButtons) {
                    btn.setEnabled(true);
                    btn.setAlpha(1.0f);
                }
            }
        });
    }

    private void bindViews(View view) {
        tvLeftName      = view.findViewById(R.id.tvLeftName);
        tvRightName     = view.findViewById(R.id.tvRightName);
        tvLeftScore     = view.findViewById(R.id.tvLeftScore);
        tvRightScore    = view.findViewById(R.id.tvRightScore);
        tvTimer         = view.findViewById(R.id.tvTimer);
        tvTargetNumber  = view.findViewById(R.id.tvTargetNumber2);
        etExpression    = view.findViewById(R.id.etExpression);
        btnStop         = view.findViewById(R.id.btnStop);
        btnDelete       = view.findViewById(R.id.btnDelete);
        btnPlus         = view.findViewById(R.id.btnPlus);
        btnMinus        = view.findViewById(R.id.btnMinus);
        btnMul          = view.findViewById(R.id.btnMul);
        btnDiv          = view.findViewById(R.id.btnDiv);
        btnOpen         = view.findViewById(R.id.btnOpen);
        btnClose        = view.findViewById(R.id.btnClose);
        btnNum1         = view.findViewById(R.id.btnNum1);
        btnNum2         = view.findViewById(R.id.btnNum2);
        btnNum3         = view.findViewById(R.id.btnNum3);
        btnNum4         = view.findViewById(R.id.btnNum4);
        btnNum5         = view.findViewById(R.id.btnNum5);
        btnNum6         = view.findViewById(R.id.btnNum6);
        tvLeftNumber    = view.findViewById(R.id.tvLeftNumber);
        tvRightNumber   = view.findViewById(R.id.tvRightNumber);
        tvStatusMessage = view.findViewById(R.id.tvStatusMessage);
    }



    private void setupSensor() {
        sensorManager = (SensorManager) requireActivity().getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer,
                    SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (gameAdvanceListener != null) {
            FirebaseDatabase.getInstance()
                    .getReference("matches")
                    .child(matchId)
                    .child("currentGame")
                    .removeEventListener(gameAdvanceListener);
            gameAdvanceListener = null;
        }
        if (presenceHelper != null) presenceHelper.detach();   // ← dodato
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;

        float x = event.values[0], y = event.values[1], z = event.values[2];
        float acceleration = (float) Math.sqrt(x * x + y * y + z * z)
                - SensorManager.GRAVITY_EARTH;

        if (acceleration > SHAKE_THRESHOLD) {
            long now = System.currentTimeMillis();
            if (now - lastShakeTime > SHAKE_SLOP_TIME_MS) {
                lastShakeTime = now;
                MojBrojGameState state = viewModel.getGameState().getValue();
                if (state != null && (!state.targetRevealed || !state.numbersRevealed)) {
                    viewModel.onStopPressed();
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}