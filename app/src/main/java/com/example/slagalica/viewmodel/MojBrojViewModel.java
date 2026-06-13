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

    // Faze tajmera – čuvaju se odvojeno od timera da cancelTimer() ne gubi fazu
    private static final int PHASE_NONE    = 0;
    private static final int PHASE_TARGET  = 1;
    private static final int PHASE_NUMBERS = 2;
    private static final int PHASE_PLAY    = 3;

    private int currentTimerPhase = PHASE_NONE;

    // Statistika za čuvanje
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
        initial.stopPlayer = 1;
        repository.updateGameState(initial);
    }


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
            state.p1Result = result;
            state.p1Submitted = true;
        } else {
            state.p2Expression = expression;
            state.p2Result = result;
            state.p2Submitted = true;
        }

        myRoundsPlayed++;
        if (result == state.targetNumber) myRoundsSolved++;

        // Zaustavi lokalni tajmer – predali smo, više nam ne treba
        stopTimer();

        advanceIfNeeded(state);
    }




    private void handleTimerSync(MojBrojGameState state) {

        // Dok se prikazuju rezultati runde – nema tajmera
        if (state.showingRoundResult) {
            stopTimer();
            timerText.setValue("Prikazuju se rezultati...");
            return;
        }

        if ("finished".equals(state.status)) {
            stopTimer();
            timerText.setValue("Igra završena");
            return;
        }

        if (!state.targetRevealed) {
            if (amIStopPlayer(state)) {
                startTimerPhase(PHASE_TARGET, 5);
            } else {
                stopTimer();
                timerText.setValue("Čeka se stop...");
            }
        } else if (!state.numbersRevealed) {
            if (amIStopPlayer(state)) {
                startTimerPhase(PHASE_NUMBERS, 5);
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
                // Predao sam – ugasi moj tajmer, čekam protivnika
                stopTimer();
                timerText.setValue("Predato – čeka se...");
            }
        }
    }


    private void startTimerPhase(int phase, int seconds) {
        if (currentTimerPhase == phase && timer != null) {
            // Isti tajmer već teče, ne dirajmo ga
            return;
        }


        if (timer != null) {
            timer.cancel();
            timer = null;
        }

        currentTimerPhase = phase;  // setuj PRE nego što timer krene

        timer = new CountDownTimer(seconds * 1000L, 1000) {
            @Override
            public void onTick(long ms) {
                timerText.setValue((ms / 1000) + "s");
            }
            @Override
            public void onFinish() {
                timer = null;
                // currentTimerPhase OSTAJE postavljen na fazu koja je istekla
                // da handleTimeOut() zna šta da uradi
                int expiredPhase = currentTimerPhase;
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
                // Automatska predaja – kao da je igrač kliknuo "Predaj" sa praznim izrazom
                boolean mySubmitted = "player1".equals(myPlayerId)
                        ? state.p1Submitted : state.p2Submitted;
                if (!mySubmitted) {
                    if ("player1".equals(myPlayerId)) {
                        state.p1Submitted = true;
                        state.p1Result = Integer.MIN_VALUE;
                    } else {
                        state.p2Submitted = true;
                        state.p2Result = Integer.MIN_VALUE;
                    }
                    myRoundsPlayed++;
                    advanceIfNeeded(state);
                }
                break;
        }
    }




    private void advanceIfNeeded(MojBrojGameState state) {
        boolean bothSubmitted = state.p1Submitted && state.p2Submitted;
        if (!bothSubmitted) {
            // Samo jedan je predao – sačuvaj i čekaj drugog
            repository.updateGameState(state);
            return;
        }

        // Oba su predala – dodeli poene za ovu rundu
        awardRoundPoints(state);

        if (state.round == 1) {
            // Postavi flag za prikaz rezultata
            state.showingRoundResult = true;
            repository.updateGameState(state);

            // Samo player1 pokreće countdown i prelaz na rundu 2
            // (player2 samo čeka novi state iz baze)
            if ("player1".equals(myPlayerId)) {
                new CountDownTimer(5000, 1000) {
                    @Override
                    public void onTick(long ms) {
                        timerText.setValue("Sledeća runda za: " + (ms / 1000) + "s");
                    }
                    @Override
                    public void onFinish() {
                        // Uzmi najsvežiji state iz LiveData
                        MojBrojGameState fresh = gameState.getValue();
                        if (fresh == null) return;
                        goToRound2(fresh);
                        repository.updateGameState(fresh);
                    }
                }.start();
            }
        } else {
            // Runda 2 završena – kraj igre
            finishGame(state);
            repository.updateGameState(state);
        }
    }


    private void awardRoundPoints(MojBrojGameState state) {
        int target = state.targetNumber;
        int r1 = state.p1Result;
        int r2 = state.p2Result;

        boolean p1Hit = (r1 == target);
        boolean p2Hit = (r2 == target);

        if (p1Hit) state.p1Score += 10;
        if (p2Hit) state.p2Score += 10;

        if (!p1Hit && !p2Hit) {
            int d1 = helper.distanceFromTarget(r1, target);
            int d2 = helper.distanceFromTarget(r2, target);

            if (d1 == Integer.MAX_VALUE && d2 == Integer.MAX_VALUE) {
                // Niko nije uneo ništa
            } else if (d1 == Integer.MAX_VALUE) {
                state.p2Score += 5;
            } else if (d2 == Integer.MAX_VALUE) {
                state.p1Score += 5;
            } else if (d1 < d2) {
                state.p1Score += 5;
            } else if (d2 < d1) {
                state.p2Score += 5;
            } else {
                // Ista razlika → stopPlayer dobija poene
                if (state.stopPlayer == 1) state.p1Score += 5;
                else state.p2Score += 5;
            }
        }
    }

    private void goToRound2(MojBrojGameState state) {
        state.showingRoundResult = false;   // ← obavezno, inače ostaje zauvek true
        state.round = 2;
        state.stopPlayer = 2;

        state.targetNumber = helper.generateTargetNumber();
        state.availableNumbers = helper.generateAvailableNumbers();

        state.targetRevealed = false;
        state.numbersRevealed = false;
        state.p1Submitted = false;
        state.p2Submitted = false;
        state.p1Result = -1;
        state.p2Result = -1;
        state.p1Expression = "";
        state.p2Expression = "";
    }

    private void finishGame(MojBrojGameState state) {
        state.status = "finished";
        boolean iWon = ("player1".equals(myPlayerId) ? state.p1Score : state.p2Score)
                > ("player1".equals(myPlayerId) ? state.p2Score : state.p1Score);
        saveMojBrojStats(iWon);
    }




    private boolean amIStopPlayer(MojBrojGameState state) {
        return (state.stopPlayer == 1 && "player1".equals(myPlayerId))
                || (state.stopPlayer == 2 && "player2".equals(myPlayerId));
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

    @Override
    protected void onCleared() {
        super.onCleared();
        stopTimer();
        if (repository != null) repository.removeListener();
    }
}
