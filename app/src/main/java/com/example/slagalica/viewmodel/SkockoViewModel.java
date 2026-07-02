// SkockoViewModel.java
package com.example.slagalica.viewmodel;

import android.os.CountDownTimer;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.slagalica.helper.SkockoHelper;
import com.example.slagalica.models.skocko.FirebaseAttempt;
import com.example.slagalica.models.skocko.SkockoFeedback;
import com.example.slagalica.models.skocko.SkockoGameState;
import com.example.slagalica.models.skocko.SkockoSymbol;
import com.example.slagalica.repository.SkockoRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SkockoViewModel extends ViewModel {

    private SkockoRepository repository;
    private final SkockoHelper skockoHelper = new SkockoHelper();

    private final MutableLiveData<SkockoGameState> gameState = new MutableLiveData<>();
    private final MutableLiveData<String> timerText = new MutableLiveData<>();

    private CountDownTimer timer;

    private String matchId;
    private String myRole;   // "player1" ili "player2"
    private String myUid;

    // Statistika
    private int myWinAttemptNumber = 0;
    private boolean myRoundWon = false;

    // Flags
    private boolean selfRegistered = false;
    private boolean matchFinishedRegistered = false;
    private boolean roundTransitionInProgress = false;

    // Početni bodovi iz meča
    private int matchStartingScoreP1 = 0;
    private int matchStartingScoreP2 = 0;

    // Server time offset
    private long serverTimeOffset = 0;

    // Za detekciju promene stanja tajmera
    private int lastActivePlayerState = -1;
    private boolean lastOpponentChanceState = false;
    private int lastRoundState = -1;

    private boolean opponentLeft = false;

    // ----------------------------------------------------------------
    // INIT
    // ----------------------------------------------------------------

    public void init(String matchId, String myRole) {
        this.matchId = matchId;
        this.myRole = myRole;
        this.myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        this.repository = new SkockoRepository(matchId);

        // 1. Učitaj server time offset, pa tek onda sve ostalo
        FirebaseDatabase.getInstance()
                .getReference(".info/serverTimeOffset")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Long offset = snapshot.getValue(Long.class);
                        serverTimeOffset = (offset != null) ? offset : 0;
                        loadMatchScores();
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        serverTimeOffset = 0;
                        loadMatchScores();
                    }
                });
    }

    private boolean isHost() {
        return "player1".equals(myRole) || (opponentLeft && "player2".equals(myRole));
    }

    private void forceOpponentTimeout(SkockoGameState state) {
        if (!state.isOpponentChance) {
            state.isOpponentChance = true;
            state.activePlayer = (state.rundaZapocinje == 1) ? 2 : 1;
            state.roundEndTime = serverNow() + 10_000;
            repository.updateGameState(state);
        } else {
            endRoundLogic(state);
        }
    }

    public void onOpponentLeft() {
        opponentLeft = true;

        // Ako igra još nije inicijalizovana (protivnik je otišao pre nego što je
        // ova igra i počela), preuzimam ulogu domaćina i pokrećem je odmah -
        // inače bi ostala zauvek neinicijalizovana jer je nju mogao da pokrene
        // samo player1.
        if (isHost()) {
            initializeGameIfNeeded();
        }

        SkockoGameState state = gameState.getValue();
        if (state == null || "finished".equals(state.status)) return;

        // 1) Protivnik je bio na potezu -> odmah preskoči na sledeću fazu/kraj runde
        //    umesto da se čeka pun tajmer (30s / 10s)
        if (!amIActive(state)) {
            if (timer != null) { timer.cancel(); timer = null; }
            forceOpponentTimeout(state);
            return;
        }

        // 2) Runda je gotova i čeka na host-prelaz koji je trebalo da pokrene
        //    player1, a on je taj koji je otišao
        if (isHost() && state.showingRoundResult && state.round == 1 && !roundTransitionInProgress) {
            roundTransitionInProgress = true;
            final Map<String, Integer> scoresSnapshot =
                    state.scores != null ? new HashMap<>(state.scores) : new HashMap<>();
            transitionToRound2(state, scoresSnapshot);
        }
    }

    private long serverNow() {
        return System.currentTimeMillis() + serverTimeOffset;
    }

    private void loadMatchScores() {
        FirebaseDatabase.getInstance()
                .getReference("matches")
                .child(matchId)
                .child("scores")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot playerSnapshot : snapshot.getChildren()) {
                            String playerUid = playerSnapshot.getKey();
                            Integer savedScore = playerSnapshot.getValue(Integer.class);
                            int score = (savedScore != null) ? savedScore : 0;

                            if (playerUid != null && playerUid.equals(myUid)) {
                                if ("player1".equals(myRole)) matchStartingScoreP1 = score;
                                else                          matchStartingScoreP2 = score;
                            } else {
                                if ("player1".equals(myRole)) matchStartingScoreP2 = score;
                                else                          matchStartingScoreP1 = score;
                            }
                        }
                        startListeningToGameState();
                        setupInitialGameIfHost();
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        startListeningToGameState();
                        setupInitialGameIfHost();
                    }
                });
    }

    // ----------------------------------------------------------------
    // LISTENER
    // ----------------------------------------------------------------

    private void startListeningToGameState() {
        repository.listenToGameState(state -> {
            if (state == null) return;

            // Player2 se registruje u scores mapu
            if (state.scores != null && !state.scores.containsKey(myUid) && !selfRegistered) {
                selfRegistered = true;
                int myStartScore = "player1".equals(myRole) ? matchStartingScoreP1 : matchStartingScoreP2;
                state.scores.put(myUid, myStartScore);
                repository.updateGameState(state);
                return;
            }

            // Player1 detektuje kraj runde 1 i pokreće prelaz
            if (state.showingRoundResult && state.round == 1
                    && isHost() && !roundTransitionInProgress) {
                roundTransitionInProgress = true;
                final Map<String, Integer> scoresSnapshot =
                        state.scores != null ? new HashMap<>(state.scores) : new HashMap<>();

                new CountDownTimer(3000, 1000) {
                    public void onTick(long ms) {}
                    public void onFinish() {
                        SkockoGameState current = gameState.getValue();
                        if (current == null) current = state;
                        transitionToRound2(current, scoresSnapshot);
                    }
                }.start();

                gameState.setValue(state);
                return;
            }

            gameState.setValue(state);
            handleTimerSync(state);
        });
    }

    // ----------------------------------------------------------------
    // GETTERS
    // ----------------------------------------------------------------

    public LiveData<SkockoGameState> getGameState() { return gameState; }
    public LiveData<String> getTimerText()          { return timerText; }
    public String getMyRole()                       { return myRole; }

    // ----------------------------------------------------------------
    // TIMER
    // ----------------------------------------------------------------

    private void handleTimerSync(SkockoGameState state) {
        if ("finished".equals(state.status)) {
            if (timer != null) { timer.cancel(); timer = null; }
            timerText.setValue("Igra završena");
            finishMatch(state);
            return;
        }

        boolean amIActive = amIActive(state);

        // Resetuj tajmer na svaku promenu stanja
        if (state.activePlayer != lastActivePlayerState ||
                state.isOpponentChance != lastOpponentChanceState ||
                state.round != lastRoundState) {
            if (timer != null) { timer.cancel(); timer = null; }
            lastActivePlayerState    = state.activePlayer;
            lastOpponentChanceState  = state.isOpponentChance;
            lastRoundState           = state.round;
        }

        if (amIActive) {
            if (timer == null) {
                // Koristimo roundEndTime ako postoji, inače fallback na fiksno vreme
                int seconds;
                if (state.roundEndTime > 0) {
                    long timeLeftMs = state.roundEndTime - serverNow();
                    if (timeLeftMs <= 0) { handleTimeOut(); return; }
                    seconds = (int) (timeLeftMs / 1000);
                } else {
                    seconds = state.isOpponentChance ? 10 : 30;
                }
                startTimer(seconds);
            }
        } else {
            if (timer != null) { timer.cancel(); timer = null; }
            timerText.setValue("Čeka se protivnik...");
        }
    }

    private void startTimer(int seconds) {
        if (timer != null) timer.cancel();
        timer = new CountDownTimer(seconds * 1000L, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timerText.setValue((millisUntilFinished / 1000) + "s");
            }
            @Override
            public void onFinish() {
                timerText.setValue("0s");
                timer = null;
                handleTimeOut();
            }
        }.start();
    }

    private void handleTimeOut() {
        SkockoGameState state = gameState.getValue();
        if (state == null || !amIActive(state)) return;

        // Ako je protivnik otišao, nema ko da iskoristi bonus šansu od 10s -
        // runda se odmah završava, umesto da se čeka pogodak koji nikad neće doći.
        if (!state.isOpponentChance && !opponentLeft) {
            state.isOpponentChance = true;
            state.activePlayer = (state.rundaZapocinje == 1) ? 2 : 1;
            state.roundEndTime = serverNow() + 10_000;
            repository.updateGameState(state);
        } else {
            endRoundLogic(state);
        }
    }

    // ----------------------------------------------------------------
    // KRAJ RUNDE
    // ----------------------------------------------------------------

    private void endRoundLogic(SkockoGameState state) {
        if (timer != null) { timer.cancel(); timer = null; }

        if (state.round == 1) {
            final Map<String, Integer> scoresSnapshot =
                    state.scores != null ? new HashMap<>(state.scores) : new HashMap<>();

            state.showingRoundResult = true;
            repository.updateGameState(state);

            // Samo player1 pokreće prelaz
            if (isHost()) {
                roundTransitionInProgress = true;
                new CountDownTimer(3000, 1000) {
                    public void onTick(long ms) {}
                    public void onFinish() {
                        SkockoGameState current = gameState.getValue();
                        if (current == null) current = state;
                        transitionToRound2(current, scoresSnapshot);
                    }
                }.start();
            }
        } else {
            state.status = "finished";
            saveSkockoStats(state);
            repository.updateGameState(state);
        }
    }

    private void transitionToRound2(SkockoGameState state, Map<String, Integer> scoresFromRound1) {
        state.round = 2;
        state.rundaZapocinje = 2;
        // Ako je protivnik otišao, preostali igrač (domaćin) ostaje aktivan i u
        // rundi 2, umesto da se red dodeli igraču koga više nema (čime bi igra
        // zauvek ostala zaglavljena).
        state.activePlayer = opponentLeft
                ? ("player1".equals(myRole) ? 1 : 2)
                : 2;
        state.isOpponentChance = false;
        state.showingRoundResult = false;
        state.attempts.clear();
        state.roundEndTime = serverNow() + 30_000;
        state.scores = scoresFromRound1;

        // Novo rešenje za rundu 2
        List<SkockoSymbol> newSol = skockoHelper.generateSolution();
        state.solution.clear();
        for (SkockoSymbol s : newSol) state.solution.add(s.name());

        // Reset lokalnih stat varijabli
        myRoundWon = false;
        myWinAttemptNumber = 0;

        repository.updateGameState(state);
    }

    // ----------------------------------------------------------------
    // ZAVRŠETAK MEČA
    // ----------------------------------------------------------------

    private void finishMatch(SkockoGameState state) {
        if (matchFinishedRegistered) return;
        matchFinishedRegistered = true;

        int myScore = (state.scores != null && state.scores.containsKey(myUid))
                ? state.scores.get(myUid) : 0;

        // Upiši skor u zajednički čvor meča
        FirebaseDatabase.getInstance()
                .getReference("matches")
                .child(matchId)
                .child("scores")
                .child(myUid)
                .setValue(myScore);

        // Samo player1 inkrementira currentGame
        if (isHost()) {
            FirebaseDatabase.getInstance()
                    .getReference("matches")
                    .child(matchId)
                    .child("currentGame")
                    .setValue(ServerValue.increment(1));
        }
    }

    // ----------------------------------------------------------------
    // FIRESTORE STATISTIKA
    // ----------------------------------------------------------------

    private void saveSkockoStats(SkockoGameState state) {
        if (myUid == null || state.scores == null) return;

        int myScore  = state.scores.containsKey(myUid) ? state.scores.get(myUid) : 0;
        int oppScore = 0;
        for (Map.Entry<String, Integer> entry : state.scores.entrySet()) {
            if (!entry.getKey().equals(myUid)) { oppScore = entry.getValue(); break; }
        }
        boolean iWon = myScore > oppScore;

        Map<String, Object> updates = new HashMap<>();

        if (myRoundWon && myWinAttemptNumber >= 1 && myWinAttemptNumber <= 6) {
            updates.put("stats.skocko.attempt" + myWinAttemptNumber, FieldValue.increment(1));
        } else {
            updates.put("stats.skocko.failed", FieldValue.increment(1));
        }

        updates.put("stats.skocko.wins",       FieldValue.increment(iWon ? 1 : 0));
        updates.put("stats.skocko.losses",     FieldValue.increment(iWon ? 0 : 1));
        updates.put("stats.global.totalGames", FieldValue.increment(1));
        updates.put("stats.global.wins",       FieldValue.increment(iWon ? 1 : 0));
        updates.put("stats.global.losses",     FieldValue.increment(iWon ? 0 : 1));

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(myUid)
                .update(updates);
    }

    // ----------------------------------------------------------------
    // SUBMIT ATTEMPT
    // ----------------------------------------------------------------

    public void submitAttempt(List<SkockoSymbol> attemptSymbols) {
        SkockoGameState state = gameState.getValue();
        if (state == null || "finished".equals(state.status)) return;
        if (!amIActive(state)) return;

        for (SkockoSymbol s : attemptSymbols) if (s == null) return;

        List<SkockoSymbol> solutionSymbols = new ArrayList<>();
        for (String s : state.solution) solutionSymbols.add(SkockoSymbol.valueOf(s));

        SkockoFeedback feedback = skockoHelper.evaluate(solutionSymbols, attemptSymbols);

        List<String> symbolStrings = new ArrayList<>();
        for (SkockoSymbol s : attemptSymbols) symbolStrings.add(s.name());

        int activePlayerNum = "player1".equals(myRole) ? 1 : 2;
        FirebaseAttempt newAttempt = new FirebaseAttempt(
                activePlayerNum, symbolStrings, feedback.getRed(), feedback.getYellow());
        state.attempts.add(newAttempt);

        if (feedback.getRed() == 4) {
            int points;
            int attemptIdx = state.attempts.size() - 1;
            if (!state.isOpponentChance) {
                points = (attemptIdx <= 1) ? 20 : (attemptIdx <= 3) ? 15 : 10;
            } else {
                points = 10;
            }

            updatePlayerScore(state, points);

            myRoundWon = true;
            myWinAttemptNumber = state.attempts.size();

            endRoundLogic(state);
            return;
        }

        if (state.isOpponentChance) {
            endRoundLogic(state);
            return;
        }

        if (state.attempts.size() == 6) {
            // Ako je protivnik otišao, nema ko da iskoristi bonus šansu od 10s -
            // runda se odmah završava umesto da se prelazi u tu fazu.
            if (opponentLeft) {
                endRoundLogic(state);
                return;
            }
            state.isOpponentChance = true;
            state.activePlayer = (state.rundaZapocinje == 1) ? 2 : 1;
            state.roundEndTime = serverNow() + 10_000;
        }

        repository.updateGameState(state);
    }

    private void updatePlayerScore(SkockoGameState state, int points) {
        if (state.scores == null) state.scores = new HashMap<>();
        int current = state.scores.containsKey(myUid) ? state.scores.get(myUid) : 0;
        Map<String, Integer> newScores = new HashMap<>(state.scores);
        newScores.put(myUid, current + points);
        state.scores = newScores;
    }

    // ----------------------------------------------------------------
    // SETUP (HOST)
    // ----------------------------------------------------------------

    public void setupInitialGameIfHost() {
        if (isHost()) {
            initializeGameIfNeeded();
        }
    }

    // Postaje true čim je poziv za inicijalizaciju igre pokrenut, da se izbjegne
    // duplo (re)generisanje rešenja pri ponovljenim pozivima.
    private boolean gameInitStarted = false;

    private void initializeGameIfNeeded() {
        if (gameInitStarted) return;

        SkockoGameState current = gameState.getValue();
        if (current != null && current.scores != null && !current.scores.isEmpty()) {
            gameInitStarted = true;
            return;
        }

        gameInitStarted = true;

        SkockoGameState initialState = new SkockoGameState();

        List<SkockoSymbol> initialSol = skockoHelper.generateSolution();
        for (SkockoSymbol s : initialSol) initialState.solution.add(s.name());

        // FIX: isto obrazloženje kao kod Asocijacija
        initialState.activePlayer = "player1".equals(myRole) ? 1 : 2;
        initialState.player1Id = "player1".equals(myRole) ? myUid : "";

        initialState.roundEndTime = serverNow() + 30_000;
        initialState.scores = new HashMap<>();
        int myStartScore = "player1".equals(myRole) ? matchStartingScoreP1 : matchStartingScoreP2;
        initialState.scores.put(myUid, myStartScore);

        selfRegistered = true;
        repository.updateGameState(initialState);
    }

    // ----------------------------------------------------------------
    // HELPERS
    // ----------------------------------------------------------------

    private boolean amIActive(SkockoGameState state) {
        return (state.activePlayer == 1 && "player1".equals(myRole))
                || (state.activePlayer == 2 && "player2".equals(myRole));
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (repository != null) repository.removeListener();
        if (timer != null) timer.cancel();
    }
}