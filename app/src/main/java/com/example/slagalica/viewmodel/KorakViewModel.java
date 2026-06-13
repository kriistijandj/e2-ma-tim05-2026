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

/**
 * ViewModel za "Korak po korak".
 *
 * Timer logika:
 *  - Svaka runda traje max 70s (7 koraka * 10s)
 *  - Svaka 10s otkriva se novi korak (revealedHints++)
 *  - Igrač može da pogodi u bilo kom trenutku
 *  - Ako ne pogodi u svojoj rundi -> isOpponentChance = true, protivnik ima 10s
 */
public class KorakViewModel extends ViewModel {

    private KorakRepository repository;
    private final KorakHelper helper = new KorakHelper();

    private final MutableLiveData<KorakGameState> gameState = new MutableLiveData<>();
    private final MutableLiveData<String> timerText = new MutableLiveData<>();

    private CountDownTimer hintTimer;  // Tiker za otkrivanje koraka (10s interval)
    private CountDownTimer opponentTimer; // Tiker za šansu protivnika (10s)
    private String myPlayerId;

    // Statistika: prati koji korak je bio aktivan kad je igrač pogodio (po rundi)
    private final int[] myHintWhenSolved = {0, 0}; // [runda1, runda2] – 0 = nije pogodio

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

    /**
     * Player1 (host) postavlja početno stanje.
     */
    public void setupInitialGameIfHost() {
        if (!"player1".equals(myPlayerId)) return;

        KorakHelper.KorakQuestion q = helper.getRandomQuestion();
        KorakGameState initial = new KorakGameState();
        initial.answer = q.answer;
        initial.hints = q.hints;
        initial.revealedHints = 1;
        repository.updateGameState(initial);
    }

    // ================================
    // KORISNIČKI DOGADJAJ – pogadjanje
    // ================================

    /**
     * Igrač pokušava da odgovori.
     * guess – tekst koji je igrač uneo
     * Vraća: true = tačno, false = netačno
     */
    public boolean submitGuess(String guess) {
        KorakGameState state = gameState.getValue();
        if (state == null || "finished".equals(state.status)) return false;
        if (!amIActive(state)) return false;

        boolean correct = state.answer.trim().equalsIgnoreCase(guess.trim());

        if (correct) {
            int points;
            if (state.isOpponentChance) {
                points = 5; // Protivnik pogodio za 5 bodova
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

            // Statistika
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

    // ================================
    // TIMER LOGIKA
    // ================================

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
            // 10s za protivnika
            if (opponentTimer == null) {
                startOpponentTimer();
            }
        } else {
            // Glavni hint timer (svakih 10s novi korak)
            if (hintTimer == null && state.revealedHints < 7) {
                startHintTimer(state);
            }
        }
    }

    /**
     * Pokreće timer koji svakih 10s otkriva novi korak.
     * Ukupno 7 koraka, kreće od revealedHints.
     */
    private void startHintTimer(KorakGameState initialState) {
        cancelHintTimer();
        // 10 sekundi do sledeceg koraka
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
            // Svi koraci otvoreni, igrač nije pogodio -> šansa protivnika
            state.isOpponentChance = true;
            state.activePlayer = (state.rundaZapocinje == 1) ? 2 : 1;
        }

        repository.updateGameState(state);

        // Nastavljamo timer samo ako još nije šansa protivnika
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

        // Protivnik nije pogodio u svojoj šansi – kraj runde
        endRoundLogic(state);
        repository.updateGameState(state);
    }

    // Igrač (domaćin runde) nije pogodio ni u jednom koraku
    // (ovo se poziva iz revealNextHint kada se otvore svi koraci)
    // -> već se postavljeno isOpponentChance = true gore

    // ================================
    // KRAJ RUNDE / KRAJ IGRE
    // ================================

    private void endRoundLogic(KorakGameState state) {
        cancelAllTimers();

        if (state.round == 1) {
            // Prelazak na rundu 2
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
            // Kraj igre
            state.status = "finished";
            int myScore  = "player1".equals(myPlayerId) ? state.p1Score : state.p2Score;
            int oppScore = "player1".equals(myPlayerId) ? state.p2Score : state.p1Score;
            saveKorakStats(myScore > oppScore);
        }
    }

    // ================================
    // STATISTIKA
    // ================================

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

    // ================================
    // POMOĆNE METODE
    // ================================

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
