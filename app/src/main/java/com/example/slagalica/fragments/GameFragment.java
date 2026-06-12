package com.example.slagalica.fragments;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.example.slagalica.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class GameFragment extends Fragment {

    private DatabaseReference lobbyRef;

    public GameFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_game, container, false);

        // Referenca na mali lobi u Realtime bazi
        lobbyRef = FirebaseDatabase.getInstance().getReference().child("skocko_lobby");

        // Klik na Skočka pokreće automatsku podelu uloga
        CardView cardSkocko = view.findViewById(R.id.cardSkocko);
        if (cardSkocko != null) {
            cardSkocko.setOnClickListener(v -> podeliUlogeIDekodiraj(view, R.id.nav_gameSkocko));
        }

        // Klik na Asocijacije pokreće istu stvar ali za asocijacije
        CardView cardAsocijacije = view.findViewById(R.id.cardAsocijacije);
        if (cardAsocijacije != null) {
            cardAsocijacije.setOnClickListener(v -> {
                // Ako koristiš isti princip soba, samo proslediš akciju za Asocijacije
                podeliUlogeIDekodiraj(view, R.id.nav_gameAsocijacije);
            });
        }

        // Ostale statičke navigacije
        setupNavigation(view, R.id.cardMojBroj, R.id.nav_mojbroj);
        setupNavigation(view, R.id.cardKoZnaZna, R.id.nav_gameKoZnaZna);
        setupNavigation(view, R.id.cardSpojnice, R.id.nav_gameSpojnice);
        setupNavigation(view, R.id.cardKorak, R.id.nav_korak);

        return view;
    }

    private void podeliUlogeIDekodiraj(View view, int navActionId) {
        lobbyRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String status = snapshot.getValue(String.class);
                Bundle args = new Bundle();

                // Podrazumevana soba koju tvoj kod već koristi
                args.putString("ROOM_ID", "test_game_001");

                if (status == null || "slobodno".equals(status)) {
                    // PRVI TELEFON KOJI KLIKNE: Zauzima mesto i postaje player1
                    lobbyRef.setValue("zauzeto");
                    args.putString("PLAYER_ROLE", "player1");

                    Toast.makeText(getContext(), "Ušao si kao Player 1", Toast.LENGTH_SHORT).show();
                    Navigation.findNavController(view).navigate(navActionId, args);
                } else {
                    // DRUGI TELEFON KOJI KLIKNE: Vidi da je zauzeto, postaje player2 i oslobađa lobi za sledeći put
                    lobbyRef.setValue("slobodno");
                    args.putString("PLAYER_ROLE", "player2");

                    Toast.makeText(getContext(), "Ušao si kao Player 2", Toast.LENGTH_SHORT).show();
                    Navigation.findNavController(view).navigate(navActionId, args);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getContext(), "Greška: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupNavigation(View parent, int cardId, int navActionId) {
        CardView card = parent.findViewById(cardId);
        if (card != null) {
            card.setOnClickListener(v ->
                    Navigation.findNavController(parent).navigate(navActionId)
            );
        }
    }
}