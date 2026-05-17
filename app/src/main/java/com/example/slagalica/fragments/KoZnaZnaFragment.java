package com.example.slagalica.fragments;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.example.slagalica.R;
import com.google.android.material.button.MaterialButton;

import java.util.Random;

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

    // ====== STANJE ======
    private int  currentQuestion   = 0;
    private int  scorePlayer1      = 0;
    private int  scorePlayer2      = 0;
    private int  player1AnswerIndex = -1; // field, ne lokalna varijabla!
    private int  player2Choice      = -1;
    private boolean player1Answered = false;
    private boolean player2Answered = false;
    private long player1AnswerTime  = -1;
    private long player2AnswerTime  = -1;
    private long questionStartTime  = 0;

    private CountDownTimer questionTimer;
    private CountDownTimer player2Timer;
    private final Random random = new Random();

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

        btnAnswerA.setOnClickListener(v -> onPlayer1Answer(0));
        btnAnswerB.setOnClickListener(v -> onPlayer1Answer(1));
        btnAnswerC.setOnClickListener(v -> onPlayer1Answer(2));
        btnAnswerD.setOnClickListener(v -> onPlayer1Answer(3));

        loadQuestion(currentQuestion);

        return rootView;
    }

    // ==============================
    // UČITAVANJE PITANJA
    // ==============================

    private void loadQuestion(int idx) {
        // Reset stanja za novo pitanje
        player1AnswerIndex = -1;
        player2Choice      = -1;
        player1Answered    = false;
        player2Answered    = false;
        player1AnswerTime  = -1;
        player2AnswerTime  = -1;
        questionStartTime  = System.currentTimeMillis();

        tvQuestionNumber.setText("Pitanje " + (idx + 1) + " / 5");
        tvQuestion.setText(QUESTIONS[idx]);
        tvStatus.setText("");

        btnAnswerA.setText("A)  " + ANSWERS[idx][0]);
        btnAnswerB.setText("B)  " + ANSWERS[idx][1]);
        btnAnswerC.setText("C)  " + ANSWERS[idx][2]);
        btnAnswerD.setText("D)  " + ANSWERS[idx][3]);

        resetButtonTints();
        setButtonsEnabled(true);
        updateScoreUI();

        startQuestionTimer();
        startPlayer2Timer();
    }

    // ==============================
    // TAJMER: 5 SEKUNDI PO PITANJU
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
                onQuestionEnd();
            }
        }.start();
    }

    // ==============================
    // ISTEKLO VRIJEME — završi pitanje
    // ==============================

    private void onQuestionEnd() {
        if (player2Timer != null) player2Timer.cancel();

        // Ako igrač 2 nije stigao odgovoriti — smatra se da nije odgovorio
        if (!player2Answered) {
            player2Answered = true;
            player2Choice   = -1; // nije odgovorio
        }

        // Ako igrač 1 nije stigao odgovoriti
        if (!player1Answered) {
            player1Answered    = true;
            player1AnswerIndex = -1; // nije odgovorio
        }

        resolveQuestion();
    }

    // ==============================
    // VIRTUALNI IGRAČ 2
    // 70% šanse da odgovori tačno
    // Odgovara između 500ms i 4500ms
    // ==============================

    private void startPlayer2Timer() {
        if (player2Timer != null) player2Timer.cancel();

        long delay = 500 + random.nextInt(4000);

        // Odluči šta će bot odabrati
        boolean willAnswerCorrectly = random.nextInt(100) < 67;
        if (willAnswerCorrectly) {
            player2Choice = CORRECT[currentQuestion];
        } else {
            int wrong;
            do {
                wrong = random.nextInt(4);
            } while (wrong == CORRECT[currentQuestion]);
            player2Choice = wrong;
        }

        player2Timer = new CountDownTimer(delay, delay) {
            @Override
            public void onTick(long millisUntilFinished) {}

            @Override
            public void onFinish() {
                if (!player2Answered) {
                    player2Answered   = true;
                    player2AnswerTime = System.currentTimeMillis() - questionStartTime;
                    checkBothAnswered();
                }
            }
        }.start();
    }

    // ==============================
    // ODGOVOR IGRAČA 1
    // ==============================

    private void onPlayer1Answer(int answerIdx) {
        if (player1Answered) return;

        player1Answered    = true;
        player1AnswerIndex = answerIdx;
        player1AnswerTime  = System.currentTimeMillis() - questionStartTime;

        // Vizualno označi odabir
        setButtonTint(answerButtons[answerIdx],
                answerIdx == CORRECT[currentQuestion] ? COLOR_CORRECT : COLOR_WRONG);

        setButtonsEnabled(false);
        checkBothAnswered();
    }

    // ==============================
    // PROVJERA: da li su oba odgovorila
    // ==============================

    private void checkBothAnswered() {
        if (!player1Answered || !player2Answered) return;

        if (questionTimer != null) questionTimer.cancel();
        if (player2Timer  != null) player2Timer.cancel();

        resolveQuestion();
    }

    // ==============================
    // RJEŠAVANJE PITANJA — logika po specifikaciji
    // ==============================

    private void resolveQuestion() {
        boolean p1Correct = (player1AnswerIndex == CORRECT[currentQuestion]);
        boolean p2Correct = (player2Choice == CORRECT[currentQuestion]);

        if (p1Correct && p2Correct) {
            // Oba tačno — bodove dobija brži
            if (player1AnswerTime <= player2AnswerTime) {
                scorePlayer1 += 10;
                tvStatus.setText("✓ Oba tačno — ti si brži! +10");
            } else {
                scorePlayer2 += 10;
                tvStatus.setText("✓ Oba tačno — bot brži! Bot +10");
            }

        } else if (p1Correct && !p2Correct) {
            scorePlayer1 += 10;
            if (player2Choice != -1) scorePlayer2 -= 5;
            tvStatus.setText("✓ Tačno! +10 bodova");

        } else if (!p1Correct && p2Correct) {
            scorePlayer2 += 10;
            if (player1AnswerIndex != -1) scorePlayer1 -= 5;
            tvStatus.setText("✗ Bot odgovorio tačno. Bot +10");

        } else {
            // Niko nije tačno
            if (player1AnswerIndex != -1) scorePlayer1 -= 5;
            if (player2Choice != -1)      scorePlayer2 -= 5;

            if (player1AnswerIndex == -1 && player2Choice == -1) {
                tvStatus.setText("Niko nije odgovorio — prelazimo dalje");
            } else {
                tvStatus.setText("✗ Netačno! -5 bodova");
            }
        }

        // Uvijek pokaži tačan odgovor
        setButtonTint(answerButtons[CORRECT[currentQuestion]], COLOR_CORRECT);

        updateScoreUI();

        // Pauza 1.5s pa na sljedeće pitanje
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
        if (player2Timer  != null) player2Timer.cancel();

        setButtonsEnabled(false);
        tvQuestionNumber.setText("Kraj igre!");
        tvTimer.setText("—");
        tvQuestion.setText("Igra je završena!");

        if (scorePlayer1 > scorePlayer2) {
            tvStatus.setText("🏆 Pobijedio si!  " + scorePlayer1 + " : " + scorePlayer2);
        } else if (scorePlayer2 > scorePlayer1) {
            tvStatus.setText("Bot pobijedio.  " + scorePlayer1 + " : " + scorePlayer2);
        } else {
            tvStatus.setText("Neriješeno!  " + scorePlayer1 + " : " + scorePlayer2);
        }

        updateScoreUI();
        // Nakon 3 sekunde vrati na listu igara
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
        tvScorePlayer1.setText("Bodovi: " + scorePlayer1);
        tvScorePlayer2.setText("Bodovi: " + scorePlayer2);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (questionTimer != null) questionTimer.cancel();
        if (player2Timer  != null) player2Timer.cancel();
    }
}