package com.example.slagalica.games;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
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
import com.example.slagalica.models.mojbroj.MojBrojGameState;
import com.example.slagalica.viewmodel.MojBrojViewModel;

import static android.content.Context.SENSOR_SERVICE;

import java.util.Arrays;
import java.util.List;

public class MojBrojFragment extends Fragment implements SensorEventListener {

    private TextView tvLeftName, tvRightName, tvLeftScore, tvRightScore;
    private TextView tvLeftNumber, tvRightNumber;
    private TextView tvTimer, tvTargetNumber, tvStatusMessage;
    private Button btnNum1, btnNum2, btnNum3, btnNum4, btnNum5, btnNum6;
    private Button btnStop, btnDelete, btnPlus, btnMinus, btnMul, btnDiv, btnOpen, btnClose;
    private EditText etExpression;

    private MojBrojViewModel viewModel;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private long lastShakeTime = 0;
    private static final float SHAKE_THRESHOLD = 12f;
    private static final int SHAKE_SLOP_TIME_MS = 500;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_moj_broj, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bindViews(view);
        setupKeyboard();
        setupSensor();

        viewModel = new ViewModelProvider(this).get(MojBrojViewModel.class);

        String gameId = "room_mojbroj_001";
        String myRole = "player1";
        if (getArguments() != null) {
            gameId = getArguments().getString("ROOM_ID", gameId);
            myRole = getArguments().getString("PLAYER_ROLE", myRole);
        }

        viewModel.init(gameId, myRole);
        viewModel.setupInitialGameIfHost();

        viewModel.getTimerText().observe(getViewLifecycleOwner(),
                t -> tvTimer.setText(t));

