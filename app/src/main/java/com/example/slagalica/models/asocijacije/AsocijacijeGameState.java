package com.example.slagalica.models.asocijacije;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AsocijacijeGameState {
    public int round = 1;
    public int activePlayer = 1;        // koji igrač je na potezu (1 ili 2)
    public int rundaZapocinje = 1;      // ko počinje novu rundu (1 ili 2)
    public String status = "active";    // "active" | "finished"

    // KORAK PATTERN: ID igrača 1 koji kontroliše tok meča i prelaze
    public String player1Id = "";

    // KORAK PATTERN: Mapa u kojoj se bodovi čuvaju po ključu UID-a korisnika
    public Map<String, Integer> scores = new HashMap<>();

    // === NOVO POLJE ZA SINHRONIZACIJU TAJMERA ===
    // Čuva timestamp u milisekundama kada runda treba da istekne
    public long roundEndTime = 0;

    // Ako je true, igrač je pogodio kolonu i smije samo pogađati, ne otvarati polja
    public boolean isGuessOnlyMode = false;

    // Matrica otvorenih polja: 4 kolone x 4 reda
    public List<List<Boolean>> openedFields = new ArrayList<>();

    // Status rešenosti kolona (ključevi: "A", "B", "C", "D")
    public Map<String, Boolean> columnResolved = new HashMap<>();
    public boolean finalResolved = false;

    // --- Identično MojBroj patternu ---

    // Prikazuje ekran "Runda 1 završena" između rundi (player1 ga postavlja)
    public boolean showingRoundResult = false;

    // Ready mehanizam – oba igrača moraju biti ready pre nego što player1 inicijalizuje igru
    public Map<String, Boolean> ready = new HashMap<>();

    public AsocijacijeGameState() {
        for (int c = 0; c < 4; c++) {
            List<Boolean> col = new ArrayList<>();
            for (int r = 0; r < 4; r++) col.add(false);
            openedFields.add(col);
        }
        columnResolved.put("A", false);
        columnResolved.put("B", false);
        columnResolved.put("C", false);
        columnResolved.put("D", false);
    }
}