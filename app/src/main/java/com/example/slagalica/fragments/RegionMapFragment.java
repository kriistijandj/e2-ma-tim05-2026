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

import com.example.slagalica.R;
import com.example.slagalica.viewmodel.RegionViewModel;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.util.List;

import androidx.core.content.ContextCompat;

public class RegionMapFragment extends Fragment {

    private MapView mapView;
    private RegionViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Configuration.getInstance().setUserAgentValue(requireContext().getPackageName());
        View view = inflater.inflate(R.layout.fragment_region_map, container, false);
        mapView = view.findViewById(R.id.mapView);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(7.5);
        mapView.getController().setCenter(new GeoPoint(44.5, 21.0));
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.btnRangLista).setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.nav_region_list));

        viewModel = new ViewModelProvider(this).get(RegionViewModel.class);
        viewModel.getUserLocations().observe(getViewLifecycleOwner(), this::showMarkers);
        viewModel.loadUserLocations();
    }

    private void showMarkers(List<double[]> locations) {
        mapView.getOverlays().clear();
        for (double[] loc : locations) {
            Marker marker = new Marker(mapView);
            marker.setPosition(new GeoPoint(loc[0], loc[1]));
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marker.setIcon(ContextCompat.getDrawable(requireContext(),
                    android.R.drawable.ic_menu_myplaces));
            mapView.getOverlays().add(marker);
        }
        mapView.invalidate();
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }
}
