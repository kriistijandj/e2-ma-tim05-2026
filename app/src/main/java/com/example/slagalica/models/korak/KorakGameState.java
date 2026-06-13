package com.example.slagalica.models.korak;

import java.util.ArrayList;
import java.util.List;

public class KorakGameState {

    // Trenutna runda (1 ili 2)
    public int round = 1;

    // Ko trenutno pogađa (1 = player1, 2 = player2)
    public int activePlayer = 1;

    // Ko je započeo ovu rundu (za logiku isOpponentChance)
    public int rundaZapocinje = 1;

    // Da li je protivnik na svojoj šansi od 10s
    public boolean isOpponentChance = false;

    // Koliko koraka je otkriveno (1–7)
    public int revealedHints = 1;

    // Da li je aktivni igrač već video svih 7 koraka i čeka poslednji tajmer
    // (true = 7. korak otkriven, tajmer od 10s teče, još NIJE isOpponentChance)
    public boolean lastHintShowing = false;

    // Lista nagoveštaja (index 0 = najteži, index 6 = najlakši)
    public List<String> hints = new ArrayList<>();


    public String answer = "";


    public String revealedAnswer = "";


    public boolean p1Solved = false;
    public boolean p2Solved = false;


    public int p1SolvedOnHint = 0;
    public int p2SolvedOnHint = 0;


    public int p1Score = 0;
    public int p2Score = 0;


    public String status = "active";

    public boolean showingRoundResult = false;

    public KorakGameState() {}
}
