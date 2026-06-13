package com.example.slagalica.models.korak_po_korak;

public class KorakPoKorakGameState {

    // 👤 igrači
    public String player1Id;
    public String player2Id;

    // 🎯 čiji je potez (BITNO)
    public String currentPlayerId;

    // 📊 poeni
    public int score1;
    public int score2;

    // 🔁 runda (1 ili 2)
    public int round;

    // 🧩 trenutna reč / pitanje
    public String correctAnswer;

    // 🔎 hintovi
    public int currentHint;

    // ⏱ stanje igre
    public boolean roundActive;

    public boolean opponentChance;

    // ⏳ timer (opciono, ali korisno)
    public long timeLeft;

    // ⚙️ default constructor (OBAVEZAN za Firebase)
    public KorakPoKorakGameState() {
    }
}