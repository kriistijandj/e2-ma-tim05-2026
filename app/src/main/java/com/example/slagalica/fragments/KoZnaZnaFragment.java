package com.example.slagalica.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.example.slagalica.R;

public class KoZnaZnaFragment extends Fragment {

    private View rootView;
    private TextView tvQuestionNumber, tvTimer, tvQuestion, tvStatus;
    private TextView tvScorePlayer1, tvScorePlayer2;
    private Button btnAnswerA, btnAnswerB, btnAnswerC, btnAnswerD;

    private static final int COLOR_DEFAULT = Color.parseColor("#90A4AE");
    private static final int COLOR_CORRECT = Color.parseColor("#388E3C");
    private static final int COLOR_WRONG   = Color.parseColor("#D32F2F");

    private static final String[] QUESTIONS = {
            "Koji grad je glavni grad Francuske?",
            "Koliko planeta ima u Sunčevom sistemu?",
            "Ko je napisao Hamlet?",
            "Koja je najduža rijeka na svijetu?",
            "U kojoj godini je počeo Drugi svjetski rat?"
    };

    private static final String[][] ANSWERS = {
            {"A) Berlin", "B) Madrid", "C) Pariz", "D) Rim"},
            {"A) 7", "B) 8", "C) 9", "D) 10"},
            {"A) Tolstoj", "B) Dostojevski", "C) Šekspir", "D) Homer"},
            {"A) Amazon", "B) Nil", "C) Jangce", "D) Misisipi"},
            {"A) 1935", "B) 1937", "C) 1939", "D) 1941"}
    };

    private static final int[] CORRECT = {2, 1, 2, 1, 2};

    private int currentQuestion = 0;
    private int scorePlayer1    = 0;
    private int scorePlayer2    = 0;

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

        loadQuestion(currentQuestion);
        setupClickListeners();

        return view;
    }

    private void loadQuestion(int idx) {
        tvQuestionNumber.setText("Pitanje " + (idx + 1) + " / 5");
        tvTimer.setText("5");
        tvStatus.setText("");

        btnAnswerA.setText(ANSWERS[idx][0]);
        btnAnswerB.setText(ANSWERS[idx][1]);
        btnAnswerC.setText(ANSWERS[idx][2]);
        btnAnswerD.setText(ANSWERS[idx][3]);

        resetButtonColors();
        updateScoreUI();
    }

    private void setupClickListeners() {
        btnAnswerA.setOnClickListener(v -> onAnswerClicked(0));
        btnAnswerB.setOnClickListener(v -> onAnswerClicked(1));
        btnAnswerC.setOnClickListener(v -> onAnswerClicked(2));
        btnAnswerD.setOnClickListener(v -> onAnswerClicked(3));
    }

    private void onAnswerClicked(int answerIdx) {
        Button[] buttons = {btnAnswerA, btnAnswerB, btnAnswerC, btnAnswerD};

        setAllButtonsEnabled(false);

        if (answerIdx == CORRECT[currentQuestion]) {
            buttons[answerIdx].setBackgroundColor(COLOR_CORRECT);
            tvStatus.setText("Tačno! +10 bodova");
            scorePlayer1 += 10;
        } else {
            buttons[answerIdx].setBackgroundColor(COLOR_WRONG);
            buttons[CORRECT[currentQuestion]].setBackgroundColor(COLOR_CORRECT);
            tvStatus.setText("Netačno! -5 bodova");
            scorePlayer1 -= 5;
        }

        updateScoreUI();

        rootView.postDelayed(() -> {
            currentQuestion++;
            if (currentQuestion < QUESTIONS.length) {
                loadQuestion(currentQuestion);
            } else {
                tvStatus.setText("Igra završena!");
                setAllButtonsEnabled(false);
            }
        }, 1000);
    }

    private void resetButtonColors() {
        btnAnswerA.setBackgroundColor(COLOR_DEFAULT);
        btnAnswerB.setBackgroundColor(COLOR_DEFAULT);
        btnAnswerC.setBackgroundColor(COLOR_DEFAULT);
        btnAnswerD.setBackgroundColor(COLOR_DEFAULT);
        setAllButtonsEnabled(true);
    }

    private void setAllButtonsEnabled(boolean enabled) {
        btnAnswerA.setEnabled(enabled);
        btnAnswerB.setEnabled(enabled);
        btnAnswerC.setEnabled(enabled);
        btnAnswerD.setEnabled(enabled);
    }

    private void updateScoreUI() {
        tvScorePlayer1.setText(String.valueOf(scorePlayer1));
        tvScorePlayer2.setText(String.valueOf(scorePlayer2));
    }
}