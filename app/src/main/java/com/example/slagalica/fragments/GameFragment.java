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

    public GameFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_game, container, false);

        // Svaka igra ima svoj lobi i svoju sobu
        CardView cardSkocko = view.findViewById(R.id.cardSkocko);
        if (cardSkocko != null) {
            cardSkocko.setOnClickListener(v ->
                    podeliUloge(view, R.id.nav_gameSkocko,
                            "lobby_skocko", "room_skocko_001"));
        }

        CardView cardAsocijacije = view.findViewById(R.id.cardAsocijacije);
        if (cardAsocijacije != null) {
            cardAsocijacije.setOnClickListener(v ->
                    podeliUloge(view, R.id.nav_gameAsocijacije,
                            "lobby_asocijacije", "room_asocijacije_001"));
        }

        CardView cardKoZnaZna = view.findViewById(R.id.cardKoZnaZna);
        if (cardKoZnaZna != null) {
            cardKoZnaZna.setOnClickListener(v ->
                    podeliUloge(view, R.id.nav_gameKoZnaZna,
                            "lobby_koznaZna", "room_koznaZna_001"));
        }

        CardView cardSpojnice = view.findViewById(R.id.cardSpojnice);
        if (cardSpojnice != null) {
            cardSpojnice.setOnClickListener(v ->
                    podeliUloge(view, R.id.nav_gameSpojnice,
                            "lobby_spojnice", "room_spojnice_001"));
        }

        CardView cardMojBroj = view.findViewById(R.id.cardMojBroj);
        if (cardMojBroj != null) cardMojBroj.setOnClickListener(v ->
                podeliUloge(view, R.id.nav_mojbroj, "lobby_mojbroj", "room_mojbroj_001"));

        CardView cardKorak = view.findViewById(R.id.cardKorak);
        if (cardKorak != null) cardKorak.setOnClickListener(v ->
                podeliUloge(view, R.id.nav_korak, "lobby_korak", "room_korak_001"));

        return view;
    }

    // ==============================
    // LOBI SISTEM – svaka igra ima svoj lobi i sobu
    // ==============================

    private void podeliUloge(View view, int navActionId,
                             String lobbyKey, String roomId) {

        DatabaseReference lobbyRef = FirebaseDatabase.getInstance()
                .getReference().child(lobbyKey);

        lobbyRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String status = snapshot.getValue(String.class);
                Bundle args = new Bundle();
                args.putString("ROOM_ID", roomId);

                if (status == null || "slobodno".equals(status)) {
                    lobbyRef.setValue("zauzeto");
                    args.putString("PLAYER_ROLE", "player1");
                    Toast.makeText(getContext(),
                            "Ušao si kao Player 1", Toast.LENGTH_SHORT).show();
                } else {
                    lobbyRef.setValue("slobodno");
                    args.putString("PLAYER_ROLE", "player2");
                    Toast.makeText(getContext(),
                            "Ušao si kao Player 2", Toast.LENGTH_SHORT).show();
                }

                Navigation.findNavController(view).navigate(navActionId, args);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getContext(),
                        "Greška: " + error.getMessage(), Toast.LENGTH_SHORT).show();
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