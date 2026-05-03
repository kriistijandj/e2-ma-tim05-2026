package com.example.slagalica.activities;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.slagalica.R;
import com.example.slagalica.games.KorakPoKorakFragment;

public class GameActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        // pokreni prvu igru (fragment)
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.gameContainer, new KorakPoKorakFragment())
                .commit();
    }
}