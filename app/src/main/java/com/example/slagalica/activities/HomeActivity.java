package com.example.slagalica.activities;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.navigation.NavOptions;

import com.example.slagalica.R;
import com.example.slagalica.helper.NotificationHelper;
import com.example.slagalica.models.NotificationType;
import com.google.android.material.navigation.NavigationView;

public class HomeActivity extends AppCompatActivity {

    public static final String CHAT_CHANNEL_ID = "chat_channel";
    public static final String RANK_CHANNEL_ID = "rank_channel";
    public static final String REWARD_CHANNEL_ID = "reward_channel";
    public static final String OTHER_CHANNEL_ID = "other_channel";
    private NavController navController;
    private DrawerLayout drawer;
    private AppBarConfiguration appBarConfiguration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        createNotificationChannels();

        drawer = findViewById(R.id.drawer_layout);
        NavigationView navView = findViewById(R.id.nav_view);
        Toolbar toolbar = findViewById(R.id.toolbar);

        setSupportActionBar(toolbar);


        NavHostFragment navHostFragment =
                (NavHostFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.fragment_nav_content_main);

        navController = navHostFragment.getNavController();

        appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_home,
                R.id.nav_notifications,
                R.id.nav_profile
        ).setOpenableLayout(drawer).build();


        NavigationUI.setupWithNavController(navView, navController);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);


        navView.setNavigationItemSelectedListener(item -> {

            if (item.getItemId() == R.id.nav_logout) {


                navController.navigate(
                        R.id.nav_login,
                        null,
                        new NavOptions.Builder()
                                .setPopUpTo(R.id.nav_home, true)
                                .build()
                );

                drawer.closeDrawers();
                return true;
            }


            boolean handled = NavigationUI.onNavDestinationSelected(item, navController);
            drawer.closeDrawers();
            return handled;
        });

        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {

            if (destination.getId() == R.id.nav_login ||
                    destination.getId() == R.id.nav_signup) {

                getSupportActionBar().hide();

            } else {

                getSupportActionBar().show();
            }
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        100
                );
            }
        }
    }
    private void createNotificationChannels() {

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){

            NotificationChannel chat =
                    new NotificationChannel(
                            CHAT_CHANNEL_ID,
                            "Chat notifications",
                            NotificationManager.IMPORTANCE_HIGH);

            NotificationChannel rank =
                    new NotificationChannel(
                            RANK_CHANNEL_ID,
                            "Ranking notifications",
                            NotificationManager.IMPORTANCE_DEFAULT);

            NotificationChannel reward =
                    new NotificationChannel(
                            REWARD_CHANNEL_ID,
                            "Reward notifications",
                            NotificationManager.IMPORTANCE_HIGH);

            NotificationChannel other =
                    new NotificationChannel(
                            OTHER_CHANNEL_ID,
                            "Other notifications",
                            NotificationManager.IMPORTANCE_DEFAULT);

            NotificationManager manager =
                    getSystemService(NotificationManager.class);

            manager.createNotificationChannel(chat);
            manager.createNotificationChannel(rank);
            manager.createNotificationChannel(reward);
            manager.createNotificationChannel(other);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }
}