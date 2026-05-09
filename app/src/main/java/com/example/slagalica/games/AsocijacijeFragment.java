package com.example.slagalica.games;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.slagalica.R;

public class AsocijacijeFragment extends Fragment {

    private Button[][] fields = new Button[4][4];
    private Button[] columnSolutions = new Button[4];
    private Button btnFinal;
    private EditText etGuess;
    private TextView tvTimer, tvLeftName, tvRightName, tvLeftScore, tvRightScore;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_asocijacije, container, false);

        // HUD
        tvLeftName = v.findViewById(R.id.tvLeftName);
        tvRightName = v.findViewById(R.id.tvRightName);
        tvLeftScore = v.findViewById(R.id.tvLeftScore);
        tvRightScore = v.findViewById(R.id.tvRightScore);
        tvTimer = v.findViewById(R.id.tvTimer);

        // Dummy podaci (GUI)
        tvLeftName.setText("Igrač 1");
        tvRightName.setText("Igrač 2");
        tvLeftScore.setText("Bodovi: 0");
        tvRightScore.setText("Bodovi: 0");
        tvTimer.setText("120s");

        etGuess = v.findViewById(R.id.etGuess);
        btnFinal = v.findViewById(R.id.btnFinal);

        // Kolone
        setupColumn(v, 0, new int[]{R.id.a1, R.id.a2, R.id.a3, R.id.a4}, R.id.colA);
        setupColumn(v, 1, new int[]{R.id.b1, R.id.b2, R.id.b3, R.id.b4}, R.id.colB);
        setupColumn(v, 2, new int[]{R.id.c1, R.id.c2, R.id.c3, R.id.c4}, R.id.colC);
        setupColumn(v, 3, new int[]{R.id.d1, R.id.d2, R.id.d3, R.id.d4}, R.id.colD);

        // Konačno rešenje – samo vizuelno
        btnFinal.setOnClickListener(view -> {
            btnFinal.setText("KONAČNO REŠENJE");
            btnFinal.setBackgroundColor(Color.parseColor("#4CAF50"));
        });

        return v;
    }

    private void setupColumn(View v, int col,
                             int[] fieldIds,
                             int solutionId) {

        for (int i = 0; i < 4; i++) {
            fields[col][i] = v.findViewById(fieldIds[i]);

            fields[col][i].setOnClickListener(view -> {
                Button b = (Button) view;
                b.setText("Otvoreno");
                b.setEnabled(false);
                b.setBackgroundColor(Color.WHITE);
                b.setTextColor(Color.BLACK);
            });
        }

        columnSolutions[col] = v.findViewById(solutionId);
        columnSolutions[col].setOnClickListener(view -> {
            Button b = (Button) view;
            b.setText("REŠENJE");
            b.setBackgroundColor(Color.parseColor("#4CAF50"));
        });
    }
}