package com.example.slagalica.models;

import java.util.Map;

public class Match {
    public String matchId;
    public String player1Id;
    public String player2Id;
    public String status; // "waiting", "in_progress", "finished"
    public int currentGame; // 0-5
    public Map<String, Integer> scores; // player1 -> bodovi, player2 -> bodovi
    public long createdAt;
    public boolean isFriendly;

    public Match() {}
}