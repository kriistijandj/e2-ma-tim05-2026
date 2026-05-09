package com.example.slagalica.fragments;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.example.slagalica.R;

public class HomeFragment extends Fragment {

    public HomeFragment() {
        // Obavezan prazan konstruktor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        // Povezivanje dugmeta za navigaciju ka GameFragment (izboru igara)
        Button btnGame = view.findViewById(R.id.btnStartGame);

        btnGame.setOnClickListener(v ->
                Navigation.findNavController(view).navigate(R.id.nav_game)
        );

        return view;
    }
}