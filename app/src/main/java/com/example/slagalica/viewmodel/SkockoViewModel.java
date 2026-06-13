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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SkockoViewModel extends ViewModel {

    private SkockoRepository repository;
    private final SkockoHelper skockoHelper = new SkockoHelper();

    private final MutableLiveData<SkockoGameState> gameState = new MutableLiveData<>();
    private final MutableLiveData<String> timerText = new MutableLiveData<>();

    private CountDownTimer timer;
    private String myPlayerId; // "player1" ili "player2"

    // ====== STATISTIKA (prati se lokalno tokom partije) ======
    // Bilježimo u kom pokuša je pogođeno (1-6), ili 0 ako nije
    private int myWinAttemptNumber = 0;   // koji pokušaj je bio pobjednički (0 = nije pogođeno)
    private boolean myRoundWon     = false;

    public void init(String gameId, String myPlayerId) {
        this.myPlayerId = myPlayerId;
        this.repository = new SkockoRepository(gameId);

        this.repository.listenToGameState(state -> {
            gameState.setValue(state);
            handleTimerSync(state);
        });
    }

    public LiveData<SkockoGameState> getGameState() { return gameState; }
    public LiveData<String> getTimerText()          { return timerText; }
    public String getMyPlayerId()                   { return myPlayerId; }

    private int lastActivePlayerState = -1;
    private boolean lastOpponentChanceState = false;
    private int lastRoundState = -1;

    private void handleTimerSync(SkockoGameState state) {
        if ("finished".equals(state.status)) {
            if (timer != null) {
                timer.cancel();
                timer = null;
            }
            return;
        }

        boolean amIActive = (state.activePlayer == 1 && "player1".equals(myPlayerId)) ||
                (state.activePlayer == 2 && "player2".equals(myPlayerId));

        //Ako je došlo do promene igrača, runde ili režima igre, obavezno uništavamo stari tajmer!
        if (state.activePlayer != lastActivePlayerState ||
                state.isOpponentChance != lastOpponentChanceState ||
                state.round != lastRoundState) {

            if (timer != null) {
                timer.cancel();
                timer = null;
            }

            lastActivePlayerState = state.activePlayer;
            lastOpponentChanceState = state.isOpponentChance;
            lastRoundState = state.round;
        }

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

        int activePlayerNum = "player1".equals(myPlayerId) ? 1 : 2;
        if (state.activePlayer != activePlayerNum) return;

        if (!state.isOpponentChance) {
            state.isOpponentChance = true;
            state.activePlayer = (state.rundaZapocinje == 1) ? 2 : 1;
            repository.updateGameState(state);
        } else {
            endRoundLogic(state);
        }
    }

    private void endRoundLogic(SkockoGameState state) {
        if (state.round == 1) {
            // --- PRELAZAK NA RUNDU 2 ---
            state.round = 2;
            state.rundaZapocinje = 2;
            state.activePlayer = 2; // Rundu 2 započinje igrač 2 prema pravilu a.
            state.isOpponentChance = false;
            state.attempts.clear();

            // Generisanje novog rešenja za drugu rundu
            List<SkockoSymbol> newSol = skockoHelper.generateSolution();
            state.solution.clear();
            for (SkockoSymbol s : newSol) {
                state.solution.add(s.name());
            }

            // RESETUJ LOKALNE PARAMETRE ZA NOVU RUNDU DA NE ZAKLJUČAJU KOD
            myRoundWon = false;
            myWinAttemptNumber = 0;

        } else {
            // --- KRAJ OBE RUNDE ---
            state.status = "finished";

            int myScore  = "player1".equals(myPlayerId) ? state.p1Score : state.p2Score;
            int oppScore = "player1".equals(myPlayerId) ? state.p2Score : state.p1Score;
            boolean iWon = myScore > oppScore;

            saveSkockoStats(iWon);
        }

        repository.updateGameState(state);
    }

    // ==============================
    // UPIS STATISTIKE U FIRESTORE
    // ==============================

    private void saveSkockoStats(boolean iWon) {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        if (uid == null) return;

        Map<String, Object> updates = new HashMap<>();

        // Koji pokušaj je bio pobjednički
        if (myRoundWon && myWinAttemptNumber >= 1 && myWinAttemptNumber <= 6) {
            updates.put("stats.skocko.attempt" + myWinAttemptNumber,
                    FieldValue.increment(1));
        } else {
            updates.put("stats.skocko.failed", FieldValue.increment(1));
        }

        updates.put("stats.skocko.wins",         FieldValue.increment(iWon ? 1 : 0));
        updates.put("stats.skocko.losses",       FieldValue.increment(iWon ? 0 : 1));
        updates.put("stats.global.totalGames",   FieldValue.increment(1));
        updates.put("stats.global.wins",         FieldValue.increment(iWon ? 1 : 0));
        updates.put("stats.global.losses",       FieldValue.increment(iWon ? 0 : 1));

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .update(updates);
    }

    public void submitAttempt(List<SkockoSymbol> attemptSymbols) {
        SkockoGameState state = gameState.getValue();
        //  Ako je igra završena ili stanje nije učitano, blokiraj slanje
        if (state == null || "finished".equals(state.status)) return;

        // Provera da li uopšte imam pravo da pošaljem (da li sam ja aktivan igrač)
        int activePlayerNum = "player1".equals(myPlayerId) ? 1 : 2;
        if (state.activePlayer != activePlayerNum) return;

        List<SkockoSymbol> solutionSymbols = new ArrayList<>();
        for (String s : state.solution) {
            solutionSymbols.add(SkockoSymbol.valueOf(s));
        }

        // Ako lista sadrži null vrednosti (prazna polja), nemoj slati u bazu
        for (SkockoSymbol s : attemptSymbols) {
            if (s == null) return;
        }

        SkockoFeedback feedback = skockoHelper.evaluate(solutionSymbols, attemptSymbols);

        List<String> symbolStrings = new ArrayList<>();
        for (SkockoSymbol s : attemptSymbols) symbolStrings.add(s.name());

        FirebaseAttempt newAttempt = new FirebaseAttempt(
                activePlayerNum, symbolStrings, feedback.getRed(), feedback.getYellow());
        state.attempts.add(newAttempt);

        // Provera pogotka (4 crvena)
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
            else                      state.p2Score += points;

            if (activePlayerNum == ("player1".equals(myPlayerId) ? 1 : 2)) {
                myRoundWon        = true;
                myWinAttemptNumber = state.attempts.size();
            }

            endRoundLogic(state);
            return;
        }

        // Šansa protivnika – promašio je u svojih 10 sekundi, kraj runde!
        if (state.isOpponentChance) {
            endRoundLogic(state);
            return;
        }

        // Iskorišćeno svih 6 pokušaja regularnog igrača – aktivira se šansa protivnika
        if (state.attempts.size() == 6) {
            state.isOpponentChance = true;
            state.activePlayer = (state.rundaZapocinje == 1) ? 2 : 1;
        }

        repository.updateGameState(state);
    }

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