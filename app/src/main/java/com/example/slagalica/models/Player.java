package com.example.slagalica.models;

public class Player {
    private String uid;
    private String email;
    private String username;
    private String region;

    public int score = 0;

    public Player() {}

    public Player(String uid, String email, String username, String region) {
        this.uid = uid;
        this.email = email;
        this.username = username;
        this.region = region;
    }

    // getteri i setteri
}