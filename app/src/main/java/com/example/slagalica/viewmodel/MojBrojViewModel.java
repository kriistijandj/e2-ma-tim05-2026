package com.example.slagalica.viewmodel;

import android.os.CountDownTimer;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.slagalica.helper.MojBrojHelper;
import com.example.slagalica.models.mojbroj.MojBrojGameState;
import com.example.slagalica.repository.MojBrojRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;


public class MojBrojViewModel extends ViewModel {

    private MojBrojRepository repository;
    private final MojBrojHelper helper = new MojBrojHelper();

    private final MutableLiveData<MojBrojGameState> gameState = new MutableLiveData<>();
    private final MutableLiveData<String> timerText = new MutableLiveData<>();

    private CountDownTimer timer;
    private String myPlayerId;


    private static final int PHASE_TARGET  = 1; // 5s za otkrivanje targetNumber
    private static final int PHASE_NUMBERS = 2; // 5s za otkrivanje brojeva
    private static final int PHASE_PLAY    = 3; // 60s za unos izraza
    private int currentTimerPhase = PHASE_TARGET;


    private boolean myRoundSolved = false;
    private int myRoundsPlayed = 0;
    private int myRoundsSolved = 0;

    public void init(String gameId, String playerId) {
        this.myPlayerId = playerId;
        this.repository = new MojBrojRepository(gameId);
        this.repository.listenToGameState(state -> {
            gameState.setValue(state);
            handleTimerSync(state);
        });
    }

    public LiveData<MojBrojGameState> getGameState() { return gameState; }
    public LiveData<String> getTimerText()           { return timerText; }
    public String getMyPlayerId()                    { return myPlayerId; }


    public void setupInitialGameIfHost() {
        if (!"player1".equals(myPlayerId)) return;

        MojBrojGameState initial = new MojBrojGameState();
        initial.targetNumber = helper.generateTargetNumber();
        initial.availableNumbers = helper.generateAvailableNumbers();
        repository.updateGameState(initial);
    }


    public void onStopPressed() {
        MojBrojGameState state = gameState.getValue();
        if (state == null || "finished".equals(state.status)) return;

        if (!amIActive(state)) return;

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
        if (!amIActive(state)) return;

        int result = helper.evaluate(expression);

        if ("player1".equals(myPlayerId)) {
            state.p1Expression = expression;
            state.p1Result = result;
            state.p1Submitted = true;
        } else {
            state.p2Expression = expression;
            state.p2Result = result;
            state.p2Submitted = true;
        }


        myRoundsPlayed++;
        if (result == state.targetNumber) {
            myRoundSolved = true;
            myRoundsSolved++;
        }

        advanceIfNeeded(state);
    }



    private void handleTimerSync(MojBrojGameState state) {
        if ("finished".equals(state.status)) {
            cancelTimer();
            return;
        }

        boolean active = amIActive(state);

        if (!active) {
            cancelTimer();
            timerText.setValue("Čeka se protivnik...");
            return;
        }


        if (!state.targetRevealed) {
            if (currentTimerPhase != PHASE_TARGET || timer == null) {
                currentTimerPhase = PHASE_TARGET;
                startTimer(5);
            }
        } else if (!state.numbersRevealed) {
            if (currentTimerPhase != PHASE_NUMBERS || timer == null) {
                currentTimerPhase = PHASE_NUMBERS;
                startTimer(5);
            }
        } else {
            if (currentTimerPhase != PHASE_PLAY || timer == null) {
                currentTimerPhase = PHASE_PLAY;
                startTimer(60);
            }
        }
    }

    private void startTimer(int seconds) {
        cancelTimer();
        timer = new CountDownTimer(seconds * 1000L, 1000) {
            @Override
            public void onTick(long ms) { timerText.setValue((ms / 1000) + "s"); }
            @Override
            public void onFinish() {
                timerText.setValue("0s");
                timer = null;
                handleTimeOut();
            }
        }.start();
    }

    private void cancelTimer() {
        if (timer != null) { timer.cancel(); timer = null; }
    }

    private void handleTimeOut() {
        MojBrojGameState state = gameState.getValue();
        if (state == null) return;
        if (!amIActive(state)) return;

        switch (currentTimerPhase) {
            case PHASE_TARGET:
                state.targetRevealed = true;
                repository.updateGameState(state);
                break;
            case PHASE_NUMBERS:
                state.numbersRevealed = true;
                repository.updateGameState(state);
                break;
            case PHASE_PLAY:
                // Igrač nije uneo ništa – šalje prazan izraz (rezultat -1 = nije igrao)
                if ("player1".equals(myPlayerId) && !state.p1Submitted) {
                    state.p1Submitted = true;
                    state.p1Result = -1;
                    myRoundsPlayed++;
                } else if ("player2".equals(myPlayerId) && !state.p2Submitted) {
                    state.p2Submitted = true;
                    state.p2Result = -1;
                    myRoundsPlayed++;
                }
                advanceIfNeeded(state);
                break;
        }
    }



    private void advanceIfNeeded(MojBrojGameState state) {
        if (state.round == 1) {
            // Runda 1 – player1 igra; završava se kad player1 preda
            if (state.p1Submitted) {
                awardRound1Points(state);
                goToRound2(state);
            }
        } else {
            // Runda 2 – player2 igra; završava se kad player2 preda
            if (state.p2Submitted) {
                awardRound2Points(state);
                finishGame(state);
            }
        }
        repository.updateGameState(state);
    }


    private void awardRound1Points(MojBrojGameState state) {
        int target = state.targetNumber;
        int r1 = state.p1Result; // player1 igrao u rundi 1

        if (r1 == target) {
            state.p1Score += 10;
        }

    }

    private void awardRound2Points(MojBrojGameState state) {
        int target = state.targetNumber;
        int r2 = state.p2Result; // player2 igrao u rundi 2

        if (r2 == target) {
            state.p2Score += 10;
        }


        if (state.p1Result != target && state.p2Result != target) {
            int d1 = helper.distanceFromTarget(state.p1Result, target);
            int d2 = helper.distanceFromTarget(state.p2Result, target);

            if (d1 == Integer.MAX_VALUE && d2 == Integer.MAX_VALUE) {
                // Oba igrača nisu ništa uneli
            } else if (d1 < d2) {
                state.p1Score += 5;
            } else if (d2 < d1) {
                state.p2Score += 5;
            } else if (d1 == d2 && d1 != Integer.MAX_VALUE) {

                state.p1Score += 5;
            }
        }
    }

    private void goToRound2(MojBrojGameState state) {
        state.round = 2;
        state.activePlayer = 2;

        state.targetNumber = helper.generateTargetNumber();
        state.availableNumbers = helper.generateAvailableNumbers();
        state.targetRevealed = false;
        state.numbersRevealed = false;
        state.p2Submitted = false;


        currentTimerPhase = PHASE_TARGET;
    }

    private void finishGame(MojBrojGameState state) {
        state.status = "finished";
        boolean iWon = ("player1".equals(myPlayerId) ? state.p1Score : state.p2Score)
                > ("player1".equals(myPlayerId) ? state.p2Score : state.p1Score);
        saveMojBrojStats(iWon);
    }


    private void saveMojBrojStats(boolean iWon) {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;
        if (uid == null) return;

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



    private boolean amIActive(MojBrojGameState state) {
        return (state.activePlayer == 1 && "player1".equals(myPlayerId))
                || (state.activePlayer == 2 && "player2".equals(myPlayerId));
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        cancelTimer();
        if (repository != null) repository.removeListener();
    }
}
