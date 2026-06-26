package com.example.slagalica.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.slagalica.R;
import com.example.slagalica.region.SerbiaRegions;
import com.example.slagalica.viewmodel.RegionViewModel;

public class RegionDetailFragment extends Fragment {

    private RegionViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_region_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String regionName = getArguments() != null
                ? getArguments().getString("regionName", "") : "";

        ImageView ivIcon = view.findViewById(R.id.ivRegionIcon);
        TextView tvName = view.findViewById(R.id.tvRegionName);
        TextView tvGold = view.findViewById(R.id.tvGold);
        TextView tvSilver = view.findViewById(R.id.tvSilver);
        TextView tvBronze = view.findViewById(R.id.tvBronze);
        TextView tvActive = view.findViewById(R.id.tvActivePlayers);
        TextView tvTotal = view.findViewById(R.id.tvTotalPlayers);

        tvName.setText(regionName);
        int idx = SerbiaRegions.indexOf(regionName);
        if (idx >= 0) ivIcon.setImageResource(SerbiaRegions.DRAWABLE_IDS[idx]);

        tvGold.setText("Učitava...");
        tvSilver.setText("");
        tvBronze.setText("");
        tvActive.setText("Učitava...");
        tvTotal.setText("");

        viewModel = new ViewModelProvider(this).get(RegionViewModel.class);
        viewModel.getSelectedRegion().observe(getViewLifecycleOwner(), region -> {
            if (region == null) return;
            tvGold.setText("🥇 " + region.getGold() + "x");
            tvSilver.setText("🥈 " + region.getSilver() + "x");
            tvBronze.setText("🥉 " + region.getBronze() + "x");
            tvActive.setText("Aktivni igrači: " + region.getActivePlayers());
            tvTotal.setText("Ukupno registrovanih: " + region.getTotalPlayers());
        });

        if (!regionName.isEmpty()) {
            viewModel.loadRegionDetail(regionName);
        }
    }
}
