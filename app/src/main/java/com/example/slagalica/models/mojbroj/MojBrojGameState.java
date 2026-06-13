package com.example.slagalica.models.mojbroj;

import java.util.ArrayList;
import java.util.List;


public class MojBrojGameState {


    public int round = 1;


    public int activePlayer = 1;


    public int targetNumber = 0;


    public List<Integer> availableNumbers = new ArrayList<>();


    public boolean targetRevealed = false;


    public boolean numbersRevealed = false;


    public String p1Expression = "";


    public String p2Expression = "";


    public int p1Result = -1;


    public int p2Result = -1;


    public boolean p1Submitted = false;


    public boolean p2Submitted = false;


    public int p1Score = 0;
    public int p2Score = 0;


    public String status = "active";


    public MojBrojGameState() {}
}
