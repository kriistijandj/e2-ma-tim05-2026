package com.example.slagalica.games;

import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.slagalica.R;
import com.example.slagalica.games.data.AssociationData;
import com.example.slagalica.models.AssociationGame;
import com.example.slagalica.models.Column;

public class AsocijacijeFragment extends Fragment {

    private AssociationGame game;
    private Button[][] fieldButtons = new Button[4][4];
    private Button[] columnSolutionButtons = new Button[4];
    private Button btnFinal;

    private TextView tvTurn, tvTimer, tvScore;
    private EditText etGuess;
    private CountDownTimer timer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_asocijacije, container, false);

        game = AssociationData.createGame();

        tvTurn = v.findViewById(R.id.tvTurn);
        tvTimer = v.findViewById(R.id.tvTimer);
        tvScore = v.findViewById(R.id.tvScore);
        etGuess = v.findViewById(R.id.etGuess);
        btnFinal = v.findViewById(R.id.btnFinal);

        // Mapiranje Polja
        setupColumn(0, v, new int[]{R.id.a1, R.id.a2, R.id.a3, R.id.a4}, R.id.colA);
        setupColumn(1, v, new int[]{R.id.b1, R.id.b2, R.id.b3, R.id.b4}, R.id.colB);
        setupColumn(2, v, new int[]{R.id.c1, R.id.c2, R.id.c3, R.id.c4}, R.id.colC);
        setupColumn(3, v, new int[]{R.id.d1, R.id.d2, R.id.d3, R.id.d4}, R.id.colD);

        // Final Guess Listener
        btnFinal.setOnClickListener(view -> {
            String guess = etGuess.getText().toString().trim();
            if (guess.isEmpty()) {
                show("Unesite rešenje prvo!");
                return;
            }

            if (game.guessFinal(guess)) {
                show("TAČNO!");
                revealEverything();
            } else {
                show("Pogrešno!");
                // Model već menja igrača (switchPlayer) u guessFinal metodi
            }
            etGuess.setText("");
            updateUI();
        });

        updateUI();
        startTimer();
        return v;
    }

    private void setupColumn(int colIdx, View v, int[] fieldIds, int solBtnId) {
        for (int row = 0; row < 4; row++) {
            int finalRow = row;
            fieldButtons[colIdx][row] = v.findViewById(fieldIds[row]);
            fieldButtons[colIdx][row].setOnClickListener(view -> {
                if (game.openField(colIdx, finalRow)) {
                    Column col = game.rounds[game.currentRound].columns[colIdx];
                    fieldButtons[colIdx][finalRow].setText(col.fields[finalRow]);
                    fieldButtons[colIdx][finalRow].setBackgroundColor(Color.WHITE);
                    fieldButtons[colIdx][finalRow].setTextColor(Color.BLACK);
                    // Nakon otvaranja polja u Slagalici se NE menja igrač,
                    // on ima pravo da pogađa (zato ne zovemo switch)
                }
            });
        }

        columnSolutionButtons[colIdx] = v.findViewById(solBtnId);
        columnSolutionButtons[colIdx].setOnClickListener(view -> {
            String guess = etGuess.getText().toString().trim();
            if (guess.isEmpty()) {
                show("Unesite rešenje u polje pa kliknite na kolonu.");
                return;
            }

            if (game.guessColumn(colIdx, guess)) {
                show("Tačna kolona!");
                revealColumn(colIdx);
            } else {
                show("Pogrešno!");
            }
            etGuess.setText("");
            updateUI();
        });
    }

    private void revealColumn(int colIdx) {
        Column col = game.rounds[game.currentRound].columns[colIdx];
        columnSolutionButtons[colIdx].setText(col.solution);
        columnSolutionButtons[colIdx].setBackgroundColor(Color.parseColor("#4CAF50"));
        columnSolutionButtons[colIdx].setTextColor(Color.WHITE);

        for (int i = 0; i < 4; i++) {
            fieldButtons[colIdx][i].setText(col.fields[i]);
            fieldButtons[colIdx][i].setEnabled(false);
            fieldButtons[colIdx][i].setBackgroundColor(Color.parseColor("#FFFFFF"));
        }
    }

    private void revealEverything() {
        for (int i = 0; i < 4; i++) revealColumn(i);
        btnFinal.setText(game.rounds[game.currentRound].finalSolution);
        btnFinal.setBackgroundColor(Color.parseColor("#4CAF50"));
        if (timer != null) timer.cancel();
    }

    private void updateUI() {
        tvTurn.setText("Na potezu: " + game.getCurrentPlayer().name);
        tvScore.setText("P1: " + game.getPlayer1().score + " | P2: " + game.getPlayer2().score);
    }

    private void startTimer() {
        if (timer != null) timer.cancel();
        timer = new CountDownTimer(120000, 1000) {
            public void onTick(long millis) {
                tvTimer.setText(millis / 1000 + "s");
            }
            public void onFinish() {
                show("Vreme isteklo!");
                revealEverything();
            }
        }.start();
    }

    private void show(String msg) {
        Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        if (timer != null) timer.cancel();
        super.onDestroyView();
    }
}