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
import androidx.lifecycle.ViewModelProvider;

import com.example.slagalica.R;
import com.example.slagalica.models.skocko.FirebaseAttempt;
import com.example.slagalica.models.skocko.SkockoGameState;
import com.example.slagalica.models.skocko.SkockoSymbol;
import com.example.slagalica.viewmodel.SkockoViewModel;

import java.util.ArrayList;
import java.util.List;

public class SkockoFragment extends Fragment {

    private TableLayout tableAttempts;
    private ImageView[][] cells = new ImageView[6][4];
    private LinearLayout[] feedbackContainers = new LinearLayout[6];

    private TextView tvLeftName, tvRightName, tvLeftScore, tvRightScore, tvTimer;

    private SkockoViewModel viewModel;
    private final List<SkockoSymbol> localAttempt = new ArrayList<>();
    private int currentCol = 0;
    private int activeRowInUi = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_skocko, container, false);

        tableAttempts = v.findViewById(R.id.tableAttempts);
        tvLeftName = v.findViewById(R.id.tvLeftName);
        tvRightName = v.findViewById(R.id.tvRightName);
        tvLeftScore = v.findViewById(R.id.tvLeftScore);
        tvRightScore = v.findViewById(R.id.tvRightScore);
        tvTimer = v.findViewById(R.id.tvTimerSkocko);

        setupGrid();
        setupButtons(v);

        resetLocalAttempt();

        // Inicijalizacija ViewModel-a (Troslojna arhitektura)
        viewModel = new ViewModelProvider(this).get(SkockoViewModel.class);

        // Uzimamo podatke (Ovo posle menjate pravim matchmaking podacima)
        String gameId = "test_game_001";
        String myPlayerId = "player1"; // Na drugom uređaju stavite "player2"

        viewModel.init(gameId, myPlayerId);
        viewModel.setupInitialGameIfHost();

        // ---------------- POSMATRAČI (OBSERVERS) ----------------
        viewModel.getTimerText().observe(getViewLifecycleOwner(), timeStr -> tvTimer.setText(timeStr));

        viewModel.getGameState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;
            renderUiFromState(state);
        });

        return v;
    }

    private void renderUiFromState(SkockoGameState state) {
        // Ažuriraj rezultate i imena
        String ja = viewModel.getMyPlayerId();
        tvLeftName.setText("Igrač 1" + (state.activePlayer == 1 ? " ✎" : ""));
        tvRightName.setText("Igrač 2" + (state.activePlayer == 2 ? " ✎" : ""));
        tvLeftScore.setText("Bodovi: " + state.p1Score);
        tvRightScore.setText("Bodovi: " + state.p2Score);

        // Očisti kompletnu tabelu pre ponovnog iscrtavanja stanja iz baze
        clearGridGraphics();

        // Iscrtaj sve potvrđene pokušaje iz Firebase-a
        int i = 0;
        for (FirebaseAttempt attempt : state.attempts) {
            if (i >= 6) break;
            for (int j = 0; j < 4; j++) {
                SkockoSymbol sym = SkockoSymbol.valueOf(attempt.symbols.get(j));
                cells[i][j].setImageResource(sym.getDrawableId());
            }
            showFeedback(i, attempt.red, attempt.yellow);
            i++;
        }

        // Određujemo u kom redu trenutni igrač kuca (ako je on na potezu)
        activeRowInUi = state.isOpponentChance ? 5 : state.attempts.size();

        if ("finished".equals(state.status)) {
            Toast.makeText(getContext(), "Igra Skočko je završena!", Toast.LENGTH_LONG).show();
        }
    }

    private void addSymbol(int drawableId) {
        SkockoGameState state = viewModel.getGameState().getValue();
        if (state == null || "finished".equals(state.status)) return;

        // Provera da li sam JA uopšte na potezu
        boolean amIActive = (state.activePlayer == 1 && "player1".equals(viewModel.getMyPlayerId())) ||
                (state.activePlayer == 2 && "player2".equals(viewModel.getMyPlayerId()));
        if (!amIActive) return;

        if (currentCol >= 4 || activeRowInUi >= 6) return;

        cells[activeRowInUi][currentCol].setImageResource(drawableId);
        localAttempt.set(currentCol, SkockoSymbol.fromDrawableId(drawableId));
        currentCol++;
    }

    private void deleteSymbol() {
        if (currentCol == 0) return;
        currentCol--;
        cells[activeRowInUi][currentCol].setImageDrawable(null);
        localAttempt.set(currentCol, null);
    }

    private void submitAttempt() {
        if (currentCol < 4) {
            Toast.makeText(getContext(), "Popunite sva polja!", Toast.LENGTH_SHORT).show();
            return;
        }
        // Šaljemo lokalni pokušaj u ViewModel na proveru i slanje u Firebase
        viewModel.submitAttempt(new ArrayList<>(localAttempt));
        resetLocalAttempt();
    }

    private void resetLocalAttempt() {
        localAttempt.clear();
        for (int i = 0; i < 4; i++) localAttempt.add(null);
        currentCol = 0;
    }

    private void clearGridGraphics() {
        for (int i = 0; i < 6; i++) {
            feedbackContainers[i].removeAllViews();
            for (int j = 0; j < 4; j++) {
                // Ako je to red u kom trenutno igrač kuca lokalno, ne briši mu unos usred kucanja
                if (i == activeRowInUi && currentCol > 0) continue;
                cells[i][j].setImageDrawable(null);
            }
        }
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

            TableRow.LayoutParams flp = new TableRow.LayoutParams(feedbackWidth, TableRow.LayoutParams.MATCH_PARENT);
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

    private void showFeedback(int row, int red, int yellow) {
        LinearLayout fb = feedbackContainers[row];
        fb.removeAllViews();
        for (int i = 0; i < red; i++) fb.addView(makeCircle(Color.RED));
        for (int i = 0; i < yellow; i++) fb.addView(makeCircle(Color.YELLOW));
        for (int i = 0; i < 4 - red - yellow; i++) fb.addView(makeCircle(Color.DKGRAY));
    }

    private View makeCircle(int color) {
        View v = new View(getContext());
        float d = getResources().getDisplayMetrics().density;
        int size = (int) (12 * d);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMargins(4, 0, 4, 0);
        v.setLayoutParams(lp);
        v.setBackgroundColor(color);
        return v;
    }
}