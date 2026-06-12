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

import java.util.List;

public class AsocijacijeViewModel extends ViewModel {

    private AsocijacijeRepository repository;
    private final MutableLiveData<AsocijacijeGameState> gameState = new MutableLiveData<>();
    private final MutableLiveData<String> timerText = new MutableLiveData<>();

    private CountDownTimer roundTimer;
    private String myPlayerId; // "player1" ili "player2"

    // Lokalna kopija asocijacije (reči i rešenja) učitana iz AssociationData šablona
    private AssociationGame associationDataStatic;

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
    public LiveData<String> getTimerText() { return timerText; }
    public String getMyPlayerId() { return myPlayerId; }

    // Vraća tekst za određeno polje ako je otvoreno
    public String getFieldText(int col, int row) {
        AsocijacijeGameState state = gameState.getValue();
        if (state == null || !state.openedFields.get(col).get(row)) return "";

        // Mapiranje trenutne runde (1 ili 2 -> indeks 0 ili 1)
        int roundIdx = state.round - 1;
        return associationDataStatic.rounds[roundIdx].columns[col].fields[row];
    }

    // Vraća rešenje kolone ako je rešena
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

    // Vraća konačno rešenje ako je pogođeno
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
                startTimer(120); // 2 minuta (120 sekundi) po specifikaciji
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

        // Isteklo vreme runde -> prelazak na sledeću rundu ili kraj igre
        endRoundLogic(state);
    }

    // --- KLIK NA POLJE ---
    public void openField(int col, int row) {
        AsocijacijeGameState state = gameState.getValue();
        if (state == null || state.isGuessOnlyMode) return;

        if (state.openedFields.get(col).get(row)) return; // Već otvoreno

        // Otvori polje lokalno u stanju
        state.openedFields.get(col).set(row, true);

        // Nakon otvaranja polja, igrač ima pravo da pogađa rešenje te kolone ili konačno rešenje.
        // Potez mu se NE menja odmah, već ostaje aktivan dok ne pošalje netačan odgovor ili ne unese ništa.
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
            // Pogađanje KONAČNOG rešenja
            if (currentRoundData.finalSolution.equalsIgnoreCase(guess)) {
                // Tačno konačno rešenje! Računanje kompletne matematike bodova:
                int scoreGained = 7;

                for (int c = 0; c < 4; c++) {
                    String colKey = getColumnKey(c);
                    Column columnData = currentRoundData.columns[c];

                    if (!Boolean.TRUE.equals(state.columnResolved.get(colKey))) {
                        // Kolona nije bila rešena uopšte -> 6 bodova po specifikaciji
                        scoreGained += 6;
                        state.columnResolved.put(colKey, true);
                    } else {
                        // Kolona je već bila rešena od ranije -> 2 boda + broj neotvorenih polja u njoj
                        int unopenedCount = 0;
                        for (int r = 0; r < 4; r++) {
                            if (!state.openedFields.get(c).get(r)) unopenedCount++;
                        }
                        scoreGained += (2 + unopenedCount);
                    }

                    // Automatski otvaramo sva polja u svim kolonama jer je asocijacija kompletno rešena
                    for (int r = 0; r < 4; r++) state.openedFields.get(c).set(r, true);
                }

                state.finalResolved = true;
                if (activePlayerNum == 1) state.p1Score += scoreGained;
                else state.p2Score += scoreGained;

                endRoundLogic(state);
                return true;
            } else {
                // Netačno konačno rešenje -> menja se igrač, gasi se guess only mod
                switchPlayer(state);
                return false;
            }
        } else {
            // Pogađanje rešenja određene KOLONE ("A", "B", "C", "D")
            int colIdx = getColumnIndex(target);
            Column columnData = currentRoundData.columns[colIdx];

            if (Boolean.TRUE.equals(state.columnResolved.get(target))) return false; // Već rešena

            if (columnData.solution.equalsIgnoreCase(guess)) {
                // Tačno rešenje kolone! Matematika: 2 boda + 1 bod za svako neotvoreno polje u toj koloni
                int unopenedCount = 0;
                for (int r = 0; r < 4; r++) {
                    if (!state.openedFields.get(colIdx).get(r)) unopenedCount++;
                }
                int scoreGained = 2 + unopenedCount;

                // Označi kolonu kao rešenu
                state.columnResolved.put(target, true);

                // Automatski otvori sva skrivena polja te kolone
                for (int r = 0; r < 4; r++) state.openedFields.get(colIdx).set(r, true);

                if (activePlayerNum == 1) state.p1Score += scoreGained;
                else state.p2Score += scoreGained;

                // PRAVILO 1: Igrač ostaje na potezu, ali ulazi u GuessOnly režim (ne može otvarati polja, samo pogađati dalje)
                state.isGuessOnlyMode = true;
                repository.updateGameState(state);
                return true;
            } else {
                // Netačno rešenje kolone -> gubi se potez
                switchPlayer(state);
                return false;
            }
        }
    }

    private void switchPlayer(AsocijacijeGameState state) {
        state.isGuessOnlyMode = false; // Reset režima pogađanja za sledećeg igrača
        state.activePlayer = (state.activePlayer == 1) ? 2 : 1;
        repository.updateGameState(state);
    }

    private void endRoundLogic(AsocijacijeGameState state) {
        if (state.round == 1) {
            // Prelazak na drugu rundu
            state.round = 2;
            state.rundaZapocinje = 2;
            state.activePlayer = 2; // Runda 2 započinje Igrač 2
            state.isGuessOnlyMode = false;
            state.finalResolved = false;

            // Ponovo zaključaj sva polja i kolone
            for (int c = 0; c < 4; c++) {
                state.columnResolved.put(getColumnKey(c), false);
                for (int r = 0; r < 4; r++) {
                    state.openedFields.get(c).set(r, false);
                }
            }
        } else {
            // Kraj obe runde asocijacija
            state.status = "finished";
        }
        repository.updateGameState(state);
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
            default: return 3;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (repository != null) repository.removeListener();
        if (roundTimer != null) roundTimer.cancel();
    }
}