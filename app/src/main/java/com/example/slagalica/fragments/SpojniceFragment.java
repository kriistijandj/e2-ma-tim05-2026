package com.example.slagalica.fragments;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.example.slagalica.R;
import com.google.android.material.button.MaterialButton;

import java.util.Random;

public class SpojniceFragment extends Fragment {

    private TextView tvRound, tvTimer, tvKriterijum, tvStatus;
    private TextView tvScorePlayer1, tvScorePlayer2;
    private MaterialButton[] leftButtons  = new MaterialButton[5];
    private MaterialButton[] rightButtons = new MaterialButton[5];

    private static final int COLOR_DEFAULT   = Color.parseColor("#F2AF14");
    private static final int COLOR_SELECTED  = Color.parseColor("#3A7BFF");
    private static final int COLOR_CONNECTED = Color.parseColor("#4CAF50");
    private static final int COLOR_WAITING   = Color.parseColor("#90A4AE");
    private static final int COLOR_WRONG     = Color.parseColor("#D32F2F");

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

    private static final int PHASE_PLAYER1 = 0;
    private static final int PHASE_PLAYER2 = 1;
    private static final int PHASE_DONE    = 2;

    private int currentRound  = 0;
    private int currentPhase  = PHASE_PLAYER1;
    private int scorePlayer1  = 0;
    private int scorePlayer2  = 0;

    private int selectedLeftIndex  = -1;
    private int selectedRightIndex = -1;

    private int[]     connectedRight    = new int[5];
    private boolean[] connectionCorrect = new boolean[5];
    private boolean[] leftUsed          = new boolean[5];
    private boolean[] rightUsed         = new boolean[5];

    private CountDownTimer roundTimer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random   = new Random();

    private int botMaxFirstPhase  = 5;
    private int botConnectedCount = 0;

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

        setupClickListeners();
        startRound(0, PHASE_PLAYER1);

