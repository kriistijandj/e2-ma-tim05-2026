package com.example.slagalica.models.skocko;

import java.util.List;

public class SkockoRound {

    private String playerId;

    private List<SkockoSymbol> solution;

    private List<SkockoAttempt> attempts;

    private boolean solved;

    private int solvedAttempt;

    private int score;

    public SkockoRound() {}
}