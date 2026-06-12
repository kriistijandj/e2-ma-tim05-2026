package com.example.slagalica.helper;

import com.example.slagalica.models.skocko.SkockoFeedback;
import com.example.slagalica.models.skocko.SkockoSymbol;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SkockoHelper {

    public List<SkockoSymbol> generateSolution(){

        List<SkockoSymbol> nesto = new ArrayList<>();
        Random random = new Random();

        for(int i =0;i<4;i++)
        {
            int randomNum2 = random.nextInt(SkockoSymbol.values().length);
            nesto.add(SkockoSymbol.values()[randomNum2]);

        }
        return  nesto;
    }

    public SkockoFeedback evaluate(List<SkockoSymbol> solution, List<SkockoSymbol> attempt) {
        int red = 0;
        int yellow = 0;

        // Nizovi koji nam govore da li smo taj element već obradili/iskoristili
        boolean[] solutionUsed = new boolean[4];
        boolean[] attemptUsed = new boolean[4];

        // 1. Prolaz: Tražimo tačne pogotke (Crveni kružići)
        for (int i = 0; i < 4; i++) {
            if (attempt.get(i) == solution.get(i)) {
                red++;
                solutionUsed[i] = true;
                attemptUsed[i] = true;
            }
        }

        // 2. Prolaz: Tražimo simbole koji postoje u rešenju ali na drugom mestu (Žuti kružići)
        for (int i = 0; i < 4; i++) {
            if (attemptUsed[i]) continue; // Preskačemo već iskorišćene iz prvog prolaza

            for (int j = 0; j < 4; j++) {
                if (!solutionUsed[j] && attempt.get(i) == solution.get(j)) {
                    yellow++;
                    solutionUsed[j] = true; // Iskoristili smo ovaj simbol iz rešenja
                    break;
                }
            }
        }

        return new SkockoFeedback(red, yellow);
    }
}
