package com.example.slagalica.games;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.CountDownTimer;
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

import com.example.slagalica.R;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

import java.util.Random;

public class MojBrojFragment extends Fragment {

    // GAME STATE
    private int round = 1;
    private int currentPlayer = 1;

    private int targetNumber;
    private int[] numbers = new int[6];

    private int player1Result = 0;
    private int player2Result = 0;

    private int player1Score = 0;
    private int player2Score = 0;

    // FLAGS
    private boolean targetRevealed = false;
    private boolean numbersRevealed = false;
    private boolean waitingAutoReveal = false;

    // UI
    private TextView tvTarget, tvTimer, tvLeftScore, tvRightScore;
    private EditText etExpression;

    // TIMERS
    private CountDownTimer roundTimer;
    private CountDownTimer autoRevealTimer;
    private CountDownTimer aiTimer;

    // SENSOR
    private SensorManager sensorManager;
    private Sensor accelerometer;

    private float lastX, lastY, lastZ;
    private static final float SHAKE_THRESHOLD = 12.0f;

    private TextView tvLeftNumber, tvRightNumber;

    // VIEW
    private View rootView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_moj_broj, container, false);
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        etExpression = view.findViewById(R.id.etExpression);
        tvTarget = view.findViewById(R.id.tvTargetNumber2);
        tvTimer = view.findViewById(R.id.tvTimer);
        tvLeftScore = view.findViewById(R.id.tvLeftScore);
        tvRightScore = view.findViewById(R.id.tvRightScore);
        tvLeftNumber = view.findViewById(R.id.tvLeftNumber);
        tvRightNumber = view.findViewById(R.id.tvRightNumber);

        Button btnStop = view.findViewById(R.id.btnStop);
        Button btnSubmit = new Button(getContext());
        btnSubmit.setText("POTVRDI");
        ((ViewGroup) view).addView(btnSubmit);

        btnStop.setOnClickListener(v -> handleStop());
        btnSubmit.setOnClickListener(v -> submit());

        setupButtons(view);
        initSensor();
    }

    // ================= STOP LOGIKA =================
    private void handleStop() {

        if (!targetRevealed) {
            generateTarget();
            targetRevealed = true;
            return;
        }

        if (!numbersRevealed && !waitingAutoReveal) {

            waitingAutoReveal = true;

            autoRevealTimer = new CountDownTimer(5000, 1000) {
                @Override public void onTick(long l) {}

                @Override public void onFinish() {
                    revealNumbers();
                }
            }.start();

            return;
        }

        if (!numbersRevealed) {
            if (autoRevealTimer != null) autoRevealTimer.cancel();
            revealNumbers();
        }
    }

    // ================= GAME FLOW =================
    private void generateTarget() {
        targetNumber = new Random().nextInt(900) + 100;
        tvTarget.setText("Traženi broj: " + targetNumber);
    }

    private void revealNumbers() {
        generateNumbers();
        numbersRevealed = true;
        startRoundTimer();
    }

    private void generateNumbers() {

        Random r = new Random();

        for (int i = 0; i < 4; i++) {
            numbers[i] = r.nextInt(9) + 1;
        }

        int[] mid = {10, 15, 20};
        int[] big = {25, 50, 75, 100};

        numbers[4] = mid[r.nextInt(mid.length)];
        numbers[5] = big[r.nextInt(big.length)];

        updateNumberButtons();
    }

    private void updateNumberButtons() {
        ((Button) rootView.findViewById(R.id.btnNum1)).setText(String.valueOf(numbers[0]));
        ((Button) rootView.findViewById(R.id.btnNum2)).setText(String.valueOf(numbers[1]));
        ((Button) rootView.findViewById(R.id.btnNum3)).setText(String.valueOf(numbers[2]));
        ((Button) rootView.findViewById(R.id.btnNum4)).setText(String.valueOf(numbers[3]));
        ((Button) rootView.findViewById(R.id.btnNum5)).setText(String.valueOf(numbers[4]));
        ((Button) rootView.findViewById(R.id.btnNum6)).setText(String.valueOf(numbers[5]));
    }

    // ================= TIMER =================
    private void startRoundTimer() {

        roundTimer = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long ms) {
                tvTimer.setText(String.valueOf(ms / 1000));
            }

            @Override
            public void onFinish() {
                submit();
                showResults();
            }
        }.start();
    }

    // ================= SUBMIT =================
    private void submit() {

        if (roundTimer != null) roundTimer.cancel();

        String expr = etExpression.getText().toString();

        int result = 0;

        try {
            Expression e = new ExpressionBuilder(expr).build();
            result = (int) e.evaluate();
        } catch (Exception ignored) {}

        if (currentPlayer == 1) {

            player1Result = result;
            currentPlayer = 2;

            Toast.makeText(getContext(), "AI igra...", Toast.LENGTH_SHORT).show();
            simulateAI();

        } else {
            player2Result = result;
            calculateScore();
        }
    }

    // ================= AI =================
    private void simulateAI() {

        aiTimer = new CountDownTimer(3000, 1000) {
            @Override public void onTick(long l) {}

            @Override
            public void onFinish() {

                Random r = new Random();
                player2Result = targetNumber + (r.nextInt(21) - 10);

                calculateScore();
            }
        }.start();
    }

    // ================= SCORE =================
    private void calculateScore() {

        int diff1 = Math.abs(targetNumber - player1Result);
        int diff2 = Math.abs(targetNumber - player2Result);

        boolean p1Exact = player1Result == targetNumber;
        boolean p2Exact = player2Result == targetNumber;

        if (p1Exact && !p2Exact) {
            player1Score += 10;
        } else if (!p1Exact && p2Exact) {
            player2Score += 10;
        } else if (!p1Exact && !p2Exact) {

            if (diff1 < diff2) player1Score += 5;
            else if (diff2 < diff1) player2Score += 5;
            else {
                if (currentPlayer == 2) player2Score += 5;
                else player1Score += 5;
            }
        } else {
            player1Score += 10;
            player2Score += 10;
        }


        updateUI();
        nextRound();
    }

    private void showResults() {
        tvLeftNumber.setText(String.valueOf(player1Result));
        tvRightNumber.setText(String.valueOf(player2Result));
    }

    private void updateUI() {
        tvLeftScore.setText("Bodovi: " + player1Score);
        tvRightScore.setText("Bodovi: " + player2Score);
    }

    // ================= ROUND =================
    private void nextRound() {

        if (round == 1) {
            round = 2;
            resetGameState();
        } else {
            Toast.makeText(getContext(), "KRAJ IGRE", Toast.LENGTH_LONG).show();
        }
    }

    private void resetGameState() {

        targetRevealed = false;
        numbersRevealed = false;
        waitingAutoReveal = false;
        currentPlayer = 1;

        etExpression.setText("");
        tvTarget.setText("???");
        tvTimer.setText("60");

        tvLeftNumber.setText("???");
        tvRightNumber.setText("???");

        for (int i = 1; i <= 6; i++) {
            int id = getResources().getIdentifier("btnNum" + i, "id", getContext().getPackageName());
            ((Button) rootView.findViewById(id)).setText("?");
        }
    }

    // ================= BUTTONS =================
    private void setupButtons(View view) {

        View.OnClickListener l = v ->
                etExpression.append(((Button) v).getText().toString());

        int[] ids = {
                R.id.btnNum1, R.id.btnNum2, R.id.btnNum3,
                R.id.btnNum4, R.id.btnNum5, R.id.btnNum6,
                R.id.btnPlus, R.id.btnMinus, R.id.btnMul,
                R.id.btnDiv, R.id.btnOpen, R.id.btnClose
        };

        for (int id : ids) {
            view.findViewById(id).setOnClickListener(l);
        }

        view.findViewById(R.id.btnDelete).setOnClickListener(v -> {
            String t = etExpression.getText().toString();
            if (!t.isEmpty()) {
                etExpression.setText(t.substring(0, t.length() - 1));
            }
        });
    }

    // ================= SHAKE SENSOR =================
    private void initSensor() {

        sensorManager = (SensorManager) requireContext().getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        SensorEventListener listener = new SensorEventListener() {

            @Override
            public void onSensorChanged(SensorEvent event) {

                float x = event.values[0];
                float y = event.values[1];
                float z = event.values[2];

                float force = Math.abs(x + y + z - lastX - lastY - lastZ);

                if (force > SHAKE_THRESHOLD) {
                    handleStop();
                }

                lastX = x;
                lastY = y;
                lastZ = z;
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {}
        };

        sensorManager.registerListener(listener, accelerometer,
                SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    public void onPause() {
        super.onPause();
        sensorManager.unregisterListener((SensorEventListener) this);
    }
}