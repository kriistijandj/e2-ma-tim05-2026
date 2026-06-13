package com.example.slagalica.models.korak;

import java.util.ArrayList;
import java.util.List;

/**
 * Stanje igre "Korak po korak" koje se čuva u Firebase Realtime Database.
 *
 * Firebase putanja: games/{gameId}/korak
 *
 * Tok igre:
 *  - round 1: player1 igra, player2 gleda
 *  - Ako player1 ne pogodi -> isOpponentChance = true, player2 ima 10s
 *  - round 2: player2 igra, player1 gleda
 *  - Ako player2 ne pogodi -> isOpponentChance = true, player1 ima 10s
 */
public class KorakGameState {

    // Koja runda (1 ili 2)
    public int round = 1;

    // Ko igra aktivno (1 = player1, 2 = player2)
    public int activePlayer = 1;

    // Ko je "domaćin" (zapoceo) tu rundu
    public int rundaZapocinje = 1;

    // Da li je protivnik na šansi (10s da pogodi)
    public boolean isOpponentChance = false;

    // Koliko je koraka već otkriveno (0-7), korak se otkriva svake 10s
    public int revealedHints = 1; // Uvek kreće sa 1 otvorenim

    // Lista od 7 pojmova (koraci) koje se otkrivaju jedan po jedan
    // Čuvaju se kao stringovi, npr. ["Hint7", "Hint6", ..., "Hint1"]
    // Index 0 = najtezi, index 6 = najlaksi
    public List<String> hints = new ArrayList<>();

    // Tačan odgovor (pojam koji igrač treba da pogodi)
    public String answer = "";

    // Da li je player1 pogodio
    public boolean p1Solved = false;

    // Da li je player2 pogodio
    public boolean p2Solved = false;

    // Koji korak je bio kada je player1 pogodio (1-7), 0 = nije pogodio
    public int p1SolvedOnHint = 0;

    // Koji korak je bio kada je player2 pogodio (1-7), 0 = nije pogodio
    public int p2SolvedOnHint = 0;

    // Ukupni bodovi
    public int p1Score = 0;
    public int p2Score = 0;

    // Status: "active" ili "finished"
    public String status = "active";

    // Prazan konstruktor obavezan za Firebase
    public KorakGameState() {}
}
