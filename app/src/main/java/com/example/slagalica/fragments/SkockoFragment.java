package com.example.slagalica.fragments;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;

import com.example.slagalica.R;
import com.example.slagalica.models.SkockoAttempt;
import com.example.slagalica.models.SkockoSymbol;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link SkockoFragment} factory method to
 * create an instance of this fragment.
 */
public class SkockoFragment extends Fragment {

    private SkockoSymbol selectedSymbol = null;
    private int currentAttempt = 0;
    private int currentIndex = 0;

    private SkockoAttempt[] attempts = new SkockoAttempt[6];

    private GridLayout grid;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_skocko, container, false);

        grid = view.findViewById(R.id.gridAttempts);

        for (int i = 0; i < 6; i++) {
            attempts[i] = new SkockoAttempt();
        }

        setupButtons(view);
        setupGrid();

        return view;
    }

    private void setupButtons(View view) {

        view.findViewById(R.id.btnSkocko).setOnClickListener(v -> selectedSymbol = SkockoSymbol.SKOCKO);
        view.findViewById(R.id.btnKvadrat).setOnClickListener(v -> selectedSymbol = SkockoSymbol.KVADRAT);
        view.findViewById(R.id.btnKrug).setOnClickListener(v -> selectedSymbol = SkockoSymbol.KRUG);
        view.findViewById(R.id.btnSrce).setOnClickListener(v -> selectedSymbol = SkockoSymbol.SRCE);
        view.findViewById(R.id.btnTrougao).setOnClickListener(v -> selectedSymbol = SkockoSymbol.TROUGAO);
        view.findViewById(R.id.btnZvezda).setOnClickListener(v -> selectedSymbol = SkockoSymbol.ZVEZDA);

        view.findViewById(R.id.btnSubmit).setOnClickListener(v -> submit());
    }

    private void setupGrid() {
        grid.removeAllViews();

        for (int i = 0; i < 24; i++) {
            Button cell = new Button(getContext());
            cell.setText("");
            cell.setEnabled(false);
            grid.addView(cell);
        }
    }

    private void submit() {

        if (selectedSymbol == null) return;

        if (currentIndex < 4) {

            attempts[currentAttempt].setValue(currentIndex, selectedSymbol);

            int position = currentAttempt * 4 + currentIndex;
            Button cell = (Button) grid.getChildAt(position);
            cell.setText(symbolToText(selectedSymbol));

            currentIndex++;

            if (currentIndex == 4) {
                currentAttempt++;
                currentIndex = 0;
            }
        }
    }

    private String symbolToText(SkockoSymbol s) {
        switch (s) {
            case SKOCKO: return "S";
            case KVADRAT: return "□";
            case KRUG: return "○";
            case SRCE: return "♥";
            case TROUGAO: return "△";
            case ZVEZDA: return "★";
        }
        return "";
    }
}