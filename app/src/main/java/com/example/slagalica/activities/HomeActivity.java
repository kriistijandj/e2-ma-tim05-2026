package com.example.slagalica.activities;

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
import com.google.android.material.navigation.NavigationView;

public class HomeActivity extends AppCompatActivity {

    private NavController navController;
    private DrawerLayout drawer;
    private AppBarConfiguration appBarConfiguration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);


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
    }

    @Override
    public boolean onSupportNavigateUp() {
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }
}