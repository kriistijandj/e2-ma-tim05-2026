package com.example.slagalica.games;

import android.graphics.Color;
import android.os.Bundle;
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
import androidx.lifecycle.ViewModelProvider;

import com.example.slagalica.R;
import com.example.slagalica.models.asocijacije.AsocijacijeGameState;
import com.example.slagalica.viewmodel.AsocijacijeViewModel;

import java.util.Map;

public class AsocijacijeFragment extends Fragment {

    private final Button[][] fields = new Button[4][4];
    private final Button[] columnSolutions = new Button[4];
    private Button btnFinal;
    private EditText etGuess;
    private TextView tvTimer, tvLeftName, tvRightName, tvLeftScore, tvRightScore;

    private boolean isClickPending = false;
    private AsocijacijeViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_asocijacije, container, false);

        tvLeftName = v.findViewById(R.id.tvLeftName);
        tvRightName = v.findViewById(R.id.tvRightName);
        tvLeftScore = v.findViewById(R.id.tvLeftScore);
        tvRightScore = v.findViewById(R.id.tvRightScore);
        tvTimer = v.findViewById(R.id.tvTimer);
        etGuess = v.findViewById(R.id.etGuess);
        btnFinal = v.findViewById(R.id.btnFinal);

        // Inicijalizacija koordinatne mreže dugmića
        setupColumnViews(v, 0, new int[]{R.id.a1, R.id.a2, R.id.a3, R.id.a4}, R.id.colA);
        setupColumnViews(v, 1, new int[]{R.id.b1, R.id.b2, R.id.b3, R.id.b4}, R.id.colB);
        setupColumnViews(v, 2, new int[]{R.id.c1, R.id.c2, R.id.c3, R.id.c4}, R.id.colC);
        setupColumnViews(v, 3, new int[]{R.id.d1, R.id.d2, R.id.d3, R.id.d4}, R.id.colD);

        // Inicijalizacija ViewModel-a
        viewModel = new ViewModelProvider(this).get(AsocijacijeViewModel.class);

        // Usklađeno sa Korak po Korak nomenklaturom ključeva
        String matchId = "test_game_001";
        String myRole = "player1";

        if (getArguments() != null) {
            matchId = getArguments().getString("MATCH_ID", "test_game_001");
            myRole = getArguments().getString("PLAYER_ROLE", "player1");
        }

        viewModel.init(matchId, myRole);
        //viewModel.setupInitialGameIfHost();

        // Klik na dugme KONAČNO (Slanje konačnog rešenja)
        btnFinal.setOnClickListener(view -> {
            String guess = etGuess.getText().toString();
            if (guess.trim().isEmpty()) {
                Toast.makeText(getContext(), "Unesite predlog u polje ispod!", Toast.LENGTH_SHORT).show();
                return;
            }
            boolean isCorrect = viewModel.submitGuess("FINAL", guess);
            if (isCorrect) Toast.makeText(getContext(), "Tačno konačno rešenje!", Toast.LENGTH_SHORT).show();
            etGuess.setText("");
        });

        // OSLUŠKIVANJE PROMENA (OBSERVERS)
        viewModel.getTimerText().observe(getViewLifecycleOwner(), text -> tvTimer.setText(text));

        viewModel.getGameState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;
            renderScreenFromState(state);
        });

        return v;
    }

    private void setupColumnViews(View v, int col, int[] fieldIds, int solutionId) {
        for (int i = 0; i < 4; i++) {
            final int rowIdx = i;
            fields[col][i] = v.findViewById(fieldIds[i]);

            // Klik na pojedinačno polje (npr. A1, B3...)
            fields[col][i].setOnClickListener(view -> {
                if (isClickPending) return;

                isClickPending = true;
                viewModel.openField(col, rowIdx);
            });
        }

        columnSolutions[col] = v.findViewById(solutionId);

        // Klik na rešenje kolone (Slanje rešenja za tu kolonu)
        columnSolutions[col].setOnClickListener(view -> {
            String guess = etGuess.getText().toString();
            if (guess.trim().isEmpty()) {
                Toast.makeText(getContext(), "Unesite predlog u polje ispod!", Toast.LENGTH_SHORT).show();
                return;
            }
            String colLetter = (col == 0) ? "A" : (col == 1) ? "B" : (col == 2) ? "C" : "D";

            isClickPending = true;
            boolean isCorrect = viewModel.submitGuess(colLetter, guess);
            if (isCorrect) Toast.makeText(getContext(), "Kolona " + colLetter + " je rešena!", Toast.LENGTH_SHORT).show();
            etGuess.setText("");
        });
    }

    private void renderScreenFromState(AsocijacijeGameState state) {
        isClickPending = false;

        tvLeftName.setText("Igrač 1" + (state.activePlayer == 1 ? " ★" : ""));
        tvRightName.setText("Igrač 2" + (state.activePlayer == 2 ? " ★" : ""));

        // ========================================================
        // KORAK PATTERN: Izvlačenje bodova preko UID-a iz scores mape
        // ========================================================
        int p1Score = 0;
        int p2Score = 0;

        if (state.scores != null) {
            // Ako mapa sadrži UID hosta (player1Id), izvlačimo njegove bodove za Igrača 1
            if (state.scores.containsKey(state.player1Id)) {
                Integer s1 = state.scores.get(state.player1Id);
                p1Score = s1 != null ? s1 : 0;
            }

            // Drugi UID u mapi koji nije player1Id dodeljuje se Igraču 2
            for (Map.Entry<String, Integer> entry : state.scores.entrySet()) {
                if (!entry.getKey().equals(state.player1Id)) {
                    p2Score = entry.getValue() != null ? entry.getValue() : 0;
                    break;
                }
            }
        }

        tvLeftScore.setText("Bodovi: " + p1Score);
        tvRightScore.setText("Bodovi: " + p2Score);

        // Provera da li sam ja trenutno aktivni igrač na osnovu uloge (role)
        boolean amIActive = (state.activePlayer == 1 && "player1".equals(viewModel.getMyRole())) ||
                (state.activePlayer == 2 && "player2".equals(viewModel.getMyRole()));

        // Iscrtavanje matrice polja i kolona na osnovu podataka iz baze
        for (int c = 0; c < 4; c++) {
            String colKey = (c == 0) ? "A" : (c == 1) ? "B" : (c == 2) ? "C" : "D";
            boolean isColResolved = Boolean.TRUE.equals(state.columnResolved.get(colKey));

            // Iscrtaj pojedinačna polja u koloni
            for (int r = 0; r < 4; r++) {
                boolean isFieldOpened = state.openedFields.get(c).get(r);
                if (isFieldOpened) {
                    fields[c][r].setText(viewModel.getFieldText(c, r));
                    fields[c][r].setBackgroundColor(Color.WHITE);
                    fields[c][r].setTextColor(Color.BLACK);
                    fields[c][r].setEnabled(false);
                } else {
                    fields[c][r].setText(colKey + (r + 1));
                    fields[c][r].setBackgroundColor(Color.parseColor("#3F51B5")); // Plava boja Slagalice
                    fields[c][r].setTextColor(Color.WHITE);

                    // Polje je aktivno samo ako sam ja na potezu i ako nismo u GuessOnly režimu.
                    fields[c][r].setEnabled(amIActive && !state.isGuessOnlyMode);
                }
            }

            // Iscrtaj dugme za rešenje kolone
            if (isColResolved) {
                columnSolutions[c].setText(viewModel.getColumnSolutionText(c));
                columnSolutions[c].setBackgroundColor(Color.parseColor("#4CAF50")); // Zelena
                columnSolutions[c].setTextColor(Color.WHITE);
                columnSolutions[c].setEnabled(false);
            } else {
                columnSolutions[c].setText("KOLONA " + colKey);
                columnSolutions[c].setBackgroundColor(Color.parseColor("#FFA000")); // Narandžasta
                columnSolutions[c].setTextColor(Color.WHITE);
                columnSolutions[c].setEnabled(amIActive);
            }
        }

        // Iscrtavanje dugmeta za Konačno rešenje
        if (state.finalResolved) {
            btnFinal.setText(viewModel.getFinalSolutionText());
            btnFinal.setBackgroundColor(Color.parseColor("#2E7D32")); // Tamno zelena
            btnFinal.setTextColor(Color.WHITE);
            btnFinal.setEnabled(false);
            etGuess.setEnabled(false);
        } else {
            btnFinal.setText("KONAČNO REŠENJE");
            btnFinal.setBackgroundColor(Color.parseColor("#D32F2F")); // Crvena
            btnFinal.setTextColor(Color.WHITE);
            btnFinal.setEnabled(amIActive);
            etGuess.setEnabled(amIActive);
        }

        // Ako je cela igra gotova ili je vreme isteklo, blokiraj unose
        if ("finished".equals(state.status)) {
            Toast.makeText(getContext(), "Igra Asocijacije je završena!", Toast.LENGTH_LONG).show();
            etGuess.setEnabled(false);
            btnFinal.setEnabled(false);
            for (int c = 0; c < 4; c++) {
                columnSolutions[c].setEnabled(false);
                for (int r = 0; r < 4; r++) {
                    fields[c][r].setEnabled(false);
                }
            }
        }
    }
}