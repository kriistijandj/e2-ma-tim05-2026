package com.example.slagalica.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.slagalica.R;

public class HomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        Button btnGame = findViewById(R.id.btnStartGame);

        btnGame.setOnClickListener(v -> {
            startActivity(new Intent(this, GameActivity.class));
        });

        Button notif = findViewById(R.id.buttonNotif);
        notif.setOnClickListener(v -> {
            startActivity(new Intent(this, NotificationActivity.class));
        });
    }
}