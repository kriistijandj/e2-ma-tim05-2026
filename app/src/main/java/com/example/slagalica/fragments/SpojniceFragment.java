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

public class SpojniceFragment extends Fragment {

    private TextView tvRound, tvTimer, tvKriterijum, tvStatus;
    private TextView tvScorePlayer1, tvScorePlayer2;
    private Button btnPotvrdi;

    private Button[] leftButtons = new Button[5];
    private Button[] rightButtons = new Button[5];

    private int selectedLeftIndex  = -1;
    private int selectedRightIndex = -1;

    private boolean[] leftUsed  = new boolean[5];
    private boolean[] rightUsed = new boolean[5];

    // connectedRight[i] = j znači lijevi[i] je spojen sa desnim[j]
    private int[] connectedRight = new int[]{-1, -1, -1, -1, -1};

    private static final int COLOR_SELECTED  = Color.parseColor("#1976D2");
    private static final int COLOR_CONNECTED = Color.parseColor("#C8E6C9");
    private static final int COLOR_DEFAULT = Color.parseColor("#90A4AE");    private static final int COLOR_WAITING   = Color.parseColor("#FFF9C4");

    // Primjer podataka – zamijeniti sa podacima iz baze
    private static final String[] LEFT_TERMS  = {
            "Riblja čorba", "Bajaga", "EKV", "Đ. Balašević", "Generacija 5"
    };
    private static final String[] RIGHT_TERMS = {
            "Kao dva sveta", "Tri put sam video Tita", "Ona spava", "Šta ima novo", "Nebo"
    };

    private int currentRound  = 1;
    private int scorePlayer1  = 0;
    private int scorePlayer2  = 0;

    public SpojniceFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_spojnice, container, false);

        tvRound        = view.findViewById(R.id.tvRound);
        tvTimer        = view.findViewById(R.id.tvTimer);
        tvKriterijum   = view.findViewById(R.id.tvKriterijum);
        tvStatus       = view.findViewById(R.id.tvStatus);
        tvScorePlayer1 = view.findViewById(R.id.tvScorePlayer1);
        tvScorePlayer2 = view.findViewById(R.id.tvScorePlayer2);
        btnPotvrdi     = view.findViewById(R.id.btnPotvrdi);

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

        setupRound();
        setupClickListeners();

        return view;
    }

    private void setupRound() {
        selectedLeftIndex  = -1;
        selectedRightIndex = -1;
        for (int i = 0; i < 5; i++) {
            connectedRight[i] = -1;
            leftUsed[i]  = false;
            rightUsed[i] = false;
        }

        tvRound.setText("Runda " + currentRound + " / 2");
        tvTimer.setText("30s");
        tvStatus.setText("");
        btnPotvrdi.setEnabled(true);
        btnPotvrdi.setText("Potvrdi vezu");

        for (int i = 0; i < 5; i++) {
            leftButtons[i].setText(LEFT_TERMS[i]);
            leftButtons[i].setBackgroundColor(COLOR_DEFAULT);
            leftButtons[i].setEnabled(true);

            rightButtons[i].setText(RIGHT_TERMS[i]);
            rightButtons[i].setBackgroundColor(COLOR_DEFAULT);
            rightButtons[i].setEnabled(true);
        }

        updateScoreUI();
    }

    private void setupClickListeners() {
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            leftButtons[i].setOnClickListener(v  -> onLeftClicked(idx));
            rightButtons[i].setOnClickListener(v -> onRightClicked(idx));
        }
        btnPotvrdi.setOnClickListener(v -> onPotvrdiClicked());
    }

    private void onLeftClicked(int idx) {
        if (leftUsed[idx]) return;

        if (selectedLeftIndex == idx) {
            // Deselektuj
            leftButtons[idx].setBackgroundColor(COLOR_DEFAULT);
            selectedLeftIndex = -1;
        } else {
            // Poništi staru selekciju
            if (selectedLeftIndex != -1) {
                leftButtons[selectedLeftIndex].setBackgroundColor(COLOR_DEFAULT);
            }
            selectedLeftIndex = idx;
            leftButtons[idx].setBackgroundColor(COLOR_SELECTED);

            // Ako je desni već čekao – napravi vezu
            if (selectedRightIndex != -1) {
                makeConnection(selectedLeftIndex, selectedRightIndex);
            }
        }
    }

    private void onRightClicked(int idx) {
        if (rightUsed[idx]) return;

        if (selectedLeftIndex != -1) {
            // Lijevi je već selektovan – napravi vezu
            makeConnection(selectedLeftIndex, idx);
        } else {
            // Nema lijevog – označi desni kao "čeka"
            if (selectedRightIndex != -1) {
                rightButtons[selectedRightIndex].setBackgroundColor(COLOR_DEFAULT);
            }
            selectedRightIndex = idx;
            rightButtons[idx].setBackgroundColor(COLOR_WAITING);
        }
    }

    private void makeConnection(int leftIdx, int rightIdx) {
        leftUsed[leftIdx]       = true;
        rightUsed[rightIdx]     = true;
        connectedRight[leftIdx] = rightIdx;

        leftButtons[leftIdx].setBackgroundColor(COLOR_CONNECTED);
        rightButtons[rightIdx].setBackgroundColor(COLOR_CONNECTED);

        selectedLeftIndex  = -1;
        selectedRightIndex = -1;

        if (allConnected()) {
            tvStatus.setText("Svi pojmovi su spojeni!");
            btnPotvrdi.setText("Završi rundu");
        }
    }

    private boolean allConnected() {
        for (boolean u : leftUsed) if (!u) return false;
        return true;
    }

    private void onPotvrdiClicked() {
        if (currentRound == 1) {
            currentRound = 2;
            tvStatus.setText("Runda 2 počinje!");
            setupRound();
        } else {
            tvStatus.setText("Igra završena!");
            btnPotvrdi.setEnabled(false);
        }
    }

    private void updateScoreUI() {
        tvScorePlayer1.setText(String.valueOf(scorePlayer1));
        tvScorePlayer2.setText(String.valueOf(scorePlayer2));
    }
}