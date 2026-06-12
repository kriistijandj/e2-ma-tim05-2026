package com.example.slagalica.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.slagalica.R;
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
    private Button btnAnswerA, btnAnswerB, btnAnswerC, btnAnswerD;

    // ====== BOJE ======
    private static final int COLOR_DEFAULT = Color.parseColor("#90A4AE");
    private static final int COLOR_CORRECT = Color.parseColor("#388E3C");
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
            {"A) Berlin", "B) Madrid", "C) Pariz",   "D) Rim"},
            {"A) 7",      "B) 8",      "C) 9",        "D) 10"},
            {"A) Tolstoj","B) Dostojevski","C) Šekspir","D) Homer"},
            {"A) Amazon", "B) Nil",     "C) Jangce",  "D) Misisipi"},
            {"A) 1935",   "B) 1937",    "C) 1939",    "D) 1941"}
    };
    private static final int[] CORRECT = {2, 1, 2, 1, 2};

    // ====== FIREBASE ======
    private DatabaseReference gameRef;
    private String gameId;
    private String myPlayerId;
    private ValueEventListener questionListener;

    // ====== STANJE ======
    private int     currentQuestion   = 0;
    private int     scorePlayer1      = 0;
    private int     scorePlayer2      = 0;
    private boolean myAnswered        = false;
    private boolean questionResolved  = false;
    private long    questionStartTime = 0;

    // ====== STATISTIKA ======
    private int myCorrectAnswers = 0;
    private int myWrongAnswers   = 0;

    private CountDownTimer questionTimer;

    public KoZnaZnaFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_ko_zna_zna, container, false);
        rootView = view;

        tvQuestionNumber = view.findViewById(R.id.tvQuestionNumber);
        tvTimer          = view.findViewById(R.id.tvTimer);
        tvQuestion       = view.findViewById(R.id.tvQuestion);
        tvStatus         = view.findViewById(R.id.tvStatus);
        tvScorePlayer1   = view.findViewById(R.id.tvScorePlayer1);
        tvScorePlayer2   = view.findViewById(R.id.tvScorePlayer2);

        btnAnswerA = view.findViewById(R.id.btnAnswerA);
        btnAnswerB = view.findViewById(R.id.btnAnswerB);
        btnAnswerC = view.findViewById(R.id.btnAnswerC);
        btnAnswerD = view.findViewById(R.id.btnAnswerD);

        btnAnswerA.setOnClickListener(v -> submitAnswer(0));
        btnAnswerB.setOnClickListener(v -> submitAnswer(1));
        btnAnswerC.setOnClickListener(v -> submitAnswer(2));
        btnAnswerD.setOnClickListener(v -> submitAnswer(3));

        // Čitaj argumente iz GameFragment lobija
        gameId     = "room_koznaZna_001";
        myPlayerId = "player1";

        if (getArguments() != null) {
            gameId     = getArguments().getString("ROOM_ID",     "room_koznaZna_001");
            myPlayerId = getArguments().getString("PLAYER_ROLE", "player1");
        }

        gameRef = FirebaseDatabase.getInstance().getReference("games").child(gameId);

        // Player1 briše staro stanje i inicijalizuje igru
        if ("player1".equals(myPlayerId)) {
            gameRef.removeValue((error, ref) -> loadQuestion(0));
        } else {
            // Player2 čeka da player1 postavi pitanje
            waitForQuestion();
        }

        return view;
    }

    // ==============================
    // PLAYER2 ČEKA PRVO PITANJE
    // ==============================

    private void waitForQuestion() {
        tvStatus.setText("Čekanje...");
        setAllButtonsEnabled(false);

        gameRef.child("currentQuestion").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Integer idx = snapshot.getValue(Integer.class);
                if (idx != null) {
                    gameRef.child("currentQuestion").removeEventListener(this);
                    loadQuestion(idx);
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    // ==============================
    // UČITAVANJE PITANJA
    // ==============================

    private void loadQuestion(int idx) {
        currentQuestion  = idx;
        myAnswered       = false;
        questionResolved = false;
        questionStartTime = System.currentTimeMillis();

        tvQuestionNumber.setText("Pitanje " + (idx + 1) + " / 5");
        tvQuestion.setText(QUESTIONS[idx]);
        tvStatus.setText("Čeka se odgovor...");

        btnAnswerA.setText(ANSWERS[idx][0]);
        btnAnswerB.setText(ANSWERS[idx][1]);
        btnAnswerC.setText(ANSWERS[idx][2]);
        btnAnswerD.setText(ANSWERS[idx][3]);

        resetButtonColors();
        setAllButtonsEnabled(true);
        updateScoreUI();

        // Player1 upisuje indeks pitanja u Firebase
        if ("player1".equals(myPlayerId)) {
            gameRef.child("currentQuestion").setValue(idx);
            // Briši stare odgovore za ovo pitanje
            gameRef.child("answers").child(String.valueOf(idx)).removeValue();
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
            public void onTick(long ms) {
                tvTimer.setText(String.valueOf(ms / 1000 + 1));
            }
            @Override
            public void onFinish() {
                tvTimer.setText("0");
                if (!myAnswered) submitAnswer(-1);
            }
        }.start();
    }

    // ==============================
    // IGRAČ KLIKNE ODGOVOR
    // ==============================

    private void submitAnswer(int answerIdx) {
        if (myAnswered) return;
        myAnswered = true;
        setAllButtonsEnabled(false);

        long answerTime = System.currentTimeMillis() - questionStartTime;

        // Vizualno odmah pokaži odgovor
        Button[] buttons = {btnAnswerA, btnAnswerB, btnAnswerC, btnAnswerD};
        if (answerIdx >= 0) {
            boolean correct = (answerIdx == CORRECT[currentQuestion]);
            buttons[answerIdx].setBackgroundColor(correct ? COLOR_CORRECT : COLOR_WRONG);
            if (correct) myCorrectAnswers++;
            else         myWrongAnswers++;
        } else {
            myWrongAnswers++;
        }

        // Upiši u Firebase
        gameRef.child("answers").child(String.valueOf(currentQuestion))
                .child(myPlayerId).child("answerIndex").setValue(answerIdx);
        gameRef.child("answers").child(String.valueOf(currentQuestion))
                .child(myPlayerId).child("answerTime").setValue(answerTime);
    }

    // ==============================
    // SLUŠAJ KAD OBA ODGOVORE
    // ==============================

    private void listenForBothAnswers(int questionIdx) {
        if (questionListener != null) {
            gameRef.child("answers").child(String.valueOf(questionIdx))
                    .removeEventListener(questionListener);
            questionListener = null;
        }

        DatabaseReference answersRef = gameRef.child("answers")
                .child(String.valueOf(questionIdx));

        questionListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (questionResolved) return;
                if (!snapshot.hasChild("player1") || !snapshot.hasChild("player2")) return;

                questionResolved = true;
                if (questionTimer != null) questionTimer.cancel();

                // Ukloni listener odmah
                answersRef.removeEventListener(this);
                questionListener = null;

                Integer p1Ans  = snapshot.child("player1").child("answerIndex").getValue(Integer.class);
                Integer p2Ans  = snapshot.child("player2").child("answerIndex").getValue(Integer.class);
                Long    p1Time = snapshot.child("player1").child("answerTime").getValue(Long.class);
                Long    p2Time = snapshot.child("player2").child("answerTime").getValue(Long.class);

                int  p1Answer = p1Ans  != null ? p1Ans  : -1;
                int  p2Answer = p2Ans  != null ? p2Ans  : -1;
                long p1T      = p1Time != null ? p1Time : 9999;
                long p2T      = p2Time != null ? p2Time : 9999;

                resolveQuestion(p1Answer, p2Answer, p1T, p2T);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };

        answersRef.addValueEventListener(questionListener);
    }

    // ==============================
    // RAZRIJEŠI PITANJE
    // ==============================

    private void resolveQuestion(int p1Answer, int p2Answer, long p1Time, long p2Time) {
        Button[] buttons = {btnAnswerA, btnAnswerB, btnAnswerC, btnAnswerD};

        // Pokaži tačan odgovor
        buttons[CORRECT[currentQuestion]].setBackgroundColor(COLOR_CORRECT);

        // Pokaži i protivnikov odgovor
        String opponent = "player1".equals(myPlayerId) ? "player2" : "player1";
        int oppAnswer = "player1".equals(myPlayerId) ? p2Answer : p1Answer;
        if (oppAnswer >= 0 && oppAnswer != CORRECT[currentQuestion]) {
            buttons[oppAnswer].setBackgroundColor(COLOR_WRONG);
        }

        boolean p1Correct = (p1Answer == CORRECT[currentQuestion]);
        boolean p2Correct = (p2Answer == CORRECT[currentQuestion]);

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
            tvStatus.setText(p1Answer == -1 && p2Answer == -1
                    ? "Niko nije odgovorio." : "✗ Netačno!");
        }

        // Samo player1 upisuje skorove
        if ("player1".equals(myPlayerId)) {
            gameRef.child("scores").child("player1").setValue(scorePlayer1);
            gameRef.child("scores").child("player2").setValue(scorePlayer2);
        }

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
    // KRAJ IGRE
    // ==============================

    private void endGame() {
        if (questionTimer != null) questionTimer.cancel();
        setAllButtonsEnabled(false);

        if ("player1".equals(myPlayerId)) {
            gameRef.child("status").setValue("finished");
        }

        tvQuestionNumber.setText("Kraj igre!");
        tvTimer.setText("—");
        tvQuestion.setText("Igra završena!");

        String p1Label = "player1".equals(myPlayerId) ? "Ti" : "Protivnik";
        String p2Label = "player2".equals(myPlayerId) ? "Ti" : "Protivnik";
        int myScore    = "player1".equals(myPlayerId) ? scorePlayer1 : scorePlayer2;
        int oppScore   = "player1".equals(myPlayerId) ? scorePlayer2 : scorePlayer1;
        boolean iWon   = myScore > oppScore;

        if (scorePlayer1 > scorePlayer2)
            tvStatus.setText("🏆 " + p1Label + " pobijedio!\n" + p1Label + ": " + scorePlayer1 + "\n" + p2Label + ": " + scorePlayer2);
        else if (scorePlayer2 > scorePlayer1)
            tvStatus.setText("🏆 " + p2Label + " pobijedio!\n" + p1Label + ": " + scorePlayer1 + "\n" + p2Label + ": " + scorePlayer2);
        else
            tvStatus.setText("Neriješeno!\n" + p1Label + ": " + scorePlayer1 + "\n" + p2Label + ": " + scorePlayer2);

        updateScoreUI();

        // Upis statistike u Firestore
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        if (uid != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("stats.koznaZna.correct",  FieldValue.increment(myCorrectAnswers));
            updates.put("stats.koznaZna.wrong",    FieldValue.increment(myWrongAnswers));
            updates.put("stats.koznaZna.wins",     FieldValue.increment(iWon ? 1 : 0));
            updates.put("stats.koznaZna.losses",   FieldValue.increment(iWon ? 0 : 1));
            updates.put("stats.global.totalGames", FieldValue.increment(1));
            updates.put("stats.global.wins",       FieldValue.increment(iWon ? 1 : 0));
            updates.put("stats.global.losses",     FieldValue.increment(iWon ? 0 : 1));
            FirebaseFirestore.getInstance().collection("users").document(uid).update(updates);
        }

        rootView.postDelayed(() -> {
            if (getView() != null)
                androidx.navigation.Navigation.findNavController(getView()).navigate(R.id.nav_game);
        }, 3000);
    }

    // ====== HELPERI ======

    private void resetButtonColors() {
        btnAnswerA.setBackgroundColor(COLOR_DEFAULT);
        btnAnswerB.setBackgroundColor(COLOR_DEFAULT);
        btnAnswerC.setBackgroundColor(COLOR_DEFAULT);
        btnAnswerD.setBackgroundColor(COLOR_DEFAULT);
    }

    private void setAllButtonsEnabled(boolean enabled) {
        btnAnswerA.setEnabled(enabled);
        btnAnswerB.setEnabled(enabled);
        btnAnswerC.setEnabled(enabled);
        btnAnswerD.setEnabled(enabled);
    }

    private void updateScoreUI() {
        if ("player1".equals(myPlayerId)) {
            tvScorePlayer1.setText(String.valueOf(scorePlayer1));
            tvScorePlayer2.setText(String.valueOf(scorePlayer2));
        } else {
            tvScorePlayer1.setText(String.valueOf(scorePlayer2));
            tvScorePlayer2.setText(String.valueOf(scorePlayer1));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (questionTimer != null) questionTimer.cancel();
        if (questionListener != null) {
            gameRef.child("answers").child(String.valueOf(currentQuestion))
                    .removeEventListener(questionListener);
        }
    }
}