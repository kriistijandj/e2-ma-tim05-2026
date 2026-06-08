package com.example.slagalica.models.skocko;

import java.util.List;

public class FirebaseAttempt {
    public int player;
    public List<String> symbols;
    public int red;
    public int yellow;

    public FirebaseAttempt() {}

    public FirebaseAttempt(int player, List<String> symbols, int red, int yellow) {
        this.player = player;
        this.symbols = symbols;
        this.red = red;
        this.yellow = yellow;
    }
}