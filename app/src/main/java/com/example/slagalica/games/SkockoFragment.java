package com.example.slagalica.games;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.TextView;

import com.example.slagalica.R;

public class SkockoFragment extends Fragment {

    private GridLayout grid;
    private Button btnSubmit;

    private String[][] board = new String[6][4];
    private int row = 0;
    private int col = 0;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_skocko, container, false);

        grid = view.findViewById(R.id.gridAttempts);
        btnSubmit = view.findViewById(R.id.btnSubmit);

        initBoard();

        setupSymbolButtons(view);

        btnSubmit.setOnClickListener(v -> {
            if (col == 4) {   // samo ako je red popunjen
                row++;
                col = 0;
            }
        });

        return view;
    }

    private void initBoard() {
        for (int i = 0; i < 24; i++) {
            TextView tv = new TextView(getContext());
            tv.setText("");
            tv.setTextSize(18f);
            tv.setPadding(10, 10, 10, 10);
            grid.addView(tv);
        }
    }

    private void setupSymbolButtons(View view) {

        Button s = view.findViewById(R.id.btnSkocko);
        Button k = view.findViewById(R.id.btnKvadrat);
        Button kr = view.findViewById(R.id.btnKrug);
        Button sr = view.findViewById(R.id.btnSrce);
        Button t = view.findViewById(R.id.btnTrougao);
        Button z = view.findViewById(R.id.btnZvezda);

        View.OnClickListener listener = v -> {
            if (row >= 6 || col >= 4) return;

            String value = ((Button) v).getText().toString();

            int index = row * 4 + col;
            TextView cell = (TextView) grid.getChildAt(index);
            cell.setText(value);

            board[row][col] = value;
            col++;
        };

        s.setOnClickListener(listener);
        k.setOnClickListener(listener);
        kr.setOnClickListener(listener);
        sr.setOnClickListener(listener);
        t.setOnClickListener(listener);
        z.setOnClickListener(listener);
    }
}