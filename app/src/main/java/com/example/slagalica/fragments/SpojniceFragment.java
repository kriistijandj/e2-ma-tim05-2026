package com.example.slagalica.fragments;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.slagalica.R;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class SpojniceFragment extends Fragment {

    // ====== UI ======
    private TextView tvRound, tvTimer, tvKriterijum, tvStatus;
    private TextView tvScorePlayer1, tvScorePlayer2;
    private MaterialButton[] leftButtons  = new MaterialButton[5];
    private MaterialButton[] rightButtons = new MaterialButton[5];

    // ====== BOJE ======
    private static final int COLOR_DEFAULT   = Color.parseColor("#F2AF14");
    private static final int COLOR_SELECTED  = Color.parseColor("#3A7BFF");
    private static final int COLOR_CONNECTED = Color.parseColor("#4CAF50");
    private static final int COLOR_WAITING   = Color.parseColor("#90A4AE");
    private static final int COLOR_WRONG     = Color.parseColor("#D32F2F");

    // ====== PODACI ======
    // Runda 0: izvođači i pjesme, Runda 1: naučnici i otkrića
    private static final String[][] LEFT_TERMS = {
            {"Riblja čorba", "Bajaga", "EKV", "Đ. Balašević", "Generacija 5"},
            {"Einstein",     "Tesla",  "Curie", "Newton",     "Darwin"}
    };
    private static final String[][] RIGHT_TERMS = {
            {"Kao dva sveta", "Tri put sam video Tita", "Ona spava", "Šta ima novo", "Nebo"},
            {"Relativnost",   "Struja",                 "Radijum",   "Gravitacija", "Evolucija"}
    };
    private static final String[] KRITERIJUMI = {
            "Poveži izvođače sa njihovim pesmama",
            "Poveži naučnike sa njihovim otkrićima"
    };
    // Tačno mapiranje: left[i] -> right[i] (0-4 su svi tačni po redu)
    private static final int[] CORRECT_MAPPING = {0, 1, 2, 3, 4};

    // ====== FIREBASE ======
    private DatabaseReference gameRef;
    private String gameId;
    private String myPlayerId;
    private ValueEventListener roundStateListener;

    // ====== STANJE ======
    // currentRound: 0 ili 1
    // U rundi 0: player1 igra aktivan, player2 ispravlja
    // U rundi 1: player2 igra aktivan, player1 ispravlja
    private int currentRound = 0;

    // Faza: "waiting" = čekamo da aktivan igrač završi
    //       "fixing"  = fixing igrač ispravlja
    //       "done"    = runda gotova
    private String myPhase = "waiting";

    private int scorePlayer1 = 0;
    private int scorePlayer2 = 0;

    private int selectedLeftIndex  = -1;
    private int selectedRightIndex = -1;

    private boolean[] leftUsed    = new boolean[5];
    private boolean[] rightUsed   = new boolean[5];
    private int[]     connectedRight = new int[]{-1, -1, -1, -1, -1};

    // ====== STATISTIKA ======
    private int myConnectedCorrect = 0;
    private int myConnectedTotal   = 0;

    private CountDownTimer roundTimer;

    public SpojniceFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_spojnice, container, false);

        tvRound        = view.findViewById(R.id.tvRound);
        tvTimer        = view.findViewById(R.id.tvTimer);
        tvKriterijum   = view.findViewById(R.id.tvKriterijum);
        tvStatus       = view.findViewById(R.id.tvStatus);
        tvScorePlayer1 = view.findViewById(R.id.tvLeftScore);
        tvScorePlayer2 = view.findViewById(R.id.tvRightScore);

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

        // Čitaj argumente iz GameFragment lobija
        gameId     = "test_game_spojnice_001";
        myPlayerId = "player1";

        if (getArguments() != null) {
            gameId     = getArguments().getString("ROOM_ID",     "test_game_spojnice_001");
            myPlayerId = getArguments().getString("PLAYER_ROLE", "player1");
        }

        gameRef = FirebaseDatabase.getInstance().getReference("games").child(gameId);

        setupClickListeners();

        // Player1 inicijalizuje igru i briše staro stanje
        if ("player1".equals(myPlayerId)) {
            gameRef.removeValue((error, ref) -> startRound(0));
        } else {
            startRound(0);
        }

        return view;
    }

    // ==============================
    // DA LI SMIJE KLIKNUTI
    // ==============================

    private boolean canIClick() {
        if ("active".equals(myPhase)) {
            return (currentRound == 0 && "player1".equals(myPlayerId))
                    || (currentRound == 1 && "player2".equals(myPlayerId));
        }
        if ("fixing".equals(myPhase)) {
            return (currentRound == 0 && "player2".equals(myPlayerId))
                    || (currentRound == 1 && "player1".equals(myPlayerId));
        }
        return false;
    }

    // ==============================
    // POKRETANJE RUNDE
    // ==============================

    private void startRound(int round) {
        currentRound      = round;
        myPhase           = "active";
        selectedLeftIndex  = -1;
        selectedRightIndex = -1;

        for (int i = 0; i < 5; i++) {
            leftUsed[i]      = false;
            rightUsed[i]     = false;
            connectedRight[i] = -1;
        }

        tvRound.setText("Runda " + (round + 1) + " / 2");
        tvKriterijum.setText(KRITERIJUMI[round]);
        tvTimer.setText("30");

        for (int i = 0; i < 5; i++) {
            leftButtons[i].setText(LEFT_TERMS[round][i]);
            rightButtons[i].setText(RIGHT_TERMS[round][i]);
            setButtonTint(leftButtons[i],  COLOR_DEFAULT);
            setButtonTint(rightButtons[i], COLOR_DEFAULT);
        }

        updateScoreUI();
        updateStatusAndTimer();

        // Slušaj stanje runde iz Firebase
        listenForRoundState(round);
    }

    private void updateStatusAndTimer() {
        boolean active = (currentRound == 0 && "player1".equals(myPlayerId))
                || (currentRound == 1 && "player2".equals(myPlayerId));
        boolean fixing = (currentRound == 0 && "player2".equals(myPlayerId))
                || (currentRound == 1 && "player1".equals(myPlayerId));

        if ("active".equals(myPhase)) {
            if (active) {
                tvStatus.setText("🎮 Igrač " + (currentRound == 0 ? "1" : "2")
                        + " (Ti) igraš — poveži pojmove!");
                startCountdown(30, this::onActiveTimerFinished);
            } else {
                tvStatus.setText("⏳ Igrač " + (currentRound == 0 ? "1" : "2")
                        + " igra, čekaj...");
                tvTimer.setText("—");
            }
        } else if ("fixing".equals(myPhase)) {
            if (fixing) {
                tvStatus.setText("🔧 Igrač " + (currentRound == 0 ? "2" : "1")
                        + " (Ti) ispravlja!");
                startCountdown(30, this::onFixingTimerFinished);
            } else {
                tvStatus.setText("⏳ Igrač " + (currentRound == 0 ? "2" : "1")
                        + " ispravlja...");
                tvTimer.setText("—");
            }
        }
    }

    // ==============================
    // TAJMER
    // ==============================

    private void startCountdown(int seconds, Runnable onFinish) {
        if (roundTimer != null) roundTimer.cancel();
        roundTimer = new CountDownTimer(seconds * 1000L, 1000) {
            @Override public void onTick(long ms) { tvTimer.setText(String.valueOf(ms / 1000 + 1)); }
            @Override public void onFinish() {
                tvTimer.setText("0");
                onFinish.run();
            }
        }.start();
    }

    private void onActiveTimerFinished() {
        // Aktivan igrač završio – šalje signal u Firebase
        submitActivePhaseEnd();
    }

    private void onFixingTimerFinished() {
        // Fixing igrač završio – šalje signal u Firebase
        gameRef.child("rounds").child(String.valueOf(currentRound))
                .child("phase").setValue("done");
    }

    // ==============================
    // CLICK LISTENERI
    // ==============================

    private void setupClickListeners() {
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            leftButtons[i].setOnClickListener(v  -> onLeftClicked(idx));
            rightButtons[i].setOnClickListener(v -> onRightClicked(idx));
        }
    }

    private void onLeftClicked(int idx) {
        if (!canIClick()) return;
        if (leftUsed[idx]) return;

        if (selectedLeftIndex == idx) {
            setButtonTint(leftButtons[idx], COLOR_DEFAULT);
            selectedLeftIndex = -1;
        } else {
            if (selectedLeftIndex != -1)
                setButtonTint(leftButtons[selectedLeftIndex], COLOR_DEFAULT);
            selectedLeftIndex = idx;
            setButtonTint(leftButtons[idx], COLOR_SELECTED);
            if (selectedRightIndex != -1)
                makeConnection(selectedLeftIndex, selectedRightIndex);
        }
    }

    private void onRightClicked(int idx) {
        if (!canIClick()) return;
        if (rightUsed[idx]) return;

        if (selectedLeftIndex != -1) {
            makeConnection(selectedLeftIndex, idx);
        } else {
            if (selectedRightIndex != -1)
                setButtonTint(rightButtons[selectedRightIndex], COLOR_DEFAULT);
            selectedRightIndex = idx;
            setButtonTint(rightButtons[idx], COLOR_WAITING);
        }
    }

    // ==============================
    // NAPRAVI VEZU LOKALNO + UPIŠI U FIREBASE
    // ==============================

    private void makeConnection(int leftIdx, int rightIdx) {
        leftUsed[leftIdx]       = true;
        rightUsed[rightIdx]     = true;
        connectedRight[leftIdx] = rightIdx;

        boolean correct = (CORRECT_MAPPING[leftIdx] == rightIdx);
        setButtonTint(leftButtons[leftIdx],   correct ? COLOR_CONNECTED : COLOR_WRONG);
        setButtonTint(rightButtons[rightIdx], correct ? COLOR_CONNECTED : COLOR_WRONG);

        selectedLeftIndex  = -1;
        selectedRightIndex = -1;

        myConnectedTotal++;
        if (correct) myConnectedCorrect++;

        gameRef.child("rounds").child(String.valueOf(currentRound))
                .child("connections").child(String.valueOf(leftIdx))
                .child("rightIdx").setValue(rightIdx);
        gameRef.child("rounds").child(String.valueOf(currentRound))
                .child("connections").child(String.valueOf(leftIdx))
                .child("madeBy").setValue(myPlayerId);

        if (correct) {
            if ("player1".equals(myPlayerId)) scorePlayer1 += 2;
            else                              scorePlayer2 += 2;
            updateScoreUI();
        }

        // Ako su svi spojeni u aktivnoj fazi – završi aktivnu fazu
        if (allConnected() && "active".equals(myPhase)) {
            if (roundTimer != null) roundTimer.cancel();
            submitActivePhaseEnd();
            return;
        }

        // Ako su svi slobodni spojeni u fixing fazi – odmah završi, ne čekaj tajmer
        if ("fixing".equals(myPhase) && allConnected()) {
            if (roundTimer != null) roundTimer.cancel();
            tvTimer.setText("—");
            gameRef.child("rounds").child(String.valueOf(currentRound))
                    .child("phase").setValue("done");
        }
    }
    private boolean allConnected() {
        for (boolean u : leftUsed) if (!u) return false;
        return true;
    }

    // ==============================
    // KRAJ AKTIVNE FAZE
    // ==============================

    private void submitActivePhaseEnd() {
        // Provjeri ima li netačnih veza, obriši ih
        boolean anyWrong = false;
        for (int i = 0; i < 5; i++) {
            if (leftUsed[i] && connectedRight[i] != CORRECT_MAPPING[i]) {
                anyWrong = true;
                gameRef.child("rounds").child(String.valueOf(currentRound))
                        .child("connections").child(String.valueOf(i)).removeValue();
                leftUsed[i]      = false;
                rightUsed[connectedRight[i]] = false;
                connectedRight[i] = -1;
                setButtonTint(leftButtons[i],          COLOR_DEFAULT);
                setButtonTint(rightButtons[connectedRight[i] >= 0 ? connectedRight[i] : i], COLOR_DEFAULT);
            }
        }

        boolean anyFree = false;
        for (boolean u : leftUsed) if (!u) { anyFree = true; break; }

        String nextPhase = (anyFree || anyWrong) ? "fixing" : "done";
        gameRef.child("rounds").child(String.valueOf(currentRound))
                .child("phase").setValue(nextPhase);
    }

    // ==============================
    // SLUŠAJ STANJE RUNDE IZ FIREBASE
    // (oba igrača vide iste promjene)
    // ==============================

    private void listenForRoundState(int round) {
        if (roundStateListener != null) {
            gameRef.child("rounds").child(String.valueOf(round))
                    .removeEventListener(roundStateListener);
        }

        roundStateListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String phase = snapshot.child("phase").getValue(String.class);
                if (phase == null) return;

                // Sinhronizuj veze protivnika na ekran
                DataSnapshot connections = snapshot.child("connections");
                for (DataSnapshot connSnap : connections.getChildren()) {
                    int leftIdx      = Integer.parseInt(connSnap.getKey());
                    Integer rightIdx = connSnap.child("rightIdx").getValue(Integer.class);
                    String madeBy    = connSnap.child("madeBy").getValue(String.class);

                    if (rightIdx == null) continue;
                    if (madeBy != null && madeBy.equals(myPlayerId)) continue; // već prikazano lokalno

                    boolean correct = (CORRECT_MAPPING[leftIdx] == rightIdx);
                    leftUsed[leftIdx]    = true;
                    rightUsed[rightIdx]  = true;
                    connectedRight[leftIdx] = rightIdx;

                    setButtonTint(leftButtons[leftIdx],   correct ? COLOR_CONNECTED : COLOR_WRONG);
                    setButtonTint(rightButtons[rightIdx], correct ? COLOR_CONNECTED : COLOR_WRONG);
                }

                // Reaguj na promjenu faze
                if ("fixing".equals(phase) && "active".equals(myPhase)) {
                    myPhase = "fixing";
                    if (roundTimer != null) roundTimer.cancel();
                    updateStatusAndTimer();

                } else if ("done".equals(phase) && !"done".equals(myPhase)) {
                    myPhase = "done";
                    if (roundTimer != null) roundTimer.cancel();
                    onRoundDone(round);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };

        gameRef.child("rounds").child(String.valueOf(round))
                .addValueEventListener(roundStateListener);
    }

    // ==============================
    // RUNDA ZAVRŠENA
    // ==============================

    private void onRoundDone(int round) {
        if (roundStateListener != null) {
            gameRef.child("rounds").child(String.valueOf(round))
                    .removeEventListener(roundStateListener);
            roundStateListener = null;
        }

        tvStatus.setText("✅ Runda " + (round + 1) + " završena!");
        tvTimer.setText("—");

        if (getView() != null) {
            getView().postDelayed(() -> {
                if (round + 1 < 2) {
                    startRound(round + 1);
                } else {
                    endGame();
                }
            }, 2000);
        }
    }

    // ==============================
    // KRAJ IGRE
    // ==============================

    private void endGame() {
        if (roundTimer != null) roundTimer.cancel();

        gameRef.child("status").setValue("finished");
        gameRef.child("scores").child("player1").setValue(scorePlayer1);
        gameRef.child("scores").child("player2").setValue(scorePlayer2);

        tvRound.setText("Igra završena!");
        tvTimer.setText("—");
        tvKriterijum.setText("");

        String p1Label = "player1".equals(myPlayerId) ? "Ti" : "Protivnik";
        String p2Label = "player2".equals(myPlayerId) ? "Ti" : "Protivnik";
        int myScore    = "player1".equals(myPlayerId) ? scorePlayer1 : scorePlayer2;
        int oppScore   = "player1".equals(myPlayerId) ? scorePlayer2 : scorePlayer1;
        boolean iWon   = myScore > oppScore;

        String rezultat;
        if (scorePlayer1 > scorePlayer2)
            rezultat = "🏆 " + p1Label + " pobijedio!\n" + p1Label + ": " + scorePlayer1 + "\n" + p2Label + ": " + scorePlayer2;
        else if (scorePlayer2 > scorePlayer1)
            rezultat = "🏆 " + p2Label + " pobijedio!\n" + p1Label + ": " + scorePlayer1 + "\n" + p2Label + ": " + scorePlayer2;
        else
            rezultat = "Neriješeno!\n" + p1Label + ": " + scorePlayer1 + "\n" + p2Label + ": " + scorePlayer2;

        tvStatus.setText(rezultat);
        updateScoreUI();

        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        if (uid != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("stats.spojnice.connected",    FieldValue.increment(myConnectedCorrect));
            updates.put("stats.spojnice.total",        FieldValue.increment(myConnectedTotal));
            updates.put("stats.spojnice.wins",         FieldValue.increment(iWon ? 1 : 0));
            updates.put("stats.spojnice.losses",       FieldValue.increment(iWon ? 0 : 1));
            updates.put("stats.global.totalGames",     FieldValue.increment(1));
            updates.put("stats.global.wins",           FieldValue.increment(iWon ? 1 : 0));
            updates.put("stats.global.losses",         FieldValue.increment(iWon ? 0 : 1));
            FirebaseFirestore.getInstance().collection("users").document(uid).update(updates);
        }

        if (getView() != null) {
            getView().postDelayed(() -> {
                if (getView() != null)
                    androidx.navigation.Navigation.findNavController(getView()).navigate(R.id.nav_game);
            }, 3000);
        }
    }

    // ====== HELPERI ======

    private void setButtonTint(MaterialButton btn, int color) {
        btn.setBackgroundTintList(ColorStateList.valueOf(color));
    }

    private void updateScoreUI() {
        if ("player1".equals(myPlayerId)) {
            tvScorePlayer1.setText("Ti: " + scorePlayer1);
            tvScorePlayer2.setText("Protivnik: " + scorePlayer2);
        } else {
            tvScorePlayer1.setText("Protivnik: " + scorePlayer1);
            tvScorePlayer2.setText("Ti: " + scorePlayer2);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (roundTimer != null) roundTimer.cancel();
        if (roundStateListener != null && gameRef != null) {
            gameRef.child("rounds").child(String.valueOf(currentRound))
                    .removeEventListener(roundStateListener);
        }
    }
}