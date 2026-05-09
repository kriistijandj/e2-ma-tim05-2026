package com.example.slagalica.models;

public class Column {
    public String[] fields;      // 4 pojma
    public boolean[] opened;     // otvorena polja
    public String solution;
    public boolean solved = false;

    public Column(String[] fields, String solution) {
        this.fields = fields;
        this.solution = solution;
        this.opened = new boolean[]{false, false, false, false};
    }

    public int unopenedCount() {
        int c = 0;
        for (boolean o : opened) if (!o) c++;
        return c;
    }
}