        viewModel.getGameState().observe(getViewLifecycleOwner(),
                this::renderUiFromState);
    }

    // ─── Renderovanje UI-ja ───────────────────────────────────────────────────

    private void renderUiFromState(MojBrojGameState state) {
        if (state == null) return;

        String myId = viewModel.getMyPlayerId();
        boolean iAmPlayer1 = "player1".equals(myId);
        boolean iAmStopPlayer = (state.stopPlayer == 1 && iAmPlayer1)
                || (state.stopPlayer == 2 && !iAmPlayer1);

        // ── Imena i bodovi
        tvLeftName.setText("Igrač 1" + (state.stopPlayer == 1 ? " ✎" : ""));
        tvRightName.setText("Igrač 2" + (state.stopPlayer == 2 ? " ✎" : ""));
        tvLeftScore.setText("Bodovi: " + state.p1Score);
        tvRightScore.setText("Bodovi: " + state.p2Score);

        // ── Traženi broj
        if (state.targetRevealed) {
            tvTargetNumber.setText("Traženi broj: " + state.targetNumber);
        } else {
            tvTargetNumber.setText("Traženi broj: ???");
        }

        // ── Dostupni brojevi na dugmadima
        if (state.numbersRevealed && state.availableNumbers != null
                && state.availableNumbers.size() == 6) {
            btnNum1.setText(String.valueOf(state.availableNumbers.get(0)));
            btnNum2.setText(String.valueOf(state.availableNumbers.get(1)));
            btnNum3.setText(String.valueOf(state.availableNumbers.get(2)));
            btnNum4.setText(String.valueOf(state.availableNumbers.get(3)));
            btnNum5.setText(String.valueOf(state.availableNumbers.get(4)));
            btnNum6.setText(String.valueOf(state.availableNumbers.get(5)));
        } else {
            btnNum1.setText("?"); btnNum2.setText("?");
            btnNum3.setText("?"); btnNum4.setText("?");
            btnNum5.setText("?"); btnNum6.setText("?");
        }

        // ── Proveri da li je ovaj igrač već predao u ovoj rundi
        boolean mySubmitted = iAmPlayer1 ? state.p1Submitted : state.p2Submitted;

        // ── Kontrola unosa: mogu da unosim kad su brojevi otkriveni i još nisam predao
        boolean canInput = state.numbersRevealed && !mySubmitted
                && "active".equals(state.status);
        setInputEnabled(canInput);

        // ── STOP dugme logika
        if (!"active".equals(state.status)) {
            // Igra završena
            btnStop.setText("---");
            btnStop.setEnabled(false);
        } else if (!state.targetRevealed) {
            // Faza 1: stop za otkrivanje traženog broja – samo stopPlayer
            btnStop.setText("STOP (otkrij broj)");
            btnStop.setEnabled(iAmStopPlayer);
        } else if (!state.numbersRevealed) {
            // Faza 2: stop za otkrivanje dostupnih brojeva – samo stopPlayer
            btnStop.setText("STOP (otkrij brojeve)");
            btnStop.setEnabled(iAmStopPlayer);
        } else if (!mySubmitted) {
            // Faza igranja: predaja vlastitog izraza – oba igrača
            btnStop.setText("Predaj izraz");
            btnStop.setEnabled(true);
        } else {
            // Već predato – čeka se protivnik
            btnStop.setText("Predato");
            btnStop.setEnabled(false);
        }


        boolean showResults = state.p1Submitted && state.p2Submitted;
        if (showResults) {
            tvLeftNumber.setText(formatResult(state.p1Result));
            tvRightNumber.setText(formatResult(state.p2Result));
        } else {

            tvLeftNumber.setText(state.p1Submitted ? formatResult(state.p1Result) : "???");
            tvRightNumber.setText(state.p2Submitted ? formatResult(state.p2Result) : "???");
        }

        if (state.showingRoundResult) {
            tvStatusMessage.setText(
                    "Runda 1 završena!\n" + "Prelazak na rundu 2..."
            );
            tvStatusMessage.setVisibility(View.VISIBLE);
            setInputEnabled(false);
            btnStop.setEnabled(false);
            return; // ne renderuj ostatak dok traje pauza
        }


        if ("finished".equals(state.status)) {
            String winner;
            if (state.p1Score > state.p2Score) winner = "Pobedio Igrač 1!";
            else if (state.p2Score > state.p1Score) winner = "Pobedio Igrač 2!";
            else winner = "Nerešeno!";
            tvStatusMessage.setText("Igra završena! " + winner
                    + " (P1: " + state.p1Score + " – P2: " + state.p2Score + ")");
            tvStatusMessage.setVisibility(View.VISIBLE);
        } else if (mySubmitted) {
            tvStatusMessage.setText("Čeka se protivnik...");
            tvStatusMessage.setVisibility(View.VISIBLE);
        } else if (!state.numbersRevealed) {
            if (iAmStopPlayer) {
                tvStatusMessage.setText("Pritisni STOP da otkriješ " +
                        (!state.targetRevealed ? "traženi broj" : "dostupne brojeve") + "!");
            } else {
                tvStatusMessage.setText("Čeka se da protivnik stopira...");
            }
            tvStatusMessage.setVisibility(View.VISIBLE);
        } else {
            tvStatusMessage.setVisibility(View.GONE);
        }
    }


    private String formatResult(int result) {
        if (result == -1 || result == Integer.MIN_VALUE) return "—";
        return String.valueOf(result);
    }

    private void setInputEnabled(boolean enabled) {
        etExpression.setEnabled(enabled);
        btnPlus.setEnabled(enabled);
        btnMinus.setEnabled(enabled);
        btnMul.setEnabled(enabled);
        btnDiv.setEnabled(enabled);
        btnOpen.setEnabled(enabled);
        btnClose.setEnabled(enabled);
        btnDelete.setEnabled(enabled);
        btnNum1.setEnabled(enabled);
        btnNum2.setEnabled(enabled);
        btnNum3.setEnabled(enabled);
        btnNum4.setEnabled(enabled);
        btnNum5.setEnabled(enabled);
        btnNum6.setEnabled(enabled);
    }



    private void setupKeyboard() {
        List<Button> numButtons = Arrays.asList(
                btnNum1, btnNum2, btnNum3, btnNum4, btnNum5, btnNum6);

        View.OnClickListener insertOp = v -> etExpression.append(((Button) v).getText());
        btnPlus.setOnClickListener(insertOp);
        btnMinus.setOnClickListener(insertOp);
        btnMul.setOnClickListener(insertOp);
        btnDiv.setOnClickListener(insertOp);
        btnOpen.setOnClickListener(insertOp);
        btnClose.setOnClickListener(insertOp);

        for (Button btn : numButtons) {
            btn.setOnClickListener(v -> {
                etExpression.append(btn.getText());
                btn.setEnabled(false);
                btn.setAlpha(0.4f);
            });
        }

        btnDelete.setOnClickListener(v -> {
            String t = etExpression.getText().toString();
            if (!t.isEmpty()) {
                etExpression.setText(t.substring(0, t.length() - 1));
                etExpression.setSelection(etExpression.getText().length());
            }
            // Resetuj dostupnost broj-dugmadi (pojednostavljeno – sve upalimo)
            for (Button btn : numButtons) {
                MojBrojGameState state = viewModel.getGameState().getValue();
                boolean mySubmitted = state != null && (
                        "player1".equals(viewModel.getMyPlayerId())
                                ? state.p1Submitted : state.p2Submitted);
                if (!mySubmitted) {
                    btn.setEnabled(true);
                    btn.setAlpha(1.0f);
                }
            }
        });

        btnStop.setOnClickListener(v -> {
            MojBrojGameState state = viewModel.getGameState().getValue();
            if (state == null) return;

            if (!state.targetRevealed || !state.numbersRevealed) {
                // Faza stopiranja
                viewModel.onStopPressed();
            } else {
                // Faza predaje izraza
                String expr = etExpression.getText().toString().trim();
                viewModel.submitExpression(expr);
                etExpression.setText("");
                for (Button btn : numButtons) {
                    btn.setEnabled(true);
                    btn.setAlpha(1.0f);
                }
            }
        });
    }

    private void bindViews(View view) {
        tvLeftName     = view.findViewById(R.id.tvLeftName);
        tvRightName    = view.findViewById(R.id.tvRightName);
        tvLeftScore    = view.findViewById(R.id.tvLeftScore);
        tvRightScore   = view.findViewById(R.id.tvRightScore);
        tvTimer        = view.findViewById(R.id.tvTimer);
        tvTargetNumber = view.findViewById(R.id.tvTargetNumber2);
        etExpression   = view.findViewById(R.id.etExpression);
        btnStop        = view.findViewById(R.id.btnStop);
        btnDelete      = view.findViewById(R.id.btnDelete);
        btnPlus        = view.findViewById(R.id.btnPlus);
        btnMinus       = view.findViewById(R.id.btnMinus);
        btnMul         = view.findViewById(R.id.btnMul);
        btnDiv         = view.findViewById(R.id.btnDiv);
        btnOpen        = view.findViewById(R.id.btnOpen);
        btnClose       = view.findViewById(R.id.btnClose);
        btnNum1        = view.findViewById(R.id.btnNum1);
        btnNum2        = view.findViewById(R.id.btnNum2);
        btnNum3        = view.findViewById(R.id.btnNum3);
        btnNum4        = view.findViewById(R.id.btnNum4);
        btnNum5        = view.findViewById(R.id.btnNum5);
        btnNum6        = view.findViewById(R.id.btnNum6);
        tvLeftNumber   = view.findViewById(R.id.tvLeftNumber);
        tvRightNumber  = view.findViewById(R.id.tvRightNumber);
        // Sada ispravno – bindujemo po ID-u (ne više po tagu)
        tvStatusMessage = view.findViewById(R.id.tvStatusMessage);
    }

    // ─── Shake senzor ─────────────────────────────────────────────────────────

    private void setupSensor() {
        sensorManager = (SensorManager) requireActivity().getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer,
                    SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;

        float x = event.values[0], y = event.values[1], z = event.values[2];
        float acceleration = (float) Math.sqrt(x * x + y * y + z * z)
                - SensorManager.GRAVITY_EARTH;

        if (acceleration > SHAKE_THRESHOLD) {
            long now = System.currentTimeMillis();
            if (now - lastShakeTime > SHAKE_SLOP_TIME_MS) {
                lastShakeTime = now;
                // Shake radi samo u fazi stopiranja (pre nego što su brojevi otkriveni)
                MojBrojGameState state = viewModel.getGameState().getValue();
                if (state != null && (!state.targetRevealed || !state.numbersRevealed)) {
                    viewModel.onStopPressed();
                }
                // U fazi igranja shake ne radi ništa (po specifikaciji shake = stop dugme)
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
