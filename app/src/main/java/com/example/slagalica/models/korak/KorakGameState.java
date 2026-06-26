package com.example.slagalica.models.korak;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KorakGameState {

    public int round = 1;

    public int activePlayer = 1;

    public boolean isOpponentChance = false;

    public int revealedHints = 1;

    public boolean lastHintShowing = false;

    public List<String> hints = new ArrayList<>();

    public String answer = "";

    public String revealedAnswer = "";

    public Map<String, Integer> scores = new HashMap<>();

    public String status = "active";

    public boolean showingRoundResult = false;

    public String player1Id = "";
    public String player2Id = "";

    public KorakGameState() {}
}