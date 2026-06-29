package com.example.slagalica.activities;

import android.Manifest;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.navigation.NavOptions;

import com.example.slagalica.R;
import com.example.slagalica.repository.FriendRepository;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;

public class HomeActivity extends AppCompatActivity {

    public static final String CHAT_CHANNEL_ID = "chat_channel";
    public static final String RANK_CHANNEL_ID = "rank_channel";
    public static final String REWARD_CHANNEL_ID = "reward_channel";
    public static final String OTHER_CHANNEL_ID = "other_channel";

    private NavController navController;
    private DrawerLayout drawer;
    private AppBarConfiguration appBarConfiguration;

    private DatabaseReference inviteListenerRef;
    private ValueEventListener inviteValueListener;
    private AlertDialog currentInviteDialog;
    private boolean isInviteDialogShowing = false;
    private final Handler inviteHandler = new Handler(Looper.getMainLooper());
    private Runnable inviteCountdownRunnable;
    private FirebaseAuth.AuthStateListener authStateListener;
    private FriendRepository friendRepo;

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
                R.id.nav_profile,
                R.id.nav_daily_missions
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
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
            }
        }

        friendRepo = new FriendRepository();
        authStateListener = auth -> {
            FirebaseUser user = auth.getCurrentUser();
            if (user != null) {
                FirebaseFirestore.getInstance().collection("users").document(user.getUid())
                        .update("online", true);
                startInviteListener(user.getUid());
            } else {
                stopInviteListener();
            }
        };
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener);
    }

    // ─── Invite listener ──────────────────────────────────────────────────────

    private void startInviteListener(String uid) {
        stopInviteListener();
        inviteListenerRef = FirebaseDatabase.getInstance().getReference("gameInvites").child(uid);
        inviteValueListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isInviteDialogShowing) {
                    if (!snapshot.exists()) {
                        runOnUiThread(() -> {
                            dismissInviteDialog();
                            Toast.makeText(HomeActivity.this, "Poziv je otkazan", Toast.LENGTH_SHORT).show();
                        });
                    }
                    return;
                }
                if (!snapshot.exists()) return;
                String status = snapshot.child("status").getValue(String.class);
                if (!"pending".equals(status)) return;

                String inviterId = snapshot.child("inviterId").getValue(String.class);
                String inviterUsername = snapshot.child("inviterUsername").getValue(String.class);
                runOnUiThread(() -> showInviteDialog(uid, inviterId, inviterUsername));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        inviteListenerRef.addValueEventListener(inviteValueListener);
    }

    private void stopInviteListener() {
        if (inviteListenerRef != null && inviteValueListener != null) {
            inviteListenerRef.removeEventListener(inviteValueListener);
        }
        inviteListenerRef = null;
        inviteValueListener = null;
    }

    private void showInviteDialog(String myUid, String inviterId, String inviterUsername) {
        if (isInviteDialogShowing) return;
        isInviteDialogShowing = true;

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Poziv na igru!")
                .setMessage(inviterUsername + " te poziva na partiju!\n\nAutomatsko odbijanje za: 10s")
                .setCancelable(false)
                .setPositiveButton("Prihvati", null)
                .setNegativeButton("Odbij", null);

        currentInviteDialog = builder.create();
        currentInviteDialog.show();

        currentInviteDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            stopCountdown();
            isInviteDialogShowing = false;
            currentInviteDialog.dismiss();
            friendRepo.acceptInvite(myUid, inviterId, new FriendRepository.MatchCreatedCallback() {
                @Override
                public void onMatchCreated(String matchId, String role) {
                    runOnUiThread(() -> {
                        Bundle args = new Bundle();
                        args.putString("MATCH_ID", matchId);
                        args.putString("PLAYER_ROLE", role);
                        navController.navigate(R.id.nav_game, args);
                    });
                }

                @Override
                public void onError(String msg) {
                    runOnUiThread(() ->
                            Toast.makeText(HomeActivity.this, "Greška: " + msg, Toast.LENGTH_SHORT).show());
                }
            });
        });

        currentInviteDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
            stopCountdown();
            isInviteDialogShowing = false;
            currentInviteDialog.dismiss();
            friendRepo.rejectInvite(myUid);
        });

        final int[] secs = {10};
        inviteCountdownRunnable = new Runnable() {
            @Override
            public void run() {
                secs[0]--;
                if (secs[0] <= 0) {
                    dismissInviteDialog();
                    friendRepo.rejectInvite(myUid);
                    return;
                }
                if (currentInviteDialog != null && currentInviteDialog.isShowing()) {
                    currentInviteDialog.setMessage(
                            inviterUsername + " te poziva na partiju!\n\nAutomatsko odbijanje za: " + secs[0] + "s");
                    inviteHandler.postDelayed(this, 1000);
                }
            }
        };
        inviteHandler.postDelayed(inviteCountdownRunnable, 1000);
    }

    private void stopCountdown() {
        if (inviteCountdownRunnable != null) {
            inviteHandler.removeCallbacks(inviteCountdownRunnable);
            inviteCountdownRunnable = null;
        }
    }

    private void dismissInviteDialog() {
        stopCountdown();
        isInviteDialogShowing = false;
        if (currentInviteDialog != null && currentInviteDialog.isShowing()) {
            currentInviteDialog.dismiss();
        }
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    protected void onDestroy() {
        super.onDestroy();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            FirebaseFirestore.getInstance().collection("users").document(user.getUid())
                    .update("online", false);
        }
        stopInviteListener();
        stopCountdown();
        if (authStateListener != null) {
            FirebaseAuth.getInstance().removeAuthStateListener(authStateListener);
        }
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel chat = new NotificationChannel(
                    CHAT_CHANNEL_ID, "Chat notifications", NotificationManager.IMPORTANCE_HIGH);
            NotificationChannel rank = new NotificationChannel(
                    RANK_CHANNEL_ID, "Ranking notifications", NotificationManager.IMPORTANCE_DEFAULT);
            NotificationChannel reward = new NotificationChannel(
                    REWARD_CHANNEL_ID, "Reward notifications", NotificationManager.IMPORTANCE_HIGH);
            NotificationChannel other = new NotificationChannel(
                    OTHER_CHANNEL_ID, "Other notifications", NotificationManager.IMPORTANCE_DEFAULT);

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(chat);
            manager.createNotificationChannel(rank);
            manager.createNotificationChannel(reward);
            manager.createNotificationChannel(other);

            android.util.Log.d("FIREBASE_NOTIF", "Kanali su uspešno registrovani u HomeActivity!");
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }
}
