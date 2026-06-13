package com.example.slagalica.models.korak;

import java.util.ArrayList;
import java.util.List;


public class KorakGameState {


    public int round = 1;


    public int activePlayer = 1;


    public int rundaZapocinje = 1;


    public boolean isOpponentChance = false;


    public int revealedHints = 1;


    public List<String> hints = new ArrayList<>();


    public String answer = "";


    public boolean p1Solved = false;


    public boolean p2Solved = false;


    public int p1SolvedOnHint = 0;


    public int p2SolvedOnHint = 0;


    public int p1Score = 0;
    public int p2Score = 0;


    public String status = "active";


    public KorakGameState() {}
}
