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

            cancelAllTimers();
            endRoundLogic(state);
        }

        repository.updateGameState(state);
        return correct;
    }



    private void handleTimerSync(KorakGameState state) {
        if ("finished".equals(state.status)) {
            cancelAllTimers();
            return;
        }

        boolean active = amIActive(state);

        if (!active) {
            cancelAllTimers();
            timerText.setValue("Čeka se protivnik...");
            return;
        }

        if (state.isOpponentChance) {

            if (opponentTimer == null) {
                startOpponentTimer();
            }
        } else {

            if (hintTimer == null && state.revealedHints < 7) {
                startHintTimer(state);
            }
        }
    }


    private void startHintTimer(KorakGameState initialState) {
        cancelHintTimer();

        hintTimer = new CountDownTimer(10_000, 1000) {
            @Override
            public void onTick(long ms) { timerText.setValue("Sledeći korak za: " + (ms / 1000) + "s"); }
            @Override
            public void onFinish() {
                hintTimer = null;
                revealNextHint();
            }
        }.start();
    }

    private void revealNextHint() {
        KorakGameState state = gameState.getValue();
        if (state == null || "finished".equals(state.status)) return;
        if (!amIActive(state) || state.isOpponentChance) return;

        state.revealedHints++;

        if (state.revealedHints >= 7) {

            state.isOpponentChance = true;
            state.activePlayer = (state.rundaZapocinje == 1) ? 2 : 1;
        }

        repository.updateGameState(state);


        if (!state.isOpponentChance) {
            startHintTimer(state);
        }
    }

    private void startOpponentTimer() {
        cancelOpponentTimer();
        opponentTimer = new CountDownTimer(10_000, 1000) {
            @Override
            public void onTick(long ms) { timerText.setValue("Šansa: " + (ms / 1000) + "s"); }
            @Override
            public void onFinish() {
                opponentTimer = null;
                handleOpponentTimeOut();
            }
        }.start();
    }

    private void handleOpponentTimeOut() {
        KorakGameState state = gameState.getValue();
        if (state == null) return;
        if (!amIActive(state)) return;


        endRoundLogic(state);
        repository.updateGameState(state);
    }



    private void endRoundLogic(KorakGameState state) {
        cancelAllTimers();

        if (state.round == 1) {

            KorakHelper.KorakQuestion q = helper.getRandomQuestion();
            state.round = 2;
            state.rundaZapocinje = 2;
            state.activePlayer = 2;
            state.isOpponentChance = false;
            state.revealedHints = 1;
            state.answer = q.answer;
            state.hints.clear();
            state.hints.addAll(q.hints);
        } else {

            state.status = "finished";
            int myScore  = "player1".equals(myPlayerId) ? state.p1Score : state.p2Score;
            int oppScore = "player1".equals(myPlayerId) ? state.p2Score : state.p1Score;
            saveKorakStats(myScore > oppScore);
        }
    }



    private void saveKorakStats(boolean iWon) {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;
        if (uid == null) return;

        Map<String, Object> updates = new HashMap<>();

        // Procenat pogadjanja po koraku (podaci za statistiku profila)
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
