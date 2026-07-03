package com.example.slagalica.fragments;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.slagalica.R;
import com.example.slagalica.helper.MatchPresenceHelper;
import com.example.slagalica.models.Match;
import com.example.slagalica.repository.TournamentRepository;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Transaction;

import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;

public class SpojniceFragment extends Fragment {

    // ====== UI ======
    private View rootView;
    private TextView tvRound, tvTimer, tvKriterijum, tvStatus;
    private TextView tvScorePlayer1, tvScorePlayer2;
    private MaterialButton[] leftButtons  = new MaterialButton[5];
    private MaterialButton[] rightButtons = new MaterialButton[5];

    private MaterialButton btnHomePage;
    private TournamentRepository repo;

    // ====== BOJE ======
    private static final int COLOR_DEFAULT   = Color.parseColor("#F2AF14");
    private static final int COLOR_SELECTED  = Color.parseColor("#3A7BFF");
    private static final int COLOR_CONNECTED = Color.parseColor("#4CAF50");
    private static final int COLOR_WAITING   = Color.parseColor("#90A4AE");
    private static final int COLOR_WRONG     = Color.parseColor("#D32F2F");

    // ====== PODACI ======
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
    private static final int[] CORRECT_MAPPING = {0, 1, 2, 3, 4};

    // ====== KONSTANTE ZA ZVEZDICE / TOKENE ======
    private static final int STARS_FOR_WIN        = 10;   // bonus pobedniku
    private static final int STARS_LOST_ON_LOSS   = 10;   // penal gubitniku
    private static final int POINTS_PER_STAR      = 40;   // svakih 40 bodova = 1 zvezda (floor)
    private static final int STARS_PER_TOKEN      = 50;   // svakih 50 zvezdica = 1 token

    // ====== IDENTIFIKATORI ======
    private String matchId;
    private String myRole;      // "player1" ili "player2"
    private String myUid;
    private boolean isTournament;
    private String tournamentId;
    private boolean isChallenge;
    private String challengeId;

    // ====== FIREBASE ======
    private DatabaseReference gameRef;      // games/{matchId}/spojnice
    private DatabaseReference matchRef;     // matches/{matchId}
    private ValueEventListener roundStateListener;
    private ValueEventListener gameAdvanceListener;

    // ====== POČETNI SKOROVI IZ MEČA ======
    private int matchStartingScoreP1 = 0;
    private int matchStartingScoreP2 = 0;

    // ====== STANJE ======
    private int currentRound = 0;

    // Faza: "active" = aktivan igrač igra, "fixing" = drugi ispravlja, "done" = runda gotova
    private String myPhase = "active";

    private int scorePlayer1 = 0;
    private int scorePlayer2 = 0;

    private int selectedLeftIndex  = -1;
    private int selectedRightIndex = -1;

    private boolean[] leftUsed      = new boolean[5];
    private boolean[] rightUsed     = new boolean[5];
    private int[]     connectedRight = new int[]{-1, -1, -1, -1, -1};

    // ====== NAVIGACIJA ======
    private boolean matchFinishedRegistered = false;

    // ====== STATISTIKA ======
    private int myConnectedCorrect = 0;
    private int myConnectedTotal   = 0;

    private CountDownTimer roundTimer;

    private MatchPresenceHelper presenceHelper;

    private boolean opponentLeft = false;

    private boolean isFriendly = false;

