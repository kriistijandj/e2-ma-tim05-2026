package com.example.slagalica.repository;

import com.example.slagalica.models.RegionModel;
import com.example.slagalica.region.SerbiaRegions;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RegionRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface RegionListCallback {
        void onResult(List<RegionModel> regions);
    }

    public interface RegionDetailCallback {
        void onResult(RegionModel region);
    }

    public interface UserLocationCallback {
        void onResult(List<double[]> locations);
    }

    public String getCurrentCycleId() {
        Calendar cal = Calendar.getInstance();
        return String.format(Locale.US, "monthly_%04d_%02d",
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1);
    }

    public String getPreviousCycleId() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, -1);
        return String.format(Locale.US, "monthly_%04d_%02d",
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1);
    }

    public void loadRegionList(RegionListCallback callback) {
        String cycleId = getCurrentCycleId();
        List<RegionModel> result = new ArrayList<>();
        for (String name : SerbiaRegions.ALL) {
            result.add(new RegionModel(name));
        }

        final int[] pending = {SerbiaRegions.ALL.size()};

        for (int i = 0; i < SerbiaRegions.ALL.size(); i++) {
            final int idx = i;
            String regionName = SerbiaRegions.ALL.get(i);
            db.collection("regionStats")
                    .document(regionName)
                    .collection("cycles")
                    .document(cycleId)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                            Long stars = task.getResult().getLong("totalStars");
                            Long count = task.getResult().getLong("playerCount");
                            result.get(idx).setTotalStars(stars != null ? stars : 0);
                            result.get(idx).setPlayerCount(count != null ? count.intValue() : 0);
                        }
                        pending[0]--;
                        if (pending[0] == 0) {
                            assignRanks(result);
                            callback.onResult(result);
                        }
                    });
        }
    }

    private void assignRanks(List<RegionModel> list) {
        List<RegionModel> sorted = new ArrayList<>(list);
        Collections.sort(sorted, (a, b) -> Long.compare(b.getTotalStars(), a.getTotalStars()));
        for (int i = 0; i < sorted.size(); i++) {
            sorted.get(i).setRank(i + 1);
        }
    }

    public void loadRegionDetail(String regionName, RegionDetailCallback callback) {
        RegionModel model = new RegionModel(regionName);
        final int[] pending = {3};

        db.collection("regionStats")
                .document(regionName)
                .collection("allTimeStats")
                .document("medals")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                        Long gold = task.getResult().getLong("gold");
                        Long silver = task.getResult().getLong("silver");
                        Long bronze = task.getResult().getLong("bronze");
                        model.setGold(gold != null ? gold.intValue() : 0);
                        model.setSilver(silver != null ? silver.intValue() : 0);
                        model.setBronze(bronze != null ? bronze.intValue() : 0);
                    }
                    pending[0]--;
                    if (pending[0] == 0) callback.onResult(model);
                });

        db.collection("users")
                .whereEqualTo("region", regionName)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        model.setTotalPlayers(task.getResult().size());
                        model.setActivePlayers(task.getResult().size());
                    }
                    pending[0]--;
                    if (pending[0] == 0) callback.onResult(model);
                });

        String cycleId = getCurrentCycleId();
        db.collection("regionStats")
                .document(regionName)
                .collection("cycles")
                .document(cycleId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                        Long stars = task.getResult().getLong("totalStars");
                        model.setTotalStars(stars != null ? stars : 0);
                    }
                    pending[0]--;
                    if (pending[0] == 0) callback.onResult(model);
                });
    }

    public void loadUserLocations(UserLocationCallback callback) {
        db.collection("users").get().addOnCompleteListener(task -> {
            List<double[]> locations = new ArrayList<>();
            if (task.isSuccessful() && task.getResult() != null) {
                for (QueryDocumentSnapshot doc : task.getResult()) {
                    String region = doc.getString("region");
                    if (region == null || !SerbiaRegions.isValid(region)) continue;
                    int idx = SerbiaRegions.indexOf(region);
                    if (idx < 0) continue;
                    double[] latLng = SerbiaRegions.randomLatLng(idx);
                    locations.add(latLng);
                }
            }
            callback.onResult(locations);
        });
    }

    public void incrementRegionStars(String regionName, long amount) {
        if (regionName == null || !SerbiaRegions.isValid(regionName)) return;
        String cycleId = getCurrentCycleId();
        Map<String, Object> updates = new HashMap<>();
        updates.put("totalStars", FieldValue.increment(amount));
        updates.put("playerCount", FieldValue.increment(1));
        db.collection("regionStats")
                .document(regionName)
                .collection("cycles")
                .document(cycleId)
                .set(updates, com.google.firebase.firestore.SetOptions.merge());
    }

    public void getPreviousCycleRank(String regionName, RegionDetailCallback callback) {
        String prevCycleId = getPreviousCycleId();
        final RegionModel model = new RegionModel(regionName);

        final int[] pending = {SerbiaRegions.ALL.size()};
        final long[] starCounts = new long[SerbiaRegions.ALL.size()];

        for (int i = 0; i < SerbiaRegions.ALL.size(); i++) {
            final int idx = i;
            String name = SerbiaRegions.ALL.get(i);
            db.collection("regionStats")
                    .document(name)
                    .collection("cycles")
                    .document(prevCycleId)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                            Long stars = task.getResult().getLong("totalStars");
                            starCounts[idx] = stars != null ? stars : 0;
                        }
                        pending[0]--;
                        if (pending[0] == 0) {
                            int myIdx = SerbiaRegions.indexOf(regionName);
                            if (myIdx < 0) { callback.onResult(model); return; }
                            long mine = starCounts[myIdx];
                            int rank = 1;
                            for (long s : starCounts) {
                                if (s > mine) rank++;
                            }
                            model.setRank(rank);
                            callback.onResult(model);
                        }
                    });
        }
    }
}
