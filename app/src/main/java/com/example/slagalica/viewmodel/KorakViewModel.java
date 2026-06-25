package com.example.slagalica.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.slagalica.helper.KorakHelper;
import com.example.slagalica.models.korak.KorakGameState;
import com.example.slagalica.repository.KorakRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;

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

    // FIX: flag da ne upisujemo sebe više puta i ne pravimo beskonačni loop
    private boolean selfRegistered = false;

    private boolean matchFinishedRegistered = false;

    public void init(String matchId, String role) {
        this.matchId = matchId;
        this.myRole = role;
        this.myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        repository = new KorakRepository(matchId);

        repository.listenToGameState(state -> {
            if (state == null) return;

            // FIX: player2 dodaje sebe u scores tek kada hints stignu (igra je inicijalizovana)
            // i samo jednom (selfRegistered flag sprečava beskonačni loop)
            if (state.scores != null
                    && !state.scores.containsKey(myUid)
                    && state.hints != null
                    && !state.hints.isEmpty()
                    && !selfRegistered) {
                selfRegistered = true;
                state.scores.put(myUid, 0);
                repository.updateGameState(state);
                // ne pozivamo gameState.setValue ovde — Firebase ce odmah
                // vratiti azurirano stanje kroz listener
                return;
            }

            gameState.setValue(state);
            handleTimerSync(state);
        });
    }

    public LiveData<KorakGameState> getGameState() {
        return gameState;
    }

    public LiveData<String> getTimerText() {
        return timerText;
    }

    // ---------------- INIT GAME ----------------

    public void signalReadyAndInit() {
        repository.setReady(myRole, () -> {

            if (!"player1".equals(myRole)) return;

            KorakHelper.KorakQuestion q = helper.getRandomQuestion();

            KorakGameState state = new KorakGameState();
            state.answer = q.answer;
            state.hints = q.hints;
            state.revealedHints = 1;
            state.round = 1;
            state.activePlayer = 1;
            state.status = "active";
            state.scores = new HashMap<>();
            state.player1Id = myUid;
            // FIX: player1 odmah oznacava sebe kao registered
            selfRegistered = true;
            state.scores.put(myUid, 0);

            repository.updateGameState(state);
        });
    }

    // ---------------- GUESS ----------------

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

    // ---------------- TIMER ----------------

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

    // ---------------- GAME FLOW ----------------

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
            if ("player1".equals(myRole)) {
                state.showingRoundResult = true;
                repository.updateGameState(state);
            }

            final Map<String, Integer> scoresFromRoundOne = state.scores != null
                    ? new HashMap<>(state.scores)
                    : new HashMap<>();

            android.util.Log.d("KORAK_LOG", "[" + myRole + "] endRoundLogic -> Zaključani bodovi za tajmer (scoresFromRoundOne): " + scoresFromRoundOne);

            new CountDownTimer(5000, 1000) {
                public void onTick(long ms) {}

                public void onFinish() {
                    android.util.Log.d("KORAK_LOG", "[" + myRole + "] Tajmer završio. Ja sam: " + myRole);

                    if (!"player1".equals(myRole)) {
                        android.util.Log.d("KORAK_LOG", "[" + myRole + "] Ja nisam player1, preskačem inicijalizaciju runde 2.");
                        return;
                    }

                    KorakGameState currentState = gameState.getValue();
                    if (currentState == null) currentState = state;

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

                    android.util.Log.d("KORAK_LOG", "[" + myRole + "] player1 šalje u bazu za RUNDU 2 bodove: " + currentState.scores);
                    repository.updateGameState(currentState);
                }
            }.start();

        } else {
            android.util.Log.d("KORAK_LOG", "[" + myRole + "] Runda 2 završena. Postavljam status na finished.");
            state.status = "finished";
            repository.updateGameState(state);

            //finishMatch(state);
        }
    }

    // ---------------- FINISH MATCH ----------------

    private void finishMatch(KorakGameState state) {
        // Ako je ovaj klijent već jednom procesirao kraj, preskoči dupliranje
        if (matchFinishedRegistered) return;
        matchFinishedRegistered = true;

        android.util.Log.d("KORAK_LOG", "[" + myRole + "] finishMatch -> Ulaz sa bodovima iz objekta: " + state.scores);
        int score = (state.scores != null && state.scores.containsKey(myUid) && state.scores.get(myUid) != null)
                ? state.scores.get(myUid)
                : 0;

        android.util.Log.d("KORAK_LOG", "[" + myRole + "] finishMatch -> Moj UID: " + myUid + " | Bodovi koje šaljem u /matches: " + score);

        // 1. Svako bezbedno upisuje svoj lični skor
        FirebaseDatabase.getInstance()
                .getReference("matches")
                .child(matchId)
                .child("scores")
                .child(myUid)
                .setValue(score);

        // 2. STRIKTNA KONTROLA: Samo player1 ima pravo da uveća broj trenutne igre u meču
        if ("player1".equals(myRole)) {
            android.util.Log.d("KORAK_LOG", "[" + myRole + "] Ja sam player1, uvećavam currentGame za +1");
            FirebaseDatabase.getInstance()
                    .getReference("matches")
                    .child(matchId)
                    .child("currentGame")
                    .setValue(ServerValue.increment(1));
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
}