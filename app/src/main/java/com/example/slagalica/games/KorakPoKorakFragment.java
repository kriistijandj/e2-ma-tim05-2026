package com.example.slagalica.games;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.slagalica.R;
import com.example.slagalica.models.korak_po_korak.KorakPoKorakGameState;
import com.example.slagalica.viewmodel.KorakPoKorakViewModel;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Arrays;
import java.util.List;

public class KorakPoKorakFragment extends Fragment {

    private KorakPoKorakViewModel viewModel;
    private KorakPoKorakGameState state;

    private String myUserId;

    // UI
    private TextView tvHintTitle;
    private TextView tvLeftScore;
    private TextView tvRightScore;
    private TextView tvTimer;
    private EditText etAnswer;
    private Button btnStop;

    private List<TextView> hintViews;

    private final List<String> hints = Arrays.asList(
            "Grad u Srbiji",
            "Ima tvrđavu",
            "Leži na dve reke",
            "Spominje se u pesmama",
            "Ima aerodrom",
            "U njemu je Skupština",
            "Počinje na B"
    );

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_korak_po_korak, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);

        // 🔐 user id
        myUserId = FirebaseAuth.getInstance()
                .getCurrentUser()
                .getUid();

        // ViewModel
        viewModel = new ViewModelProvider(this)
                .get(KorakPoKorakViewModel.class);

        viewModel.init("GAME_ID");

        // UI init
        tvHintTitle = view.findViewById(R.id.tvHintTitle);
        tvLeftScore = view.findViewById(R.id.tvLeftScore);
        tvRightScore = view.findViewById(R.id.tvRightScore);
        tvTimer = view.findViewById(R.id.tvTimer);
        etAnswer = view.findViewById(R.id.etAnswer);
        btnStop = view.findViewById(R.id.btnStop);

        hintViews = Arrays.asList(
                view.findViewById(R.id.tvHint1),
                view.findViewById(R.id.tvHint2),
                view.findViewById(R.id.tvHint3),
                view.findViewById(R.id.tvHint4),
                view.findViewById(R.id.tvHint5),
                view.findViewById(R.id.tvHint6),
                view.findViewById(R.id.tvHint7)
        );

        btnStop.setOnClickListener(v -> onAnswerClicked());

        // 🔥 FIREBASE OBSERVER
        viewModel.getGameState().observe(getViewLifecycleOwner(), newState -> {

            state = newState;

            updateUI();
            updateInputState();
        });
    }

    // =========================
    // UI UPDATE
    // =========================

    private void updateUI() {

        if (state == null) return;

        tvLeftScore.setText("Bodovi: " + state.score1);
        tvRightScore.setText("Bodovi: " + state.score2);

        tvHintTitle.setText(
                "Igrač: " + state.currentPlayerId +
                        " | Korak " + (state.currentHint + 1)
        );

        tvTimer.setText(String.valueOf(state.timeLeft / 1000));

        // prikaz hintova
        for (int i = 0; i < hintViews.size(); i++) {

            if (i <= state.currentHint) {
                hintViews.get(i).setVisibility(View.VISIBLE);
            } else {
                hintViews.get(i).setVisibility(View.INVISIBLE);
            }
        }
    }

    // =========================
    // ENABLE / DISABLE INPUT
    // =========================

    private void updateInputState() {

        if (state == null) return;

        boolean myTurn =
                myUserId.equals(state.currentPlayerId)
                        && state.roundActive;

        etAnswer.setEnabled(myTurn);
        btnStop.setEnabled(myTurn);
    }

    // =========================
    // CLICK ANSWER
    // =========================

    private void onAnswerClicked() {

        if (state == null) return;

        String answer = etAnswer.getText().toString().trim().toLowerCase();

        viewModel.setAnswer(state, answer, true, state.currentHint);

        etAnswer.setText("");
    }
}