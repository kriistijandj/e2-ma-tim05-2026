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
import android.widget.Toast;

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


    private TextView tvLeftName, tvRightName, tvLeftScore, tvRightScore, tvLeftNumber, tvRightNumber;
    private TextView tvTimer, tvTargetNumber;
    private Button btnNum1, btnNum2, btnNum3, btnNum4, btnNum5, btnNum6;
    private Button btnStop, btnDelete, btnPlus, btnMinus, btnMul, btnDiv, btnOpen, btnClose;
    private EditText etExpression;
    private TextView tvStatusMessage;


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
        setupKeyboard(view);
        setupSensor();

        viewModel = new ViewModelProvider(this).get(MojBrojViewModel.class);

        String gameId    = "room_mojbroj_001";
        String myRole    = "player1";
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
    }



    private void renderUiFromState(MojBrojGameState state) {
        if (state == null) return;

        String myId = viewModel.getMyPlayerId();

        // Imena i bodovi
        tvLeftName.setText("Igrač 1" + (state.activePlayer == 1 ? " ✎" : ""));
        tvRightName.setText("Igrač 2" + (state.activePlayer == 2 ? " ✎" : ""));
        tvLeftScore.setText("Bodovi: " + state.p1Score);
        tvRightScore.setText("Bodovi: " + state.p2Score);

        boolean iAmActive = ("player1".equals(myId) && state.activePlayer == 1)
                || ("player2".equals(myId) && state.activePlayer == 2);

        // Traženi broj
        if (state.targetRevealed) {
            tvTargetNumber.setText("Traženi broj: " + state.targetNumber);
        } else {
            tvTargetNumber.setText("Traženi broj: ???");
        }

        // Dostupni brojevi (dugmadi)
        if (state.numbersRevealed && state.availableNumbers.size() == 6) {
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

        // Blokiraj unos ako nisam na potezu ili igra nije u fazi unosa
        boolean canInput = iAmActive && state.targetRevealed && state.numbersRevealed
                && "active".equals(state.status);
        setInputEnabled(canInput);

        // Prikaz "Stop" dugmeta
        if (!state.targetRevealed) {
            btnStop.setText("STOP (otkrij broj)");
            btnStop.setEnabled(iAmActive);
        } else if (!state.numbersRevealed) {
            btnStop.setText("STOP (otkrij brojeve)");
            btnStop.setEnabled(iAmActive);
        } else if ("active".equals(state.status)) {
            btnStop.setText("Predaj izraz");
            btnStop.setEnabled(iAmActive);
        } else {
            btnStop.setText("---");
            btnStop.setEnabled(false);
        }

        // Status poruka
        if ("finished".equals(state.status)) {
            String msg = "Igra završena! P1: " + state.p1Score + " – P2: " + state.p2Score;
            tvStatusMessage.setText(msg);
            tvStatusMessage.setVisibility(View.VISIBLE);
        } else if (!iAmActive) {
            tvStatusMessage.setText("Čekanje na protivnika...");
            tvStatusMessage.setVisibility(View.VISIBLE);
        } else {
            tvStatusMessage.setVisibility(View.GONE);
        }

        boolean roundJustEnded =
                (state.round == 2 && state.p2Submitted) ||
                        (state.round == 1 && state.p1Submitted);

        if ("finished".equals(state.status) || roundJustEnded) {

            tvLeftNumber.setText(
                    state.p1Result != -1 ? String.valueOf(state.p1Result) : "-"
            );

            tvRightNumber.setText(
                    state.p2Result != -1 ? String.valueOf(state.p2Result) : "-"
            );

        } else {
            tvLeftNumber.setText("???");
            tvRightNumber.setText("???");
        }


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



    private void setupKeyboard(View view) {

        View.OnClickListener insertOp = v -> etExpression.append(((Button) v).getText());
        btnPlus.setOnClickListener(insertOp);
        btnMinus.setOnClickListener(insertOp);
        btnMul.setOnClickListener(insertOp);
        btnDiv.setOnClickListener(insertOp);
        btnOpen.setOnClickListener(insertOp);
        btnClose.setOnClickListener(insertOp);


        List<Button> numButtons = Arrays.asList(btnNum1, btnNum2, btnNum3, btnNum4, btnNum5, btnNum6);
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
            for (Button btn : numButtons) {
                btn.setEnabled(true);
                btn.setAlpha(1.0f);
            }
        });


        btnStop.setOnClickListener(v -> {
            MojBrojGameState state = viewModel.getGameState().getValue();
            if (state == null) return;

            if (!state.targetRevealed || !state.numbersRevealed) {
                viewModel.onStopPressed();
            } else {
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
        tvLeftName      = view.findViewById(R.id.tvLeftName);
        tvRightName     = view.findViewById(R.id.tvRightName);
        tvLeftScore     = view.findViewById(R.id.tvLeftScore);
        tvRightScore    = view.findViewById(R.id.tvRightScore);
        tvTimer         = view.findViewById(R.id.tvTimer);
        tvTargetNumber  = view.findViewById(R.id.tvTargetNumber2);
        etExpression    = view.findViewById(R.id.etExpression);
        btnStop         = view.findViewById(R.id.btnStop);
        btnDelete       = view.findViewById(R.id.btnDelete);
        btnPlus         = view.findViewById(R.id.btnPlus);
        btnMinus        = view.findViewById(R.id.btnMinus);
        btnMul          = view.findViewById(R.id.btnMul);
        btnDiv          = view.findViewById(R.id.btnDiv);
        btnOpen         = view.findViewById(R.id.btnOpen);
        btnClose        = view.findViewById(R.id.btnClose);
        btnNum1         = view.findViewById(R.id.btnNum1);
        btnNum2         = view.findViewById(R.id.btnNum2);
        btnNum3         = view.findViewById(R.id.btnNum3);
        btnNum4         = view.findViewById(R.id.btnNum4);
        btnNum5         = view.findViewById(R.id.btnNum5);
        btnNum6         = view.findViewById(R.id.btnNum6);
        tvLeftNumber  = view.findViewById(R.id.tvLeftNumber);
        tvRightNumber = view.findViewById(R.id.tvRightNumber);


        tvStatusMessage = view.findViewWithTag("tvStatusMessage");
        if (tvStatusMessage == null) {

            tvStatusMessage = new TextView(getContext());
        }
    }


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
                // Shake = isto kao klik na Stop
                viewModel.onStopPressed();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
