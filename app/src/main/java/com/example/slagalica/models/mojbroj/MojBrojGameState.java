package com.example.slagalica.models.mojbroj;

import java.util.ArrayList;
import java.util.List;

/**
 * Stanje igre "Moj Broj" koje se čuva u Firebase Realtime Database.
 *
 * Firebase putanja: games/{gameId}/mojbroj
 *
 * Tok igre:
 *  - round 1: player1 igra svoju rundu
 *  - round 2: player2 igra svoju rundu
 *  - Nakon svake runde porede se rezultati i dodjeljuju bodovi
 */
public class MojBrojGameState {

    // Koja runda je trenutno (1 ili 2)
    public int round = 1;

    // Koji igrač je trenutno aktivan (1 = player1, 2 = player2)
    public int activePlayer = 1;

    // Traženi broj koji oba igrača pokušavaju da nađu
    public int targetNumber = 0;

    // 6 ponuđenih brojeva (generisani na početku runde)
    public List<Integer> availableNumbers = new ArrayList<>();

    // Da li je targetNumber već otkriven (igrač kliknuo Stop)
    public boolean targetRevealed = false;

    // Da li su availableNumbers već otkriveni
    public boolean numbersRevealed = false;

    // Izraz koji je player1 uneo (runda 1)
    public String p1Expression = "";

    // Izraz koji je player2 uneo (runda 2)
    public String p2Expression = "";

    // Rezultat evaluacije izraza player1 (-1 ako nije uneo ništa)
    public int p1Result = -1;

    // Rezultat evaluacije izraza player2 (-1 ako nije uneo ništa)
    public int p2Result = -1;

    // Da li je player1 predao svoju rundu
    public boolean p1Submitted = false;

    // Da li je player2 predao svoju rundu
    public boolean p2Submitted = false;

    // Ukupni bodovi
    public int p1Score = 0;
    public int p2Score = 0;

    // Status: "active" ili "finished"
    public String status = "active";

    // Prazan konstruktor obavezan za Firebase
    public MojBrojGameState() {}
}
