package com.example.slagalica.models;

public class DailyMissionsModel {
    private boolean wonGame;
    private boolean sentChatMessage;
    private boolean playedFriendly;
    private boolean wonTournamentGame;
    private boolean claimedReward;
    private long lastResetTimestamp;

    // Firebase zahteva prazan konstruktor
    public DailyMissionsModel() {}

    public DailyMissionsModel(boolean wonGame, boolean sentChatMessage, boolean playedFriendly,
                              boolean wonTournamentGame, boolean claimedReward, long lastResetTimestamp) {
        this.wonGame = wonGame;
        this.sentChatMessage = sentChatMessage;
        this.playedFriendly = playedFriendly;
        this.wonTournamentGame = wonTournamentGame;
        this.claimedReward = claimedReward;
        this.lastResetTimestamp = lastResetTimestamp;
    }

    public boolean isWonGame() { return wonGame; }
    public void setWonGame(boolean wonGame) { this.wonGame = wonGame; }

    public boolean isSentChatMessage() { return sentChatMessage; }
    public void setSentChatMessage(boolean sentChatMessage) { this.sentChatMessage = sentChatMessage; }

    public boolean isPlayedFriendly() { return playedFriendly; }
    public void setPlayedFriendly(boolean playedFriendly) { this.playedFriendly = playedFriendly; }

    public boolean isWonTournamentGame() { return wonTournamentGame; }
    public void setWonTournamentGame(boolean wonTournamentGame) { this.wonTournamentGame = wonTournamentGame; }

    public boolean isClaimedReward() { return claimedReward; }
    public void setClaimedReward(boolean claimedReward) { this.claimedReward = claimedReward; }

    public long getLastResetTimestamp() { return lastResetTimestamp; }
    public void setLastResetTimestamp(long lastResetTimestamp) { this.lastResetTimestamp = lastResetTimestamp; }

    // Pomoćna metoda koja proverava da li su sve 4 misije ispunjene
    public boolean areAllMissionsCompleted() {
        return wonGame && sentChatMessage && playedFriendly && wonTournamentGame;
    }
}