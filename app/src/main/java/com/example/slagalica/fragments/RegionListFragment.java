package com.example.slagalica.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.adapters.RegionAdapter;
import com.example.slagalica.viewmodel.RegionViewModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class RegionListFragment extends Fragment {

    private RegionViewModel viewModel;
    private RegionAdapter adapter;
    private String myRegion = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_region_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView rv = view.findViewById(R.id.rvRegions);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));

        viewModel = new ViewModelProvider(this).get(RegionViewModel.class);

        loadMyRegion(rv);
    }

    private void loadMyRegion(RecyclerView rv) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            setupAdapter(rv);
            return;
        }
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String r = doc.getString("region");
                        myRegion = r != null ? r : "";
                    }
                    setupAdapter(rv);
                })
                .addOnFailureListener(e -> setupAdapter(rv));
    }

    private void setupAdapter(RecyclerView rv) {
        adapter = new RegionAdapter(myRegion, region -> {
            Bundle args = new Bundle();
            args.putString("regionName", region.getName());
            Navigation.findNavController(requireView())
                    .navigate(R.id.nav_region_detail, args);
        });
        rv.setAdapter(adapter);

        viewModel.getRegionList().observe(getViewLifecycleOwner(), regions -> {
            if (regions != null) adapter.setData(regions);
        });
        viewModel.startAutoRefresh();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        viewModel.stopAutoRefresh();
    }
}
