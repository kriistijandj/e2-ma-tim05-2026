package com.example.slagalica.models;

public class RegionModel {
    private String name;
    private long totalStars;
    private int playerCount;
    private int rank;
    private int gold;
    private int silver;
    private int bronze;
    private int activePlayers;
    private int totalPlayers;

    public RegionModel() {}

    public RegionModel(String name) {
        this.name = name;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public long getTotalStars() { return totalStars; }
    public void setTotalStars(long totalStars) { this.totalStars = totalStars; }
    public int getPlayerCount() { return playerCount; }
    public void setPlayerCount(int playerCount) { this.playerCount = playerCount; }
    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }
    public int getGold() { return gold; }
    public void setGold(int gold) { this.gold = gold; }
    public int getSilver() { return silver; }
    public void setSilver(int silver) { this.silver = silver; }
    public int getBronze() { return bronze; }
    public void setBronze(int bronze) { this.bronze = bronze; }
    public int getActivePlayers() { return activePlayers; }
    public void setActivePlayers(int activePlayers) { this.activePlayers = activePlayers; }
    public int getTotalPlayers() { return totalPlayers; }
    public void setTotalPlayers(int totalPlayers) { this.totalPlayers = totalPlayers; }
}
