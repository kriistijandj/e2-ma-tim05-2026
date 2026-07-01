package com.example.slagalica.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.adapters.ChallengeResultAdapter;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ChallengeResultFragment extends Fragment {

    private String challengeId;
    private ListenerRegistration listenerRegistration;

    private TextView tvStatus;
    private LinearLayout llWaitingProgress;
    private RecyclerView rvResults;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_challenge_result, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            challengeId = getArguments().getString("CHALLENGE_ID");
        }

        tvStatus = view.findViewById(R.id.tvResultStatus);
        llWaitingProgress = view.findViewById(R.id.llWaitingProgress);
        rvResults = view.findViewById(R.id.rvResults);
        rvResults.setLayoutManager(new LinearLayoutManager(requireContext()));

        view.findViewById(R.id.btnResultHome).setOnClickListener(v ->
                Navigation.findNavController(view).navigate(R.id.nav_home));

        if (challengeId == null) {
            tvStatus.setText("Nepoznat izazov.");
            return;
        }

        listenerRegistration = FirebaseFirestore.getInstance()
                .collection("challenges").document(challengeId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null || !snapshot.exists() || !isAdded()) return;
                    render(snapshot);
                });
    }

    @SuppressWarnings("unchecked")
    private void render(DocumentSnapshot snapshot) {
        String status = snapshot.getString("status");
        List<String> participants = (List<String>) snapshot.get("participants");
        if (participants == null) participants = new ArrayList<>();

        if (!"finished".equals(status)) {
            renderWaiting(snapshot, participants);
            return;
        }

        llWaitingProgress.removeAllViews();
        llWaitingProgress.setVisibility(View.GONE);
        rvResults.setVisibility(View.VISIBLE);
        tvStatus.setText("Izazov je završen!");

        Map<String, Object> results = (Map<String, Object>) snapshot.get("results");
        if (results == null) results = new java.util.HashMap<>();

        Map<String, Object> payout = (Map<String, Object>) snapshot.get("payout");
        List<String> ranking = new ArrayList<>();
        Map<String, Object> starsDelta = new java.util.HashMap<>();
        Map<String, Object> tokensDelta = new java.util.HashMap<>();
        if (payout != null) {
            Object rankingObj = payout.get("ranking");
            if (rankingObj instanceof List) ranking = (List<String>) rankingObj;
            Object starsObj = payout.get("starsDelta");
            if (starsObj instanceof Map) starsDelta = (Map<String, Object>) starsObj;
            Object tokensObj = payout.get("tokensDelta");
            if (tokensObj instanceof Map) tokensDelta = (Map<String, Object>) tokensObj;
        }

        List<ChallengeResultAdapter.ResultRow> rows = new ArrayList<>();
        Set<String> rankedUids = new HashSet<>(ranking);

        for (int i = 0; i < ranking.size(); i++) {
            String uid = ranking.get(i);
            ChallengeResultAdapter.ResultRow row = new ChallengeResultAdapter.ResultRow();
            row.uid = uid;
            row.rank = i + 1;
            row.score = scoreOf(results, uid);
            row.starsDelta = longOf(starsDelta.get(uid));
            row.tokensDelta = longOf(tokensDelta.get(uid));
            row.dnf = false;
            rows.add(row);
        }

        // Učesnici koji nisu u rangiranju (nisu završili partiju - dnf) prikazuju se na dnu.
        for (String uid : participants) {
            if (rankedUids.contains(uid)) continue;
            ChallengeResultAdapter.ResultRow row = new ChallengeResultAdapter.ResultRow();
            row.uid = uid;
            row.rank = 0;
            row.score = scoreOf(results, uid);
            row.dnf = true;
            rows.add(row);
        }

        rvResults.setAdapter(new ChallengeResultAdapter(rows));
    }

    @SuppressWarnings("unchecked")
    private void renderWaiting(DocumentSnapshot snapshot, List<String> participants) {
        rvResults.setVisibility(View.GONE);
        llWaitingProgress.setVisibility(View.VISIBLE);
        llWaitingProgress.removeAllViews();

        String status = snapshot.getString("status");
        if ("open".equals(status) || "starting".equals(status)) {
            tvStatus.setText("Izazov još nije počeo.");
            return;
        }

        tvStatus.setText("Čeka se da ostali igrači završe partiju...");

        Map<String, Object> results = (Map<String, Object>) snapshot.get("results");
        if (results == null) results = new java.util.HashMap<>();

        for (String uid : participants) {
            TextView tv = new TextView(requireContext());
            tv.setPadding(8, 8, 8, 8);
            boolean done = results.containsKey(uid);
            tv.setText((done ? "✓ " : "⏳ ") + uid + (done ? " - završio(la)" : " - u toku"));
            llWaitingProgress.addView(tv);
        }
    }

    private int scoreOf(Map<String, Object> results, String uid) {
        Object rObj = results.get(uid);
        if (rObj instanceof Map) {
            Object s = ((Map<?, ?>) rObj).get("score");
            if (s instanceof Long) return ((Long) s).intValue();
            if (s instanceof Integer) return (Integer) s;
        }
        return 0;
    }

    private long longOf(Object value) {
        if (value instanceof Long) return (Long) value;
        if (value instanceof Integer) return (Integer) value;
        return 0;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (listenerRegistration != null) listenerRegistration.remove();
    }
}
