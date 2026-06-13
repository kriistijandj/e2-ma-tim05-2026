package com.example.slagalica.games;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.slagalica.R;
import com.example.slagalica.models.korak.KorakGameState;
import com.example.slagalica.viewmodel.KorakViewModel;

import java.util.Arrays;
import java.util.List;

/**
 * Fragment za igru "Korak po korak" – multiplayer putem Firebase.
 *
 * Prima argumente:
 *   ROOM_ID    – ID sobe (npr. "room_korak_001")
 *   PLAYER_ROLE – "player1" ili "player2"
 */
public class KorakPoKorakFragment extends Fragment {

    // ---- Views ----
    private TextView tvLeftName, tvRightName, tvLeftScore, tvRightScore;
    private TextView tvTimer, tvHintTitle, tvStatusMessage;
    private List<TextView> hintViews;
    private EditText etAnswer;
    private Button btnPotvrdi;

    // ---- ViewModel ----
    private KorakViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_korak_po_korak, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bindViews(view);

        viewModel = new ViewModelProvider(this).get(KorakViewModel.class);

        String gameId = "room_korak_001";
        String myRole = "player1";
        if (getArguments() != null) {
            gameId = getArguments().getString("ROOM_ID", gameId);
            myRole = getArguments().getString("PLAYER_ROLE", myRole);
        }

        viewModel.init(gameId, myRole);
        viewModel.setupInitialGameIfHost();

        // Observeri
        viewModel.getTimerText().observe(getViewLifecycleOwner(),
                t -> tvTimer.setText(t));

        viewModel.getGameState().observe(getViewLifecycleOwner(),
                this::renderUiFromState);

        // Dugme "Potvrdi" – igrač pogadja
        btnPotvrdi.setOnClickListener(v -> {
            String guess = etAnswer.getText().toString().trim();
            if (guess.isEmpty()) {
                Toast.makeText(getContext(), "Unesi odgovor!", Toast.LENGTH_SHORT).show();
                return;
            }
            boolean correct = viewModel.submitGuess(guess);
            if (correct) {
                Toast.makeText(getContext(), "Tačno! ✓", Toast.LENGTH_SHORT).show();
                etAnswer.setText("");
            } else {
                Toast.makeText(getContext(), "Netačno. Pokušaj ponovo!", Toast.LENGTH_SHORT).show();
                etAnswer.setText("");
            }
        });

    }

    // ================================
    // UI RENDERING
    // ================================

    private void renderUiFromState(KorakGameState state) {
        if (state == null) return;

        String myId = viewModel.getMyPlayerId();
        boolean iAmActive = ("player1".equals(myId) && state.activePlayer == 1)
                || ("player2".equals(myId) && state.activePlayer == 2);

        // Imena i bodovi
        tvLeftName.setText("Igrač 1" + (state.activePlayer == 1 ? " ✎" : ""));
        tvRightName.setText("Igrač 2" + (state.activePlayer == 2 ? " ✎" : ""));
        tvLeftScore.setText("Bodovi: " + state.p1Score);
        tvRightScore.setText("Bodovi: " + state.p2Score);

        // Naslov koraka
        tvHintTitle.setText("Korak " + state.revealedHints + "/7");

        // Otkrivanje koraka – pokazujemo prvih revealedHints
        for (int i = 0; i < 7; i++) {
            if (i < hintViews.size()) {
                TextView tv = hintViews.get(i);
                if (i < state.revealedHints && i < state.hints.size()) {
                    tv.setText(state.hints.get(i));
                    tv.setVisibility(View.VISIBLE);
                } else {
                    tv.setVisibility(View.INVISIBLE);
                }
            }
        }

        // Unos odgovora
        boolean canAnswer = iAmActive && "active".equals(state.status);
        etAnswer.setEnabled(canAnswer);
        btnPotvrdi.setEnabled(canAnswer);

        // Status poruka
        if ("finished".equals(state.status)) {
            tvStatusMessage.setText("Igra završena! P1: " + state.p1Score
                    + " – P2: " + state.p2Score);
            tvStatusMessage.setVisibility(View.VISIBLE);
            etAnswer.setEnabled(false);
            btnPotvrdi.setEnabled(false);
        } else if (state.isOpponentChance && iAmActive) {
            tvStatusMessage.setText("Protivnik nije pogodio – tvoja šansa! (10s)");
            tvStatusMessage.setVisibility(View.VISIBLE);
        } else if (!iAmActive) {
            tvStatusMessage.setText("Protivnik igra, čekaj...");
            tvStatusMessage.setVisibility(View.VISIBLE);
        } else {
            tvStatusMessage.setVisibility(View.GONE);
        }
    }

    // ================================
    // VIEW BINDING
    // ================================

    private void bindViews(View view) {
        tvLeftName     = view.findViewById(R.id.tvLeftName);
        tvRightName    = view.findViewById(R.id.tvRightName);
        tvLeftScore    = view.findViewById(R.id.tvLeftScore);
        tvRightScore   = view.findViewById(R.id.tvRightScore);
        tvTimer        = view.findViewById(R.id.tvTimer);
        tvHintTitle    = view.findViewById(R.id.tvHintTitle);
        etAnswer       = view.findViewById(R.id.etAnswer);
        btnPotvrdi     = view.findViewById(R.id.btnStop);

        hintViews = Arrays.asList(
                (TextView) view.findViewById(R.id.tvHint1),
                (TextView) view.findViewById(R.id.tvHint2),
                (TextView) view.findViewById(R.id.tvHint3),
                (TextView) view.findViewById(R.id.tvHint4),
                (TextView) view.findViewById(R.id.tvHint5),
                (TextView) view.findViewById(R.id.tvHint6),
                (TextView) view.findViewById(R.id.tvHint7)
        );

        // tvStatusMessage – koristimo tvHintTitle kao fallback ako nema posebnog view-a.
        // Bolje: dodaj <TextView android:id="@+id/tvStatusMessage" ... /> u layout.
        tvStatusMessage = view.findViewWithTag("tvStatusMessage");
        if (tvStatusMessage == null) {
            // Kreiramo programski (bez layouta nije idealno, ali ne ruši build)
            tvStatusMessage = new TextView(getContext());
        }
    }
}
