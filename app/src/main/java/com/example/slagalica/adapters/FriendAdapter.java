package com.example.slagalica.adapters;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.models.FriendModel;
import com.example.slagalica.services.LeagueManager;

import java.util.List;

public class FriendAdapter extends RecyclerView.Adapter<FriendAdapter.VH> {

    public interface FriendActionListener {
        void onPlay(FriendModel friend);
        void onRemove(FriendModel friend);
    }

    private static final int[] AVATAR_RES = {
        android.R.drawable.ic_menu_myplaces,
        android.R.drawable.ic_menu_compass,
        android.R.drawable.ic_menu_gallery,
        android.R.drawable.ic_menu_camera,
        android.R.drawable.ic_menu_manage,
        android.R.drawable.ic_menu_help
    };

    private final List<FriendModel> items;
    private final FriendActionListener listener;

    public FriendAdapter(List<FriendModel> items, FriendActionListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_friend, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        FriendModel f = items.get(pos);

        int avatarIdx = (f.avatarId >= 0 && f.avatarId < AVATAR_RES.length) ? f.avatarId : 0;
        h.ivAvatar.setImageResource(AVATAR_RES[avatarIdx]);

        h.tvUsername.setText(f.username);
        h.tvStats.setText("⭐ " + f.stars + "  " + LeagueManager.getDisplayName(f.league));

        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(f.online ? 0xFF4CAF50 : 0xFF9E9E9E);
        h.vOnlineDot.setBackground(dot);

        if (f.online && !f.inMatch) {
            h.btnPlay.setVisibility(View.VISIBLE);
            h.btnPlay.setOnClickListener(v -> listener.onPlay(f));
        } else {
            h.btnPlay.setVisibility(View.GONE);
        }

        h.btnRemove.setOnClickListener(v -> listener.onRemove(f));
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvUsername, tvStats;
        View vOnlineDot;
        Button btnPlay, btnRemove;

        VH(View v) {
            super(v);
            ivAvatar = v.findViewById(R.id.ivFriendAvatar);
            tvUsername = v.findViewById(R.id.tvFriendUsername);
            tvStats = v.findViewById(R.id.tvFriendStats);
            vOnlineDot = v.findViewById(R.id.vOnlineDot);
            btnPlay = v.findViewById(R.id.btnPlayFriend);
            btnRemove = v.findViewById(R.id.btnRemoveFriend);
        }
    }
}
