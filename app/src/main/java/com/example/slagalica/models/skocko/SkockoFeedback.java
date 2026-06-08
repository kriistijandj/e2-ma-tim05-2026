package com.example.slagalica.models.skocko;

public class SkockoFeedback {

    private int red;
    private int yellow;

    public SkockoFeedback() {}

    public SkockoFeedback(int red, int yellow) {
        this.red = red;
        this.yellow = yellow;
    }

    public int getRed() {
        return red;
    }

    public int getYellow() {
        return yellow;
    }
}