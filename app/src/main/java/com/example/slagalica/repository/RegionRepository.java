package com.example.slagalica.repository;

import com.example.slagalica.models.RegionModel;
import com.example.slagalica.region.SerbiaRegions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
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
        List<RegionModel> result = new ArrayList<>();
        for (String name : SerbiaRegions.ALL) {
            result.add(new RegionModel(name));
        }

        // Zbir zvezda i broj igrača po regionu računamo direktno iz kolekcije
        // "users" — svaki igrač nosi svoje zvezde u polju "stars".
        db.collection("users").whereEqualTo("isEnabled", true).get().addOnCompleteListener(usersTask -> {
            if (usersTask.isSuccessful() && usersTask.getResult() != null) {
                Map<String, Integer> playerCounts = new HashMap<>();
                Map<String, Long> starSums = new HashMap<>();
                for (QueryDocumentSnapshot doc : usersTask.getResult()) {
                    String region = doc.getString("region");
                    if (region != null && SerbiaRegions.isValid(region)) {
                        playerCounts.put(region, playerCounts.getOrDefault(region, 0) + 1);
                        Long stars = doc.getLong("stars");
                        starSums.put(region, starSums.getOrDefault(region, 0L)
                                + (stars != null ? stars : 0L));
                    }
                }
                for (RegionModel m : result) {
                    Integer count = playerCounts.get(m.getName());
                    m.setPlayerCount(count != null ? count : 0);
                    Long stars = starSums.get(m.getName());
                    m.setTotalStars(stars != null ? stars : 0L);
                }
            }
            assignRanks(result);
            callback.onResult(result);
        });
    }

    private void assignRanks(List<RegionModel> list) {
        Collections.sort(list, (a, b) -> Long.compare(b.getTotalStars(), a.getTotalStars()));
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setRank(i + 1);
        }
    }

    public void loadRegionDetail(String regionName, RegionDetailCallback callback) {
        RegionModel model = new RegionModel(regionName);
        final int[] pending = {2};

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

        // Broj igrača i zbir zvezda regiona sabiramo iz samih igrača.
        db.collection("users")
                .whereEqualTo("region", regionName)
                .whereEqualTo("isEnabled", true)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        model.setTotalPlayers(task.getResult().size());
                        model.setActivePlayers(task.getResult().size());
                        long totalStars = 0L;
                        for (QueryDocumentSnapshot doc : task.getResult()) {
                            Long stars = doc.getLong("stars");
                            if (stars != null) totalStars += stars;
                        }
                        model.setTotalStars(totalStars);
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
                    Double lat = doc.getDouble("lat");
                    Double lng = doc.getDouble("lng");
                    int idx = SerbiaRegions.indexOf(region);
                    if (lat != null && lng != null && isWithinSerbia(lat, lng)) {
                        locations.add(new double[]{lat, lng});
                    } else if (idx >= 0) {
                        double[] pos = SerbiaRegions.randomLatLng(idx);
                        locations.add(pos);
                        saveRandomLocation(doc.getId(), pos[0], pos[1]);
                    }
                }
            }
            callback.onResult(locations);
        });
    }

    private boolean isWithinSerbia(double lat, double lng) {
        return lat >= 41.8 && lat <= 46.2 && lng >= 18.8 && lng <= 23.1;
    }

    private void saveRandomLocation(String uid, double lat, double lng) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("lat", lat);
        updates.put("lng", lng);
        db.collection("users").document(uid).update(updates);
    }

    public void saveUserLocation(double lat, double lng, Runnable onComplete) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            if (onComplete != null) onComplete.run();
            return;
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("lat", lat);
        updates.put("lng", lng);
        db.collection("users").document(user.getUid()).update(updates)
                .addOnCompleteListener(t -> { if (onComplete != null) onComplete.run(); });
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
