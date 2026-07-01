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
import com.example.slagalica.helper.MatchPresenceHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
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
            {"A) Berlin", "B) Madrid", "C) Pariz",      "D) Rim"},
            {"A) 7",      "B) 8",      "C) 9",           "D) 10"},
            {"A) Tolstoj","B) Dostojevski","C) Šekspir", "D) Homer"},
            {"A) Amazon", "B) Nil",     "C) Jangce",     "D) Misisipi"},
            {"A) 1935",   "B) 1937",    "C) 1939",       "D) 1941"}
    };
    private static final int[] CORRECT = {2, 1, 2, 1, 2};

    // ====== IDENTIFIKATORI ======
    private String matchId;
    private String myRole;      // "player1" ili "player2"
    private String myUid;

    // ====== FIREBASE ======
    private DatabaseReference gameRef;      // games/{matchId}/koznaZna
    private DatabaseReference matchRef;     // matches/{matchId}
    private ValueEventListener questionListener;
    private ValueEventListener gameAdvanceListener;

    // ====== POČETNI SKOROVI IZ MEČA ======
    private int matchStartingScoreP1 = 0;
    private int matchStartingScoreP2 = 0;

    // ====== STANJE IGRE ======
    private int     currentQuestion   = 0;
    private int     scorePlayer1      = 0;
    private int     scorePlayer2      = 0;
    private boolean myAnswered        = false;
    private boolean questionResolved  = false;
    private long    questionStartTime = 0;

    // ====== NAVIGACIJA ======
    private boolean matchFinishedRegistered = false;
    private boolean navigationScheduled     = false;

    // ====== STATISTIKA ======
    private int myCorrectAnswers = 0;
    private int myWrongAnswers   = 0;

    private CountDownTimer questionTimer;

    private MatchPresenceHelper presenceHelper;
    private boolean opponentLeft = false;
    private boolean waitingForFirstQuestion = false;

    public KoZnaZnaFragment() {}

    // ─────────────────────────────────────────────────────────────────────────
    // LIFECYCLE
    // ─────────────────────────────────────────────────────────────────────────

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

        // ── Čitaj identifikatore iz argumenata ───────────────────────────────
        matchId  = "test_game_001";
        myRole   = "player1";
        if (getArguments() != null) {
            matchId = getArguments().getString("MATCH_ID",     "test_game_001");
            myRole  = getArguments().getString("PLAYER_ROLE", "player1");
        }

        myUid    = FirebaseAuth.getInstance().getCurrentUser().getUid();
        gameRef  = FirebaseDatabase.getInstance()
                .getReference("games").child(matchId).child("koznaZna");
        matchRef = FirebaseDatabase.getInstance()
                .getReference("matches").child(matchId);

        // ── Korak 1: učitaj početne skorove iz meča, pa tek pokreni igru ─────
        loadMatchScores();

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
                                    androidx.navigation.Navigation
                                            .findNavController(requireView())
                                            .navigate(R.id.nav_home);
                                })
                                .setNegativeButton("Otkaži", null)
                                .show();
                    }
                }
        );

        return view;
    }

    private boolean isHost() {
        return "player1".equals(myRole) || (opponentLeft && "player2".equals(myRole));
    }

    private void setupPresence() {
        presenceHelper = new com.example.slagalica.helper.MatchPresenceHelper(matchId, myUid);
        presenceHelper.markPresent();

        matchRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String p1 = snapshot.child("player1Id").getValue(String.class);
                String p2 = snapshot.child("player2Id").getValue(String.class);
                String opponentUid = "player1".equals(myRole) ? p2 : p1;

                if (opponentUid != null && presenceHelper != null) {
                    presenceHelper.listenForOpponentLeft(opponentUid, KoZnaZnaFragment.this::onOpponentLeft);
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void onOpponentLeft() {
        opponentLeft = true;

        // 1) Igra još nije ni počela (čekam prvo pitanje), a upravo sam postao host
        if (isHost() && waitingForFirstQuestion) {
            waitingForFirstQuestion = false;
            gameRef.removeValue((error, ref) -> loadQuestion(0));
            return;
        }

        // 2) Trenutno pitanje čeka na protivnikov odgovor -> upiši mu "nema odgovora"
        //    umesto da čekam njegov (nepostojeći) tajmer
        if (!questionResolved) {
            String opponentRole = "player1".equals(myRole) ? "player2" : "player1";
            gameRef.child("answers").child(String.valueOf(currentQuestion))
                    .child(opponentRole).child("answerIndex").setValue(-1);
            gameRef.child("answers").child(String.valueOf(currentQuestion))
                    .child(opponentRole).child("answerTime").setValue(9999L);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (questionTimer != null) questionTimer.cancel();
        if (questionListener != null) {
            gameRef.child("answers").child(String.valueOf(currentQuestion))
                    .removeEventListener(questionListener);
            questionListener = null;
        }
        if (gameAdvanceListener != null) {
            matchRef.child("currentGame").removeEventListener(gameAdvanceListener);
            gameAdvanceListener = null;
        }
        if (presenceHelper != null) presenceHelper.detach();   // ← dodato
    }

    // ─────────────────────────────────────────────────────────────────────────
    // KORAK 1 — UČITAJ POČETNE SCOROVE IZ MEČA (kao u SkockoViewModel)
    // ─────────────────────────────────────────────────────────────────────────

    private void loadMatchScores() {
        matchRef.child("scores").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot child : snapshot.getChildren()) {
                    String uid   = child.getKey();
                    Integer val  = child.getValue(Integer.class);
                    int score    = val != null ? val : 0;

                    if (myUid.equals(uid)) {
                        if ("player1".equals(myRole)) matchStartingScoreP1 = score;
                        else                          matchStartingScoreP2 = score;
                    } else {
                        if ("player1".equals(myRole)) matchStartingScoreP2 = score;
                        else                          matchStartingScoreP1 = score;
                    }
                }
                startGame();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                startGame(); // nastavi čak i ako učitavanje ne uspe
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // KORAK 2 — POKRETANJE IGRE
    // ─────────────────────────────────────────────────────────────────────────

    private void startGame() {
        // Postavi početne skorove iz meča
        scorePlayer1 = matchStartingScoreP1;
        scorePlayer2 = matchStartingScoreP2;
        updateScoreUI();

        if (isHost()) {
            // Player1 briše staro stanje i inicijalizuje igru
            gameRef.removeValue((error, ref) -> loadQuestion(0));
        } else {
            // Player2 čeka da player1 postavi prvo pitanje
            waitForQuestion();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PLAYER2 ČEKA PRVO PITANJE
    // ─────────────────────────────────────────────────────────────────────────

    private void waitForQuestion() {
        waitingForFirstQuestion = true;   // ← dodato
        tvStatus.setText("Čekanje...");
        setAllButtonsEnabled(false);

        gameRef.child("currentQuestion").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Integer idx = snapshot.getValue(Integer.class);
                if (idx != null) {
                    waitingForFirstQuestion = false;   // ← dodato
                    gameRef.child("currentQuestion").removeEventListener(this);
                    loadQuestion(idx);
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }
    // ─────────────────────────────────────────────────────────────────────────
    // UČITAVANJE PITANJA
    // ─────────────────────────────────────────────────────────────────────────

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

        if ("player1".equals(myRole)) {
            gameRef.child("currentQuestion").setValue(idx);
            gameRef.child("answers").child(String.valueOf(idx)).removeValue();
        }

        startQuestionTimer();
        listenForBothAnswers(idx);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TAJMER: 5 SEKUNDI
    // ─────────────────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────────────────
    // IGRAČ KLIKNE ODGOVOR
    // ─────────────────────────────────────────────────────────────────────────

    private void submitAnswer(int answerIdx) {
        if (myAnswered) return;
        myAnswered = true;
        setAllButtonsEnabled(false);

        long answerTime = System.currentTimeMillis() - questionStartTime;

        Button[] buttons = {btnAnswerA, btnAnswerB, btnAnswerC, btnAnswerD};
        if (answerIdx >= 0) {
            boolean correct = (answerIdx == CORRECT[currentQuestion]);
            buttons[answerIdx].setBackgroundColor(correct ? COLOR_CORRECT : COLOR_WRONG);
            if (correct) myCorrectAnswers++;
            else         myWrongAnswers++;
        } else {
            myWrongAnswers++;
        }

        gameRef.child("answers").child(String.valueOf(currentQuestion))
                .child(myRole).child("answerIndex").setValue(answerIdx);
        gameRef.child("answers").child(String.valueOf(currentQuestion))
                .child(myRole).child("answerTime").setValue(answerTime);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SLUŠAJ KAD OBA ODGOVORE
    // ─────────────────────────────────────────────────────────────────────────

    private void listenForBothAnswers(int questionIdx) {
        if (questionListener != null) {
            gameRef.child("answers").child(String.valueOf(questionIdx - 1))
                    .removeEventListener(questionListener);
            questionListener = null;
        }

        DatabaseReference answersRef = gameRef.child("answers")
                .child(String.valueOf(questionIdx));

        questionListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (questionResolved) return;
                // Čekamo da oba igrača upišu odgovor (player1 i player2)
                if (!snapshot.hasChild("player1") || !snapshot.hasChild("player2")) return;

                questionResolved = true;
                if (questionTimer != null) questionTimer.cancel();

                answersRef.removeEventListener(this);
                questionListener = null;

                Integer p1Ans  = snapshot.child("player1").child("answerIndex").getValue(Integer.class);
                Integer p2Ans  = snapshot.child("player2").child("answerIndex").getValue(Integer.class);
                Long    p1Time = snapshot.child("player1").child("answerTime").getValue(Long.class);
                Long    p2Time = snapshot.child("player2").child("answerTime").getValue(Long.class);

                resolveQuestion(
                        p1Ans  != null ? p1Ans  : -1,
                        p2Ans  != null ? p2Ans  : -1,
                        p1Time != null ? p1Time : 9999L,
                        p2Time != null ? p2Time : 9999L
                );
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };

        answersRef.addValueEventListener(questionListener);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RAZRIJEŠI PITANJE
    // ─────────────────────────────────────────────────────────────────────────

    private void resolveQuestion(int p1Answer, int p2Answer, long p1Time, long p2Time) {
        Button[] buttons = {btnAnswerA, btnAnswerB, btnAnswerC, btnAnswerD};

        buttons[CORRECT[currentQuestion]].setBackgroundColor(COLOR_CORRECT);

        int oppAnswer = "player1".equals(myRole) ? p2Answer : p1Answer;
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

        // Samo player1 upisuje scorove u Firebase (kao u Skočko)
        if ("player1".equals(myRole)) {
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

    // ─────────────────────────────────────────────────────────────────────────
    // KRAJ IGRE — upiši u matches/{matchId}/scores, inkrementiraj currentGame
    // ─────────────────────────────────────────────────────────────────────────

    private void endGame() {
        if (matchFinishedRegistered) return;
        matchFinishedRegistered = true;

        if (questionTimer != null) questionTimer.cancel();
        setAllButtonsEnabled(false);

        // Odredi moj finalni skor
        int myFinalScore  = "player1".equals(myRole) ? scorePlayer1 : scorePlayer2;
        int oppFinalScore = "player1".equals(myRole) ? scorePlayer2 : scorePlayer1;
        boolean iWon      = myFinalScore > oppFinalScore;

        // ── Upiši skor u zajednički čvor meča (kao u Skočko finishMatch) ─────
        matchRef.child("scores").child(myUid).setValue(myFinalScore);

        // ── Samo player1 inkremantira currentGame ─────────────────────────────
        if (isHost()) {
            matchRef.child("currentGame").setValue(ServerValue.increment(1));
        }

        // ── Firestore statistika ──────────────────────────────────────────────
        Map<String, Object> updates = new HashMap<>();
        updates.put("stats.koznaZna.correct",  FieldValue.increment(myCorrectAnswers));
        updates.put("stats.koznaZna.wrong",    FieldValue.increment(myWrongAnswers));
        updates.put("stats.koznaZna.wins",     FieldValue.increment(iWon ? 1 : 0));
        updates.put("stats.koznaZna.losses",   FieldValue.increment(iWon ? 0 : 1));
        updates.put("stats.global.totalGames", FieldValue.increment(1));
        updates.put("stats.global.wins",       FieldValue.increment(iWon ? 1 : 0));
        updates.put("stats.global.losses",     FieldValue.increment(iWon ? 0 : 1));
        FirebaseFirestore.getInstance().collection("users").document(myUid).update(updates);

        // ── UI prikaz rezultata ───────────────────────────────────────────────
        tvQuestionNumber.setText("Kraj igre!");
        tvTimer.setText("—");
        tvQuestion.setText("Igra završena!");

        String p1Label = "player1".equals(myRole) ? "Ti" : "Protivnik";
        String p2Label = "player2".equals(myRole) ? "Ti" : "Protivnik";

        if (scorePlayer1 > scorePlayer2)
            tvStatus.setText("🏆 " + p1Label + " pobijedio!\n"
                    + p1Label + ": " + scorePlayer1 + "\n" + p2Label + ": " + scorePlayer2);
        else if (scorePlayer2 > scorePlayer1)
            tvStatus.setText("🏆 " + p2Label + " pobijedio!\n"
                    + p1Label + ": " + scorePlayer1 + "\n" + p2Label + ": " + scorePlayer2);
        else
            tvStatus.setText("Neriješeno!\n"
                    + p1Label + ": " + scorePlayer1 + "\n" + p2Label + ": " + scorePlayer2);

        updateScoreUI();

        // ── Čekaj Firebase signal pa naviguiraj (kao u Skočko) ───────────────
        navigationScheduled = true;
        listenForNextGame();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ČEKANJE NA FIREBASE PRE NAVIGACIJE (isti obrazac kao Skočko)
    // ─────────────────────────────────────────────────────────────────────────

    private void listenForNextGame() {
        gameAdvanceListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Integer game = snapshot.getValue(Integer.class);

                // KoZnaZna je case 4, prelazimo kad currentGame >= 5
                if (game != null && game >= 5) {
                    matchRef.child("currentGame").removeEventListener(this);
                    gameAdvanceListener = null;

                    if (!isAdded() || getView() == null) return;

                    Bundle args = new Bundle();
                    args.putString("MATCH_ID",    matchId);
                    args.putString("PLAYER_ROLE", myRole);

                    androidx.navigation.Navigation
                            .findNavController(requireView())
                            .navigate(R.id.nav_game, args);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };

        matchRef.child("currentGame").addValueEventListener(gameAdvanceListener);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERI
    // ─────────────────────────────────────────────────────────────────────────

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
        if ("player1".equals(myRole)) {
            tvScorePlayer1.setText(String.valueOf(scorePlayer1));
            tvScorePlayer2.setText(String.valueOf(scorePlayer2));
        } else {
            // Player2 vidi sebe levo
            tvScorePlayer1.setText(String.valueOf(scorePlayer2));
            tvScorePlayer2.setText(String.valueOf(scorePlayer1));
        }
    }
}