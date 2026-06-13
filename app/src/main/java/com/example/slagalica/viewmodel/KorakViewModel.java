package com.example.slagalica.viewmodel;

import android.os.CountDownTimer;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.slagalica.helper.KorakHelper;
import com.example.slagalica.models.korak.KorakGameState;
import com.example.slagalica.repository.KorakRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;


public class KorakViewModel extends ViewModel {

    private KorakRepository repository;
    private final KorakHelper helper = new KorakHelper();

    private final MutableLiveData<KorakGameState> gameState = new MutableLiveData<>();
    private final MutableLiveData<String> timerText = new MutableLiveData<>();


    private CountDownTimer hintTimer;

    private CountDownTimer opponentTimer;

    private String myPlayerId;


    private final int[] myHintWhenSolved = {0, 0};


    private boolean timerRunningForLastHint = false;
    private boolean timerRunningForOpponent = false;
    private int timerRunningForHintCount = -1;

    public void init(String gameId, String playerId) {
        this.myPlayerId = playerId;
        this.repository = new KorakRepository(gameId);
        this.repository.listenToGameState(state -> {
            gameState.setValue(state);
            handleTimerSync(state);
        });
    }

    public LiveData<KorakGameState> getGameState() { return gameState; }
    public LiveData<String> getTimerText()         { return timerText; }
    public String getMyPlayerId()                  { return myPlayerId; }


    public void setupInitialGameIfHost() {
        if (!"player1".equals(myPlayerId)) return;

        KorakHelper.KorakQuestion q = helper.getRandomQuestion();
        KorakGameState initial = new KorakGameState();
        initial.answer = q.answer;
        initial.hints = q.hints;
        initial.revealedHints = 1;
        initial.activePlayer = 1;
        initial.rundaZapocinje = 1;
        repository.updateGameState(initial);
    }


    public boolean submitGuess(String guess) {
        KorakGameState state = gameState.getValue();
        if (state == null || "finished".equals(state.status)) return false;
        if (!amIActive(state)) return false;

        boolean correct = state.answer.trim().equalsIgnoreCase(guess.trim());

        if (correct) {
            int points;
            if (state.isOpponentChance) {
                points = 5;
            } else {
                points = helper.calculateScore(state.revealedHints);
            }

            if ("player1".equals(myPlayerId)) {
                state.p1Score += points;
                state.p1Solved = true;
                state.p1SolvedOnHint = state.revealedHints;
            } else {
                state.p2Score += points;
                state.p2Solved = true;
                state.p2SolvedOnHint = state.revealedHints;
            }

            int roundIdx = state.round - 1;
            if (roundIdx >= 0 && roundIdx < 2) {
                myHintWhenSolved[roundIdx] = state.revealedHints;
            }

            // Prikaži rešenje i završi rundu
            state.revealedAnswer = state.answer;
            cancelAllTimers();
            endRoundLogic(state);
            repository.updateGameState(state);
        }

        return correct;
    }



    private void handleTimerSync(KorakGameState state) {

        if (state.showingRoundResult) {
            cancelAllTimers();
            // Tajmer tekst već se setuje iz CountDownTimer-a u endRoundLogic
            return;
        }

        if ("finished".equals(state.status)) {
            cancelAllTimers();
            timerText.setValue("Igra završena");
            return;
        }

        boolean iAmActiveNow = amIActive(state);

        if (!iAmActiveNow) {
            // Nije moj red – ugasi lokalne tajmere, prikaži poruku
            cancelAllTimers();
            if (state.isOpponentChance) {
                timerText.setValue("Čeka se protivnikova šansa...");
            } else {
                timerText.setValue("Čeka se protivnik...");
            }
            return;
        }

        if (state.isOpponentChance) {

            if (!timerRunningForOpponent) {
                timerRunningForOpponent = true;
                timerRunningForLastHint = false;
                timerRunningForHintCount = -1;
                startOpponentTimer();
            }
        } else if (state.lastHintShowing) {

            if (!timerRunningForLastHint) {
                timerRunningForLastHint = true;
                timerRunningForOpponent = false;
                timerRunningForHintCount = -1;
                startLastHintTimer();
            }
        } else {
            // Normalna faza – tajmer između koraka
            if (timerRunningForHintCount != state.revealedHints) {
                timerRunningForHintCount = state.revealedHints;
                timerRunningForLastHint = false;
                timerRunningForOpponent = false;
                startHintTimer();
            }
        }
    }


    private void startHintTimer() {
        cancelHintTimer();
        hintTimer = new CountDownTimer(10_000, 1000) {
            @Override
            public void onTick(long ms) {
                timerText.setValue("Sledeći korak za: " + (ms / 1000) + "s");
            }
            @Override
            public void onFinish() {
                hintTimer = null;
                timerRunningForHintCount = -1;
                revealNextHintOrTransition();
            }
        }.start();
    }


    private void startLastHintTimer() {
        cancelHintTimer();
        hintTimer = new CountDownTimer(10_000, 1000) {
            @Override
            public void onTick(long ms) {
                timerText.setValue("Poslednji korak – " + (ms / 1000) + "s");
            }
            @Override
            public void onFinish() {
                hintTimer = null;
                timerRunningForLastHint = false;
                // Prelazimo na protivnikovu šansu
                transitionToOpponentChance();
            }
        }.start();
    }


