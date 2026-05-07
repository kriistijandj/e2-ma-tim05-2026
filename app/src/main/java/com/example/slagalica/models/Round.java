package com.example.slagalica.models;

public class Round {
    public Column[] columns; // 4 kolone
    public String finalSolution;
    public boolean finished = false;

    public Round(Column[] columns, String finalSolution) {
        this.columns = columns;
        this.finalSolution = finalSolution;
    }
}