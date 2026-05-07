package com.example.slagalica.games;

import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.slagalica.R;

public class SkockoFragment extends Fragment {

    private TableLayout tableAttempts;
    private TextView[][] attemptCells = new TextView[6][4];
    private LinearLayout[] feedbackContainers = new LinearLayout[6];

    private int currentRow = 0;
    private int currentCol = 0;
    private CountDownTimer timer;
    private TextView tvTimer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_skocko, container, false);

        tableAttempts = v.findViewById(R.id.tableAttempts);
        tvTimer = v.findViewById(R.id.tvTimerSkocko);

        setupGrid();
        setupInputButtons(v);

        v.findViewById(R.id.btnSubmit).setOnClickListener(view -> submitAttempt());
        v.findViewById(R.id.btnDelete).setOnClickListener(view -> deleteSymbol());

        startTimer();

        return v;
    }

    private void setupGrid() {
        float density = getResources().getDisplayMetrics().density;
        int cellSize = (int) (45 * density);
        int margin = (int) (4 * density);
        int feedbackWidth = (int) (110 * density);

        for (int i = 0; i < 6; i++) {
            TableRow row = new TableRow(getContext());
            row.setLayoutParams(new TableLayout.LayoutParams(
                    TableLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));
            row.setGravity(Gravity.CENTER);
            row.setPadding(0, margin, 0, margin);

            for (int j = 0; j < 4; j++) {
                TextView cell = new TextView(getContext());
                TableRow.LayoutParams lp = new TableRow.LayoutParams(cellSize, cellSize);
                lp.setMargins(margin, 0, margin, 0);
                cell.setLayoutParams(lp);

                cell.setBackgroundColor(Color.parseColor("#124E54"));
                cell.setTextColor(Color.WHITE);
                cell.setGravity(Gravity.CENTER);
                cell.setTextSize(20);
                cell.setTypeface(null, android.graphics.Typeface.BOLD);

                attemptCells[i][j] = cell;
                row.addView(cell);
            }

            View separator = new View(getContext());
            row.addView(separator, new TableRow.LayoutParams((int)(20 * density), 1));

            LinearLayout feedback = new LinearLayout(getContext());
            feedback.setOrientation(LinearLayout.HORIZONTAL);
            feedback.setGravity(Gravity.CENTER);

            TableRow.LayoutParams feedbackLp = new TableRow.LayoutParams(feedbackWidth, TableRow.LayoutParams.MATCH_PARENT);
            feedback.setLayoutParams(feedbackLp);

            feedbackContainers[i] = feedback;
            row.addView(feedback);

            // Inicijalno popunjavanje praznim kružićima
            for (int k = 0; k < 4; k++) {
                addCircle(feedback, Color.parseColor("#33444444"));
            }

            tableAttempts.addView(row);
        }
    }

    // ISPRAVLJENO: Samo jedna verzija addCircle metode
    private void addCircle(LinearLayout container, int color) {
        View circle = new View(getContext());
        float density = getResources().getDisplayMetrics().density;

        int size = (int) (18 * density);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMargins((int)(3 * density), 0, (int)(3 * density), 0);

        circle.setLayoutParams(lp);
        // Osiguraj da circle_shape.xml postoji u drawable folderu
        circle.setBackgroundResource(R.drawable.circle_shape);
        circle.getBackground().setTint(color);

        container.addView(circle);
    }

    private void setupInputButtons(View v) {
        int[] btnIds = {R.id.btnSkocko, R.id.btnKvadrat, R.id.btnKrug, R.id.btnSrce, R.id.btnTrougao, R.id.btnZvezda};
        for (int id : btnIds) {
            Button b = v.findViewById(id);
            if (b != null) {
                b.setOnClickListener(view -> addSymbol(b.getText().toString()));
            }
        }
    }

    private void addSymbol(String symbol) {
        if (currentRow < 6 && currentCol < 4) {
            attemptCells[currentRow][currentCol].setText(symbol);
            currentCol++;
        }
    }

    private void deleteSymbol() {
        if (currentCol > 0) {
            currentCol--;
            attemptCells[currentRow][currentCol].setText("");
        }
    }

    private void submitAttempt() {
        if (currentCol < 4) {
            Toast.makeText(getContext(), "Popunite sva polja!", Toast.LENGTH_SHORT).show();
            return;
        }

        // GUI simulacija provere
        showFeedback(currentRow, 2, 1);

        currentRow++;
        currentCol = 0;

        if (currentRow == 6) {
            Toast.makeText(getContext(), "Kraj runde!", Toast.LENGTH_LONG).show();
        }
    }

    private void showFeedback(int rowIdx, int redCount, int yellowCount) {
        feedbackContainers[rowIdx].removeAllViews();
        feedbackContainers[rowIdx].setPadding(10, 0, 10, 0);

        for (int i = 0; i < redCount; i++) {
            addCircle(feedbackContainers[rowIdx], Color.RED);
        }

        for (int i = 0; i < yellowCount; i++) {
            addCircle(feedbackContainers[rowIdx], Color.YELLOW);
        }

        int grayCount = 4 - (redCount + yellowCount);
        for (int i = 0; i < grayCount; i++) {
            addCircle(feedbackContainers[rowIdx], Color.parseColor("#444444"));
        }
    }

    private void startTimer() {
        if (timer != null) timer.cancel();
        timer = new CountDownTimer(30000, 1000) {
            public void onTick(long ms) {
                if (tvTimer != null) tvTimer.setText(ms / 1000 + "s");
            }
            public void onFinish() {
                if (getContext() != null) Toast.makeText(getContext(), "Vreme isteklo!", Toast.LENGTH_SHORT).show();
            }
        }.start();
    }

    @Override
    public void onDestroyView() {
        if (timer != null) timer.cancel();
        super.onDestroyView();
    }
}