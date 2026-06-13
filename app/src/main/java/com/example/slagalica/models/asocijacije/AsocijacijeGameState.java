package com.example.slagalica.models.asocijacije;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AsocijacijeGameState {
    public int round = 1;
    public int activePlayer = 1;
    public int rundaZapocinje = 1;
    public String status = "active"; // active, finished

    public int p1Score = 0;
    public int p2Score = 0;

    // Specifično stanje za potez igrača
    // Ako je true, igrač je pogodio kolonu i ima pravo samo da pogađa dalje, ne da otvara polja
    public boolean isGuessOnlyMode = false;

    // Matrica otvorenih polja: 4 kolone (0=A, 1=B, 2=C, 3=D) x 4 reda (0=1, 1=2, 2=3, 3=4)
    public List<List<Boolean>> openedFields = new ArrayList<>();

    // Status rešenosti kolona (ključevi: "A", "B", "C", "D")
    public Map<String, Boolean> columnResolved = new HashMap<>();
    public boolean finalResolved = false;

    public AsocijacijeGameState() {
        // Inicijalizacija matrice polja (sva zatvorena na početku)
        for (int c = 0; c < 4; c++) {
            List<Boolean> col = new ArrayList<>();
            for (int r = 0; r < 4; r++) {
                col.add(false);
            }
            openedFields.add(col);
        }
        // Inicijalizacija kolona
        columnResolved.put("A", false);
        columnResolved.put("B", false);
        columnResolved.put("C", false);
        columnResolved.put("D", false);
    }
}