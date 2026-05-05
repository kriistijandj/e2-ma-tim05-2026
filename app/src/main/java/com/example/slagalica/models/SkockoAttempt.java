package com.example.slagalica.models;

import java.util.ArrayList;
import java.util.List;

public class SkockoAttempt {
    private List<SkockoSymbol> values = new ArrayList<>();

    public SkockoAttempt() {
        for (int i = 0; i < 4; i++) {
            values.add(null);
        }
    }

    public List<SkockoSymbol> getValues() {
        return values;
    }

    public void setValue(int index, SkockoSymbol symbol) {
        values.set(index, symbol);
    }
}