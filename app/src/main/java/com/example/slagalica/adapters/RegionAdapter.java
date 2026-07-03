package com.example.slagalica.adapters;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.models.RegionModel;
import com.example.slagalica.region.SerbiaRegions;

import java.util.ArrayList;
import java.util.List;

public class RegionAdapter extends RecyclerView.Adapter<RegionAdapter.VH> {

    public interface OnRegionClickListener {
        void onRegionClick(RegionModel region);
    }

    private List<RegionModel> items = new ArrayList<>();
    private final String myRegion;
    private final OnRegionClickListener listener;

    public RegionAdapter(String myRegion, OnRegionClickListener listener) {
        this.myRegion = myRegion;
        this.listener = listener;
    }

    public void setData(List<RegionModel> data) {
        this.items = data;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_region, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        RegionModel m = items.get(pos);
        h.tvRank.setText(String.valueOf(m.getRank()));
        h.tvName.setText(m.getName());
        h.tvStars.setText(m.getTotalStars() + " ★");
        h.tvCount.setText(m.getPlayerCount() + " igrača");

        int idx = SerbiaRegions.indexOf(m.getName());
        if (idx >= 0) {
            h.ivIcon.setImageResource(SerbiaRegions.DRAWABLE_IDS[idx]);
        }

        if (m.getName().equals(myRegion)) {
            h.root.setBackgroundColor(Color.parseColor("#E3F2FD"));
            h.tvName.setTypeface(null, Typeface.BOLD);
        } else {
            h.root.setBackgroundColor(Color.WHITE);
            h.tvName.setTypeface(null, Typeface.NORMAL);
        }

        h.root.setOnClickListener(v -> {
            if (listener != null) listener.onRegionClick(m);
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        LinearLayout root;
        TextView tvRank, tvName, tvStars, tvCount;
        ImageView ivIcon;

        VH(View v) {
            super(v);
            root = v.findViewById(R.id.itemRoot);
            tvRank = v.findViewById(R.id.tvRank);
            tvName = v.findViewById(R.id.tvRegionName);
            tvStars = v.findViewById(R.id.tvStars);
            tvCount = v.findViewById(R.id.tvPlayerCount);
            ivIcon = v.findViewById(R.id.ivRegionIcon);
        }
    }
}
