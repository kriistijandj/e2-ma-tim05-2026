package com.example.slagalica.models.skocko;

import com.example.slagalica.R;

public enum SkockoSymbol {
    SKOCKO(R.drawable.ic_skocko),
    KVADRAT(R.drawable.ic_kvadrat),
    KRUG(R.drawable.ic_krug),
    SRCE(R.drawable.ic_srce),
    TROUGAO(R.drawable.ic_trougao),
    ZVEZDA(R.drawable.ic_zvezda);

    private final int drawableId;

    SkockoSymbol(int drawableId) {
        this.drawableId = drawableId;
    }

    public int getDrawableId() {
        return drawableId;
    }

    // Pomoćna metoda da dobijemo enum na osnovu drawable resursa
    public static SkockoSymbol fromDrawableId(int drawableId) {
        for (SkockoSymbol symbol : values()) {
            if (symbol.getDrawableId() == drawableId) {
                return symbol;
            }
        }
        return null;
    }
}