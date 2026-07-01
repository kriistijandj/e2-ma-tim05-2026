package com.example.slagalica.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

public class ChallengeResultAdapter extends RecyclerView.Adapter<ChallengeResultAdapter.VH> {

    public static class ResultRow {
        public String uid;
        public int rank; // 1-based; 0 for DNF/unranked
        public int score;
        public long starsDelta;
        public long tokensDelta;
        public boolean dnf;
    }

    private static final int[] AVATAR_RES = {
            android.R.drawable.ic_menu_myplaces,
            android.R.drawable.ic_menu_compass,
            android.R.drawable.ic_menu_gallery,
            android.R.drawable.ic_menu_camera,
            android.R.drawable.ic_menu_manage,
            android.R.drawable.ic_menu_help
    };

    private final List<ResultRow> items;

    public ChallengeResultAdapter(List<ResultRow> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_challenge_result, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        ResultRow row = items.get(pos);

        h.tvRank.setText(row.dnf ? "—" : String.valueOf(row.rank));
        h.tvScore.setText(row.dnf ? "Nije završio(la)" : (row.score + " poena"));

        if (row.dnf) {
            h.tvDelta.setText("");
        } else {
            long netStars = row.starsDelta;
            long netTokens = row.tokensDelta;
            String sign = (netStars > 0 || netTokens > 0) ? "+" : "";
            h.tvDelta.setText("⭐" + sign + netStars + "  🎟" + sign + netTokens);
            h.tvDelta.setTextColor((netStars > 0 || netTokens > 0) ? Color.parseColor("#4CAF50")
                    : (netStars < 0 || netTokens < 0) ? Color.parseColor("#D32F2F") : Color.GRAY);
        }

        FirebaseFirestore.getInstance().collection("users").document(row.uid).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    String username = doc.getString("username");
                    h.tvUsername.setText(username != null ? username : row.uid);

                    Long avatarId = doc.getLong("avatarId");
                    if (avatarId != null) {
                        int idx = avatarId.intValue();
                        if (idx >= 0 && idx < AVATAR_RES.length) {
                            h.ivAvatar.setImageResource(AVATAR_RES[idx]);
                        }
                    }
                });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvRank, tvUsername, tvScore, tvDelta;
        ImageView ivAvatar;

        VH(View v) {
            super(v);
            tvRank = v.findViewById(R.id.tvResultRank);
            tvUsername = v.findViewById(R.id.tvResultUsername);
            tvScore = v.findViewById(R.id.tvResultScore);
            tvDelta = v.findViewById(R.id.tvResultDelta);
            ivAvatar = v.findViewById(R.id.ivResultAvatar);
        }
    }
}
