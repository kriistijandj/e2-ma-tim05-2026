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
import androidx.navigation.Navigation;

import com.example.slagalica.R;
import com.example.slagalica.helper.MatchPresenceHelper;
import com.example.slagalica.models.skocko.FirebaseAttempt;
import com.example.slagalica.models.skocko.SkockoGameState;
import com.example.slagalica.models.skocko.SkockoSymbol;
import com.example.slagalica.viewmodel.SkockoViewModel;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SkockoFragment extends Fragment {

    private TableLayout tableAttempts;
    private ImageView[][] cells = new ImageView[7][4];
    private LinearLayout[] feedbackContainers = new LinearLayout[7];

    private TextView tvLeftName, tvRightName, tvLeftScore, tvRightScore, tvTimer;
    private TextView tvRoundResult;

    private SkockoViewModel viewModel;
    private final List<SkockoSymbol> localAttempt = new ArrayList<>();
    private int currentCol = 0;
    private int activeRowInUi = 0;
    private int lastRenderedRound = -1;

    // ─── Navigacija ───────────────────────────────────────────────────────────
    private String matchId;
    private String myRole;
    private boolean isTournament;
    private String tournamentId;
    private ValueEventListener gameAdvanceListener;
    private boolean navigationScheduled = false;

    private MatchPresenceHelper presenceHelper;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_skocko, container, false);

        tableAttempts = v.findViewById(R.id.tableAttempts);
        tvLeftName    = v.findViewById(R.id.tvLeftName);
        tvRightName   = v.findViewById(R.id.tvRightName);
        tvLeftScore   = v.findViewById(R.id.tvLeftScore);
        tvRightScore  = v.findViewById(R.id.tvRightScore);
        tvTimer       = v.findViewById(R.id.tvTimerSkocko);
        tvRoundResult = v.findViewById(R.id.tvRoundResult);

        setupGrid();
        setupButtons(v);
        resetLocalAttempt();

        viewModel = new ViewModelProvider(this).get(SkockoViewModel.class);

        matchId = "test_game_001";
        myRole  = "player1";
        if (getArguments() != null) {
            matchId = getArguments().getString("MATCH_ID",     "test_game_001");
            myRole  = getArguments().getString("PLAYER_ROLE", "player1");
            isTournament = getArguments().getBoolean("IS_TOURNAMENT", false);
            tournamentId = getArguments().getString("TOURNAMENT_ID");
        }

        viewModel.init(matchId, myRole);

        setupPresence();

        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                new androidx.activity.OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                .setTitle("Napusti partiju")
                                .setMessage("Ako izađeš, gubiš partiju i ne dobijaš zvezde. Nastaviti?")
                                .setPositiveButton("Napusti", (d, w) -> {
                                    if (presenceHelper != null) presenceHelper.leaveMatch();
                                    Navigation.findNavController(requireView())
                                            .navigate(R.id.nav_home);
                                })
                                .setNegativeButton("Otkaži", null)
                                .show();
                    }
                }
        );

        viewModel.getTimerText().observe(getViewLifecycleOwner(),
                timeStr -> tvTimer.setText(timeStr));

        viewModel.getGameState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;
            renderUiFromState(state);
        });

        // Čeka završetak igre pa prelazi na sledeću
        viewModel.getGameState().observe(getViewLifecycleOwner(), state -> {
            if (state != null && "finished".equals(state.status) && !navigationScheduled) {
                navigationScheduled = true;
                listenForNextGame();
            }
        });

        return v;
    }

    private void setupPresence() {
        String myUid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
        presenceHelper = new com.example.slagalica.helper.MatchPresenceHelper(matchId, myUid);
        presenceHelper.markPresent();

        FirebaseDatabase.getInstance()
                .getReference("matches").child(matchId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String p1 = snapshot.child("player1Id").getValue(String.class);
                        String p2 = snapshot.child("player2Id").getValue(String.class);
                        String opponentUid = "player1".equals(myRole) ? p2 : p1;

                        if (opponentUid != null && presenceHelper != null) {
                            presenceHelper.listenForOpponentLeft(opponentUid, () -> {
                                if (viewModel != null) viewModel.onOpponentLeft();
                            });
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    // ─── Čekanje na Firebase pre navigacije ──────────────────────────────────

    private void listenForNextGame() {
        DatabaseReference ref = FirebaseDatabase.getInstance()
                .getReference("matches")
                .child(matchId)
                .child("currentGame");

        gameAdvanceListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Integer game = snapshot.getValue(Integer.class);

                // Skočko je case 3, prelazimo kad currentGame >= 4
                if (game != null && game >= 4) {
                    ref.removeEventListener(this);

                    if (!isAdded() || getView() == null) return;

                    Bundle args = new Bundle();
                    args.putString("MATCH_ID", matchId);
                    args.putString("PLAYER_ROLE", myRole);
                    args.putBoolean("IS_TOURNAMENT", isTournament);
                    args.putString("TOURNAMENT_ID", tournamentId);

                    Navigation.findNavController(requireView())
                            .navigate(R.id.nav_game, args);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };

        ref.addValueEventListener(gameAdvanceListener);
    }

    // ─── Cleanup ─────────────────────────────────────────────────────────────

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (gameAdvanceListener != null) {
            FirebaseDatabase.getInstance()
                    .getReference("matches")
                    .child(matchId)
                    .child("currentGame")
                    .removeEventListener(gameAdvanceListener);
            gameAdvanceListener = null;
        }
        if (presenceHelper != null) presenceHelper.detach();   // ← dodato
    }

    // ─── Renderovanje UI-ja ───────────────────────────────────────────────────

    private void renderUiFromState(SkockoGameState state) {
        if (state == null) return;

        if (state.round != lastRenderedRound) {
            resetLocalAttempt();
            lastRenderedRound = state.round;
        }

        if (state.showingRoundResult) {
            tvRoundResult.setVisibility(View.VISIBLE);
            tvRoundResult.setText("Runda 1 završena!\nSledeća runda počinje za trenutak...");
        } else {
            tvRoundResult.setVisibility(View.GONE);
        }

        int leftScore  = 0;
        int rightScore = 0;
        if (state.scores != null) {
            for (Map.Entry<String, Integer> entry : state.scores.entrySet()) {
                if (entry.getKey().equals(state.player1Id)) {
                    leftScore = entry.getValue();
                } else {
                    rightScore = entry.getValue();
                }
            }
        }

        tvLeftName.setText("Igrač 1"  + (state.activePlayer == 1 ? " ✎" : ""));
        tvRightName.setText("Igrač 2" + (state.activePlayer == 2 ? " ✎" : ""));
        tvLeftScore.setText("Bodovi: "  + leftScore);
        tvRightScore.setText("Bodovi: " + rightScore);

        clearGridGraphics();

        int i = 0;
        for (FirebaseAttempt attempt : state.attempts) {
            if (i >= 7) break;
            for (int j = 0; j < 4; j++) {
                SkockoSymbol sym = SkockoSymbol.valueOf(attempt.symbols.get(j));
                cells[i][j].setImageResource(sym.getDrawableId());
            }
            showFeedback(i, attempt.red, attempt.yellow);
            i++;
        }

        activeRowInUi = state.isOpponentChance ? 6 : state.attempts.size();

        if ("finished".equals(state.status)) {
            String resultMsg = "Igra Skočko je završena!";
            if (state.scores != null && state.scores.size() == 2) {
                int p1 = 0, p2 = 0;
                for (Map.Entry<String, Integer> entry : state.scores.entrySet()) {
                    if (entry.getKey().equals(state.player1Id)) p1 = entry.getValue();
                    else                                         p2 = entry.getValue();
                }
                if (p1 > p2)      resultMsg = "Igrač 1 pobjeđuje! (" + p1 + " : " + p2 + ")";
                else if (p2 > p1) resultMsg = "Igrač 2 pobjeđuje! (" + p1 + " : " + p2 + ")";
                else              resultMsg = "Nerešeno! (" + p1 + " : " + p2 + ")";
            }
            Toast.makeText(getContext(), resultMsg, Toast.LENGTH_LONG).show();
        }
    }

    // ─── Unos simbola ─────────────────────────────────────────────────────────

    private void addSymbol(int drawableId) {
        SkockoGameState state = viewModel.getGameState().getValue();
        if (state == null || "finished".equals(state.status)) return;

        boolean amIActive = (state.activePlayer == 1 && "player1".equals(viewModel.getMyRole())) ||
                (state.activePlayer == 2 && "player2".equals(viewModel.getMyRole()));
        if (!amIActive) return;

        if (currentCol >= 4 || activeRowInUi >= 7) return;

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
        viewModel.submitAttempt(new ArrayList<>(localAttempt));
        resetLocalAttempt();
    }

    private void resetLocalAttempt() {
        localAttempt.clear();
        for (int i = 0; i < 4; i++) localAttempt.add(null);
        currentCol = 0;
    }

    // ─── Grid i feedback ──────────────────────────────────────────────────────

    private void clearGridGraphics() {
        for (int i = 0; i < 7; i++) {
            feedbackContainers[i].removeAllViews();
            for (int j = 0; j < 4; j++) {
                if (i == activeRowInUi && currentCol > 0) continue;
                cells[i][j].setImageDrawable(null);
            }
        }
    }

    private void setupGrid() {
        float d    = getResources().getDisplayMetrics().density;
        int size   = (int) (45 * d);
        int margin = (int) (4 * d);
        int fbWidth = (int) (90 * d);

        for (int i = 0; i < 7; i++) {
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
            TableRow.LayoutParams flp = new TableRow.LayoutParams(fbWidth,
                    TableRow.LayoutParams.MATCH_PARENT);
            feedback.setLayoutParams(flp);
            feedbackContainers[i] = feedback;
            row.addView(feedback);
            tableAttempts.addView(row);
        }
    }

    private void setupButtons(View v) {
        bindSymbol(v, R.id.btnSkocko,  R.drawable.ic_skocko);
        bindSymbol(v, R.id.btnKvadrat, R.drawable.ic_kvadrat);
        bindSymbol(v, R.id.btnKrug,    R.drawable.ic_krug);
        bindSymbol(v, R.id.btnSrce,    R.drawable.ic_srce);
        bindSymbol(v, R.id.btnTrougao, R.drawable.ic_trougao);
        bindSymbol(v, R.id.btnZvezda,  R.drawable.ic_zvezda);
        v.findViewById(R.id.btnDelete).setOnClickListener(view -> deleteSymbol());
        v.findViewById(R.id.btnSubmit).setOnClickListener(view -> submitAttempt());
    }

    private void bindSymbol(View v, int btnId, int drawableId) {
        ((ImageButton) v.findViewById(btnId)).setOnClickListener(view -> addSymbol(drawableId));
    }

    private void showFeedback(int row, int red, int yellow) {
        LinearLayout fb = feedbackContainers[row];
        fb.removeAllViews();
        for (int i = 0; i < red; i++)               fb.addView(makeCircle(Color.RED));
        for (int i = 0; i < yellow; i++)            fb.addView(makeCircle(Color.YELLOW));
        for (int i = 0; i < 4 - red - yellow; i++) fb.addView(makeCircle(Color.DKGRAY));
    }

    private View makeCircle(int color) {
        View circle = new View(getContext());
        float d = getResources().getDisplayMetrics().density;
        int size = (int) (12 * d);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMargins(4, 0, 4, 0);
        circle.setLayoutParams(lp);
        circle.setBackgroundColor(color);
        return circle;
    }
}