package com.example.slagalica.fragments;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.slagalica.R;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class KoZnaZnaFragment extends Fragment {

    // ====== UI ======
    private View rootView;
    private TextView tvQuestionNumber, tvTimer, tvQuestion, tvStatus;
    private TextView tvScorePlayer1, tvScorePlayer2;
    private MaterialButton btnAnswerA, btnAnswerB, btnAnswerC, btnAnswerD;
    private MaterialButton[] answerButtons;

    // ====== BOJE ======
    private static final int COLOR_DEFAULT = Color.parseColor("#F2AF14");
    private static final int COLOR_CORRECT = Color.parseColor("#4CAF50");
    private static final int COLOR_WRONG   = Color.parseColor("#D32F2F");

    // ====== PITANJA ======
    private static final String[] QUESTIONS = {
            "Koji grad je glavni grad Francuske?",
            "Koliko planeta ima u Sunčevom sistemu?",
            "Ko je napisao Hamlet?",
            "Koja je najduža rijeka na svijetu?",
            "U kojoj godini je počeo Drugi svjetski rat?"
    };
    private static final String[][] ANSWERS = {
            {"Berlin",  "Madrid",      "Pariz",   "Rim"},
            {"7",       "8",           "9",       "10"},
            {"Tolstoj", "Dostojevski", "Šekspir", "Homer"},
            {"Amazon",  "Nil",         "Jangce",  "Misisipi"},
            {"1935",    "1937",        "1939",    "1941"}
    };
    private static final int[] CORRECT = {2, 1, 2, 1, 2};

    // ====== FIREBASE ======
    private DatabaseReference gameRef;
    private String gameId;
    private String myPlayerId;

    // ====== STANJE ======
    private int     currentQuestion   = 0;
    private int     scorePlayer1      = 0;
    private int     scorePlayer2      = 0;
    private boolean myAnswered        = false;
    private long    questionStartTime = 0;

    // ====== STATISTIKA (prati se lokalno tokom partije) ======
    private int myCorrectAnswers = 0;
    private int myWrongAnswers   = 0;

    private CountDownTimer questionTimer;
    private ValueEventListener answerListener;

    public KoZnaZnaFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_ko_zna_zna, container, false);

        tvQuestionNumber = rootView.findViewById(R.id.tvQuestionNumber);
        tvTimer          = rootView.findViewById(R.id.tvTimer);
        tvQuestion       = rootView.findViewById(R.id.tvQuestion);
        tvStatus         = rootView.findViewById(R.id.tvStatus);
        tvScorePlayer1   = rootView.findViewById(R.id.tvScorePlayer1);
        tvScorePlayer2   = rootView.findViewById(R.id.tvScorePlayer2);

        btnAnswerA = rootView.findViewById(R.id.btnAnswerA);
        btnAnswerB = rootView.findViewById(R.id.btnAnswerB);
        btnAnswerC = rootView.findViewById(R.id.btnAnswerC);
        btnAnswerD = rootView.findViewById(R.id.btnAnswerD);
        answerButtons = new MaterialButton[]{btnAnswerA, btnAnswerB, btnAnswerC, btnAnswerD};

        btnAnswerA.setOnClickListener(v -> submitAnswer(0));
        btnAnswerB.setOnClickListener(v -> submitAnswer(1));
        btnAnswerC.setOnClickListener(v -> submitAnswer(2));
        btnAnswerD.setOnClickListener(v -> submitAnswer(3));

        // Čitamo ROOM_ID i PLAYER_ROLE iz GameFragment lobija
        gameId     = "test_game_001";
        myPlayerId = "player1";

        if (getArguments() != null) {
            gameId     = getArguments().getString("ROOM_ID",     "test_game_001");
            myPlayerId = getArguments().getString("PLAYER_ROLE", "player1");
        }

        gameRef = FirebaseDatabase.getInstance().getReference("games").child(gameId);

        loadQuestion(0);

        return rootView;
    }

    // ==============================
    // UČITAVANJE PITANJA
    // ==============================

    private void loadQuestion(int idx) {
        currentQuestion   = idx;
        myAnswered        = false;
        questionStartTime = System.currentTimeMillis();

        tvQuestionNumber.setText("Pitanje " + (idx + 1) + " / 5");
        tvQuestion.setText(QUESTIONS[idx]);
        tvStatus.setText("Čeka se odgovor...");

        btnAnswerA.setText("A)  " + ANSWERS[idx][0]);
        btnAnswerB.setText("B)  " + ANSWERS[idx][1]);
        btnAnswerC.setText("C)  " + ANSWERS[idx][2]);
        btnAnswerD.setText("D)  " + ANSWERS[idx][3]);

        resetButtonTints();
        setButtonsEnabled(true);
        updateScoreUI();

        if ("player1".equals(myPlayerId)) {
            gameRef.child("questionIndex").setValue(idx);
        }

        startQuestionTimer();
        listenForBothAnswers(idx);
    }

    // ==============================
    // TAJMER: 5 SEKUNDI
    // ==============================

    private void startQuestionTimer() {
        if (questionTimer != null) questionTimer.cancel();

        questionTimer = new CountDownTimer(5000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                tvTimer.setText(String.valueOf(millisUntilFinished / 1000 + 1));
            }
            @Override
            public void onFinish() {
                tvTimer.setText("0");
                if (!myAnswered) {
                    submitAnswer(-1);
                }
            }
        }.start();
    }

    // ==============================
    // IGRAČ KLIKNE ODGOVOR
    // ==============================

    private void submitAnswer(int answerIdx) {
        if (myAnswered) return;
        myAnswered = true;
        setButtonsEnabled(false);

        long answerTime = System.currentTimeMillis() - questionStartTime;

        if (answerIdx >= 0) {
            boolean correct = (answerIdx == CORRECT[currentQuestion]);
            setButtonTint(answerButtons[answerIdx], correct ? COLOR_CORRECT : COLOR_WRONG);

            // Brojimo lokalno za statistiku
            if (correct) myCorrectAnswers++;
            else         myWrongAnswers++;
        } else {
            // Nije odgovorio – računa se kao netačno
            myWrongAnswers++;
        }

        DatabaseReference answerRef = gameRef
                .child("answers")
                .child(String.valueOf(currentQuestion))
                .child(myPlayerId);

        answerRef.child("answerIndex").setValue(answerIdx);
        answerRef.child("answerTime").setValue(answerTime);
    }

    // ==============================
    // SLUŠAJ KAD OBA IGRAČA ODGOVORE
    // ==============================

    private void listenForBothAnswers(int questionIdx) {
        if (answerListener != null) {
            gameRef.child("answers").child(String.valueOf(questionIdx))
                    .removeEventListener(answerListener);
        }

        DatabaseReference questionAnswersRef = gameRef
                .child("answers")
                .child(String.valueOf(questionIdx));

        answerListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean hasPlayer1 = snapshot.hasChild("player1");
                boolean hasPlayer2 = snapshot.hasChild("player2");

                if (hasPlayer1 && hasPlayer2) {
                    if (questionTimer != null) questionTimer.cancel();

                    Integer p1AnswerRaw = snapshot.child("player1")
                            .child("answerIndex").getValue(Integer.class);
                    Integer p2AnswerRaw = snapshot.child("player2")
                            .child("answerIndex").getValue(Integer.class);
                    Long p1TimeRaw = snapshot.child("player1")
                            .child("answerTime").getValue(Long.class);
                    Long p2TimeRaw = snapshot.child("player2")
                            .child("answerTime").getValue(Long.class);

                    int p1Answer = p1AnswerRaw != null ? p1AnswerRaw : -1;
                    int p2Answer = p2AnswerRaw != null ? p2AnswerRaw : -1;
                    long p1Time  = p1TimeRaw   != null ? p1TimeRaw  : 9999;
                    long p2Time  = p2TimeRaw   != null ? p2TimeRaw  : 9999;

                    resolveQuestion(p1Answer, p2Answer, p1Time, p2Time);

                    questionAnswersRef.removeEventListener(this);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };

        questionAnswersRef.addValueEventListener(answerListener);
    }

    // ==============================
    // RAZRIJEŠI PITANJE
    // ==============================

    private void resolveQuestion(int p1Answer, int p2Answer, long p1Time, long p2Time) {
        boolean p1Correct = (p1Answer == CORRECT[currentQuestion]);
        boolean p2Correct = (p2Answer == CORRECT[currentQuestion]);

        setButtonTint(answerButtons[CORRECT[currentQuestion]], COLOR_CORRECT);

        if (p1Correct && p2Correct) {
            if (p1Time <= p2Time) {
                scorePlayer1 += 10;
                tvStatus.setText("✓ Oba tačno — Igrač 1 brži! +10");
            } else {
                scorePlayer2 += 10;
                tvStatus.setText("✓ Oba tačno — Igrač 2 brži! +10");
            }
        } else if (p1Correct) {
            scorePlayer1 += 10;
            if (p2Answer != -1) scorePlayer2 -= 5;
            tvStatus.setText("✓ Igrač 1 tačno! +10");
        } else if (p2Correct) {
            scorePlayer2 += 10;
            if (p1Answer != -1) scorePlayer1 -= 5;
            tvStatus.setText("✓ Igrač 2 tačno! +10");
        } else {
            if (p1Answer != -1) scorePlayer1 -= 5;
            if (p2Answer != -1) scorePlayer2 -= 5;
            if (p1Answer == -1 && p2Answer == -1) {
                tvStatus.setText("Niko nije odgovorio.");
            } else {
                tvStatus.setText("✗ Netačno! -5 bodova");
            }
        }

        gameRef.child("scores").child("player1").setValue(scorePlayer1);
        gameRef.child("scores").child("player2").setValue(scorePlayer2);

        updateScoreUI();

        rootView.postDelayed(this::nextQuestion, 1500);
    }

    private void nextQuestion() {
        currentQuestion++;
        if (currentQuestion < QUESTIONS.length) {
            loadQuestion(currentQuestion);
        } else {
            endGame();
        }
    }

    // ==============================
    // KRAJ IGRE – upisuje statistiku
    // ==============================

    private void endGame() {
        if (questionTimer != null) questionTimer.cancel();
        setButtonsEnabled(false);

        gameRef.child("status").setValue("finished");

        tvQuestionNumber.setText("Kraj igre!");
        tvTimer.setText("—");
        tvQuestion.setText("Igra završena!");

        String p1Label = "player1".equals(myPlayerId) ? "Ti" : "Protivnik";
        String p2Label = "player2".equals(myPlayerId) ? "Ti" : "Protivnik";

        int myScore  = "player1".equals(myPlayerId) ? scorePlayer1 : scorePlayer2;
        int oppScore = "player1".equals(myPlayerId) ? scorePlayer2 : scorePlayer1;
        boolean iWon = myScore > oppScore;

        if (scorePlayer1 > scorePlayer2) {
            tvStatus.setText("🏆 " + p1Label + " pobijedio!\n\n"
                    + p1Label + ": " + scorePlayer1 + "\n"
                    + p2Label + ": " + scorePlayer2);
        } else if (scorePlayer2 > scorePlayer1) {
            tvStatus.setText("🏆 " + p2Label + " pobijedio!\n\n"
                    + p1Label + ": " + scorePlayer1 + "\n"
                    + p2Label + ": " + scorePlayer2);
        } else {
            tvStatus.setText("Neriješeno!\n\n"
                    + p1Label + ": " + scorePlayer1 + "\n"
                    + p2Label + ": " + scorePlayer2);
        }

        updateScoreUI();

        // ── Upis statistike u Firestore ───────────────────────────────────
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        if (uid != null) {
            FirebaseFirestore db = FirebaseFirestore.getInstance();

            Map<String, Object> updates = new HashMap<>();
            updates.put("stats.koznaZna.correct",  FieldValue.increment(myCorrectAnswers));
            updates.put("stats.koznaZna.wrong",    FieldValue.increment(myWrongAnswers));
            updates.put("stats.koznaZna.wins",     FieldValue.increment(iWon ? 1 : 0));
            updates.put("stats.koznaZna.losses",   FieldValue.increment(iWon ? 0 : 1));
            updates.put("stats.global.totalGames", FieldValue.increment(1));
            updates.put("stats.global.wins",       FieldValue.increment(iWon ? 1 : 0));
            updates.put("stats.global.losses",     FieldValue.increment(iWon ? 0 : 1));

            db.collection("users").document(uid).update(updates);
        }
        // ─────────────────────────────────────────────────────────────────

        rootView.postDelayed(() -> {
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

    private void setButtonTint(MaterialButton btn, int color) {
        btn.setBackgroundTintList(ColorStateList.valueOf(color));
    }

    private void resetButtonTints() {
        for (MaterialButton btn : answerButtons) {
            setButtonTint(btn, COLOR_DEFAULT);
        }
    }

    private void setButtonsEnabled(boolean enabled) {
        for (MaterialButton btn : answerButtons) {
            btn.setEnabled(enabled);
        }
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
        if (questionTimer != null) questionTimer.cancel();
        if (answerListener != null) {
            gameRef.child("answers").child(String.valueOf(currentQuestion))
                    .removeEventListener(answerListener);
        }
    }
}