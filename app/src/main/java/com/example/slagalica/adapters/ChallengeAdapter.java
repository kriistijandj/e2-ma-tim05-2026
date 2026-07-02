package com.example.slagalica.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.models.Challenge;

import java.util.List;

public class ChallengeAdapter extends RecyclerView.Adapter<ChallengeAdapter.VH> {

    public interface ChallengeActionListener {
        void onJoin(Challenge challenge);
    }

    private static final int[] AVATAR_RES = {
            android.R.drawable.ic_menu_myplaces,
            android.R.drawable.ic_menu_compass,
            android.R.drawable.ic_menu_gallery,
            android.R.drawable.ic_menu_camera,
            android.R.drawable.ic_menu_manage,
            android.R.drawable.ic_menu_help
    };

    private final List<Challenge> items;
    private final ChallengeActionListener listener;

    public ChallengeAdapter(List<Challenge> items, ChallengeActionListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_challenge, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Challenge c = items.get(pos);

        h.tvStake.setText("⭐ " + c.stakeStars + "   🎟 " + c.stakeTokens);
        h.tvCount.setText(c.participants.size() + "/4 učesnika");
        h.tvCreator.setText("Kreator: " + c.creatorId.substring(0, Math.min(6, c.creatorId.length())));

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(c.creatorId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    String username = doc.getString("username");
                    if (username != null) h.tvCreator.setText("Kreator: " + username);

                    Long avatarId = doc.getLong("avatarId");
                    if (avatarId != null) {
                        int idx = avatarId.intValue();
                        if (idx >= 0 && idx < AVATAR_RES.length) {
                            h.ivAvatar.setImageResource(AVATAR_RES[idx]);
                        }
                    }
                });

        h.btnJoin.setOnClickListener(v -> listener.onJoin(c));
    }

    @Override
    public int getItemCount() { return items.size(); }

    public void setData(List<Challenge> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvCreator, tvStake, tvCount;
        Button btnJoin;

        VH(View v) {
            super(v);
            ivAvatar = v.findViewById(R.id.ivChallengeAvatar);
            tvCreator = v.findViewById(R.id.tvChallengeCreator);
            tvStake = v.findViewById(R.id.tvChallengeStake);
            tvCount = v.findViewById(R.id.tvChallengeCount);
            btnJoin = v.findViewById(R.id.btnJoinChallenge);
        }
    }
}
