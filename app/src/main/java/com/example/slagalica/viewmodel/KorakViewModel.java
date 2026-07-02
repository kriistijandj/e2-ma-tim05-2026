package com.example.slagalica.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.slagalica.helper.KorakHelper;
import com.example.slagalica.models.korak.KorakGameState;
import com.example.slagalica.repository.KorakRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

import android.os.CountDownTimer;

public class KorakViewModel extends ViewModel {

    private KorakRepository repository;
    private final KorakHelper helper = new KorakHelper();

    private final MutableLiveData<KorakGameState> gameState = new MutableLiveData<>();
    private final MutableLiveData<String> timerText = new MutableLiveData<>();

    private String matchId;
    private String myRole;
    private String myUid;

    private CountDownTimer hintTimer;
    private CountDownTimer opponentTimer;

    private boolean timerRunningForLastHint = false;
    private boolean timerRunningForOpponent = false;
    private int timerRunningForHintCount = -1;

    private boolean selfRegistered = false;

    private boolean matchFinishedRegistered = false;
    private boolean currentGameIncremented = false;


    private boolean roundTransitionInProgress = false;


    private boolean opponentHasLeft = false;

    public void init(String matchId, String role) {
        this.matchId = matchId;
        this.myRole = role;
        this.myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        repository = new KorakRepository(matchId);

        repository.listenToGameState(state -> {
            if (state == null) return;


            if (state.scores != null
                    && !state.scores.containsKey(myUid)
                    && state.hints != null
                    && !state.hints.isEmpty()
                    && !selfRegistered) {
                selfRegistered = true;
                state.scores.put(myUid, 0);
                if ("player1".equals(myRole)) {
                    state.player1Id = myUid;
                } else {
                    state.player2Id = myUid;
                }
                repository.updateGameState(state);

                return;
            }

            gameState.setValue(state);


            if (handleAbsentOpponent(state)) {

                return;
            }


            if (state.showingRoundResult && state.round == 1
                    && isHost() && !roundTransitionInProgress) {
                roundTransitionInProgress = true;
                final Map<String, Integer> scoresFromRoundOne = state.scores != null
                        ? new HashMap<>(state.scores)
                        : new HashMap<>();

                new CountDownTimer(1000, 1000) {
                    public void onTick(long ms) {}
                    public void onFinish() {
                        KorakGameState currentState = gameState.getValue();
                        if (currentState == null) currentState = state;
                        buildRound2(currentState, scoresFromRoundOne);
                    }
                }.start();
                return;
            }

            handleTimerSync(state);
        });
    }

    public LiveData<KorakGameState> getGameState() {
        return gameState;
    }

    public LiveData<String> getTimerText() {
        return timerText;
    }


    private boolean gameInitStarted = false;

    private boolean isHost() {
        return "player1".equals(myRole) || (opponentHasLeft && "player2".equals(myRole));
    }

    public void signalReadyAndInit(boolean isSolo) {

        if (isSolo) {

            repository.setReadySolo(this::initializeGameIfNeeded);
            return;
        }

        repository.setReady(myRole, () -> {
            if (isHost()) {
                initializeGameIfNeeded();
            }
        });


        if (opponentHasLeft && isHost()) {
            initializeGameIfNeeded();
        }
    }

    private void initializeGameIfNeeded() {
        if (gameInitStarted) return;


        KorakGameState current = gameState.getValue();
        if (current != null && current.hints != null && !current.hints.isEmpty()) {
            gameInitStarted = true;
            return;
        }

        gameInitStarted = true;

        KorakHelper.KorakQuestion q = helper.getRandomQuestion();

        KorakGameState state = new KorakGameState();
        state.answer = q.answer;
        state.hints = q.hints;
        state.revealedHints = 1;
        state.round = 1;
        state.activePlayer = 1;
        state.status = "active";
        state.scores = new HashMap<>();
        if ("player1".equals(myRole)) {
            state.player1Id = myUid;
        } else {
            state.player2Id = myUid;
        }
        // FIX: domaćin odmah oznacava sebe kao registered
        selfRegistered = true;
        state.scores.put(myUid, 0);

        repository.updateGameState(state);
    }



