// SkockoGameState.java
package com.example.slagalica.models.skocko;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SkockoGameState {
    public int round = 1;
    public int activePlayer = 1;
    public int rundaZapocinje = 1;
    public boolean isOpponentChance = false;
    public String status = "active"; // active, finished

    public String player1Id = "";

    // UID → bodovi (kao u Asocijacijama)
    public Map<String, Integer> scores = new HashMap<>();

    // Timestamp kada runda ističe (serversko vreme)
    public long roundEndTime = 0;

    // Prikazuje ekran između rundi
    public boolean showingRoundResult = false;

    // Ready mehanizam
    public Map<String, Boolean> ready = new HashMap<>();

    public List<String> solution = new ArrayList<>();
    public List<FirebaseAttempt> attempts = new ArrayList<>();

    public SkockoGameState() {}
}