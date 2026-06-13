package com.example.slagalica.models.skocko;

import java.util.ArrayList;
import java.util.List;

public class SkockoGameState {
    public int round = 1;
    public int activePlayer = 1;
    public int rundaZapocinje = 1;
    public boolean isOpponentChance = false;

    // Čuvamo simbole kao Stringove (npr. ["SKOCKO", "SRCE", "..."]) radi lakšeg čuvanja u Firebase
    public List<String> solution = new ArrayList<>();

    // Lista svih pokušaja u bazi
    public List<FirebaseAttempt> attempts = new ArrayList<>();

    public int p1Score = 0;
    public int p2Score = 0;
    public String status = "active"; // active, finished

    public SkockoGameState() {} // Neophodan prazan konstruktor za Firebase
}