    public boolean submitGuess(String guess) {

        KorakGameState state = gameState.getValue();
        if (state == null || "finished".equals(state.status)) return false;

        if (!amIActive(state)) return false;

        boolean correct = state.answer.trim().equalsIgnoreCase(guess.trim());
        if (!correct) return false;

        int points = state.isOpponentChance
                ? 5
                : helper.calculateScore(state.revealedHints);

        if (state.scores == null) {
            state.scores = new HashMap<>();
        }

        int current = state.scores.containsKey(myUid)
                ? state.scores.get(myUid)
                : 0;

        Map<String, Integer> newScores = new HashMap<>(state.scores);
        newScores.put(myUid, current + points);
        state.scores = newScores;

        android.util.Log.d("KORAK_LOG", "[" + myRole + "] submitGuess -> Novi lokalni skor pre slanja u bazu: " + state.scores);

        //state.scores.put(myUid, current + points);
        state.revealedAnswer = state.answer;

        cancelAllTimers();
        endRoundLogic(state);

        repository.updateGameState(state);

        return true;
    }



    private void handleTimerSync(KorakGameState state) {

        if ("finished".equals(state.status)) {
            cancelAllTimers();
            timerText.setValue("Igra završena");
            finishMatch(state);
            return;
        }

        if (!amIActive(state)) {
            cancelAllTimers();
            timerText.setValue("Čeka se protivnik...");
            return;
        }

        if (state.isOpponentChance) {
            if (!timerRunningForOpponent) {
                timerRunningForOpponent = true;
                startOpponentTimer();
            }
        } else if (state.lastHintShowing) {
            if (!timerRunningForLastHint) {
                cancelAllTimers();
                timerRunningForLastHint = true;
                startLastHintTimer();
            }
        } else {
            if (timerRunningForHintCount != state.revealedHints) {
                cancelAllTimers();
                timerRunningForHintCount = state.revealedHints;
                startHintTimer();
            }
        }
    }

    private void startHintTimer() {
        cancelHintTimer();

        hintTimer = new CountDownTimer(10000, 1000) {
            public void onTick(long ms) {
                timerText.setValue("Sledeći korak: " + (ms / 1000));
            }

            public void onFinish() {
                revealNextHint();
            }
        }.start();
    }

    private void startLastHintTimer() {
        cancelHintTimer();

        hintTimer = new CountDownTimer(10000, 1000) {
            public void onTick(long ms) {
                timerText.setValue("Poslednji korak: " + (ms / 1000));
            }

            public void onFinish() {
                transitionToOpponent();
            }
        }.start();
    }

    private void startOpponentTimer() {
        cancelOpponentTimer();

        opponentTimer = new CountDownTimer(10000, 1000) {
            public void onTick(long ms) {
                timerText.setValue("Protivnik igra: " + (ms / 1000));
            }

            public void onFinish() {
                opponentTimeout();
            }
        }.start();
    }



    private void revealNextHint() {
        KorakGameState state = gameState.getValue();
        if (state == null) return;

        state.revealedHints++;

        if (state.revealedHints >= 7) {
            state.revealedHints = 7;
            state.lastHintShowing = true;
        }

        repository.updateGameState(state);
    }

    private void transitionToOpponent() {
        KorakGameState state = gameState.getValue();
        if (state == null) return;

        if (opponentHasLeft) {

            state.revealedAnswer = state.answer;
            endRoundLogic(state);
            repository.updateGameState(state);
            return;
        }

        state.revealedHints = 7;
        state.isOpponentChance = true;
        state.lastHintShowing = false;
        state.activePlayer = state.activePlayer == 1 ? 2 : 1;

        repository.updateGameState(state);
    }

    private void opponentTimeout() {
        KorakGameState state = gameState.getValue();
        if (state == null) return;

        state.revealedAnswer = state.answer;

        endRoundLogic(state);
        repository.updateGameState(state);
    }

    private void endRoundLogic(KorakGameState state) {
        cancelAllTimers();

        android.util.Log.d("KORAK_LOG", "[" + myRole + "] endRoundLogic -> Ulaz u metodu. Trenutna runda: " + state.round + ", Trenutni bodovi u state: " + state.scores);

        if (state.round == 1) {
            state.showingRoundResult = true;
            repository.updateGameState(state);

            final Map<String, Integer> scoresFromRoundOne = state.scores != null
                    ? new HashMap<>(state.scores)
                    : new HashMap<>();

            android.util.Log.d("KORAK_LOG", "[" + myRole + "] endRoundLogic -> Zaključani bodovi za tajmer (scoresFromRoundOne): " + scoresFromRoundOne);


            if (isHost() && !roundTransitionInProgress) {
                roundTransitionInProgress = true;
                new CountDownTimer(5000, 1000) {
                    public void onTick(long ms) {}

                    public void onFinish() {
                        KorakGameState currentState = gameState.getValue();
                        if (currentState == null) currentState = state;
                        buildRound2(currentState, scoresFromRoundOne);
                    }
                }.start();
            }

        } else {
            android.util.Log.d("KORAK_LOG", "[" + myRole + "] Runda 2 završena. Postavljam status na finished.");
            state.status = "finished";
            repository.updateGameState(state);


        }
    }