        return view;
    }

    private void startRound(int round, int phase) {
        currentPhase       = phase;
        selectedLeftIndex  = -1;
        selectedRightIndex = -1;
        botConnectedCount  = 0;
        handler.removeCallbacksAndMessages(null);

        if (phase == PHASE_PLAYER1) {
            for (int i = 0; i < 5; i++) {
                connectedRight[i]    = -1;
                connectionCorrect[i] = false;
                leftUsed[i]          = false;
                rightUsed[i]         = false;
            }

            tvRound.setText("Runda " + (round + 1) + " / 2");
            tvKriterijum.setText(KRITERIJUMI[round]);

            for (int i = 0; i < 5; i++) {
                leftButtons[i].setText(LEFT_TERMS[round][i]);
                rightButtons[i].setText(RIGHT_TERMS[round][i]);
                setButtonTint(leftButtons[i],  COLOR_DEFAULT);
                setButtonTint(rightButtons[i], COLOR_DEFAULT);
            }

            if (round == 0) {
                tvStatus.setText("Ti igraš — 30 sekundi!");
                setAllButtonsEnabled(true);
                startTimer(30, () -> onPhaseTimeout(round));
            } else {
                botMaxFirstPhase = 5;
                tvStatus.setText("Bot igra prvu fazu runde 2...");
                setAllButtonsEnabled(false);
                startTimer(30, () -> onPhaseTimeout(round));
                scheduleBotMove(round, true);
            }

        } else if (phase == PHASE_PLAYER2) {

            resetWrongConnections();

            if (round == 0) {
                tvStatus.setText("Bot dobija šansu za netačne/nepovezane!");
                setAllButtonsEnabled(false);
                startTimer(30, () -> onBotPhaseEnd(round));
                scheduleBotMove(round, false);
            } else {
                tvStatus.setText("Ti dobijaš šansu za netačne/nepovezane — 30s!");
                for (int i = 0; i < 5; i++) {
                    leftButtons[i].setEnabled(!leftUsed[i]);
                    rightButtons[i].setEnabled(!rightUsed[i]);
                }
                startTimer(30, () -> onBotPhaseEnd(round));
            }
        }

        updateScoreUI();
    }

    private void scheduleBotMove(int round, boolean isFirstPhase) {
        handler.postDelayed(() -> {
            if (currentPhase == PHASE_DONE) return;

            if (isFirstPhase && botConnectedCount >= botMaxFirstPhase) {
                if (roundTimer != null) roundTimer.cancel();
                onPhaseTimeout(round);
                return;
            }

            // Nađi prvi slobodan lijevi
            int leftIdx = -1;
            for (int i = 0; i < 5; i++) {
                if (!leftUsed[i]) { leftIdx = i; break; }
            }

            if (leftIdx == -1) {
                if (roundTimer != null) roundTimer.cancel();
                if (isFirstPhase) onPhaseTimeout(round);
                else onBotPhaseEnd(round);
                return;
            }

            // 50/50 tačno ili netačno
            int rightIdx;
            if (random.nextBoolean()) {
                rightIdx = CORRECT_MAPPING[leftIdx];
                if (rightUsed[rightIdx]) {
                    rightIdx = findFreeRight();
                    if (rightIdx == -1) { onBotPhaseEnd(round); return; }
                }
            } else {
                rightIdx = findWrongFreeRight(leftIdx);
                if (rightIdx == -1) {
                    rightIdx = CORRECT_MAPPING[leftIdx];
                    if (rightUsed[rightIdx]) {
                        rightIdx = findFreeRight();
                        if (rightIdx == -1) { onBotPhaseEnd(round); return; }
                    }
                }
            }

            makeConnection(leftIdx, rightIdx, false);
            botConnectedCount++;

            // Zakaži sljedeći potez
            scheduleBotMove(round, isFirstPhase);

        }, 1500);
    }

    private void resetWrongConnections() {
        for (int i = 0; i < 5; i++) {
            if (leftUsed[i] && !connectionCorrect[i]) {
                int wrongRight = connectedRight[i];
                setButtonTint(leftButtons[i], COLOR_DEFAULT);
                if (wrongRight >= 0) {
                    setButtonTint(rightButtons[wrongRight], COLOR_DEFAULT);
                    rightUsed[wrongRight] = false;
                }
                leftUsed[i]          = false;
                connectedRight[i]    = -1;
                connectionCorrect[i] = false;
            }
        }
    }

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

    private void setupClickListeners() {
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            leftButtons[i].setOnClickListener(v  -> onLeftClicked(idx));
            rightButtons[i].setOnClickListener(v -> onRightClicked(idx));
        }
    }

    private void onLeftClicked(int idx) {
        if (leftUsed[idx]) return;
        if (currentRound == 0 && currentPhase != PHASE_PLAYER1) return;
        if (currentRound == 1 && currentPhase != PHASE_PLAYER2) return;

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
                makeConnection(selectedLeftIndex, selectedRightIndex, true);
            }
        }
    }

    private void onRightClicked(int idx) {
        if (rightUsed[idx]) return;
        if (currentRound == 0 && currentPhase != PHASE_PLAYER1) return;
        if (currentRound == 1 && currentPhase != PHASE_PLAYER2) return;

        if (selectedLeftIndex != -1) {
            makeConnection(selectedLeftIndex, idx, true);
        } else {
            if (selectedRightIndex != -1) {
                setButtonTint(rightButtons[selectedRightIndex], COLOR_DEFAULT);
            }
            selectedRightIndex = idx;
            setButtonTint(rightButtons[idx], COLOR_WAITING);
        }
    }

    private void makeConnection(int leftIdx, int rightIdx, boolean isHuman) {
        if (leftUsed[leftIdx] || rightUsed[rightIdx]) return;

        leftUsed[leftIdx]       = true;
        rightUsed[rightIdx]     = true;
        connectedRight[leftIdx] = rightIdx;

        boolean correct = (CORRECT_MAPPING[leftIdx] == rightIdx);
        connectionCorrect[leftIdx] = correct;

        setButtonTint(leftButtons[leftIdx],   correct ? COLOR_CONNECTED : COLOR_WRONG);
        setButtonTint(rightButtons[rightIdx], correct ? COLOR_CONNECTED : COLOR_WRONG);

        if (correct) {
            if (isHuman) scorePlayer1 += 2;
            else         scorePlayer2 += 2;
        }

        selectedLeftIndex  = -1;
        selectedRightIndex = -1;
        updateScoreUI();

        // Sve tačno spojeno — završi odmah
        if (allCorrectlyConnected()) {
            handler.removeCallbacksAndMessages(null);
            if (roundTimer != null) roundTimer.cancel();
            onRoundDone(currentRound);
            return;
        }

        // Igrač spojio sve slobodne — daj šansu botu odmah
        if (isHuman) {
            boolean anyFreeLeft = false;
            for (int i = 0; i < 5; i++) {
                if (!leftUsed[i]) { anyFreeLeft = true; break; }
            }
            if (!anyFreeLeft) {
                handler.removeCallbacksAndMessages(null);
                if (roundTimer != null) roundTimer.cancel();
                if (currentPhase == PHASE_PLAYER1) {
                    onPhaseTimeout(currentRound);
                } else {
                    onBotPhaseEnd(currentRound);
                }
            }
        }
    }

    private boolean allCorrectlyConnected() {
        for (int i = 0; i < 5; i++) {
            if (!connectionCorrect[i]) return false;
        }
        return true;
    }

    private int findWrongFreeRight(int leftIdx) {
        int correct = CORRECT_MAPPING[leftIdx];
        for (int j = 0; j < 5; j++) {
            if (!rightUsed[j] && j != correct) return j;
        }
        return -1;
    }

    private int findFreeRight() {
        for (int j = 0; j < 5; j++) {
            if (!rightUsed[j]) return j;
        }
        return -1;
    }

    private void onPhaseTimeout(int round) {
        boolean hasIssues = false;
        for (int i = 0; i < 5; i++) {
            if (!leftUsed[i] || !connectionCorrect[i]) {
                hasIssues = true;
                break;
            }
        }

        if (hasIssues) {
            tvStatus.setText(round == 0
                    ? "Tvoje vrijeme isteklo. Bot dobija šansu!"
                    : "Bot završio. Ti dobijaš šansu za ostatak!");
            startRound(round, PHASE_PLAYER2);
        } else {
            onRoundDone(round);
        }
    }

    private void onBotPhaseEnd(int round) {
        onRoundDone(round);
    }

    private void onRoundDone(int round) {
        currentPhase = PHASE_DONE;
        handler.removeCallbacksAndMessages(null);
        if (roundTimer != null) roundTimer.cancel();

        tvStatus.setText("Runda " + (round + 1) + " završena!");
        setAllButtonsEnabled(false);

        if (getView() != null) {
            getView().postDelayed(() -> {
                if (round + 1 < 2) {
                    currentRound++;
                    startRound(currentRound, PHASE_PLAYER1);
                } else {
                    endGame();
                }
            }, 2000);
        }
    }

    private void endGame() {
        handler.removeCallbacksAndMessages(null);
        if (roundTimer != null) roundTimer.cancel();
        setAllButtonsEnabled(false);

        tvRound.setText("Igra završena!");
        tvTimer.setText("—");
        tvKriterijum.setText("");

        String rezultat;
        if (scorePlayer1 > scorePlayer2) {
            rezultat = "🏆 Pobijedio si!\n\nTi: " + scorePlayer1 + " bod.   Bot: " + scorePlayer2 + " bod.";
        } else if (scorePlayer2 > scorePlayer1) {
            rezultat = "Bot pobijedio!\n\nTi: " + scorePlayer1 + " bod.   Bot: " + scorePlayer2 + " bod.";
        } else {
            rezultat = "Neriješeno!\n\nTi: " + scorePlayer1 + " bod.   Bot: " + scorePlayer2 + " bod.";
        }

        tvStatus.setText(rezultat);
        updateScoreUI();

        // Nakon 3 sekunde automatski vrati na listu igara
        handler.postDelayed(() -> {
            if (getView() != null) {
                androidx.navigation.Navigation
                        .findNavController(getView())
                        .navigate(R.id.nav_game);
            }
        }, 3000);
    }

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
        tvScorePlayer1.setText("Bodovi: " + scorePlayer1);
        tvScorePlayer2.setText("Bodovi: " + scorePlayer2);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
        if (roundTimer != null) roundTimer.cancel();
    }
}