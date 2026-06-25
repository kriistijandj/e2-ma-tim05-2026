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
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import androidx.annotation.NonNull;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class AsocijacijeViewModel extends ViewModel {

    private AsocijacijeRepository repository;
    private final MutableLiveData<AsocijacijeGameState> gameState = new MutableLiveData<>();
    private final MutableLiveData<String> timerText = new MutableLiveData<>();

    private CountDownTimer roundTimer;

    private String matchId; // Identifikator celog meča iz lobi sistema
    private String myRole;  // "player1" ili "player2"
    private String myUid;

    private AssociationGame associationDataStatic;

    // Lokalne varijable za statistiku na nivou partije
    private int mySolvedColumns  = 0;
    private boolean myFinalSolved = false;

    // Flags po uzoru na KorakViewModel za sprečavanje dupliranja i petlji
    private boolean selfRegistered = false;
    private boolean matchFinishedRegistered = false;

    private int matchStartingScoreP1 = 0;
    private int matchStartingScoreP2 = 0;
    private boolean isMatchScoreLoaded = false;

    public void init(String matchId, String role) {
        this.matchId = matchId;
        this.myRole = role;
        this.myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        this.repository = new AsocijacijeRepository(matchId);
        this.associationDataStatic = AssociationData.createGame();

        FirebaseDatabase.getInstance()
                .getReference("matches")
                .child(matchId)
                .child("scores")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot playerSnapshot : snapshot.getChildren()) {
                            String playerUid = playerSnapshot.getKey();
                            int savedScore = playerSnapshot.getValue(Integer.class) != null ? playerSnapshot.getValue(Integer.class) : 0;

                            if (playerUid != null && playerUid.equals(myUid)) {
                                if ("player1".equals(myRole)) matchStartingScoreP1 = savedScore;
                                else                           matchStartingScoreP2 = savedScore;
                            } else {
                                if ("player1".equals(myRole)) matchStartingScoreP2 = savedScore;
                                else                           matchStartingScoreP1 = savedScore;
                            }
                        }
                        isMatchScoreLoaded = true;

                        android.util.Log.d("ASOCIJACIJE_LOG", "[init] Bodovi učitani iz meča -> Moj Uloga: " + myRole
                                + " | matchStartingScoreP1: " + matchStartingScoreP1
                                + " | matchStartingScoreP2: " + matchStartingScoreP2);

                        // POKRETANJE SLUŠANJA PROMENA
                        startListeningToGameState();

                        // FIX: Tek ovde, kada imamo prave bodove, host sme da kreira igru!
                        setupInitialGameIfHost();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        isMatchScoreLoaded = true;
                        startListeningToGameState();
                        setupInitialGameIfHost(); // Sigurnosni fallback
                    }
                });
    }

    // Prebaci listener u pomoćnu metodu
    private void startListeningToGameState() {
        this.repository.listenToGameState(state -> {
            if (state == null) return;

            // Kada se player2 registruje, postavi mu startni skor umesto 0
            if (state.scores != null && !state.scores.containsKey(myUid) && !selfRegistered) {
                selfRegistered = true;
                int myStartScore = "player1".equals(myRole) ? matchStartingScoreP1 : matchStartingScoreP2;
                state.scores.put(myUid, myStartScore);

                android.util.Log.d("ASOCIJACIJE_LOG", "[Listener] Player2 ubacuje sebe. Postavlja početni skor: " + myStartScore);
                repository.updateGameState(state);
                return;
            }

            gameState.setValue(state);
            handleTimerAndTurnSync(state);
        });
    }

    public LiveData<AsocijacijeGameState> getGameState() { return gameState; }
    public LiveData<String> getTimerText()               { return timerText; }
    public String getMyRole()                            { return myRole; }

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
            timerText.setValue("Igra završena");
            finishMatch(state); // Okidanje završetka meča i upisa
            return;
        }

        boolean amIActive = amIActive(state);

        long currentTime = System.currentTimeMillis();
        long timeLeftMs = state.roundEndTime - currentTime;

        if (timeLeftMs <= 0) {
            if (roundTimer != null) {
                roundTimer.cancel();
                roundTimer = null;
            }
            timerText.setValue("0s");
            if (amIActive) {
                handleTimeOut();
            }
            return;
        }

        if (amIActive) {
            startTimer((int) (timeLeftMs / 1000));
        } else {
            if (roundTimer != null) {
                roundTimer.cancel();
                roundTimer = null;
            }
            timerText.setValue("Čeka se protivnik (" + (timeLeftMs / 1000) + "s)");
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
        if (state == null || !amIActive(state)) return;

        endRoundLogic(state);
    }

    public void openField(int col, int row) {
        AsocijacijeGameState state = gameState.getValue();
        if (state == null || state.isGuessOnlyMode || !amIActive(state)) return;
        if (state.openedFields.get(col).get(row)) return;

        state.openedFields.get(col).set(row, true);
        state.isGuessOnlyMode = true;

        repository.updateGameState(state);
    }

    public boolean submitGuess(String target, String guess) {
        AsocijacijeGameState state = gameState.getValue();
        if (state == null || guess.trim().isEmpty() || !amIActive(state)) return false;

        int roundIdx = state.round - 1;
        Round currentRoundData = associationDataStatic.rounds[roundIdx];
        guess = guess.trim();

        if ("FINAL".equals(target)) {
            if (currentRoundData.finalSolution.equalsIgnoreCase(guess)) {
                int scoreGained = 7;

                for (int c = 0; c < 4; c++) {
                    String colKey = getColumnKey(c);
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
                myFinalSolved = true;

                // KORAK PATTERN: Bezbedno dodavanje bodova kroz novu mapu u bazu
                updatePlayerScore(state, scoreGained);

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

                mySolvedColumns++;

                // KORAK PATTERN: Bezbedno dodavanje bodova
                updatePlayerScore(state, scoreGained);

                state.isGuessOnlyMode = true;
                repository.updateGameState(state);
                return true;
            } else {
                switchPlayer(state);
                return false;
            }
        }
    }

    // Pomoćna metoda za inkrementiranje skora unutar mape u stilu KorakViewModel-a
    private void updatePlayerScore(AsocijacijeGameState state, int points) {
        if (state.scores == null) {
            state.scores = new HashMap<>();
        }
        int current = state.scores.containsKey(myUid) ? state.scores.get(myUid) : 0;

        Map<String, Integer> newScores = new HashMap<>(state.scores);
        newScores.put(myUid, current + points);
        state.scores = newScores;
    }

    private void switchPlayer(AsocijacijeGameState state) {
        state.isGuessOnlyMode = false;
        state.activePlayer = (state.activePlayer == 1) ? 2 : 1;
        repository.updateGameState(state);
    }

    private void endRoundLogic(AsocijacijeGameState state) {
        if (roundTimer != null) roundTimer.cancel();

        if (state.round == 1) {
            // Zaključavamo osvojene bodove iz prve runde da ih prenesemo u drugu rundu
            final Map<String, Integer> scoresFromRoundOne = state.scores != null
                    ? new HashMap<>(state.scores)
                    : new HashMap<>();

            if ("player1".equals(myRole)) {
                state.showingRoundResult = true;
                repository.updateGameState(state);
            }

            // Pauza od 5 sekundi (isto kao u Koraku) pre prelaska na drugu rundu
            new CountDownTimer(5000, 1000) {
                public void onTick(long ms) {}

                public void onFinish() {
                    if (!"player1".equals(myRole)) return;

                    AsocijacijeGameState currentState = gameState.getValue();
                    if (currentState == null) currentState = state;

                    currentState.round = 2;
                    currentState.rundaZapocinje = 2;
                    currentState.activePlayer = 2;
                    currentState.isGuessOnlyMode = false;
                    currentState.finalResolved = false;
                    currentState.showingRoundResult = false;
                    currentState.roundEndTime = System.currentTimeMillis() + 120000;

                    for (int c = 0; c < 4; c++) {
                        currentState.columnResolved.put(getColumnKey(c), false);
                        for (int r = 0; r < 4; r++) {
                            currentState.openedFields.get(c).set(r, false);
                        }
                    }

                    // VRAĆANJE BODOVA IZ PRVE RUNDE NA KOJE SE DODAJU NOVI BODOVI
                    currentState.scores = scoresFromRoundOne;

                    repository.updateGameState(currentState);
                }
            }.start();

        } else {
            state.status = "finished";

            // Pokretanje lokalnog upisa u Firestore statistike pre slanja u centralni meč
            calculateAndSaveFirestoreStats(state);

            repository.updateGameState(state);
        }
    }

    // ---------------- FINISH MATCH (IDENTIČNO KORAKU) ----------------

    private void finishMatch(AsocijacijeGameState state) {
        if (matchFinishedRegistered) return;
        matchFinishedRegistered = true;

        int score = (state.scores != null && state.scores.containsKey(myUid) && state.scores.get(myUid) != null)
                ? state.scores.get(myUid)
                : 0;

        // 1. Upis u zajednički čvor meča u Realtime bazi
        FirebaseDatabase.getInstance()
                .getReference("matches")
                .child(matchId)
                .child("scores")
                .child(myUid)
                .setValue(score);

        // 2. Samo player1 inkrementira currentGame da bi lobi sistem prebacio na sledeću igru
        if ("player1".equals(myRole)) {
            FirebaseDatabase.getInstance()
                    .getReference("matches")
                    .child(matchId)
                    .child("currentGame")
                    .setValue(ServerValue.increment(1));
        }
    }

    // ---------------- FIRESTORE STATISTIKA ----------------

    private void calculateAndSaveFirestoreStats(AsocijacijeGameState state) {
        if (myUid == null || state.scores == null) return;

        int myScore = state.scores.containsKey(myUid) ? state.scores.get(myUid) : 0;

        // Pronalaženje protivničkog skora radi provere pobede/poraza
        int oppScore = 0;
        for (Map.Entry<String, Integer> entry : state.scores.entrySet()) {
            if (!entry.getKey().equals(myUid)) {
                oppScore = entry.getValue();
                break;
            }
        }
        boolean iWon = myScore > oppScore;

        int totalSolved = mySolvedColumns + (myFinalSolved ? 1 : 0);
        int totalUnsolved = Math.max(0, 10 - totalSolved);

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
                .document(myUid)
                .update(updates);
    }

    public void setupInitialGameIfHost() {
        if ("player1".equals(myRole)) {
            AsocijacijeGameState initialState = new AsocijacijeGameState();
            initialState.roundEndTime = System.currentTimeMillis() + 120000;
            initialState.player1Id = myUid;

            selfRegistered = true;
            initialState.scores = new HashMap<>();
            initialState.scores.put(myUid, matchStartingScoreP1);

            android.util.Log.d("ASOCIJACIJE_LOG", "[Host] Kreiram igru, ubacujem startne bodove za Player1: " + matchStartingScoreP1);

            repository.updateGameState(initialState);
        }
    }

    private boolean amIActive(AsocijacijeGameState state) {
        return (state.activePlayer == 1 && "player1".equals(myRole))
                || (state.activePlayer == 2 && "player2".equals(myRole));
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