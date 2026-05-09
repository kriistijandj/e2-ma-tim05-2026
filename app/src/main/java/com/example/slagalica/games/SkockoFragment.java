package com.example.slagalica.games;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
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
    private ImageView[][] cells = new ImageView[6][4];
    private LinearLayout[] feedbackContainers = new LinearLayout[6];

    private int currentRow = 0;
    private int currentCol = 0;
    private boolean[] rowLocked = new boolean[6];

    private TextView tvLeftName, tvRightName, tvLeftScore, tvRightScore, tvTimer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_skocko, container, false);

        tableAttempts = v.findViewById(R.id.tableAttempts);

        tvLeftName = v.findViewById(R.id.tvLeftName);
        tvRightName = v.findViewById(R.id.tvRightName);
        tvLeftScore = v.findViewById(R.id.tvLeftScore);
        tvRightScore = v.findViewById(R.id.tvRightScore);
        tvTimer = v.findViewById(R.id.tvTimerSkocko);

        tvLeftName.setText("Igrač 1");
        tvRightName.setText("Igrač 2");
        tvLeftScore.setText("Bodovi: 0");
        tvRightScore.setText("Bodovi: 0");
        tvTimer.setText("30s");

        setupGrid();
        setupButtons(v);

        return v;
    }

    private void setupGrid() {
        float d = getResources().getDisplayMetrics().density;
        int size = (int) (45 * d);
        int margin = (int) (4 * d);
        int feedbackWidth = (int) (90 * d);

        for (int i = 0; i < 6; i++) {
            TableRow row = new TableRow(getContext());
            row.setGravity(Gravity.CENTER);

            for (int j = 0; j < 4; j++) {
                ImageView iv = new ImageView(getContext());
                TableRow.LayoutParams lp = new TableRow.LayoutParams(size, size);
                lp.setMargins(margin, margin, margin, margin);
                iv.setLayoutParams(lp);
                iv.setBackgroundColor(Color.parseColor("#888888"));
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);

                cells[i][j] = iv;
                row.addView(iv);
            }

            LinearLayout feedback = new LinearLayout(getContext());
            feedback.setOrientation(LinearLayout.HORIZONTAL);
            feedback.setGravity(Gravity.CENTER);

            TableRow.LayoutParams flp =
                    new TableRow.LayoutParams(feedbackWidth, TableRow.LayoutParams.MATCH_PARENT);
            feedback.setLayoutParams(flp);

            feedbackContainers[i] = feedback;
            row.addView(feedback);

            tableAttempts.addView(row);
        }
    }

    private void setupButtons(View v) {
        bindSymbol(v, R.id.btnSkocko, R.drawable.ic_skocko);
        bindSymbol(v, R.id.btnKvadrat, R.drawable.ic_kvadrat);
        bindSymbol(v, R.id.btnKrug, R.drawable.ic_krug);
        bindSymbol(v, R.id.btnSrce, R.drawable.ic_srce);
        bindSymbol(v, R.id.btnTrougao, R.drawable.ic_trougao);
        bindSymbol(v, R.id.btnZvezda, R.drawable.ic_zvezda);

        v.findViewById(R.id.btnDelete).setOnClickListener(view -> deleteSymbol());
        v.findViewById(R.id.btnSubmit).setOnClickListener(view -> submitAttempt());
    }

    private void bindSymbol(View v, int btnId, int drawableId) {
        ImageButton btn = v.findViewById(btnId);
        btn.setOnClickListener(view -> addSymbol(drawableId));
    }

    private void addSymbol(int drawableId) {
        if (currentRow >= 6) return;
        if (rowLocked[currentRow]) return;
        if (currentCol >= 4) return;

        cells[currentRow][currentCol].setImageResource(drawableId);
        currentCol++;
    }

    private void deleteSymbol() {
        if (currentRow >= 6) return;
        if (rowLocked[currentRow]) return;
        if (currentCol == 0) return;

        currentCol--;
        cells[currentRow][currentCol].setImageDrawable(null);
    }

    private void submitAttempt() {
        if (currentCol < 4) {
            Toast.makeText(getContext(), "Popunite sva polja!", Toast.LENGTH_SHORT).show();
            return;
        }

        showFeedback(currentRow, 2, 1); // GUI simulacija

        rowLocked[currentRow] = true;
        currentRow++;
        currentCol = 0;

        if (currentRow == 6) {
            Toast.makeText(getContext(), "Kraj runde!", Toast.LENGTH_LONG).show();
        }
    }

    // ---------------- FEEDBACK ----------------

    private void showFeedback(int row, int red, int yellow) {
        LinearLayout fb = feedbackContainers[row];
        fb.removeAllViews();

        for (int i = 0; i < red; i++) {
            fb.addView(makeCircle(Color.RED));
        }
        for (int i = 0; i < yellow; i++) {
            fb.addView(makeCircle(Color.YELLOW));
        }
        for (int i = 0; i < 4 - red - yellow; i++) {
            fb.addView(makeCircle(Color.DKGRAY));
        }
    }

    private View makeCircle(int color) {
        View v = new View(getContext());
        float d = getResources().getDisplayMetrics().density;
        int size = (int) (12 * d);

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(size, size);
        lp.setMargins(4, 0, 4, 0);
        v.setLayoutParams(lp);
        v.setBackgroundColor(color);
        return v;
    }
}