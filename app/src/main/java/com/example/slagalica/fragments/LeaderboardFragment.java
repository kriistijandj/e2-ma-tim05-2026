package com.example.slagalica.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.adapters.LeaderboardAdapter;
import com.example.slagalica.helper.DateHelper;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LeaderboardFragment extends Fragment {

    private TabLayout tabLayoutCycle;
    private TextView tvCycleRange;
    private RecyclerView rvLeaderboard;

    private FirebaseFirestore db;
    private List<Map<String, Object>> playerList = new ArrayList<>();
    private LeaderboardAdapter adapter;

    private boolean isWeeklySelected = true;

    // Handler i Tajmer za automatsko ažuriranje (Zahtev d)
    private final Handler updateHandler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;
    private static final long UPDATE_INTERVAL = 2 * 60 * 1000; // 2 minuta u milisekundama

    public LeaderboardFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_leaderboard, container, false);

        db = FirebaseFirestore.getInstance();

        tabLayoutCycle = view.findViewById(R.id.tabLayoutCycle);
        tvCycleRange = view.findViewById(R.id.tvCycleRange);
        rvLeaderboard = view.findViewById(R.id.rvLeaderboard);

        rvLeaderboard.setLayoutManager(new LinearLayoutManager(getContext()));

        // Slušalac promene tabova (Nedeljna vs Mesečna)
        tabLayoutCycle.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                isWeeklySelected = (tab.getPosition() == 0);
                updateCycleDateRange();
                loadLeaderboardData();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        // Inicijalni prikaz datuma
        updateCycleDateRange();

        // Postavljanje ponavljajućeg zadatka na svaka 2 minuta
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                loadLeaderboardData();
                updateHandler.postDelayed(this, UPDATE_INTERVAL);
            }
        };

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Pokreni automatsko osvežavanje čim ekran postane vidljiv
        updateHandler.post(updateRunnable);
    }

    @Override
    public void onPause() {
        super.onPause();
        // Zaustavi tajmer kada korisnik napusti ekran da ne trošimo resurse
        updateHandler.removeCallbacks(updateRunnable);
    }

    // Računanje i prikaz opsega datuma (Zahtev e)
    private void updateCycleDateRange() {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());

        if (isWeeklySelected) {
            // Postavi na ponedeljak trenutne nedelje
            cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
            String startDate = sdf.format(cal.getTime());

            // Pomeri do nedelje
            cal.add(Calendar.DAY_OF_WEEK, 6);
            String endDate = sdf.format(cal.getTime());

            tvCycleRange.setText("Tekući ciklus: " + startDate + " - " + endDate);
        } else {
            // Postavi na prvi dan u mesecu
            cal.set(Calendar.DAY_OF_MONTH, 1);
            String startDate = sdf.format(cal.getTime());

            // Pomeri na poslednji dan u mesecu
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
            String endDate = sdf.format(cal.getTime());

            tvCycleRange.setText("Tekući ciklus: " + startDate + " - " + endDate);
        }
    }

    // Povlačenje podataka iz Firestore-a
    private void loadLeaderboardData() {
        String targetStarsField = isWeeklySelected ? "weeklyStars" : "monthlyStars";
        String targetCycleField = isWeeklySelected ? "lastWeeklyCycle" : "lastMonthlyCycle";
        String currentCycleId = isWeeklySelected ?
                DateHelper.getCurrentWeeklyCycleId() : DateHelper.getCurrentMonthlyCycleId();

        // Query: sortiraj po zvezdama opadajuće, ali uzimamo samo igrače čije su zvezde > 0
        // i koji pripadaju TEKUĆEM ciklusu
        db.collection("users")
                .whereEqualTo(targetCycleField, currentCycleId)
                .whereGreaterThan(targetStarsField, 0)
                .orderBy(targetStarsField, Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    playerList.clear();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        playerList.add(doc.getData());
                    }
                    adapter = new LeaderboardAdapter(playerList, isWeeklySelected);
                    rvLeaderboard.setAdapter(adapter);
                })
                .addOnFailureListener(e -> {
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "Greška pri osvežavanju: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private String getCurrentWeeklyCycleId() {
        Calendar cal = Calendar.getInstance();
        int year = cal.get(Calendar.YEAR);
        int week = cal.get(Calendar.WEEK_OF_YEAR);
        return year + "_W" + week;
    }

    private String getCurrentMonthlyCycleId() {
        Calendar cal = Calendar.getInstance();
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH) + 1;
        return year + "_M" + String.format(Locale.getDefault(), "%02d", month);
    }
}