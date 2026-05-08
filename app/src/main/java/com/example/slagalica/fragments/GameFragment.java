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
import com.example.slagalica.R;

public class GameFragment extends Fragment {

    public GameFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_game, container, false);

        setupNavigation(view, R.id.cardSkocko, R.id.nav_gameSkocko);
        setupNavigation(view, R.id.cardMojBroj, R.id.nav_mojbroj);
        setupNavigation(view, R.id.cardAsocijacije, R.id.nav_gameAsocijacije);
        setupNavigation(view, R.id.cardKoZnaZna, R.id.nav_gameKoZnaZna);
        setupNavigation(view, R.id.cardSpojnice, R.id.nav_gameSpojnice);
        setupNavigation(view, R.id.cardKorak, R.id.nav_korak);

        return view;
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