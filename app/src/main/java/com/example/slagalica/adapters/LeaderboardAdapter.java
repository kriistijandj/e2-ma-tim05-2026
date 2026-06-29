package com.example.slagalica.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;

import java.util.List;
import java.util.Map;

public class LeaderboardAdapter extends RecyclerView.Adapter<LeaderboardAdapter.ViewHolder> {

    private final List<Map<String, Object>> players;
    private final boolean isWeekly;

    public LeaderboardAdapter(List<Map<String, Object>> players, boolean isWeekly) {
        this.players = players;
        this.isWeekly = isWeekly;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_leaderboard_player, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Map<String, Object> player = players.get(position);

        // Ranka/Pozicija kreće od 1
        holder.tvRankPosition.setText((position + 1) + ".");

        // Korisničko ime
        String username = (String) player.get("username");
        holder.tvRankUsername.setText(username != null ? username : "Nepoznato");

        // Broj osvojenih zvezda u ovom ciklusu
        long stars = 0;
        if (isWeekly) {
            stars = toLong(player.get("weeklyStars"));
        } else {
            stars = toLong(player.get("monthlyStars"));
        }
        holder.tvRankStars.setText(String.valueOf(stars));

        // Ikona lige
        String leagueIcon = (String) player.get("leagueIcon");
        if (leagueIcon == null) leagueIcon = "bronze_league";

        // Mapiranje na osnovu stringa iz baze u res/drawable resurse
        switch (leagueIcon) {
            case "gold_league":
                holder.ivLeagueIcon.setImageResource(android.R.drawable.btn_star_big_on); // Zameni svojim resursom
                break;
            case "silver_league":
                holder.ivLeagueIcon.setImageResource(android.R.drawable.btn_star_big_off); // Zameni svojim resursom
                break;
            default:
                holder.ivLeagueIcon.setImageResource(android.R.drawable.btn_star_big_off);
                break;
        }
    }

    @Override
    public int getItemCount() {
        return players.size();
    }

    private long toLong(Object value) {
        if (value == null) return 0;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Integer) return ((Integer) value).longValue();
        return 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvRankPosition, tvRankUsername, tvRankStars;
        ImageView ivLeagueIcon;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRankPosition = itemView.findViewById(R.id.tvRankPosition);
            ivLeagueIcon = itemView.findViewById(R.id.ivLeagueIcon);
            tvRankUsername = itemView.findViewById(R.id.tvLeaderboardUsername);
            tvRankStars = itemView.findViewById(R.id.tvLeaderboardStars);
        }
    }
}