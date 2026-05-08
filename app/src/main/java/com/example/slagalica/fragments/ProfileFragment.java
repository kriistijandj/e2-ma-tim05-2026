package com.example.slagalica.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.slagalica.R;

public class ProfileFragment extends Fragment {

    private ImageView ivAvatar, ivQrCode;
    private TextView tvUsername, tvEmail, tvTokens, tvStars, tvLeague, tvRegion;
    private TextView tvTotalGames, tvWinLoss;
    private TextView tvKoZnaZnaStat, tvMojBrojStat, tvKorakStat;
    private TextView tvAsocijacijeStat, tvSkockoStat, tvSpojniceStat;
    private Button btnChangeAvatar, btnLogout;

    public ProfileFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        // Pronađi view-ove
        ivAvatar         = view.findViewById(R.id.ivAvatar);
        ivQrCode         = view.findViewById(R.id.ivQrCode);
        tvUsername       = view.findViewById(R.id.tvUsername);
        tvEmail          = view.findViewById(R.id.tvEmail);
        tvTokens         = view.findViewById(R.id.tvTokens);
        tvStars          = view.findViewById(R.id.tvStars);
        tvLeague         = view.findViewById(R.id.tvLeague);
        tvRegion         = view.findViewById(R.id.tvRegion);
        tvTotalGames     = view.findViewById(R.id.tvTotalGames);
        tvWinLoss        = view.findViewById(R.id.tvWinLoss);
        tvKoZnaZnaStat   = view.findViewById(R.id.tvKoZnaZnaStat);
        tvMojBrojStat    = view.findViewById(R.id.tvMojBrojStat);
        tvKorakStat      = view.findViewById(R.id.tvKorakStat);
        tvAsocijacijeStat = view.findViewById(R.id.tvAsocijacijeStat);
        tvSkockoStat     = view.findViewById(R.id.tvSkockoStat);
        tvSpojniceStat   = view.findViewById(R.id.tvSpojniceStat);
        btnChangeAvatar  = view.findViewById(R.id.btnChangeAvatar);
        btnLogout        = view.findViewById(R.id.btnLogout);

        // Primjer podataka – zamijeniti sa Firebase podacima
        loadMockData();

        btnChangeAvatar.setOnClickListener(v ->
                Toast.makeText(getContext(), "Izmjena avatara – TODO", Toast.LENGTH_SHORT).show()
        );

        btnLogout.setOnClickListener(v -> {
            // TODO: Firebase signOut()
            Toast.makeText(getContext(), "Odjava...", Toast.LENGTH_SHORT).show();
        });

        return view;
    }

    private void loadMockData() {
        tvUsername.setText("korisnik123");
        tvEmail.setText("korisnik@email.com");
        tvTokens.setText("5");
        tvStars.setText("42");
        tvLeague.setText("Nulta liga");
        tvRegion.setText("Beograd");
        tvTotalGames.setText("18");
        tvWinLoss.setText("61% / 39%");
        tvKoZnaZnaStat.setText("34 / 16");
        tvMojBrojStat.setText("45%");
        tvKorakStat.setText("K1:10% K2:25% K3:40%");
        tvAsocijacijeStat.setText("12 / 6");
        tvSkockoStat.setText("P1:5% P2:20% P3:35%");
        tvSpojniceStat.setText("72%");
    }
}