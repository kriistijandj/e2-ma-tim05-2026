package com.example.slagalica.viewmodel;

import android.os.CountDownTimer;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.slagalica.helper.SkockoHelper;
import com.example.slagalica.models.skocko.FirebaseAttempt;
import com.example.slagalica.models.skocko.SkockoFeedback;
import com.example.slagalica.models.skocko.SkockoGameState;
import com.example.slagalica.models.skocko.SkockoSymbol;
import com.example.slagalica.repository.SkockoRepository;

import java.util.ArrayList;
import java.util.List;

public class SkockoViewModel extends ViewModel {

    private SkockoRepository repository;
    private final SkockoHelper skockoHelper = new SkockoHelper();

    private final MutableLiveData<SkockoGameState> gameState = new MutableLiveData<>();
    private final MutableLiveData<String> timerText = new MutableLiveData<>();

    private CountDownTimer timer;
    private String myPlayerId; // "player1" ili "player2"

    public void init(String gameId, String myPlayerId) {
        this.myPlayerId = myPlayerId;
        this.repository = new SkockoRepository(gameId);

        // Osluškujemo Firebase izmene
        this.repository.listenToGameState(state -> {
            gameState.setValue(state);
            handleTimerSync(state);
        });
    }

    public LiveData<SkockoGameState> getGameState() { return gameState; }
    public LiveData<String> getTimerText() { return timerText; }
    public String getMyPlayerId() { return myPlayerId; }

    private void handleTimerSync(SkockoGameState state) {
        // Ako je igra završena, gasimo tajmer
        if ("finished".equals(state.status)) {
            if (timer != null) timer.cancel();
            return;
        }

        // Lokalno upravljanje tajmerom u zavisnosti od stanja (Samo za aktivnog igrača)
        boolean amIActive = (state.activePlayer == 1 && "player1".equals(myPlayerId)) ||
                (state.activePlayer == 2 && "player2".equals(myPlayerId));

        if (amIActive) {
            if (timer == null) {
                int seconds = state.isOpponentChance ? 10 : 30;
                startTimer(seconds);
            }
        } else {
            if (timer != null) {
                timer.cancel();
                timer = null;
            }
            timerText.setValue("Čeka se protivnik...");
        }
    }

    private void startTimer(int seconds) {
        if (timer != null) timer.cancel();
        timer = new CountDownTimer(seconds * 1000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timerText.setValue((millisUntilFinished / 1000) + "s");
            }

            @Override
            public void onFinish() {
                timerText.setValue("0s");
                timer = null;
                handleTimeOut();
            }
        }.start();
    }

    private void handleTimeOut() {
        SkockoGameState state = gameState.getValue();
        if (state == null) return;

        // SIGURNOSNA PROVERA: Samo aktivni igrač sme da upisuje istek vremena u Firebase!
        // Ovo sprečava da oba telefona u istom milisekundom pošalju promenu i zbune bazu.
        int activePlayerNum = "player1".equals(myPlayerId) ? 1 : 2;
        if (state.activePlayer != activePlayerNum) return;

        if (!state.isOpponentChance) {
            // 1. Regularni igrač nije pogodio u 30s -> Prelazak na šansu protivnika (10 sekundi)
            state.isOpponentChance = true;
            // Protivnik postaje aktivni igrač
            state.activePlayer = (state.rundaZapocinje == 1) ? 2 : 1;
            repository.updateGameState(state);
        } else {
            // 2. Isteklo je 10 sekundi šanse za protivnika, a on nije uradio submit -> Kraj runde!
            endRoundLogic(state);
        }
    }

    private void endRoundLogic(SkockoGameState state) {
        if (state.round == 1) {
            // --- PRELAZAK NA RUNDU 2 ---
            state.round = 2;
            state.rundaZapocinje = 2;
            state.activePlayer = 2; // U drugoj rundi Player 2 igra prvi!
            state.isOpponentChance = false;
            state.attempts.clear();

            // Generisanje novog rešenja za drugu rundu
            List<SkockoSymbol> newSol = skockoHelper.generateSolution();
            state.solution.clear();
            for (SkockoSymbol s : newSol) {
                state.solution.add(s.name());
            }
        } else {
            // --- KRAJ OBE RUNDE ---
            state.status = "finished";
        }

        // Čuvanje novog stanja u bazi
        repository.updateGameState(state);
    }

    public void submitAttempt(List<SkockoSymbol> attemptSymbols) {
        SkockoGameState state = gameState.getValue();
        if (state == null) return;

        // Pretvaramo rešenje iz baze u Enume radi provere u helperu
        List<SkockoSymbol> solutionSymbols = new ArrayList<>();
        for (String s : state.solution) {
            solutionSymbols.add(SkockoSymbol.valueOf(s));
        }

        SkockoFeedback feedback = skockoHelper.evaluate(solutionSymbols, attemptSymbols);

        // Pakujemo simbole u stringove za bazu
        List<String> symbolStrings = new ArrayList<>();
        for (SkockoSymbol s : attemptSymbols) symbolStrings.add(s.name());

        int activePlayerNum = "player1".equals(myPlayerId) ? 1 : 2;
        FirebaseAttempt newAttempt = new FirebaseAttempt(activePlayerNum, symbolStrings, feedback.getRed(), feedback.getYellow());
        state.attempts.add(newAttempt);

        // Provera pogotka
        if (feedback.getRed() == 4) {
            int points = 0;
            if (!state.isOpponentChance) {
                int currentAttemptIdx = state.attempts.size() - 1;
                if (currentAttemptIdx <= 1) points = 20;
                else if (currentAttemptIdx <= 3) points = 15;
                else points = 10;
            } else {
                points = 10;
            }

            if (activePlayerNum == 1) state.p1Score += points;
            else state.p2Score += points;

            endRoundLogic(state);
            return;
        }

        // Ako je bila šansa protivnika a promašio je, odmah se završava runda
        if (state.isOpponentChance) {
            endRoundLogic(state);
            return;
        }

        // Ako je iskorišćeno svih 6 pokušaja regularnog igrača, prelazi se na šansu protivnika
        if (state.attempts.size() == 6) {
            state.isOpponentChance = true;
            state.activePlayer = (state.rundaZapocinje == 1) ? 2 : 1;
        }

        repository.updateGameState(state);
    }


    // Kada se npr. "player1" prvi put spoji, on može inicijalizovati čvor ako ne postoji
    public void setupInitialGameIfHost() {
        if ("player1".equals(myPlayerId)) {
            SkockoGameState initialState = new SkockoGameState();
            List<SkockoSymbol> initialSol = skockoHelper.generateSolution();
            for (SkockoSymbol s : initialSol) initialState.solution.add(s.name());
            repository.updateGameState(initialState);
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (repository != null) repository.removeListener();
        if (timer != null) timer.cancel();
    }
}