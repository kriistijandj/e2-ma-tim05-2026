package com.example.slagalica.viewmodel;

import android.os.CountDownTimer;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.slagalica.games.data.AssociationData;
import com.example.slagalica.models.AssociationGame;
import com.example.slagalica.models.Column;
import com.example.slagalica.models.Round;
import com.example.slagalica.models.asocijacije.AsocijacijeGameState;
import com.example.slagalica.repository.AsocijacijeRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AsocijacijeViewModel extends ViewModel {

    private AsocijacijeRepository repository;
    private final MutableLiveData<AsocijacijeGameState> gameState = new MutableLiveData<>();
    private final MutableLiveData<String> timerText = new MutableLiveData<>();

    private CountDownTimer roundTimer;
    private String myPlayerId; // "player1" ili "player2"

    private AssociationGame associationDataStatic;

    // ====== STATISTIKA (prati se lokalno tokom partije) ======
    private int mySolvedColumns  = 0;   // broj kolona koje je lokalni igrač riješio
    private boolean myFinalSolved = false; // da li je lokalni igrač pogodio konačno rješenje

    public void init(String gameId, String myPlayerId) {
        this.myPlayerId = myPlayerId;
        this.repository = new AsocijacijeRepository(gameId);
        this.associationDataStatic = AssociationData.createGame();

        this.repository.listenToGameState(state -> {
            gameState.setValue(state);
            handleTimerAndTurnSync(state);
        });
    }

    public LiveData<AsocijacijeGameState> getGameState() { return gameState; }
    public LiveData<String> getTimerText()               { return timerText; }
    public String getMyPlayerId()                        { return myPlayerId; }

    public String getFieldText(int col, int row) {
        AsocijacijeGameState state = gameState.getValue();
        if (state == null || !state.openedFields.get(col).get(row)) return "";
        int roundIdx = state.round - 1;
        return associationDataStatic.rounds[roundIdx].columns[col].fields[row];
    }

    public String getColumnSolutionText(int col) {
        AsocijacijeGameState state = gameState.getValue();
        if (state == null) return "";
        String colKey = getColumnKey(col);
        if (Boolean.TRUE.equals(state.columnResolved.get(colKey))) {
            int roundIdx = state.round - 1;
            return associationDataStatic.rounds[roundIdx].columns[col].solution;
        }
        return "";
    }

    public String getFinalSolutionText() {
        AsocijacijeGameState state = gameState.getValue();
        if (state == null || !state.finalResolved) return "";
        int roundIdx = state.round - 1;
        return associationDataStatic.rounds[roundIdx].finalSolution;
    }

    private void handleTimerAndTurnSync(AsocijacijeGameState state) {
        if ("finished".equals(state.status)) {
            if (roundTimer != null) roundTimer.cancel();
            return;
        }

        boolean amIActive = (state.activePlayer == 1 && "player1".equals(myPlayerId)) ||
                (state.activePlayer == 2 && "player2".equals(myPlayerId));

        if (amIActive) {
            if (roundTimer == null) {
                startTimer(120);
            }
        } else {
            if (roundTimer != null) {
                roundTimer.cancel();
                roundTimer = null;
            }
            timerText.setValue("Na potezu je protivnik");
        }
    }

    private void startTimer(int seconds) {
        if (roundTimer != null) roundTimer.cancel();
        roundTimer = new CountDownTimer(seconds * 1000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timerText.setValue((millisUntilFinished / 1000) + "s");
            }
            @Override
            public void onFinish() {
                timerText.setValue("0s");
                roundTimer = null;
                handleTimeOut();
            }
        }.start();
    }

    private void handleTimeOut() {
        AsocijacijeGameState state = gameState.getValue();
        if (state == null) return;

        int activePlayerNum = "player1".equals(myPlayerId) ? 1 : 2;
        if (state.activePlayer != activePlayerNum) return;

        endRoundLogic(state);
    }

    // --- KLIK NA POLJE ---
    public void openField(int col, int row) {
        AsocijacijeGameState state = gameState.getValue();
        if (state == null || state.isGuessOnlyMode) return;
        if (state.openedFields.get(col).get(row)) return;

        state.openedFields.get(col).set(row, true);
        repository.updateGameState(state);
    }

    // --- SLANJE POKUŠAJA POGAĐANJA ---
    public boolean submitGuess(String target, String guess) {
        AsocijacijeGameState state = gameState.getValue();
        if (state == null || guess.trim().isEmpty()) return false;

        int roundIdx = state.round - 1;
        Round currentRoundData = associationDataStatic.rounds[roundIdx];
        int activePlayerNum = "player1".equals(myPlayerId) ? 1 : 2;

        guess = guess.trim();

        if ("FINAL".equals(target)) {
            if (currentRoundData.finalSolution.equalsIgnoreCase(guess)) {
                int scoreGained = 7;

                for (int c = 0; c < 4; c++) {
                    String colKey = getColumnKey(c);
                    Column columnData = currentRoundData.columns[c];

                    if (!Boolean.TRUE.equals(state.columnResolved.get(colKey))) {
                        scoreGained += 6;
                        state.columnResolved.put(colKey, true);
                    } else {
                        int unopenedCount = 0;
                        for (int r = 0; r < 4; r++) {
                            if (!state.openedFields.get(c).get(r)) unopenedCount++;
                        }
                        scoreGained += (2 + unopenedCount);
                    }

                    for (int r = 0; r < 4; r++) state.openedFields.get(c).set(r, true);
                }

                state.finalResolved = true;

                // Bilježimo za statistiku – lokalni igrač je pogodio konačno
                if (activePlayerNum == ("player1".equals(myPlayerId) ? 1 : 2)) {
                    myFinalSolved = true;
                }

                if (activePlayerNum == 1) state.p1Score += scoreGained;
                else                      state.p2Score += scoreGained;

                endRoundLogic(state);
                return true;
            } else {
                switchPlayer(state);
                return false;
            }
        } else {
            int colIdx = getColumnIndex(target);
            Column columnData = currentRoundData.columns[colIdx];

            if (Boolean.TRUE.equals(state.columnResolved.get(target))) return false;

            if (columnData.solution.equalsIgnoreCase(guess)) {
                int unopenedCount = 0;
                for (int r = 0; r < 4; r++) {
                    if (!state.openedFields.get(colIdx).get(r)) unopenedCount++;
                }
                int scoreGained = 2 + unopenedCount;

                state.columnResolved.put(target, true);

                for (int r = 0; r < 4; r++) state.openedFields.get(colIdx).set(r, true);

                if (activePlayerNum == 1) state.p1Score += scoreGained;
                else                      state.p2Score += scoreGained;

                // Bilježimo za statistiku – lokalni igrač je riješio kolonu
                if (activePlayerNum == ("player1".equals(myPlayerId) ? 1 : 2)) {
                    mySolvedColumns++;
                }

                state.isGuessOnlyMode = true;
                repository.updateGameState(state);
                return true;
            } else {
                switchPlayer(state);
                return false;
            }
        }
    }

    private void switchPlayer(AsocijacijeGameState state) {
        state.isGuessOnlyMode = false;
        state.activePlayer = (state.activePlayer == 1) ? 2 : 1;
        repository.updateGameState(state);
    }

    private void endRoundLogic(AsocijacijeGameState state) {
        if (state.round == 1) {
            // --- PRELAZAK NA RUNDU 2 ---
            state.round = 2;
            state.rundaZapocinje = 2;
            state.activePlayer = 2;
            state.isGuessOnlyMode = false;
            state.finalResolved = false;

            for (int c = 0; c < 4; c++) {
                state.columnResolved.put(getColumnKey(c), false);
                for (int r = 0; r < 4; r++) {
                    state.openedFields.get(c).set(r, false);
                }
            }
        } else {
            // --- KRAJ OBE RUNDE – upisujemo statistiku ---
            state.status = "finished";

            int myScore  = "player1".equals(myPlayerId) ? state.p1Score : state.p2Score;
            int oppScore = "player1".equals(myPlayerId) ? state.p2Score : state.p1Score;
            boolean iWon = myScore > oppScore;

            saveAsocijacijeStats(iWon);
        }

        repository.updateGameState(state);
    }

    // ==============================
    // UPIS STATISTIKE U FIRESTORE
    // ==============================

    private void saveAsocijacijeStats(boolean iWon) {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        if (uid == null) return;

        // solved = kolone + konačno koje je lokalni igrač pogodio
        int totalSolved   = mySolvedColumns + (myFinalSolved ? 1 : 0);
        // unsolved = max mogući (4 kolone + 1 konačno = 5 po rundi, 2 runde = 10) minus što je riješio
        int totalUnsolved = 10 - totalSolved;
        if (totalUnsolved < 0) totalUnsolved = 0;

        Map<String, Object> updates = new HashMap<>();
        updates.put("stats.asocijacije.solved",   FieldValue.increment(totalSolved));
        updates.put("stats.asocijacije.unsolved", FieldValue.increment(totalUnsolved));
        updates.put("stats.asocijacije.wins",     FieldValue.increment(iWon ? 1 : 0));
        updates.put("stats.asocijacije.losses",   FieldValue.increment(iWon ? 0 : 1));
        updates.put("stats.global.totalGames",    FieldValue.increment(1));
        updates.put("stats.global.wins",          FieldValue.increment(iWon ? 1 : 0));
        updates.put("stats.global.losses",        FieldValue.increment(iWon ? 0 : 1));

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .update(updates);
    }

    public void setupInitialGameIfHost() {
        if ("player1".equals(myPlayerId)) {
            AsocijacijeGameState initialState = new AsocijacijeGameState();
            repository.updateGameState(initialState);
        }
    }

    private String getColumnKey(int idx) {
        if (idx == 0) return "A";
        if (idx == 1) return "B";
        if (idx == 2) return "C";
        return "D";
    }

    private int getColumnIndex(String key) {
        switch (key) {
            case "A": return 0;
            case "B": return 1;
            case "C": return 2;
            default:  return 3;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (repository != null) repository.removeListener();
        if (roundTimer != null) roundTimer.cancel();
    }
}