    private void startOpponentTimer() {
        cancelOpponentTimer();
        opponentTimer = new CountDownTimer(10_000, 1000) {
            @Override
            public void onTick(long ms) {
                timerText.setValue("Tvoja šansa: " + (ms / 1000) + "s");
            }
            @Override
            public void onFinish() {
                opponentTimer = null;
                timerRunningForOpponent = false;
                handleOpponentTimeOut();
            }
        }.start();
    }


    private void revealNextHintOrTransition() {
        KorakGameState state = gameState.getValue();
        if (state == null || "finished".equals(state.status)) return;
        if (!amIActive(state) || state.isOpponentChance || state.lastHintShowing) return;

        state.revealedHints++;

        if (state.revealedHints >= 7) {
            // Otkriven je 7. (poslednji) korak
            state.revealedHints = 7;
            state.lastHintShowing = true;
            // activePlayer ostaje isti – igrač ima još 10s
            repository.updateGameState(state);
            // handleTimerSync će pokrenuti startLastHintTimer
        } else {
            // Normalan korak – nastavljamo sa hint tajmerom
            repository.updateGameState(state);
            // handleTimerSync će pokrenuti novi startHintTimer za novi revealedHints
        }
    }


    private void transitionToOpponentChance() {
        KorakGameState state = gameState.getValue();
        if (state == null || "finished".equals(state.status)) return;
        if (!amIActive(state)) return;

        state.lastHintShowing = false;
        state.isOpponentChance = true;
        // Prebacujemo activePlayer na protivnika
        state.activePlayer = (state.rundaZapocinje == 1) ? 2 : 1;

        repository.updateGameState(state);
    }


    private void handleOpponentTimeOut() {
        KorakGameState state = gameState.getValue();
        if (state == null || "finished".equals(state.status)) return;
        if (!amIActive(state)) return;

        // Niko nije pogodio – prikaži rešenje
        state.revealedAnswer = state.answer;
        endRoundLogic(state);
        repository.updateGameState(state);
    }


    private void endRoundLogic(KorakGameState state) {
        cancelAllTimers();
        resetTimerFlags();

        if (state.round == 1) {

            state.showingRoundResult = true;

            repository.updateGameState(state);


            new CountDownTimer(5000, 1000) {
                @Override public void onTick(long ms) {
                    timerText.setValue("Rešenje: prikazuje se još " + (ms / 1000) + "s");
                }
                @Override public void onFinish() {
                    KorakGameState s = gameState.getValue();
                    if (s == null) return;
                    // Pripremi rundu 2
                    KorakHelper.KorakQuestion q = helper.getRandomQuestion();
                    s.showingRoundResult = false;
                    s.round = 2;
                    s.rundaZapocinje = 2;
                    s.activePlayer = 2;
                    s.isOpponentChance = false;
                    s.lastHintShowing = false;
                    s.revealedHints = 1;
                    s.answer = q.answer;
                    s.hints.clear();
                    s.hints.addAll(q.hints);
                    s.revealedAnswer = "";
                    s.p1Solved = false;
                    s.p2Solved = false;
                    s.p1SolvedOnHint = 0;
                    s.p2SolvedOnHint = 0;
                    repository.updateGameState(s);
                }
            }.start();
        } else {
            state.status = "finished";
            int myScore  = "player1".equals(myPlayerId) ? state.p1Score : state.p2Score;
            int oppScore = "player1".equals(myPlayerId) ? state.p2Score : state.p1Score;
            saveKorakStats(myScore > oppScore);
            repository.updateGameState(state);
        }
    }



    private void saveKorakStats(boolean iWon) {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;
        if (uid == null) return;

        Map<String, Object> updates = new HashMap<>();
        for (int i = 0; i < 2; i++) {
            int hint = myHintWhenSolved[i];
            if (hint > 0) {
                updates.put("stats.korak.solvedOnHint" + hint, FieldValue.increment(1));
            } else {
                updates.put("stats.korak.failed", FieldValue.increment(1));
            }
        }
        updates.put("stats.korak.wins",        FieldValue.increment(iWon ? 1 : 0));
        updates.put("stats.korak.losses",      FieldValue.increment(iWon ? 0 : 1));
        updates.put("stats.global.totalGames", FieldValue.increment(1));
        updates.put("stats.global.wins",       FieldValue.increment(iWon ? 1 : 0));
        updates.put("stats.global.losses",     FieldValue.increment(iWon ? 0 : 1));

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .update(updates);
    }



    private boolean amIActive(KorakGameState state) {
        return (state.activePlayer == 1 && "player1".equals(myPlayerId))
                || (state.activePlayer == 2 && "player2".equals(myPlayerId));
    }

    private void resetTimerFlags() {
        timerRunningForLastHint = false;
        timerRunningForOpponent = false;
        timerRunningForHintCount = -1;
    }

    private void cancelHintTimer() {
        if (hintTimer != null) { hintTimer.cancel(); hintTimer = null; }
    }

    private void cancelOpponentTimer() {
        if (opponentTimer != null) { opponentTimer.cancel(); opponentTimer = null; }
    }

    private void cancelAllTimers() {
        cancelHintTimer();
        cancelOpponentTimer();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        cancelAllTimers();
        if (repository != null) repository.removeListener();
    }
}