    private void buildRound2(KorakGameState currentState, Map<String, Integer> scoresFromRoundOne) {
        KorakHelper.KorakQuestion q = helper.getRandomQuestion();

        currentState.round = 2;
        currentState.activePlayer = 2;
        currentState.revealedHints = 1;
        currentState.isOpponentChance = false;
        currentState.lastHintShowing = false;
        currentState.showingRoundResult = false;
        currentState.revealedAnswer = "";
        currentState.answer = q.answer;
        currentState.hints = q.hints;

        // Vraćamo bodove
        currentState.scores = scoresFromRoundOne;

        android.util.Log.d("KORAK_LOG", "[" + myRole + "] šaljem u bazu za RUNDU 2 bodove: " + currentState.scores);
        repository.updateGameState(currentState);
    }



    private void finishMatch(KorakGameState state) {
        int score = (state.scores != null && state.scores.containsKey(myUid) && state.scores.get(myUid) != null)
                ? state.scores.get(myUid)
                : 0;

        // 1. Svako bezbedno upisuje svoj lični skor (idempotentno, može se ponoviti)
        if (!matchFinishedRegistered) {
            matchFinishedRegistered = true;
            android.util.Log.d("KORAK_LOG", "[" + myRole + "] finishMatch -> Moj UID: " + myUid + " | Bodovi koje šaljem u /matches: " + score);
            FirebaseDatabase.getInstance()
                    .getReference("matches")
                    .child(matchId)
                    .child("scores")
                    .child(myUid)
                    .setValue(score);
        }


        if (isHost() && !currentGameIncremented) {
            android.util.Log.d("KORAK_LOG", "[" + myRole + "] Ja sam domaćin, pokušavam da otključam uvećanje currentGame");
            repository.tryClaimCurrentGameIncrement(matchId, iAmTheOneWhoIncremented -> {

                currentGameIncremented = true;
                if (iAmTheOneWhoIncremented) {
                    android.util.Log.d("KORAK_LOG", "[" + myRole + "] Osvojio lock, currentGame uvećan za +1");
                } else {
                    android.util.Log.d("KORAK_LOG", "[" + myRole + "] Lock je već zauzet, currentGame NIJE ponovo uvećan");
                }
            });
        }
    }

    // ---------------- HELPERS ----------------

    private boolean amIActive(KorakGameState state) {
        return (state.activePlayer == 1 && "player1".equals(myRole))
                || (state.activePlayer == 2 && "player2".equals(myRole));
    }

    private void cancelHintTimer() {
        if (hintTimer != null) {
            hintTimer.cancel();
            hintTimer = null;
        }
    }

    private void cancelOpponentTimer() {
        if (opponentTimer != null) {
            opponentTimer.cancel();
            opponentTimer = null;
        }
    }

    private void cancelAllTimers() {
        cancelHintTimer();
        cancelOpponentTimer();

        timerRunningForLastHint = false;
        timerRunningForOpponent = false;
        timerRunningForHintCount = -1;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        cancelAllTimers();
        if (repository != null) repository.removeListener();
    }

    public void onOpponentLeft() {
        opponentHasLeft = true;

        KorakGameState state = gameState.getValue();


        if (state != null && "finished".equals(state.status)) {
            finishMatch(state);
            return;
        }


        if (state != null && state.showingRoundResult && state.round == 1
                && isHost() && !roundTransitionInProgress) {
            roundTransitionInProgress = true;
            final Map<String, Integer> scoresFromRoundOne = state.scores != null
                    ? new HashMap<>(state.scores)
                    : new HashMap<>();
            buildRound2(state, scoresFromRoundOne);
            return;
        }

        handleAbsentOpponent(state);
    }


    private boolean handleAbsentOpponent(KorakGameState state) {
        if (!opponentHasLeft) return false;
        if (state == null || "finished".equals(state.status)) return false;

        if (amIActive(state)) return false;

        cancelAllTimers();

        if (state.isOpponentChance) {

            opponentTimeout();
        } else {

            state.activePlayer = "player1".equals(myRole) ? 1 : 2;
            repository.updateGameState(state);
        }

        return true;
    }
}