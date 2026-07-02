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

    private String matchId;
    private String myRole;  // "player1" ili "player2"
    private String myUid;

    private AssociationGame associationDataStatic;

    // Lokalne varijable za statistiku na nivou partije
    private int mySolvedColumns = 0;
    private boolean myFinalSolved = false;

    // Flags za sprečavanje dupliranja i petlji
    private boolean selfRegistered = false;
    private boolean matchFinishedRegistered = false;

    // Flag koji sprečava da oba igrača istovremeno pokušaju prelaz na rundu 2
    private boolean roundTransitionInProgress = false;

    private int matchStartingScoreP1 = 0;
    private int matchStartingScoreP2 = 0;
    private boolean isMatchScoreLoaded = false;

    // Keširani server time offset (ms) koji dobijamo iz Firebase .info/serverTimeOffset
    private long serverTimeOffset = 0;

    private boolean opponentLeft = false;

    // ----------------------------------------------------------------
    // INIT
    // ----------------------------------------------------------------

    public void init(String matchId, String role) {
        this.matchId = matchId;
        this.myRole = role;
        this.myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        this.repository = new AsocijacijeRepository(matchId);
        this.associationDataStatic = AssociationData.createGame();

        // 1. Učitaj server time offset PRVO, pa tek onda sve ostalo
        FirebaseDatabase.getInstance()
                .getReference(".info/serverTimeOffset")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Long offset = snapshot.getValue(Long.class);
                        serverTimeOffset = (offset != null) ? offset : 0;
                        android.util.Log.d("ASOCIJACIJE_LOG", "[init] Server time offset: " + serverTimeOffset + "ms");
                        loadMatchScores();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        serverTimeOffset = 0;
                        loadMatchScores();
                    }
                });
    }

    /**
     * Vraća trenutno vreme usklađeno sa serverskim satom.
     * Oba igrača koriste ovo umesto System.currentTimeMillis() direktno
     * kako bi imali konzistentno računanje preostalog vremena.
     */
    private long serverNow() {
        return System.currentTimeMillis() + serverTimeOffset;
    }

    private boolean isHost() {
        return "player1".equals(myRole) || (opponentLeft && "player2".equals(myRole));
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

        AsocijacijeGameState state = gameState.getValue();
        if (state == null || "finished".equals(state.status)) return;

        // Protivnik nije (više) na potezu -> preuzimam kontrolu odmah kako bih
        // sve vreme mogao da pogađam i otvaram nova polja, umesto da se runda
        // prevremeno završi ili da čekam njegov red koji nikad neće doći.
        if (!amIActive(state)) {
            state.activePlayer = "player1".equals(myRole) ? 1 : 2;
            state.isGuessOnlyMode = false;
            repository.updateGameState(state);
        }

        // Runda je već označena kao završena (showingRoundResult), ali host
        // (player1) koji je trebalo da pokrene prelaz je upravo taj koji je otišao
        if (isHost() && state.showingRoundResult && state.round == 1 && !roundTransitionInProgress) {
            roundTransitionInProgress = true;
            final Map<String, Integer> scoresFromRoundOne = state.scores != null
                    ? new HashMap<>(state.scores) : new HashMap<>();
            transitionToRound2(state, scoresFromRoundOne);
        }
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
                                else                           matchStartingScoreP2 = score;
                            } else {
                                if ("player1".equals(myRole)) matchStartingScoreP2 = score;
                                else                           matchStartingScoreP1 = score;
                            }
                        }
                        isMatchScoreLoaded = true;

                        android.util.Log.d("ASOCIJACIJE_LOG", "[init] Bodovi učitani -> Uloga: " + myRole
                                + " | P1 start: " + matchStartingScoreP1
                                + " | P2 start: " + matchStartingScoreP2);

                        startListeningToGameState();
                        setupInitialGameIfHost();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        isMatchScoreLoaded = true;
                        startListeningToGameState();
                        setupInitialGameIfHost();
                    }
                });
    }

    // ----------------------------------------------------------------
    // LISTENER
    // ----------------------------------------------------------------

    private void startListeningToGameState() {
        this.repository.listenToGameState(state -> {
            if (state == null) return;

            // Player2 se registruje (dodaje sebe u scores mapu)
            if (state.scores != null && !state.scores.containsKey(myUid) && !selfRegistered) {
                selfRegistered = true;
                int myStartScore = "player1".equals(myRole) ? matchStartingScoreP1 : matchStartingScoreP2;
                state.scores.put(myUid, myStartScore);

                android.util.Log.d("ASOCIJACIJE_LOG", "[Listener] Player2 registracija, početni skor: " + myStartScore);
                repository.updateGameState(state);
                return;
            }

            // Ako je rundu2 treba da pokrene Player1, a Player2 je pogodio final u rundi 1,
            // state.showingRoundResult == true i rundaZapocinje == 1 → Player1 preuzima prelaz
            if (state.showingRoundResult && state.round == 1 && isHost() && !roundTransitionInProgress) {
                roundTransitionInProgress = true;
                final Map<String, Integer> scoresFromRoundOne = state.scores != null
                        ? new HashMap<>(state.scores)
                        : new HashMap<>();

                android.util.Log.d("ASOCIJACIJE_LOG", "[Player1] Detektovao showingRoundResult, pokreće prelaz na rundu 2.");

                new CountDownTimer(5000, 1000) {
                    public void onTick(long ms) {}
                    public void onFinish() {
                        AsocijacijeGameState currentState = gameState.getValue();
                        if (currentState == null) currentState = state;
                        transitionToRound2(currentState, scoresFromRoundOne);
                    }
                }.start();

                // Postavljamo state lokalno da UI vidi "showingRoundResult" odmah
                gameState.setValue(state);
                return;
            }

            gameState.setValue(state);
            handleTimerAndTurnSync(state);
        });
    }

    // ----------------------------------------------------------------
    // GETTERS
    // ----------------------------------------------------------------

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

    // ----------------------------------------------------------------
    // TIMER
    // ----------------------------------------------------------------

    private void handleTimerAndTurnSync(AsocijacijeGameState state) {
        if ("finished".equals(state.status)) {
            if (roundTimer != null) roundTimer.cancel();
            timerText.setValue("Igra završena");
            finishMatch(state);
            return;
        }

        boolean amIActive = amIActive(state);

        // Koristimo serverNow() umesto System.currentTimeMillis()
        long timeLeftMs = state.roundEndTime - serverNow();

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

        roundTimer = new CountDownTimer(seconds * 1000L, 1000) {
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

    // ----------------------------------------------------------------
    // AKCIJE IGRAČA
    // ----------------------------------------------------------------

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
                int scoreGained = 7; // bazični bodovi za konačno rešenje

                for (int c = 0; c < 4; c++) {
                    String colKey = getColumnKey(c);

                    // Proveravamo koliko ima neotvorenih polja u ovoj koloni
                    int unopenedCount = 0;
                    for (int r = 0; r < 4; r++) {
                        if (!state.openedFields.get(c).get(r)) {
                            unopenedCount++;
                        }
                    }

                    // Slajsanje po stanjima kolone:
                    if (Boolean.TRUE.equals(state.columnResolved.get(colKey))) {
                        // Stanje 1: Kolona je već ranije rešena, bodovi su već dodeljeni
                        // Ovde ne dodajemo ništa (0 bodova)
                    } else if (unopenedCount == 4) {
                        // Stanje 2: Kolona je potpuno neotvorena (netaknuta)
                        scoreGained += 6;
                    } else {
                        // Stanje 3: Kolona je delimično otvorena, a nije bila rešena
                        scoreGained += (2 + unopenedCount);
                    }

                    // Na kraju, otvaramo sva polja i rešavamo kolonu vizuelno za UI
                    state.columnResolved.put(colKey, true);
                    for (int r = 0; r < 4; r++) {
                        state.openedFields.get(c).set(r, true);
                    }
                }

                state.finalResolved = true;
                myFinalSolved = true;

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

    private void updatePlayerScore(AsocijacijeGameState state, int points) {
        if (state.scores == null) state.scores = new HashMap<>();
        int current = state.scores.containsKey(myUid) ? state.scores.get(myUid) : 0;
        Map<String, Integer> newScores = new HashMap<>(state.scores);
        newScores.put(myUid, current + points);
        state.scores = newScores;
    }

    private void switchPlayer(AsocijacijeGameState state) {
        state.isGuessOnlyMode = false;
        // Ako je protivnik otišao, ostajem aktivan - nema kome da se preda red,
        // pa nastavljam da pogađam i otvaram polja sve vreme.
        if (!opponentLeft) {
            state.activePlayer = (state.activePlayer == 1) ? 2 : 1;
        }
        repository.updateGameState(state);
    }

    // ----------------------------------------------------------------
    // KRAJ RUNDE
    // ----------------------------------------------------------------

    private void endRoundLogic(AsocijacijeGameState state) {
        if (roundTimer != null) {
            roundTimer.cancel();
            roundTimer = null;
        }

        if (state.round == 1) {
            // Sačuvaj bodove pre prelaza
            final Map<String, Integer> scoresFromRoundOne = state.scores != null
                    ? new HashMap<>(state.scores)
                    : new HashMap<>();

            // Postavi showingRoundResult = true tako da PLAYER1 može da detektuje
            // da treba pokrenuti prelaz, bez obzira ko je pogodio final
            state.showingRoundResult = true;
            repository.updateGameState(state);

            android.util.Log.d("ASOCIJACIJE_LOG", "[endRoundLogic] Runda 1 završena, ko sam: " + myRole
                    + " | showingRoundResult postavljen na true");

            // Samo Player1 pokreće tajmer za prelaz
            // Ako je Player2 pogodio, Player1 će detektovati showingRoundResult u listeneru
            if (isHost()) {
                roundTransitionInProgress = true;
                new CountDownTimer(5000, 1000) {
                    public void onTick(long ms) {}
                    public void onFinish() {
                        AsocijacijeGameState currentState = gameState.getValue();
                        if (currentState == null) currentState = state;
                        transitionToRound2(currentState, scoresFromRoundOne);
                    }
                }.start();
            }

        } else {
            // Runda 2 → kraj igre
            state.status = "finished";
            calculateAndSaveFirestoreStats(state);
            repository.updateGameState(state);
        }
    }

    /**
     * Prelaz na rundu 2 — uvijek ga izvršava Player1.
     * Poziva se ili direktno (ako je Player1 završio rundu 1)
     * ili kroz listener (ako je Player2 završio rundu 1).
     */
    private void transitionToRound2(AsocijacijeGameState currentState, Map<String, Integer> scoresFromRoundOne) {
        android.util.Log.d("ASOCIJACIJE_LOG", "[transitionToRound2] Player1 pokreće rundu 2.");

        currentState.round = 2;
        currentState.rundaZapocinje = 2;
        // Ako je protivnik otišao, domaćin (jedini preostali igrač) ostaje
        // aktivan i u rundi 2, umesto da se red dodeli igraču koga više nema.
        currentState.activePlayer = opponentLeft
                ? ("player1".equals(myRole) ? 1 : 2)
                : 2;
        currentState.isGuessOnlyMode = false;
        currentState.finalResolved = false;
        currentState.showingRoundResult = false;

        // Koristimo serverNow() umesto System.currentTimeMillis()
        currentState.roundEndTime = serverNow() + 120000;

        for (int c = 0; c < 4; c++) {
            currentState.columnResolved.put(getColumnKey(c), false);
            for (int r = 0; r < 4; r++) {
                currentState.openedFields.get(c).set(r, false);
            }
        }

        // Vraćamo bodove iz runde 1 kao bazu za rundu 2
        currentState.scores = scoresFromRoundOne;

        repository.updateGameState(currentState);
    }

    // ----------------------------------------------------------------
    // ZAVRŠETAK MEČA
    // ----------------------------------------------------------------

    private void finishMatch(AsocijacijeGameState state) {
        if (matchFinishedRegistered) return;
        matchFinishedRegistered = true;

        int score = (state.scores != null && state.scores.containsKey(myUid) && state.scores.get(myUid) != null)
                ? state.scores.get(myUid)
                : 0;

        // Upis u zajednički čvor meča u Realtime bazi
        FirebaseDatabase.getInstance()
                .getReference("matches")
                .child(matchId)
                .child("scores")
                .child(myUid)
                .setValue(score);

        // Samo player1 inkrementira currentGame da lobi sistem pređe na sledeću igru
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

    private void calculateAndSaveFirestoreStats(AsocijacijeGameState state) {
        if (myUid == null || state.scores == null) return;

        int myScore = state.scores.containsKey(myUid) ? state.scores.get(myUid) : 0;

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

    // ----------------------------------------------------------------
    // SETUP (HOST)
    // ----------------------------------------------------------------

    public void setupInitialGameIfHost() {
        if (isHost()) {
            initializeGameIfNeeded();
        }
    }

    // Postaje true čim je poziv za inicijalizaciju igre pokrenut, da se izbjegne
    // duplo (re)generisanje početnog stanja pri ponovljenim pozivima.
    private boolean gameInitStarted = false;

    private void initializeGameIfNeeded() {
        if (gameInitStarted) return;

        AsocijacijeGameState current = gameState.getValue();
        if (current != null && current.scores != null && !current.scores.isEmpty()) {
            gameInitStarted = true;
            return;
        }

        gameInitStarted = true;

        AsocijacijeGameState initialState = new AsocijacijeGameState();
        initialState.roundEndTime = serverNow() + 120000;

        // FIX: activePlayer i player1Id se postavljaju prema STVARNOJ ulozi domaćina,
        // ne uvek na 1/moj-uid - inače, kad player2 preuzme domaćinstvo (player1 otišao),
        // igra bi mislila da je red na player1 koga više nema, i zauvek bi čekala.
        initialState.activePlayer = "player1".equals(myRole) ? 1 : 2;
        initialState.player1Id = "player1".equals(myRole) ? myUid : "";

        selfRegistered = true;
        initialState.scores = new HashMap<>();
        int myStartScore = "player1".equals(myRole) ? matchStartingScoreP1 : matchStartingScoreP2;
        initialState.scores.put(myUid, myStartScore);

        repository.updateGameState(initialState);
    }

    // ----------------------------------------------------------------
    // HELPERS
    // ----------------------------------------------------------------

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