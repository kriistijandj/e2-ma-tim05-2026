package com.example.slagalica.services;

import android.app.AlertDialog;
import android.content.Context;

import androidx.fragment.app.FragmentManager;

import com.example.slagalica.activities.HomeActivity;
import com.example.slagalica.helper.NotificationHelper;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class LeagueManager {

    public static final String[] NAMES = {
            "Nulta", "Bronzana", "Srebrna", "Zlatna", "Platinasta", "Dijamantska"
    };

    public static final String[] ICONS = {
            "⚫", "🥉", "🥈", "🥇", "💎", "🌟"
    };

    // Stars required to enter each league (0 = Nulta, 1 = Bronzana 100*, 2 = Srebrna 200* ...)
    public static final long[] THRESHOLDS = {0, 100, 200, 400, 800, 1600};

    public static int getLeagueIndex(long stars) {
        for (int i = THRESHOLDS.length - 1; i >= 0; i--) {
            if (stars >= THRESHOLDS[i]) return i;
        }
        return 0;
    }

    public static String getDisplayName(int leagueIndex) {
        return ICONS[leagueIndex] + " " + NAMES[leagueIndex];
    }

    /** Bonus tokens per day granted by the league (liga N → N bonus tokens). */
    public static int getBonusTokens(int leagueIndex) {
        return leagueIndex;
    }

    /**
     * Reads current stars from Firestore, recalculates the league, and persists the change.
     * Shows a dialog if fm != null (user is in-app), otherwise shows a system notification.
     */
    public static void checkAndUpdateLeague(String uid, Context context, FragmentManager fm) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;

                    long stars = doc.getLong("stars") != null ? doc.getLong("stars") : 0;
                    int currentLeague = doc.getLong("league") != null
                            ? doc.getLong("league").intValue() : 0;
                    int newLeague = getLeagueIndex(stars);

                    if (newLeague == currentLeague) return;

                    db.collection("users").document(uid).update("league", newLeague);
                    saveLeagueNotification(uid, currentLeague, newLeague);

                    if (context == null) return;
                    if (fm != null) {
                        showLeagueDialog(context, currentLeague, newLeague);
                    } else {
                        showLeagueNotification(context, currentLeague, newLeague);
                    }
                });
    }

    /**
     * Monthly cycle penalty: reduce stars by 30%, then re-check league.
     * Call this at the end of a monthly cycle for players who didn't place.
     */
    public static void applyMonthlyPenalty(String uid, Context context, FragmentManager fm) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    long stars = doc.getLong("stars") != null ? doc.getLong("stars") : 0;
                    if (stars <= 0) return;
                    long reduced = (long) (stars * 0.7);
                    db.collection("users").document(uid)
                            .update("stars", reduced)
                            .addOnSuccessListener(v -> checkAndUpdateLeague(uid, context, fm));
                });
    }

    private static void showLeagueDialog(Context context, int oldLeague, int newLeague) {
        boolean promoted = newLeague > oldLeague;
        String title   = promoted ? "Napredovanje u ligi! 🎉" : "Pad u ligi";
        String message = promoted
                ? "Čestitamo! Prešli ste u " + getDisplayName(newLeague) + " ligu!"
                : "Pali ste na " + getDisplayName(newLeague) + " ligu.";
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private static void showLeagueNotification(Context context, int oldLeague, int newLeague) {
        boolean promoted = newLeague > oldLeague;
        String title   = promoted ? "Napredovanje u ligi! 🎉" : "Pad u ligi";
        String message = promoted
                ? "Prešli ste u " + getDisplayName(newLeague) + " ligu!"
                : "Pali ste na " + getDisplayName(newLeague) + " ligu.";
        NotificationHelper.showNotification(context, HomeActivity.REWARD_CHANNEL_ID,
                5000 + newLeague, title, message);
    }

    private static void saveLeagueNotification(String uid, int oldLeague, int newLeague) {
        boolean promoted = newLeague > oldLeague;
        String title   = promoted ? "Napredovanje u ligi!" : "Pad u ligi";
        String message = promoted
                ? "Prešli ste u " + getDisplayName(newLeague) + " ligu!"
                : "Pali ste na " + getDisplayName(newLeague) + " ligu.";

        Map<String, Object> notif = new HashMap<>();
        notif.put("title", title);
        notif.put("message", message);
        notif.put("timestamp", System.currentTimeMillis());
        notif.put("isRead", false);
        notif.put("type", "REWARD");

        FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("notifications")
                .add(notif);
    }
}
