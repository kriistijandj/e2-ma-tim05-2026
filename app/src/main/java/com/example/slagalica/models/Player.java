package com.example.slagalica.models;

public class Player {

    // ===== IDENTITET =====
    private String uid;
    private String email;
    private String username;
    private String region;
    private boolean isEnabled;

    // ===== EKONOMIJA =====
    private int tokens;   // 1 token = 1 partija
    private int stars;    // ukupne zvezde (score sistema)

    // ===== STATUS =====
    private boolean online;
    private boolean inMatch;

    public int score = 0;

    // ===== DAILY REWARD =====
    private long lastTokenClaimTimestamp;

    public Player() {}

    public Player(String uid, String email, String username, String region) {
        this.uid = uid;
        this.email = email;
        this.username = username;
        this.region = region;

        this.tokens = 5; // registracija daje 5 tokena
        this.stars = 0;

        this.online = false;
        this.inMatch = false;

        this.lastTokenClaimTimestamp = System.currentTimeMillis();
    }

    // getters / setters
}