    public SpojniceFragment() {}

// Unutar onCreateView ili onCreate dodaj inicijalizaciju:

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_spojnice, container, false);
        rootView = view;

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

        btnHomePage = view.findViewById(R.id.btnHomePage);
        btnHomePage.setOnClickListener(v -> {
            leaveMatch();

            androidx.navigation.Navigation
                    .findNavController(requireView())
                    .navigate(R.id.nav_home);
            // U turniru, pobednik polufinala treba da se vrati na ekran turnira
            // (da prati/odigra finale), ne na početnu stranicu.
            if (isChallenge && challengeId != null && !challengeId.isEmpty()) {
                // U izazovu, posle partije se ide na ekran sa rezultatom izazova
                // (koji čeka da svi učesnici završe svoju solo partiju).
                Bundle challengeArgs = new Bundle();
                challengeArgs.putString("CHALLENGE_ID", challengeId);
                androidx.navigation.Navigation
                        .findNavController(requireView())
                        .navigate(R.id.nav_challenge_result, challengeArgs);
            } else if (isTournament && tournamentId != null && !tournamentId.isEmpty()) {
                Bundle tourArgs = new Bundle();
                tourArgs.putString("TOURNAMENT_ID", tournamentId);
                androidx.navigation.Navigation
                        .findNavController(requireView())
                        .navigate(R.id.nav_tournament, tourArgs);
            } else {
                androidx.navigation.Navigation
                        .findNavController(requireView())
                        .navigate(R.id.nav_home);
            }
        });
        repo = new com.example.slagalica.repository.TournamentRepository();
        // ── Čitaj identifikatore iz argumenata ───────────────────────────────
        matchId = "test_game_001";
        myRole  = "player1";
        if (getArguments() != null) {
            matchId = getArguments().getString("MATCH_ID",    "test_game_001");
            myRole  = getArguments().getString("PLAYER_ROLE", "player1");
            isTournament = getArguments().getBoolean("IS_TOURNAMENT", false);
            tournamentId = getArguments().getString("TOURNAMENT_ID");
            isChallenge = getArguments().getBoolean("IS_CHALLENGE", false);
            challengeId = getArguments().getString("CHALLENGE_ID");
        }

        myUid    = FirebaseAuth.getInstance().getCurrentUser().getUid();
        gameRef  = FirebaseDatabase.getInstance()
                .getReference("games").child(matchId).child("spojnice");
        matchRef = FirebaseDatabase.getInstance()
                .getReference("matches").child(matchId);

        setupClickListeners();
        loadMatchScores();
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
                                    androidx.navigation.Navigation
                                            .findNavController(requireView())
                                            .navigate(R.id.nav_home);
                                })
                                .setNegativeButton("Otkaži", null)
                                .show();
                    }
                }
        );

        return view;
    }
    private boolean isHost() {
        return "player1".equals(myRole) || (opponentLeft && "player2".equals(myRole));
    }

    private void leaveMatch() {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        FirebaseDatabase.getInstance()
                .getReference("players")
                .child(uid)
                .child("inMatch")
                .setValue(false);

        if (presenceHelper != null) {
            presenceHelper.leaveMatch();
        }
    }

    private void setupPresence() {
        presenceHelper = new com.example.slagalica.helper.MatchPresenceHelper(matchId, myUid);
        if (isChallenge && challengeId != null) presenceHelper.setChallengeContext(challengeId);
        presenceHelper.markPresent();

        matchRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String p1 = snapshot.child("player1Id").getValue(String.class);
                String p2 = snapshot.child("player2Id").getValue(String.class);
                String opponentUid = "player1".equals(myRole) ? p2 : p1;

                if (opponentUid != null && presenceHelper != null) {
                    presenceHelper.listenForOpponentLeft(opponentUid, SpojniceFragment.this::onOpponentLeft);
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void onOpponentLeft() {
        opponentLeft = true;
        autoSkipIfOpponentAbsent();
    }
    private void autoSkipIfOpponentAbsent() {
        if (!opponentLeft) return;

        boolean iAmActive = (currentRound == 0 && "player1".equals(myRole))
                || (currentRound == 1 && "player2".equals(myRole));
        boolean iAmFixing = (currentRound == 0 && "player2".equals(myRole))
                || (currentRound == 1 && "player1".equals(myRole));

        if ("active".equals(myPhase) && !iAmActive) {
            // protivnik je trebalo da igra aktivnu fazu -> odmah pređi na "fixing"
            if (roundTimer != null) { roundTimer.cancel(); roundTimer = null; }
            gameRef.child("rounds").child(String.valueOf(currentRound))
                    .child("phase").setValue("fixing");
        } else if ("fixing".equals(myPhase) && !iAmFixing) {
            // protivnik je trebalo da ispravlja -> odmah pređi na "done"
            if (roundTimer != null) { roundTimer.cancel(); roundTimer = null; }
            gameRef.child("rounds").child(String.valueOf(currentRound))
                    .child("phase").setValue("done");
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (roundTimer != null) roundTimer.cancel();
        if (roundStateListener != null) {
            gameRef.child("rounds").child(String.valueOf(currentRound))
                    .removeEventListener(roundStateListener);
            roundStateListener = null;
        }
        if (gameAdvanceListener != null) {
            matchRef.child("currentGame").removeEventListener(gameAdvanceListener);
            gameAdvanceListener = null;
        }
        if (presenceHelper != null) presenceHelper.detach();   // ← dodato
    }

    // ─────────────────────────────────────────────────────────────────────────
    // KORAK 1 — UČITAJ POČETNE SCOROVE IZ MEČA
    // ─────────────────────────────────────────────────────────────────────────

    private void loadMatchScores() {

        matchRef.child("isFriendly").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean value = snapshot.getValue(Boolean.class);
                isFriendly = Boolean.TRUE.equals(value);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });

        matchRef.child("scores").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot child : snapshot.getChildren()) {
                    String uid  = child.getKey();
                    Integer val = child.getValue(Integer.class);
                    int score   = val != null ? val : 0;

                    if (myUid.equals(uid)) {
                        if ("player1".equals(myRole)) matchStartingScoreP1 = score;
                        else                          matchStartingScoreP2 = score;
                    } else {
                        if ("player1".equals(myRole)) matchStartingScoreP2 = score;
                        else                          matchStartingScoreP1 = score;
                    }
                }
                startGame();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                startGame(); // nastavi čak i ako učitavanje ne uspe
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // KORAK 2 — POKRETANJE IGRE
    // ─────────────────────────────────────────────────────────────────────────

    private void startGame() {
        scorePlayer1 = matchStartingScoreP1;
        scorePlayer2 = matchStartingScoreP2;
        updateScoreUI();

        if (isHost()) {
            // Domaćin briše staro stanje i inicijalizuje igru
            gameRef.removeValue((error, ref) -> startRound(0));
        } else {
            startRound(0);
        }
    }
    private boolean canIClick() {
        if ("active".equals(myPhase)) {
            return (currentRound == 0 && "player1".equals(myRole))
                    || (currentRound == 1 && "player2".equals(myRole));
        }
        if ("fixing".equals(myPhase)) {
            return (currentRound == 0 && "player2".equals(myRole))
                    || (currentRound == 1 && "player1".equals(myRole));
        }
        return false;
    }
    private void startRound(int round) {
        currentRound       = round;
        myPhase            = "active";
        selectedLeftIndex  = -1;
        selectedRightIndex = -1;

        for (int i = 0; i < 5; i++) {
            leftUsed[i]       = false;
            rightUsed[i]      = false;
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
        autoSkipIfOpponentAbsent();

        listenForRoundState(round);
    }

    private void updateStatusAndTimer() {
        boolean iAmActive = (currentRound == 0 && "player1".equals(myRole))
                || (currentRound == 1 && "player2".equals(myRole));
        boolean iAmFixing = (currentRound == 0 && "player2".equals(myRole))
                || (currentRound == 1 && "player1".equals(myRole));

        if ("active".equals(myPhase)) {
            if (iAmActive) {
                tvStatus.setText("🎮 Igrač " + (currentRound == 0 ? "1" : "2")
                        + " (Ti) igraš — poveži pojmove!");
                startCountdown(30, this::onActiveTimerFinished);
            } else {
                tvStatus.setText("⏳ Igrač " + (currentRound == 0 ? "1" : "2")
                        + " igra, čekaj...");
                tvTimer.setText("—");
            }
        } else if ("fixing".equals(myPhase)) {
            if (iAmFixing) {
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

    // ─────────────────────────────────────────────────────────────────────────
    // TAJMER
    // ─────────────────────────────────────────────────────────────────────────

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
        submitActivePhaseEnd();
    }

    private void onFixingTimerFinished() {
        gameRef.child("rounds").child(String.valueOf(currentRound))
                .child("phase").setValue("done");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CLICK LISTENERI
    // ─────────────────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────────────────
    // NAPRAVI VEZU LOKALNO + UPIŠI U FIREBASE
    // ─────────────────────────────────────────────────────────────────────────

    private void makeConnection(int leftIdx, int rightIdx) {
        leftUsed[leftIdx]        = true;
        rightUsed[rightIdx]      = true;
        connectedRight[leftIdx]  = rightIdx;

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
                .child("madeBy").setValue(myRole);

        if (correct) {
            if ("player1".equals(myRole)) scorePlayer1 += 2;
            else                          scorePlayer2 += 2;
            updateScoreUI();
        }

        if (allConnected() && "active".equals(myPhase)) {
            if (roundTimer != null) roundTimer.cancel();
            submitActivePhaseEnd();
            return;
        }

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

    // ─────────────────────────────────────────────────────────────────────────
    // KRAJ AKTIVNE FAZE
    // ─────────────────────────────────────────────────────────────────────────

    private void submitActivePhaseEnd() {
        boolean anyWrong = false;
        for (int i = 0; i < 5; i++) {
            if (leftUsed[i] && connectedRight[i] != CORRECT_MAPPING[i]) {
                anyWrong = true;
                gameRef.child("rounds").child(String.valueOf(currentRound))
                        .child("connections").child(String.valueOf(i)).removeValue();
                int wrongRight = connectedRight[i];
                leftUsed[i]          = false;
                rightUsed[wrongRight] = false;
                connectedRight[i]    = -1;
                setButtonTint(leftButtons[i],         COLOR_DEFAULT);
                setButtonTint(rightButtons[wrongRight], COLOR_DEFAULT);
            }
        }

        boolean anyFree = false;
        for (boolean u : leftUsed) if (!u) { anyFree = true; break; }

        String nextPhase = (anyFree || anyWrong) ? "fixing" : "done";
        gameRef.child("rounds").child(String.valueOf(currentRound))
                .child("phase").setValue(nextPhase);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SLUŠAJ STANJE RUNDE IZ FIREBASE
    // ─────────────────────────────────────────────────────────────────────────

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
                    if (myRole.equals(madeBy)) continue; // već prikazano lokalno

                    // Preskači ako je veza već sinhronizovana lokalno
                    if (leftUsed[leftIdx] && connectedRight[leftIdx] == rightIdx) continue;

                    boolean correct = (CORRECT_MAPPING[leftIdx] == rightIdx);

                    // Ako je protivnik sada napravio tačnu vezu, dodaj mu bodove
                    if (correct && !leftUsed[leftIdx]) {
                        String oppRole = "player1".equals(myRole) ? "player2" : "player1";
                        if ("player1".equals(oppRole)) scorePlayer1 += 2;
                        else                           scorePlayer2 += 2;
                        updateScoreUI();
                    }

                    leftUsed[leftIdx]       = true;
                    rightUsed[rightIdx]     = true;
                    connectedRight[leftIdx] = rightIdx;

                    setButtonTint(leftButtons[leftIdx],   correct ? COLOR_CONNECTED : COLOR_WRONG);
                    setButtonTint(rightButtons[rightIdx], correct ? COLOR_CONNECTED : COLOR_WRONG);
                }

                if ("fixing".equals(phase) && "active".equals(myPhase)) {
                    myPhase = "fixing";
                    if (roundTimer != null) roundTimer.cancel();
                    updateStatusAndTimer();
                    autoSkipIfOpponentAbsent();
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

    // ─────────────────────────────────────────────────────────────────────────
    // RUNDA ZAVRŠENA
    // ─────────────────────────────────────────────────────────────────────────

    private void onRoundDone(int round) {
        if (roundStateListener != null) {
            gameRef.child("rounds").child(String.valueOf(round))
                    .removeEventListener(roundStateListener);
            roundStateListener = null;
        }

        tvStatus.setText("✅ Runda " + (round + 1) + " završena!");
        tvTimer.setText("—");

        if (rootView != null) {
            rootView.postDelayed(() -> {
                if (round + 1 < 2) {
                    startRound(round + 1);
                } else {
                    endGame();
                }
            }, 2000);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // KRAJ IGRE — upiši u matches/{matchId}/scores, ažuriraj statistiku,
    // zvezdice i tokene
    // ─────────────────────────────────────────────────────────────────────────

    private void endGame() {
        if (matchFinishedRegistered) return;
        matchFinishedRegistered = true;

        if (roundTimer != null) roundTimer.cancel();

        int myFinalScore = "player1".equals(myRole) ? scorePlayer1 : scorePlayer2;
        int oppFinalScore = "player1".equals(myRole) ? scorePlayer2 : scorePlayer1;
        // Napomena: u slučaju nerešenog rezultata (myFinalScore == oppFinalScore)
        // igrač se trenutno tretira kao "gubitnik" za potrebe obračuna zvezdica,
        // pošto specifikacija ne definiše poseban slučaj za nerešeno.
        boolean iWon;
        if (opponentLeft) {
            // Protivnik je napustio partiju negde usput -> automatski pobeđujem,
            // bez obzira na trenutni odnos bodova (tačka 3.f)
            iWon = true;
        } else {
            // Napomena: u slučaju nerešenog rezultata (myFinalScore == oppFinalScore)
            // igrač se trenutno tretira kao "gubitnik" za potrebe obračuna zvezdica,
            // pošto specifikacija ne definiše poseban slučaj za nerešeno.
            iWon = myFinalScore > oppFinalScore;
        }

        // ── ZAJEDNIČKI UPIS SKORA U REALTIME DATABASE (Radi i za turnir i za običan meč) ──
        if (matchRef != null) {
            matchRef.child("scores").child(myUid).setValue(myFinalScore);

            // ── Samo player1 inkremantira currentGame ─────────────────────────────
            if (isHost()) {
                matchRef.child("status").setValue("finished");
                // Samo player1 menja status meča na završen
                if ("player1".equals(myRole)) {
                    matchRef.child("status").setValue("finished");
                }
            }

            // ── GRANANJE LOGIKE NAGRAĐIVANJA ──
            boolean isTournament = getArguments() != null && getArguments().getBoolean("IS_TOURNAMENT", false);
            boolean isChallengeMatch = getArguments() != null && getArguments().getBoolean("IS_CHALLENGE", false);
            String challengeMatchId = getArguments() != null ? getArguments().getString("CHALLENGE_ID") : null;
            if (isTournament) {
                // Turnirski mod koristi posebna pravila nagrađivanja
                handleTournamentEnd(myUid, iWon, myFinalScore);
            } else if (isChallengeMatch && challengeMatchId != null) {
                new com.example.slagalica.repository.ChallengeRepository()
                        .submitChallengeResult(challengeMatchId, myUid, myFinalScore,
                                new com.example.slagalica.repository.ChallengeRepository.OnChallengeActionListener() {
                                    @Override public void onSuccess() {}
                                    @Override public void onFailure(String message) {
                                        if (getContext() != null) {
                                            Toast.makeText(getContext(), "Greška pri predaji rezultata izazova: " + message,
                                                    Toast.LENGTH_LONG).show();
                                        }
                                    }
                                });
            } else {
                // Regularni mod ili prijateljski mod
                if (isFriendly) {
                    // SAMO OVO DODAJEMO: Logika za dnevnu misiju prijateljske partije
                    handleFriendlyMission(myUid);
                } else {
                    // Regularni mod ažurira standardne zvezdice, tokene, statistike i dnevne misije
                    applyStarsTokensAndStats(
                            myUid,
                            iWon,
                            myFinalScore,
                            myConnectedCorrect,
                            myConnectedTotal
                    );
                }
            }

            // ── ZAJEDNIČKO KORISNIČKO INTERFEJS OSVEŽAVANJE (Radi i za turnir) ──
            tvRound.setText("Kraj igre!");
            tvTimer.setText("—");
            if (tvKriterijum != null) tvKriterijum.setText("");

            if (isChallengeMatch) {
                tvStatus.setText("Partija u izazovu završena!\nTvoj rezultat: " + myFinalScore + " poena.");
            } else {
                String p1Label = "player1".equals(myRole) ? "Ti" : "Protivnik";
                String p2Label = "player2".equals(myRole) ? "Ti" : "Protivnik";

                if (scorePlayer1 > scorePlayer2)
                    tvStatus.setText("🏆 Pobednik: " + p1Label + "\n" + p1Label + ": " + scorePlayer1 + "\n" + p2Label + ": " + scorePlayer2);
                else if (scorePlayer2 > scorePlayer1)
                    tvStatus.setText("🏆 Pobednik: " + p2Label + "\n" + p1Label + ": " + scorePlayer1 + "\n" + p2Label + ": " + scorePlayer2);
                else
                    tvStatus.setText("Nerešeno!\n" + p1Label + ": " + scorePlayer1 + "\n" + p2Label + ": " + scorePlayer2);
            }

            if (btnHomePage != null) btnHomePage.setVisibility(View.VISIBLE);
            updateScoreUI();
        }
    }
    private void handleFriendlyMission(String uid) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference userRef = db.collection("users").document(uid);

        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(userRef);
            Boolean alreadyPlayed = snapshot.getBoolean("dailyMissions.playedFriendly");

            if (alreadyPlayed == null || !alreadyPlayed) {
                // Ažuriraj misiju
                transaction.update(userRef, "dailyMissions.playedFriendly", true);

                // Dodaj 3 zvezde za misiju
                transaction.update(userRef, "stars", FieldValue.increment(3));
                transaction.update(userRef, "weeklyStars", FieldValue.increment(3));
                transaction.update(userRef, "monthlyStars", FieldValue.increment(3));
            }
            return null;
        }).addOnFailureListener(e ->
                Log.e("FriendlyMission", "Greška pri upisu misije: " + e.getMessage())
        );
    }
    private void handleTournamentEnd(String uid, boolean won, int score) {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        DatabaseReference phaseRef = FirebaseDatabase.getInstance().getReference("matches")
                .child(matchId).child("tournamentPhase");

        phaseRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String phase = snapshot.getValue(String.class);
                if (phase == null) phase = "semi_finals";

                final String currentPhase = phase;
                DocumentReference userRef = firestore.collection("users").document(uid);



                firestore.runTransaction((Transaction.Function<Void>) transaction -> {
                    DocumentSnapshot userSnap = transaction.get(userRef);

                    Boolean alreadyWonTournament = userSnap.getBoolean("dailyMissions.wonTournamentGame");

                    if (won && (alreadyWonTournament == null || !alreadyWonTournament)) {
                        // 1. Obeleži da je misija "Pobedi partiju u turniru" ispunjena
                        transaction.update(userRef, "dailyMissions.wonTournamentGame", true);

                        // 2. Dodaj 3 zvezde za ispunjenu misiju
                        transaction.update(userRef, "stars", FieldValue.increment(3));
                        transaction.update(userRef, "weeklyStars", FieldValue.increment(3));
                        transaction.update(userRef, "monthlyStars", FieldValue.increment(3));
                    }

                    // Računanje zvezda iz bodova partije (40 bodova = 1 zvezda)
                    int starsFromScore = score / POINTS_PER_STAR;

                    long starsDelta = 0;
                    long tokensDelta = 0;

                    if ("semi_finals".equals(currentPhase)) {
                        if (won) {
                            tokensDelta = 2;
                            starsDelta = starsFromScore;
                        } else {
                            tokensDelta = 0;
                            starsDelta = 0;
                        }
                    } else if ("finals".equals(currentPhase)) {
                        if (won) {
                            tokensDelta = 3;
                            starsDelta = starsFromScore + 10;
                        } else {
                            tokensDelta = 0;
                            starsDelta = starsFromScore;
                        }
                    }

                    // Bezbedno ažuriranje profila
                    transaction.update(userRef, "stars", FieldValue.increment(starsDelta));
                    transaction.update(userRef, "tokens", FieldValue.increment(tokensDelta));

                    // Prosleđivanje rezultata u strukturu turnira
                    updateTournamentStructure(uid, won, currentPhase);

                    return null;
                }).addOnSuccessListener(unused -> {
                    Log.d("TournamentEnd", "Uspešno ažurirani turnirski podaci za korisnika.");
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "Partija završena! Turnir ažuriran.", Toast.LENGTH_SHORT).show();
                    }
                }).addOnFailureListener(e -> {
                    Log.e("TournamentEnd", "Greška pri transakciji turnira: ", e);
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "Greška pri čuvanju: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("TournamentEnd", "Otkazano čitanje faze meča: " + error.getMessage());
            }
        });
    }

    private void updateTournamentStructure(String uid, boolean won, String phase) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String tournamentId = getArguments().getString("TOURNAMENT_ID");
        if (tournamentId == null) return;

        DocumentReference tourRef = db.collection("tournaments").document(tournamentId);

        db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot tourSnap = transaction.get(tourRef);

            if ("semi_finals".equals(phase)) {
                if (won) {
                    Map<String, String> finals = (Map<String, String>) tourSnap.get("finals");
                    if (finals == null) finals = new HashMap<>();

                    // Postavi pobednika polufinala na upražnjeno mesto u finalu
                    if (finals.get("player1Id") == null || finals.get("player1Id").isEmpty()) {
                        transaction.update(tourRef, "finals.player1Id", uid);
                    } else if (finals.get("player2Id") == null || finals.get("player2Id").isEmpty()) {
                        transaction.update(tourRef, "finals.player2Id", uid);

                        // Oba finalista su poznata! Automatski generiši finalni meč u RTDB-u
                        String finalMatchId = FirebaseDatabase.getInstance().getReference().child("matches").push().getKey();
                        String p1 = finals.get("player1Id");

                        // Kreiraj finalni meč u Realtime Database-u
                        repo.createTournamentMatchInRTDB(finalMatchId, p1, uid, tournamentId, "finals");

                        transaction.update(tourRef, "finals.matchId", finalMatchId);
                        transaction.update(tourRef, "status", "finals"); // Menja se faza turnira!
                    }
                }
            } else if ("finals".equals(phase)) {
                if (won) {
                    transaction.update(tourRef, "finals.winnerId", uid);
                    transaction.update(tourRef, "status", "finished");
                }
            }
            return null;
        });
    }


    // Dodaj ove pomoćne metode na dno klase za generisanje ID-eva ciklusa (Zahtev e)
    private String getCurrentWeeklyCycleId() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int year = cal.get(java.util.Calendar.YEAR);
        int week = cal.get(java.util.Calendar.WEEK_OF_YEAR);
        return year + "_W" + week;
    }

    private String getCurrentMonthlyCycleId() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int year = cal.get(java.util.Calendar.YEAR);
        int month = cal.get(java.util.Calendar.MONTH) + 1; // Januar je 0
        return year + "_M" + String.format("%02d", month);
    }

    private void applyStarsTokensAndStats(String uid, boolean won, int myScore,
                                          int connectedCorrect, int connectedTotal) {

        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        DocumentReference userRef = firestore.collection("users").document(uid);

        final String currentWeeklyId = getCurrentWeeklyCycleId();
        final String currentMonthlyId = getCurrentMonthlyCycleId();

        firestore.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot snapshot = transaction.get(userRef);

            Long currentTokensLong = snapshot.getLong("tokens");
            long currentTokens     = currentTokensLong != null ? currentTokensLong : 0L;

            // Provera da li je počeo novi ciklus
            String lastWeeklyCycle = snapshot.getString("lastWeeklyCycle");
            String lastMonthlyCycle = snapshot.getString("lastMonthlyCycle");

            Long dbWeeklyStars = snapshot.getLong("weeklyStars");
            long currentWeeklyStars = (dbWeeklyStars != null) ? dbWeeklyStars : 0L;

            Long dbMonthlyStars = snapshot.getLong("monthlyStars");
            long currentMonthlyStars = (dbMonthlyStars != null) ? dbMonthlyStars : 0L;

            // Konstante i delta matematika za OVE partiju
            int starsFromScore = myScore / POINTS_PER_STAR;
            long starsDelta = won
                    ? (STARS_FOR_WIN + starsFromScore)
                    : (-STARS_LOST_ON_LOSS + starsFromScore);

            // Map za sve upise
            Map<String, Object> updates = new HashMap<>();

            // --- RESET LOGIKA AKO JE NOVI CIKLUS ---
            // Ako je počeo novi ciklus, podrazumevana vrednost je 0 + delta iz ove partije.
            // Ako je ciklus isti, koristimo FieldValue.increment da bezbedno dodamo/oduzmemo na serveru.
            if (lastWeeklyCycle == null || !lastWeeklyCycle.equals(currentWeeklyId)) {
                long newWeekly = Math.max(0, starsDelta);
                updates.put("weeklyStars", newWeekly);
            } else {
                // Ako igrač gubi zvezde, moramo lokalno proveriti da ne ode ispod nule na serveru
                if (currentWeeklyStars + starsDelta < 0) {
                    updates.put("weeklyStars", 0L); // Zakucaj na 0 ako ide u minus
                } else {
                    updates.put("weeklyStars", FieldValue.increment(starsDelta));
                }
            }

            if (lastMonthlyCycle == null || !lastMonthlyCycle.equals(currentMonthlyId)) {
                long newMonthly = Math.max(0, starsDelta);
                updates.put("monthlyStars", newMonthly);
            } else {
                if (currentMonthlyStars + starsDelta < 0) {
                    updates.put("monthlyStars", 0L);
                } else {
                    updates.put("monthlyStars", FieldValue.increment(starsDelta));
                }
            }

            // --- SISTEM TOKENA (Tvoj stari sistem) ---
            Long currentStarsLong  = snapshot.getLong("stars");
            long remainingStars    = currentStarsLong != null ? currentStarsLong : 0L;
            remainingStars += starsDelta;
            if (remainingStars < 0) remainingStars = 0;

            long tokensEarned = remainingStars / STARS_PER_TOKEN;
            remainingStars = remainingStars % STARS_PER_TOKEN;
            long newTokens = currentTokens + tokensEarned;

            updates.put("stars", remainingStars);
            updates.put("tokens", newTokens);
            updates.put("lastWeeklyCycle", currentWeeklyId);
            updates.put("lastMonthlyCycle", currentMonthlyId);

            // --- DNEVNE MISIJE ---
            Boolean alreadyWonToday = snapshot.getBoolean("dailyMissions.wonGame");
            if (won) {
                if (alreadyWonToday == null || !alreadyWonToday) {
                    updates.put("dailyMissions.wonGame", true);
                    remainingStars += 3;
                    if (remainingStars >= STARS_PER_TOKEN) {
                        newTokens += (remainingStars / STARS_PER_TOKEN);
                        remainingStars = remainingStars % STARS_PER_TOKEN;
                    }
                    updates.put("stars", remainingStars);
                    updates.put("tokens", newTokens);

                    // APSOLUTNO BEZBEDNO UVEĆANJE: Koristimo ponovo increment za bonus misije
                    updates.put("weeklyStars", FieldValue.increment(3));
                    updates.put("monthlyStars", FieldValue.increment(3));
                }
            }

            updates.put("stats.spojnice.connected", FieldValue.increment(connectedCorrect));
            updates.put("stats.spojnice.total",     FieldValue.increment(connectedTotal));
            updates.put("stats.spojnice.wins",      FieldValue.increment(won ? 1 : 0));
            updates.put("stats.spojnice.losses",    FieldValue.increment(won ? 0 : 1));
            updates.put("stats.global.totalGames",  FieldValue.increment(1));
            updates.put("stats.global.wins",        FieldValue.increment(won ? 1 : 0));
            updates.put("stats.global.losses",      FieldValue.increment(won ? 0 : 1));

            transaction.update(userRef, updates);
            return null;
        }).addOnSuccessListener(unused -> {
            Log.d("EndGame", "Uspešno i bezbedno ažuriran tekući ciklus.");
        }).addOnFailureListener(e -> {
            if (getContext() != null) {
                Toast.makeText(getContext(), "Greška: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }
    private void setButtonTint(MaterialButton btn, int color) {
        btn.setBackgroundTintList(ColorStateList.valueOf(color));
    }
    private void updateScoreUI() {
        if ("player1".equals(myRole)) {
            tvScorePlayer1.setText("Ti: " + scorePlayer1);
            tvScorePlayer2.setText("Protivnik: " + scorePlayer2);
        } else {
            tvScorePlayer1.setText("Protivnik: " + scorePlayer1);
            tvScorePlayer2.setText("Ti: " + scorePlayer2);
        }
    }
}