package com.example.slagalica.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.slagalica.models.korak_po_korak.KorakPoKorakGameState;
import com.example.slagalica.repository.KorakPoKorakRepository;

public class KorakPoKorakViewModel extends ViewModel {

    private KorakPoKorakRepository repository;

    private final MutableLiveData<KorakPoKorakGameState> gameState =
            new MutableLiveData<>();

    private String gameId;

    public void init(String gameId) {

        if (repository != null) return;

        this.gameId = gameId;

        repository = new KorakPoKorakRepository(gameId);

        listenToGame();
    }

    // 🔁 slušanje Firebase-a
    private void listenToGame() {

        repository.listenToGameState(new KorakPoKorakRepository.GameStateCallback() {

            @Override
            public void onStateChanged(KorakPoKorakGameState state) {
                gameState.setValue(state);
            }

            @Override
            public void onError(String error) {
                // možeš dodati log
            }
        });
    }

    // 📦 LiveData za Fragment
    public LiveData<KorakPoKorakGameState> getGameState() {
        return gameState;
    }

    // 🔼 update state (piše u Firebase)
    public void updateState(KorakPoKorakGameState state) {
        repository.updateGameState(state);
    }

    // 🎯 helper metode (da ne radiš logiku u Fragmentu)

    public void setAnswer(KorakPoKorakGameState state, String answer, boolean isCorrect, int currentHint) {

        if (isCorrect) {

            int points = 20 - (currentHint * 2);

            if (state.currentPlayerId.equals(state.player1Id)) {
                state.score1 += points;
            } else {
                state.score2 += points;
            }

            state.round++;
        }

        repository.updateGameState(state);
    }

    public void switchPlayer(KorakPoKorakGameState state) {

        String temp = state.currentPlayerId;

        state.currentPlayerId =
                temp.equals(state.player1Id)
                        ? state.player2Id
                        : state.player1Id;

        repository.updateGameState(state);
    }
}
