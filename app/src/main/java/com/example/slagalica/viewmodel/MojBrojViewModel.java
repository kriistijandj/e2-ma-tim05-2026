package com.example.slagalica.viewmodel;

import android.os.CountDownTimer;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.slagalica.helper.MojBrojHelper;
import com.example.slagalica.models.mojbroj.MojBrojGameState;
import com.example.slagalica.repository.MojBrojRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class MojBrojViewModel extends ViewModel {

    private static final String TAG = "MOJ_BROJ_LOG";

    private MojBrojRepository repository;
    private final MojBrojHelper helper = new MojBrojHelper();

    private final MutableLiveData<MojBrojGameState> gameState = new MutableLiveData<>();
    private final MutableLiveData<String> timerText = new MutableLiveData<>();

    private CountDownTimer timer;
    private String myPlayerId;
    private String matchId;

    private int matchStartingScoreP1 = 0;
    private int matchStartingScoreP2 = 0;
    private boolean isMatchScoreLoaded = false;

    private static final int PHASE_NONE    = 0;
    private static final int PHASE_TARGET  = 1;
    private static final int PHASE_NUMBERS = 2;
    private static final int PHASE_PLAY    = 3;

    private int currentTimerPhase = PHASE_NONE;

    private boolean round2TransitionScheduled = false;
    private boolean gameFinished = false;

    private boolean round1Processed = false;
    private boolean round2Processed = false;

    private int myRoundsPlayed = 0;
    private int myRoundsSolved = 0;

    private boolean opponentLeft = false;

    // -------------------------------------------------------------------------
    // INIT
    // -------------------------------------------------------------------------

    public void init(String matchId, String playerId) {
        this.matchId = matchId;
        this.myPlayerId = playerId;
        this.repository = new MojBrojRepository(matchId);


        FirebaseDatabase.getInstance()
                .getReference("matches")
                .child(matchId)
                .child("scores")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String currentUid = FirebaseAuth.getInstance().getCurrentUser() != null
                                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                                : "";

                        for (DataSnapshot playerSnapshot : snapshot.getChildren()) {
                            String playerUid = playerSnapshot.getKey();
                            int savedScore = playerSnapshot.getValue(Integer.class) != null
                                    ? playerSnapshot.getValue(Integer.class) : 0;

                            if (playerUid != null && playerUid.equals(currentUid)) {
                                if ("player1".equals(myPlayerId)) matchStartingScoreP1 = savedScore;
                                else                               matchStartingScoreP2 = savedScore;
                            } else {
                                if ("player1".equals(myPlayerId)) matchStartingScoreP2 = savedScore;
                                else                               matchStartingScoreP1 = savedScore;
                            }
                        }
                        isMatchScoreLoaded = true;
                        Log.d(TAG, "Učitani bodovi iz meča -> P1: " + matchStartingScoreP1
                                + " | P2: " + matchStartingScoreP2);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Greška pri učitavanju bodova meča: " + error.getMessage());
                        isMatchScoreLoaded = true;
                    }
                });

        repository.listenToGameState(state -> {
            gameState.setValue(state);
            handleTimerSync(state);
        });
    }

    private boolean isHost() {
        return "player1".equals(myPlayerId) || (opponentLeft && "player2".equals(myPlayerId));
    }


    public void onOpponentLeft() {
        opponentLeft = true;

        MojBrojGameState state = gameState.getValue();
        if (state == null || "finished".equals(state.status)) return;

        boolean mySubmitted = "player1".equals(myPlayerId) ? state.p1Submitted : state.p2Submitted;
        boolean opponentSubmitted = "player1".equals(myPlayerId) ? state.p2Submitted : state.p1Submitted;

        if (mySubmitted && !opponentSubmitted) {

            fillOpponentAsAbsent(state);
            advanceIfNeeded(state);
            return;
        }


        handleTimerSync(state);
    }


    private boolean gameInitStarted = false;

    public void signalReadyAndInit() {
        repository.setReady(myPlayerId, () -> {
            // Koristimo isHost() umesto "player1".equals(myPlayerId)
            if (isHost()) {
                initializeGameIfNeeded();
            }
        });


        if (opponentLeft && isHost()) {
            initializeGameIfNeeded();
        }
    }

    private void initializeGameIfNeeded() {
        if (gameInitStarted) return;


        MojBrojGameState current = gameState.getValue();
        if (current != null && current.availableNumbers != null && current.availableNumbers.size() == 6) {
            gameInitStarted = true;
            return;
        }

        gameInitStarted = true;

        MojBrojGameState initial = new MojBrojGameState();
        initial.targetNumber      = helper.generateTargetNumber();
        initial.availableNumbers  = helper.generateAvailableNumbers();
        initial.stopPlayer        = 1;
        initial.round             = 1;
        initial.status            = "active";
        repository.updateGameState(initial);
        Log.d(TAG, "Igra uspešno inicijalizovana od strane domaćina/preostalog igrača.");
    }



    public LiveData<MojBrojGameState> getGameState() { return gameState; }
    public LiveData<String>           getTimerText()  { return timerText; }
    public String                     getMyPlayerId() { return myPlayerId; }
    public int getMatchStartingScoreP1()              { return matchStartingScoreP1; }
    public int getMatchStartingScoreP2()              { return matchStartingScoreP2; }



    public void onStopPressed() {
        MojBrojGameState state = gameState.getValue();
        if (state == null || "finished".equals(state.status)) return;
        if (!amIStopPlayer(state)) return;

        if (!state.targetRevealed) {
            state.targetRevealed = true;
            repository.updateGameState(state);
        } else if (!state.numbersRevealed) {
            state.numbersRevealed = true;
            repository.updateGameState(state);
        }
    }



    public void submitExpression(String expression) {
        MojBrojGameState state = gameState.getValue();
        if (state == null || "finished".equals(state.status)) return;
        if (!state.numbersRevealed) return;

        if ("player1".equals(myPlayerId) && state.p1Submitted) return;
        if ("player2".equals(myPlayerId) && state.p2Submitted) return;

        int result = helper.evaluate(expression);

        if ("player1".equals(myPlayerId)) {
            state.p1Expression = expression;
            state.p1Result     = result;
            state.p1Submitted  = true;
        } else {
            state.p2Expression = expression;
            state.p2Result     = result;
            state.p2Submitted  = true;
        }

        myRoundsPlayed++;
        if (result == state.targetNumber) myRoundsSolved++;


        if (opponentLeft) {
            fillOpponentAsAbsent(state);
        }

        stopTimer();
        advanceIfNeeded(state);
    }


    private void fillOpponentAsAbsent(MojBrojGameState state) {
        if ("player1".equals(myPlayerId)) {
            if (!state.p2Submitted) {
                state.p2Submitted = true;
                state.p2Result    = Integer.MIN_VALUE;
            }
        } else {
            if (!state.p1Submitted) {
                state.p1Submitted = true;
                state.p1Result    = Integer.MIN_VALUE;
            }
        }
    }



    private void handleTimerSync(MojBrojGameState state) {
        if (state == null) return;

        if (state.showingRoundResult) {
            stopTimer();
            timerText.setValue("Prelazak na rundu 2...");
            return;
        }

        if ("finished".equals(state.status)) {
            stopTimer();
            timerText.setValue("Igra završena");
            if ("player2".equals(myPlayerId) && !gameFinished) {
                gameFinished = true;
                if (isMatchScoreLoaded) {
                    finishGameAndIncrement(state);
                } else {
                    new android.os.Handler(android.os.Looper.getMainLooper())
                            .postDelayed(() -> finishGameAndIncrement(state), 1000);
                }
            }
            return;
        }

        if (state.round == 2) {
            round2TransitionScheduled = false;
        }

        if (state.numbersRevealed && state.p1Submitted && state.p2Submitted
                && !state.showingRoundResult && !"finished".equals(state.status)) {
            advanceIfNeeded(state);
            return;
        }

        if (!state.targetRevealed) {
            if (amIStopPlayer(state)) {
                startTimerPhase(PHASE_TARGET, 5);
            } else if (opponentLeft) {
                // FIX: protivnik je trebalo da pritisne STOP, ali ga nema -
                // preuzimam i odmah otkrivam traženi broj umesto da čekam
                // tajmer koji nikad neće isteći na njegovom (ugašenom) uređaju.
                stopTimer();
                state.targetRevealed = true;
                repository.updateGameState(state);
            } else {
                stopTimer();
                timerText.setValue("Čeka se stop...");
            }
        } else if (!state.numbersRevealed) {
            if (amIStopPlayer(state)) {
                startTimerPhase(PHASE_NUMBERS, 5);
            } else if (opponentLeft) {
                // Isti slučaj kao gore, samo za otkrivanje brojeva.
                stopTimer();
                state.numbersRevealed = true;
                repository.updateGameState(state);
            } else {
                stopTimer();
                timerText.setValue("Čeka se stop...");
            }
        } else {
            boolean mySubmitted = "player1".equals(myPlayerId)
                    ? state.p1Submitted : state.p2Submitted;
            if (!mySubmitted) {
                startTimerPhase(PHASE_PLAY, 60);
            } else {
                stopTimer();
                timerText.setValue("Predato – čeka se...");
            }
        }
    }

    private void startTimerPhase(int phase, int seconds) {
        if (currentTimerPhase == phase && timer != null) return;

        stopTimer();
        currentTimerPhase = phase;

        timer = new CountDownTimer(seconds * 1000L, 1000) {
            @Override public void onTick(long ms) {
                timerText.setValue((ms / 1000) + "s");
            }
            @Override public void onFinish() {
                timer = null;
                int expiredPhase  = currentTimerPhase;
                currentTimerPhase = PHASE_NONE;
                handleTimeOut(expiredPhase);
            }
        }.start();
    }

    private void stopTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        currentTimerPhase = PHASE_NONE;
    }

    private void handleTimeOut(int expiredPhase) {
        MojBrojGameState state = gameState.getValue();
        if (state == null) return;

        switch (expiredPhase) {
            case PHASE_TARGET:
                if (amIStopPlayer(state) && !state.targetRevealed) {
                    state.targetRevealed = true;
                    repository.updateGameState(state);
                }
                break;

            case PHASE_NUMBERS:
                if (amIStopPlayer(state) && !state.numbersRevealed) {
                    state.numbersRevealed = true;
                    repository.updateGameState(state);
                }
                break;

            case PHASE_PLAY:
                boolean mySubmitted = "player1".equals(myPlayerId)
                        ? state.p1Submitted : state.p2Submitted;
                if (!mySubmitted) {
                    if ("player1".equals(myPlayerId)) {
                        state.p1Submitted = true;
                        state.p1Result    = Integer.MIN_VALUE;
                    } else {
                        state.p2Submitted = true;
                        state.p2Result    = Integer.MIN_VALUE;
                    }
                    myRoundsPlayed++;

                    // FIX: i ovde, ako je protivnik otišao, ne čekaj njega.
                    if (opponentLeft) {
                        fillOpponentAsAbsent(state);
                    }

                    advanceIfNeeded(state);
                }
                break;
        }
    }



    private void advanceIfNeeded(MojBrojGameState state) {
        boolean bothSubmitted = state.p1Submitted && state.p2Submitted;

        if (!bothSubmitted) {
            // Samo upisujemo svoju predaju, ne diramo tuđu
            repository.updateGameState(state);
            return;
        }

        if (state.round == 1 && round1Processed) return;
        if (state.round == 2 && round2Processed) return;

        if (state.round == 1) round1Processed = true;
        if (state.round == 2) round2Processed = true;

        if (state.round == 1) {

            if (isHost()) {
                awardRoundPoints(state); // ← premesti ovde
            }

            if (isHost()) {
                if (round2TransitionScheduled) return;
                round2TransitionScheduled = true;

                final int finalScoreP1 = state.p1Score;
                final int finalScoreP2 = state.p2Score;

                state.showingRoundResult = true;
                repository.updateGameState(state);

                new CountDownTimer(3000, 1000) {
                    @Override public void onTick(long ms) {
                        timerText.setValue("Sledeća runda za: " + (ms / 1000) + "s");
                    }
                    @Override public void onFinish() {
                        goToRound2(state);
                        state.p1Score = finalScoreP1;
                        state.p2Score = finalScoreP2;
                        state.showingRoundResult = false;
                        repository.updateGameState(state);
                    }
                }.start();

            } else {

                repository.updateGameState(state);
            }

        } else {

            if (gameFinished) return;
            gameFinished = true;

            if (!isHost()) {

                repository.updateGameState(state);
                return;
            }


            new android.os.Handler(android.os.Looper.getMainLooper())
                    .postDelayed(() -> {
                        MojBrojGameState freshState = gameState.getValue();
                        if (freshState == null) return;

                        Log.d(TAG, "[player1] freshState nakon 500ms -> "
                                + "p1Submitted: " + freshState.p1Submitted
                                + " | p2Submitted: " + freshState.p2Submitted
                                + " | p1Result: " + freshState.p1Result
                                + " | p2Result: " + freshState.p2Result
                                + " | p1Score: " + freshState.p1Score
                                + " | p2Score: " + freshState.p2Score);


                        if (!freshState.p2Submitted && !opponentLeft) {
                            new android.os.Handler(android.os.Looper.getMainLooper())
                                    .postDelayed(() -> {
                                        MojBrojGameState retryState = gameState.getValue();
                                        if (retryState == null) return;
                                        Log.d(TAG, "[player1] retryState -> p2Submitted: " + retryState.p2Submitted
                                                + " | p2Result: " + retryState.p2Result);
                                        finalizeGame(retryState);
                                    }, 500);
                        } else {
                            if (opponentLeft) {
                                fillOpponentAsAbsent(freshState);
                            }
                            finalizeGame(freshState);
                        }
                    }, 500);
        }
    }

    private void finalizeGame(MojBrojGameState state) {

        Log.d(TAG, "[finalizeGame] PRE obračuna -> p1Score: " + state.p1Score
                + " | p2Score: " + state.p2Score
                + " | p1Result: " + state.p1Result
                + " | p2Result: " + state.p2Result
                + " | target: " + state.targetNumber
                + " | stopPlayer: " + state.stopPlayer);


        awardRoundPoints(state);

        Log.d(TAG, "[finalizeGame] POSLE obračuna -> p1Score: " + state.p1Score
                + " | p2Score: " + state.p2Score);


        state.status = "finished";
        repository.updateGameState(state);

        if (isMatchScoreLoaded) {
            finishGameAndIncrement(state);
        } else {
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .postDelayed(() -> finishGameAndIncrement(state), 1000);
        }
    }

    private void finishGameAndIncrement(MojBrojGameState state) {
        saveMojBrojStats(state);

        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        if (uid == null) return;

        // Sabiramo istoriju celog meča sa spojenim bodovima iz OBERUNDE
        int myTotalScore = "player1".equals(myPlayerId)
                ? (matchStartingScoreP1 + state.p1Score)
                : (matchStartingScoreP2 + state.p2Score);

        Log.d(TAG, "[" + myPlayerId + "] Upisujem KONAČAN skor u meč: " + myTotalScore);

        FirebaseDatabase.getInstance()
                .getReference("matches")
                .child(matchId)
                .child("scores")
                .child(uid)
                .setValue(myTotalScore)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "[" + myPlayerId + "] Uspešno upisan skor.");

                        // Bilo ko da je predao zadnji, player1 (host) reaguje i pomera igru
                        if (isHost()) {
                            Log.d(TAG, "[player1] Pokrećem prebacivanje na novu igru.");
                            FirebaseDatabase.getInstance()
                                    .getReference("matches")
                                    .child(matchId)
                                    .child("currentGame")
                                    .setValue(ServerValue.increment(1));
                        }
                    }
                });
    }

    private void awardRoundPoints(MojBrojGameState state) {
        int target = state.targetNumber;
        int r1     = state.p1Result;
        int r2     = state.p2Result;

        boolean p1Hit = (r1 == target);
        boolean p2Hit = (r2 == target);

        if (p1Hit && p2Hit) {
            if (state.stopPlayer == 1) state.p1Score += 10;
            else                       state.p2Score += 10;
        }
        else if (p1Hit) {
            state.p1Score += 10;
        } else if (p2Hit) {
            state.p2Score += 10;
        }
        else {
            int d1 = helper.distanceFromTarget(r1, target);
            int d2 = helper.distanceFromTarget(r2, target);

            if (d1 == Integer.MAX_VALUE && d2 == Integer.MAX_VALUE) {
                /* niko nema validan izraz */
            }
            else if (d1 == Integer.MAX_VALUE)  { state.p2Score += 5; }
            else if (d2 == Integer.MAX_VALUE)  { state.p1Score += 5; }
            else if (d1 < d2)                  { state.p1Score += 5; }
            else if (d2 < d1)                  { state.p2Score += 5; }
            else {
                if (state.stopPlayer == 1) state.p1Score += 5;
                else                       state.p2Score += 5;
            }
        }
    }

    private void goToRound2(MojBrojGameState state) {
        round2Processed = false;
        state.showingRoundResult = false;
        state.round              = 2;
        state.stopPlayer         = 2;
        state.targetNumber       = helper.generateTargetNumber();
        state.availableNumbers   = helper.generateAvailableNumbers();
        state.targetRevealed     = false;
        state.numbersRevealed    = false;
        state.p1Submitted        = false;
        state.p2Submitted        = false;
        state.p1Result           = -1;
        state.p2Result           = -1;
        state.p1Expression       = "";
        state.p2Expression       = "";
    }



    private void saveMojBrojStats(MojBrojGameState state) {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) return;

        int myScore  = "player1".equals(myPlayerId) ? state.p1Score : state.p2Score;
        int oppScore = "player1".equals(myPlayerId) ? state.p2Score : state.p1Score;
        boolean iWon = myScore > oppScore;

        Map<String, Object> updates = new HashMap<>();
        updates.put("stats.mojbroj.roundsPlayed", FieldValue.increment(myRoundsPlayed));
        updates.put("stats.mojbroj.roundsSolved", FieldValue.increment(myRoundsSolved));
        updates.put("stats.mojbroj.wins",         FieldValue.increment(iWon ? 1 : 0));
        updates.put("stats.mojbroj.losses",       FieldValue.increment(iWon ? 0 : 1));
        updates.put("stats.global.totalGames",    FieldValue.increment(1));
        updates.put("stats.global.wins",          FieldValue.increment(iWon ? 1 : 0));
        updates.put("stats.global.losses",        FieldValue.increment(iWon ? 0 : 1));

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .update(updates);
    }

    private boolean amIStopPlayer(MojBrojGameState state) {
        // Ako je protivnik otišao, ja preuzimam ulogu STOP igrača bez obzira na rundu
        if (opponentLeft) {
            return true;
        }

        return (state.stopPlayer == 1 && "player1".equals(myPlayerId))
                || (state.stopPlayer == 2 && "player2".equals(myPlayerId));
    }
    @Override
    protected void onCleared() {
        super.onCleared();
        stopTimer();
        if (repository != null) repository.removeListener();
    }


}