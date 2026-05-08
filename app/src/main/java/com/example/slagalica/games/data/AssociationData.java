package com.example.slagalica.games.data;

import com.example.slagalica.models.AssociationGame;
import com.example.slagalica.models.Column;
import com.example.slagalica.models.Player;
import com.example.slagalica.models.Round;

public class AssociationData {

    public static AssociationGame createGame() {

        Column a1 = new Column(
                new String[]{"Sneg", "Led", "Zima", "Hladno"},
                "Pol"
        );

        Column b1 = new Column(
                new String[]{"Krov", "Temelj", "Zid", "Prozor"},
                "Kuća"
        );

        Column c1 = new Column(
                new String[]{"Pas", "Mačka", "Krava", "Konj"},
                "Životinje"
        );

        Column d1 = new Column(
                new String[]{"Jabuka", "Kruška", "Banana", "Pomorandža"},
                "Voće"
        );

        Round r1 = new Round(
                new Column[]{cloneColumn(a1), cloneColumn(b1), cloneColumn(c1), cloneColumn(d1)},
                "Priroda"
        );

        Round r2 = new Round(
                new Column[]{cloneColumn(a1), cloneColumn(b1), cloneColumn(c1), cloneColumn(d1)},
                "Svet"
        );

        return new AssociationGame(new Round[]{r1, r2}, new Player("Igrač 1"), new Player("Igrač 2"));
    }
    private static Column cloneColumn(Column c) {
        return new Column(
                c.fields.clone(),
                c.solution
        );
    }
}