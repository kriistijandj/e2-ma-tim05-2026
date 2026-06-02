package com.example.slagalica.fragments;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.slagalica.R;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class SpojniceFragment extends Fragment {

    // ====== UI ======
    private TextView tvRound, tvTimer, tvKriterijum, tvStatus;
    private TextView tvScorePlayer1, tvScorePlayer2;
    private MaterialButton[] leftButtons  = new MaterialButton[5];
    private MaterialButton[] rightButtons = new MaterialButton[5];

    // ====== BOJE ======
    private static final int COLOR_DEFAULT   = Color.parseColor("#F2AF14");
    private static final int COLOR_SELECTED  = Color.parseColor("#3A7BFF");
    private static final int COLOR_CONNECTED = Color.parseColor("#4CAF50");
    private static final int COLOR_WAITING   = Color.parseColor("#90A4AE");
    private static final int COLOR_WRONG     = Color.parseColor("#D32F2F");

    // ====== PODACI ======
    private static final String[][] LEFT_TERMS = {
            {"Riblja čorba", "Bajaga", "EKV", "Đ. Balašević", "Generacija 5"},
            {"Einstein",     "Tesla",  "Curie", "Newton",      "Darwin"}
    };
    private static final String[][] RIGHT_TERMS = {
            {"Kao dva sveta", "Tri put sam video Tita", "Ona spava", "Šta ima novo", "Nebo"},
            {"Relativnost",   "Struja",                  "Radijum",   "Gravitacija",  "Evolucija"}
    };
    private static final String[] KRITERIJUMI = {
            "Poveži izvođače sa njihovim pesmama",
            "Poveži naučnike sa njihovim otkrićima"
    };
    private static final int[] CORRECT_MAPPING = {0, 1, 2, 3, 4};

    // ====== FAZE ======
    private static final int PHASE_ACTIVE = 0;
    private static final int PHASE_FIXING = 1;
    private static final int PHASE_DONE   = 2;

    // ====== FIREBASE ======
    private DatabaseReference gameRef;
    private String gameId;
    private String myPlayerId;
    private ValueEventListener connectionsListener;
    private ValueEventListener phaseListener;

    // ====== STANJE ======
    private int currentRound  = 0;
    private int currentPhase  = PHASE_ACTIVE;
    private int scorePlayer1  = 0;
    private int scorePlayer2  = 0;

    private int selectedLeftIndex  = -1;
    private int selectedRightIndex = -1;

    private boolean[] leftUsed  = new boolean[5];
    private boolean[] rightUsed = new boolean[5];

    private CountDownTimer roundTimer;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public SpojniceFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_spojnice, container, false);

        tvRound        = view.findViewById(R.id.tvRound);
        tvTimer        = view.findViewById(R.id.tvTimer);
        tvKriterijum   = view.findViewById(R.id.tvKriterijum);
        tvStatus       = view.findViewById(R.id.tvStatus);
        tvScorePlayer1 = view.findViewById(R.id.tvLeftScore);
        tvScorePlayer2 = view.findViewById(R.id.tvRightScore);

        leftButtons[0]  = view.findViewById(R.id.btnLeft1);
        leftButtons[1]  = view.findViewById(R.id.btnLeft2);
        leftButtons[2]  = view.findViewById(R.id.btnLeft3);
        leftButtons[3]  = view.findViewById(R.id.btnLeft4);
        leftButtons[4]  = view.findViewById(R.id.btnLeft5);

        rightButtons[0] = view.findViewById(R.id.btnRight1);
        rightButtons[1] = view.findViewById(R.id.btnRight2);
        rightButtons[2] = view.findViewById(R.id.btnRight3);
        rightButtons[3] = view.findViewById(R.id.btnRight4);
        rightButtons[4] = view.findViewById(R.id.btnRight5);

        // TODO: zamijeniti sa pravim matchmakingom
        gameId     = "test_game_spojnice_001";
        myPlayerId = "player1"; // na drugom emulatoru promijeni u "player2"

        gameRef = FirebaseDatabase.getInstance().getReference("games").child(gameId);

        setupClickListeners();
        startRound(0);

        return view;
    }

    // ==============================
    // POKRETANJE RUNDE
    // Runda 0: player1 igra aktivan, player2 fixing
    // Runda 1: player2 igra aktivan, player1 fixing
    // ==============================

    private void startRound(int round) {
        currentRound  = round;
        currentPhase  = PHASE_ACTIVE;
        selectedLeftIndex  = -1;
        selectedRightIndex = -1;
        handler.removeCallbacksAndMessages(null);

        for (int i = 0; i < 5; i++) {
            leftUsed[i]  = false;
            rightUsed[i] = false;
        }

        // Player1 resetuje Firebase stanje za rundu
        if ("player1".equals(myPlayerId)) {
            gameRef.child("rounds").child(String.valueOf(round))
                    .child("connections").removeValue();
            gameRef.child("rounds").child(String.valueOf(round))
                    .child("phase").setValue("active");
        }

        tvRound.setText("Runda " + (round + 1) + " / 2");
        tvKriterijum.setText(KRITERIJUMI[round]);

        for (int i = 0; i < 5; i++) {
            leftButtons[i].setText(LEFT_TERMS[round][i]);
            rightButtons[i].setText(RIGHT_TERMS[round][i]);
            setButtonTint(leftButtons[i],  COLOR_DEFAULT);
            setButtonTint(rightButtons[i], COLOR_DEFAULT);
        }

        boolean iAmActive = isMyTurnActive(round);

        if (iAmActive) {
            tvStatus.setText("Ti igraš — 30 sekundi!");
            setAllButtonsEnabled(true);
        } else {
            tvStatus.setText("Protivnik igra, čekaj...");
            setAllButtonsEnabled(false);
        }

        startTimer(30, () -> {
            if (isMyTurnActive(round)) {
                signalActivePhaseEnd(round);
            }
        });

        listenForConnections(round);
        listenForPhaseChange(round);

        updateScoreUI();
    }

    private boolean isMyTurnActive(int round) {
        if (round == 0) return "player1".equals(myPlayerId);
        else            return "player2".equals(myPlayerId);
    }

    private boolean isMyTurnFixing(int round) {
        if (round == 0) return "player2".equals(myPlayerId);
        else            return "player1".equals(myPlayerId);
    }

    // ==============================
    // TAJMER
    // ==============================

    private void startTimer(int seconds, Runnable onFinish) {
        if (roundTimer != null) roundTimer.cancel();
        roundTimer = new CountDownTimer(seconds * 1000L, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                tvTimer.setText(String.valueOf(millisUntilFinished / 1000 + 1));
            }
            @Override
            public void onFinish() {
                tvTimer.setText("0");
                onFinish.run();
            }
        }.start();
    }

    // ==============================
    // CLICK LISTENERI
    // ==============================

    private void setupClickListeners() {
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            leftButtons[i].setOnClickListener(v  -> onLeftClicked(idx));
            rightButtons[i].setOnClickListener(v -> onRightClicked(idx));
        }
    }

    private void onLeftClicked(int idx) {
        if (leftUsed[idx]) return;

        if (selectedLeftIndex == idx) {
            setButtonTint(leftButtons[idx], COLOR_DEFAULT);
            selectedLeftIndex = -1;
        } else {
            if (selectedLeftIndex != -1) {
                setButtonTint(leftButtons[selectedLeftIndex], COLOR_DEFAULT);
            }
            selectedLeftIndex = idx;
            setButtonTint(leftButtons[idx], COLOR_SELECTED);

            if (selectedRightIndex != -1) {
                submitConnection(selectedLeftIndex, selectedRightIndex);
            }
        }
    }

    private void onRightClicked(int idx) {
        if (rightUsed[idx]) return;

        if (selectedLeftIndex != -1) {
            submitConnection(selectedLeftIndex, idx);
        } else {
            if (selectedRightIndex != -1) {
                setButtonTint(rightButtons[selectedRightIndex], COLOR_DEFAULT);
            }
            selectedRightIndex = idx;
            setButtonTint(rightButtons[idx], COLOR_WAITING);
        }
    }

    // ==============================
    // UPIŠI VEZU U FIREBASE
    // ==============================

    private void submitConnection(int leftIdx, int rightIdx) {
        selectedLeftIndex  = -1;
        selectedRightIndex = -1;

        gameRef.child("rounds")
                .child(String.valueOf(currentRound))
                .child("connections")
                .child(String.valueOf(leftIdx))
                .child("rightIdx").setValue(rightIdx);

        gameRef.child("rounds")
                .child(String.valueOf(currentRound))
                .child("connections")
                .child(String.valueOf(leftIdx))
                .child("madeBy").setValue(myPlayerId);
    }

    // ==============================
    // SLUŠAJ VEZE IZ FIREBASE
    // ==============================

    private void listenForConnections(int round) {
        if (connectionsListener != null) {
            gameRef.child("rounds").child(String.valueOf(round))
                    .child("connections").removeEventListener(connectionsListener);
        }

        // Reset bodova za novu rundu
        scorePlayer1 = 0;
        scorePlayer2 = 0;

        DatabaseReference connectionsRef = gameRef
                .child("rounds").child(String.valueOf(round))
                .child("connections");

        connectionsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Reset lokalnog stanja
                for (int i = 0; i < 5; i++) {
                    leftUsed[i]  = false;
                    rightUsed[i] = false;
                    setButtonTint(leftButtons[i],  COLOR_DEFAULT);
                    setButtonTint(rightButtons[i], COLOR_DEFAULT);
                }

                // Ponovo postavi boje za sve veze
                // Reset bodova svaki put da ne dupliramo
                int p1Score = 0;
                int p2Score = 0;

                for (DataSnapshot connSnap : snapshot.getChildren()) {
                    int leftIdx  = Integer.parseInt(connSnap.getKey());
                    Integer rightIdx = connSnap.child("rightIdx").getValue(Integer.class);
                    String madeBy   = connSnap.child("madeBy").getValue(String.class);

                    if (rightIdx == null) continue;

                    boolean correct = (CORRECT_MAPPING[leftIdx] == rightIdx);

                    leftUsed[leftIdx]   = true;
                    rightUsed[rightIdx] = true;

                    setButtonTint(leftButtons[leftIdx],
                            correct ? COLOR_CONNECTED : COLOR_WRONG);
                    setButtonTint(rightButtons[rightIdx],
                            correct ? COLOR_CONNECTED : COLOR_WRONG);

                    if (correct) {
                        if ("player1".equals(madeBy)) p1Score += 2;
                        else                          p2Score += 2;
                    }
                }

                scorePlayer1 = p1Score;
                scorePlayer2 = p2Score;
                updateScoreUI();

                // Provjeri da li je aktivni igrač spojio sve slobodne
                boolean anyFree = false;
                for (boolean u : leftUsed) if (!u) { anyFree = true; break; }

                if (!anyFree && currentPhase == PHASE_ACTIVE && isMyTurnActive(round)) {
                    if (roundTimer != null) roundTimer.cancel();
                    signalActivePhaseEnd(round);
                }

                if (!anyFree && currentPhase == PHASE_FIXING && isMyTurnFixing(round)) {
                    if (roundTimer != null) roundTimer.cancel();
                    gameRef.child("rounds").child(String.valueOf(round))
                            .child("phase").setValue("done");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };

        connectionsRef.addValueEventListener(connectionsListener);
    }

    // ==============================
    // SLUŠAJ PROMJENU FAZE
    // ==============================

    private void listenForPhaseChange(int round) {
        if (phaseListener != null) {
            gameRef.child("rounds").child(String.valueOf(round))
                    .child("phase").removeEventListener(phaseListener);
        }

        DatabaseReference phaseRef = gameRef
                .child("rounds").child(String.valueOf(round))
                .child("phase");

        phaseListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String phase = snapshot.getValue(String.class);
                if (phase == null) return;

                if ("fixing".equals(phase) && currentPhase == PHASE_ACTIVE) {
                    currentPhase = PHASE_FIXING;
                    if (roundTimer != null) roundTimer.cancel();
                    startFixingPhase(round);

                } else if ("done".equals(phase) && currentPhase != PHASE_DONE) {
                    currentPhase = PHASE_DONE;
                    if (roundTimer != null) roundTimer.cancel();
                    onRoundDone(round);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };

        phaseRef.addValueEventListener(phaseListener);
    }

    // ==============================
    // SIGNAL: aktivna faza završena
    // ==============================

    private void signalActivePhaseEnd(int round) {
        final boolean hasIssues;
        boolean tempHasIssues = false;
        for (int i = 0; i < 5; i++) {
            if (!leftUsed[i]) { tempHasIssues = true; break; }
        }
        hasIssues = tempHasIssues;

        // Provjeri i netačne
        gameRef.child("rounds").child(String.valueOf(round))
                .child("connections")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        boolean anyWrong = false;
                        for (DataSnapshot connSnap : snapshot.getChildren()) {
                            Integer rightIdx = connSnap.child("rightIdx")
                                    .getValue(Integer.class);
                            int leftIdx = Integer.parseInt(connSnap.getKey());
                            if (rightIdx != null && CORRECT_MAPPING[leftIdx] != rightIdx) {
                                anyWrong = true;
                                break;
                            }
                        }

                        boolean hasFreeOrWrong = hasIssues || anyWrong;

                        // Resetuj netačne veze iz Firebase
                        if (anyWrong) {
                            for (DataSnapshot connSnap : snapshot.getChildren()) {
                                Integer rightIdx = connSnap.child("rightIdx")
                                        .getValue(Integer.class);
                                int leftIdx = Integer.parseInt(connSnap.getKey());
                                if (rightIdx != null && CORRECT_MAPPING[leftIdx] != rightIdx) {
                                    connSnap.getRef().removeValue();
                                }
                            }
                        }

                        String nextPhase = hasFreeOrWrong ? "fixing" : "done";
                        gameRef.child("rounds").child(String.valueOf(round))
                                .child("phase").setValue(nextPhase);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    // ==============================
    // FIXING FAZA
    // ==============================

    private void startFixingPhase(int round) {
        if (isMyTurnFixing(round)) {
            tvStatus.setText("Ti dobijaš šansu za netačne/nepovezane — 30s!");
            for (int i = 0; i < 5; i++) {
                leftButtons[i].setEnabled(!leftUsed[i]);
                rightButtons[i].setEnabled(!rightUsed[i]);
            }
            startTimer(30, () ->
                    gameRef.child("rounds").child(String.valueOf(round))
                            .child("phase").setValue("done")
            );
        } else {
            tvStatus.setText("Protivnik dobija šansu za nepovezane...");
            setAllButtonsEnabled(false);
            startTimer(30, () -> {});
        }
    }

    // ==============================
    // RUNDA ZAVRŠENA
    // ==============================

    private void onRoundDone(int round) {
        currentPhase = PHASE_DONE;
        handler.removeCallbacksAndMessages(null);
        if (roundTimer != null) roundTimer.cancel();

        if (connectionsListener != null) {
            gameRef.child("rounds").child(String.valueOf(round))
                    .child("connections").removeEventListener(connectionsListener);
        }
        if (phaseListener != null) {
            gameRef.child("rounds").child(String.valueOf(round))
                    .child("phase").removeEventListener(phaseListener);
        }

        tvStatus.setText("Runda " + (round + 1) + " završena!");
        setAllButtonsEnabled(false);

        if (getView() != null) {
            getView().postDelayed(() -> {
                if (round + 1 < 2) {
                    startRound(round + 1);
                } else {
                    endGame();
                }
            }, 2000);
        }
    }

    // ==============================
    // KRAJ IGRE
    // ==============================

    private void endGame() {
        handler.removeCallbacksAndMessages(null);
        if (roundTimer != null) roundTimer.cancel();
        setAllButtonsEnabled(false);

        gameRef.child("status").setValue("finished");
        gameRef.child("scores").child("player1").setValue(scorePlayer1);
        gameRef.child("scores").child("player2").setValue(scorePlayer2);

        tvRound.setText("Igra završena!");
        tvTimer.setText("—");
        tvKriterijum.setText("");

        String p1Label = "player1".equals(myPlayerId) ? "Ti" : "Protivnik";
        String p2Label = "player2".equals(myPlayerId) ? "Ti" : "Protivnik";

        String rezultat;
        if (scorePlayer1 > scorePlayer2) {
            rezultat = "🏆 " + p1Label + " pobijedio!\n\n"
                    + p1Label + ": " + scorePlayer1 + " bod.\n"
                    + p2Label + ": " + scorePlayer2 + " bod.";
        } else if (scorePlayer2 > scorePlayer1) {
            rezultat = "🏆 " + p2Label + " pobijedio!\n\n"
                    + p1Label + ": " + scorePlayer1 + " bod.\n"
                    + p2Label + ": " + scorePlayer2 + " bod.";
        } else {
            rezultat = "Neriješeno!\n\n"
                    + p1Label + ": " + scorePlayer1 + " bod.\n"
                    + p2Label + ": " + scorePlayer2 + " bod.";
        }

        tvStatus.setText(rezultat);
        updateScoreUI();

        handler.postDelayed(() -> {
            if (getView() != null) {
                androidx.navigation.Navigation
                        .findNavController(getView())
                        .navigate(R.id.nav_game);
            }
        }, 3000);
    }

    // ==============================
    // HELPERI
    // ==============================

    private void setAllButtonsEnabled(boolean enabled) {
        for (int i = 0; i < 5; i++) {
            leftButtons[i].setEnabled(enabled);
            rightButtons[i].setEnabled(enabled);
        }
    }

    private void setButtonTint(MaterialButton btn, int color) {
        btn.setBackgroundTintList(ColorStateList.valueOf(color));
    }

    private void updateScoreUI() {
        if ("player1".equals(myPlayerId)) {
            tvScorePlayer1.setText("Ti: " + scorePlayer1);
            tvScorePlayer2.setText("Protivnik: " + scorePlayer2);
        } else {
            tvScorePlayer1.setText("Protivnik: " + scorePlayer1);
            tvScorePlayer2.setText("Ti: " + scorePlayer2);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
        if (roundTimer != null) roundTimer.cancel();
        if (connectionsListener != null) {
            gameRef.child("rounds").child(String.valueOf(currentRound))
                    .child("connections").removeEventListener(connectionsListener);
        }
        if (phaseListener != null) {
            gameRef.child("rounds").child(String.valueOf(currentRound))
                    .child("phase").removeEventListener(phaseListener);
        }
    }
}