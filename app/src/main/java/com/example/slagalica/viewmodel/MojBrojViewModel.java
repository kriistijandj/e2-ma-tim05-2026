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

    // Istorija bodova iz prethodnih igara u meču
    private int matchStartingScoreP1 = 0;
    private int matchStartingScoreP2 = 0;
    private boolean isMatchScoreLoaded = false;

    private static final int PHASE_NONE    = 0;
    private static final int PHASE_TARGET  = 1;
    private static final int PHASE_NUMBERS = 2;
    private static final int PHASE_PLAY    = 3;

    private int currentTimerPhase = PHASE_NONE;

    // FIX: pratimo da li smo već pokrenuli tajmer za prelaz na rundu 2,
    // kako bismo sprečili višestruko pokretanje zbog Firebase eventova
    private boolean round2TransitionScheduled = false;

    // FIX: pratimo da li smo već završili igru da ne bismo duplirali upis
    private boolean gameFinished = false;

    // FIX: pratimo da li smo vec obradili kraj svake runde (sprecava duplo bodovanje
    // kada advanceIfNeeded bude pozvan i lokalno i kroz Firebase event)
    private boolean round1Processed = false;
    private boolean round2Processed = false;

    private int myRoundsPlayed = 0;
    private int myRoundsSolved = 0;

    // -------------------------------------------------------------------------
    // INIT
    // -------------------------------------------------------------------------

    public void init(String matchId, String playerId) {
        this.matchId = matchId;
        this.myPlayerId = playerId;
        this.repository = new MojBrojRepository(matchId);

        // Učitaj trenutne akumulirane bodove iz celog meča pre početka ove igre
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
                        isMatchScoreLoaded = true; // nastavi i bez bodova
                    }
                });

        repository.listenToGameState(state -> {
            gameState.setValue(state);
            handleTimerSync(state);
        });
    }

    public void signalReadyAndInit() {
        repository.setReady(myPlayerId, () -> {
            if ("player1".equals(myPlayerId)) {
                MojBrojGameState initial = new MojBrojGameState();
                initial.targetNumber      = helper.generateTargetNumber();
                initial.availableNumbers  = helper.generateAvailableNumbers();
                initial.stopPlayer        = 1;
                repository.updateGameState(initial);
            }
        });
    }

    // -------------------------------------------------------------------------
    // GETTERI
    // -------------------------------------------------------------------------

    public LiveData<MojBrojGameState> getGameState() { return gameState; }
    public LiveData<String>           getTimerText()  { return timerText; }
    public String                     getMyPlayerId() { return myPlayerId; }
    public int getMatchStartingScoreP1()              { return matchStartingScoreP1; }
    public int getMatchStartingScoreP2()              { return matchStartingScoreP2; }

    // -------------------------------------------------------------------------
    // STOP DUGME
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // PREDAJA IZRAZA
    // -------------------------------------------------------------------------

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

        stopTimer();
        advanceIfNeeded(state);
    }

    // -------------------------------------------------------------------------
    // TAJMER SINHRONIZACIJA
    // -------------------------------------------------------------------------

    private void handleTimerSync(MojBrojGameState state) {
        if (state == null) return;

        // Dok se prikazuje rezultat runde 1 — zaustavi tajmer i čekaj
        if (state.showingRoundResult) {
            stopTimer();
            timerText.setValue("Prelazak na rundu 2...");
            return;
        }

        if ("finished".equals(state.status)) {
            stopTimer();
            timerText.setValue("Igra završena");
            return;
        }

        // FIX: Kada Firebase donese stanje runde 2, resetuj transition flag
        if (state.round == 2) {
            round2TransitionScheduled = false;
        }

        // FIX: Ovo je kljucni fix — kada Firebase event donese stanje u kome su
        // OBA igraca predala (npr. player1 je predao prvi i cekao, a sad je
        // stigao event da je player2 predao), treba pozvati advanceIfNeeded.
        // Bez ovoga, player1 nikad ne ulazi u advanceIfNeeded kada protivnik
        // preda posle njega, jer se advanceIfNeeded poziva samo lokalno pri predaji.
        if (state.numbersRevealed && state.p1Submitted && state.p2Submitted
                && !state.showingRoundResult && !"finished".equals(state.status)) {
            advanceIfNeeded(state);
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
                stopTimer();
                timerText.setValue("Predato – čeka se...");
            }
        }
    }

    private void startTimerPhase(int phase, int seconds) {
        // Ne restartuj isti phase ako je već aktivan
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
                    advanceIfNeeded(state);
                }
                break;
        }
    }

    // -------------------------------------------------------------------------
    // TOK IGRE — GLAVNI FIX
    // -------------------------------------------------------------------------

    private void advanceIfNeeded(MojBrojGameState state) {
        boolean bothSubmitted = state.p1Submitted && state.p2Submitted;

        // Jedan igrač je predao, drugi još nije — samo upiši u bazu i čekaj
        if (!bothSubmitted) {
            repository.updateGameState(state);
            return;
        }

        // Oba igrača su predala — izračunaj bodove za ovu rundu
        // FIX: Guard protiv dupliranog poziva (lokalni + Firebase event)
        if (state.round == 1 && round1Processed) return;
        if (state.round == 2 && round2Processed) return;
        if (state.round == 1) round1Processed = true;
        if (state.round == 2) round2Processed = true;

        awardRoundPoints(state);

        if (state.round == 1) {
            // Oba igrača upisuju state sa bodovima u bazu.
            // Time player1 dobija Firebase event sa p2Submitted=true (ako je player2 predao drugi),
            // i može da pokrene tajmer za prelaz. player2 analogno dobija event od player1.
            // Koordinaciju (showingRoundResult + goToRound2) radi isključivo player1.
            if ("player1".equals(myPlayerId)) {
                // Spreči višestruko pokretanje tajmera za prelaz
                if (round2TransitionScheduled) return;
                round2TransitionScheduled = true;

                // player1 upisuje state sa showingRoundResult=true —
                // ovaj jedan upis pokriva i bodove i signal za prikaz ekrana između rundi
                state.showingRoundResult = true;
                repository.updateGameState(state);

                new CountDownTimer(3000, 1000) {
                    @Override public void onTick(long ms) {
                        timerText.setValue("Sledeća runda za: " + (ms / 1000) + "s");
                    }
                    @Override public void onFinish() {
                        MojBrojGameState fresh = gameState.getValue();
                        if (fresh == null) {
                            Log.e(TAG, "fresh state je null pri prelasku na rundu 2!");
                            return;
                        }
                        int scoreP1 = fresh.p1Score;
                        int scoreP2 = fresh.p2Score;

                        goToRound2(fresh);

                        fresh.p1Score = scoreP1;
                        fresh.p2Score = scoreP2;

                        Log.d(TAG, "[player1] Prelazak na rundu 2. Bodovi P1=" + scoreP1 + " P2=" + scoreP2);
                        repository.updateGameState(fresh);
                    }
                }.start();
            } else {
                // player2 upisuje state sa oba submita (bez showingRoundResult) —
                // player1 ce dobiti ovaj Firebase event i pokrenuti prelaz na rundu 2
                repository.updateGameState(state);
            }
            // player2 čeka Firebase event od player1 koji će postaviti showingRoundResult=true

        } else {
            // Runda 2 je završena — kraj igre
            // FIX: Spreči dvostruki poziv finishGame
            if (gameFinished) return;

            if (isMatchScoreLoaded) {
                gameFinished = true;
                finishGame(state);
                repository.updateGameState(state);
            } else {
                // Retko — mreža kasni sa učitavanjem početnih bodova; pokušaj ponovo
                Log.w(TAG, "isMatchScoreLoaded je false, čekam 1s pa pokušavam ponovo...");
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .postDelayed(() -> advanceIfNeeded(state), 1000);
            }
        }
    }

    private void awardRoundPoints(MojBrojGameState state) {
        int target = state.targetNumber;
        int r1     = state.p1Result;
        int r2     = state.p2Result;

        boolean p1Hit = (r1 == target);
        boolean p2Hit = (r2 == target);

        if (p1Hit) state.p1Score += 10;
        if (p2Hit) state.p2Score += 10;

        if (!p1Hit && !p2Hit) {
            int d1 = helper.distanceFromTarget(r1, target);
            int d2 = helper.distanceFromTarget(r2, target);

            if      (d1 == Integer.MAX_VALUE && d2 == Integer.MAX_VALUE) { /* niko nije uneo */ }
            else if (d1 == Integer.MAX_VALUE)  { state.p2Score += 5; }
            else if (d2 == Integer.MAX_VALUE)  { state.p1Score += 5; }
            else if (d1 < d2)                  { state.p1Score += 5; }
            else if (d2 < d1)                  { state.p2Score += 5; }
            else {
                // Isti rezultat — bodove dobija onaj čija je runda bila (stopPlayer)
                if (state.stopPlayer == 1) state.p1Score += 5;
                else                       state.p2Score += 5;
            }
        }
    }

    private void goToRound2(MojBrojGameState state) {
        // FIX: Resetuj guard za rundu 2 kako bi advanceIfNeeded mogao da se pozove
        round2Processed = false;
        state.showingRoundResult = false;
        state.round              = 2;
        state.stopPlayer         = 2;  // u rundi 2 player2 stopa
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
        // p1Score i p2Score se namerno NE resetuju — čuvaju bodove iz runde 1
    }

    private void finishGame(MojBrojGameState state) {
        state.status = "finished";

        saveMojBrojStats(state);

        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        if (uid != null) {
            // Ukupan skor = bodovi iz prethodnih igara u meču + bodovi iz ove igre
            int myTotalScore = "player1".equals(myPlayerId)
                    ? (matchStartingScoreP1 + state.p1Score)
                    : (matchStartingScoreP2 + state.p2Score);

            Log.d(TAG, "[" + myPlayerId + "] Završavam igru. Upisujem ukupni skor: " + myTotalScore);

            // Svaki klijent upisuje samo svoj skor — nema race conditiona
            FirebaseDatabase.getInstance()
                    .getReference("matches")
                    .child(matchId)
                    .child("scores")
                    .child(uid)
                    .setValue(myTotalScore);
        }

        // Samo player1 uvećava currentGame brojač kako bi GameActivity prešao na sledeću igru
        if ("player1".equals(myPlayerId)) {
            Log.d(TAG, "[player1] Uvećavam currentGame za +1");
            FirebaseDatabase.getInstance()
                    .getReference("matches")
                    .child(matchId)
                    .child("currentGame")
                    .setValue(ServerValue.increment(1));
        }
    }

    // -------------------------------------------------------------------------
    // STATISTIKA
    // -------------------------------------------------------------------------

    // FIX: Primamo state kao parametar umesto da čitamo iz gameState.getValue()
    // jer se state.status = "finished" postavlja tek u finishGame, a LiveData
    // možda još nije ažurirana u tom trenutku
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

    // -------------------------------------------------------------------------
    // HELPER
    // -------------------------------------------------------------------------

    private boolean amIStopPlayer(MojBrojGameState state) {
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