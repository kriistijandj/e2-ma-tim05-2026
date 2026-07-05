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
import com.example.slagalica.helper.DateHelper;
import com.example.slagalica.helper.NotificationHelper;
import com.example.slagalica.repository.FriendRepository;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Calendar;

public class HomeActivity extends AppCompatActivity {

    public static final String CHAT_CHANNEL_ID = "chat_channel";
    public static final String RANK_CHANNEL_ID = "rank_channel";
    public static final String REWARD_CHANNEL_ID = "reward_channel";
    public static final String OTHER_CHANNEL_ID = "other_channel";

    public static final String INVITE_CHANNEL_ID = "invite_channel";
    private boolean isCheckingRewards = false;
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

    private ListenerRegistration notificationsListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        createNotificationChannels();
        startNotificationsListener();
        checkPendingRewards();
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
                R.id.nav_daily_missions,
                R.id.nav_chat
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

    private void checkPendingRewards() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String lastWeek = getPreviousWeeklyCycleId(); // Metoda ispod

        db.collection("users").document(user.getUid()).get().addOnSuccessListener(userDoc -> {
            String lastClaimed = userDoc.getString("lastClaimedWeeklyCycle");

            if (lastClaimed == null || !lastClaimed.equals(lastWeek)) {
                // IGRAČ JE ZASLUŽIO NAGRADU ZA PROŠLI CIKLUS
                calculateAndAwardTokens(user.getUid(), lastWeek);
            }
        });
    }

    private void calculateAndAwardTokens(String uid, String cycleId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // 1. Dobijamo broj zvezda našeg igrača iz tog ciklusa
        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            long myStars = doc.getLong("weeklyStars") != null ? doc.getLong("weeklyStars") : 0;

            // 2. Koliko igrača ima više zvezda od mene? (To je moja pozicija)
            db.collection("users")
                    .whereEqualTo("lastWeeklyCycle", cycleId)
                    .whereGreaterThan("weeklyStars", myStars)
                    .get()
                    .addOnSuccessListener(querySnap -> {
                        int rank = querySnap.size() + 1; // +1 jer smo mi na poziciji posle njih
                        int tokens = calculateTokensFromRank(rank);

                        if (tokens > 0) {
                            applyReward(uid, tokens, cycleId);
                        } else {
                            // Ako nije u top 10, samo obeleži da je preuzeto
                            db.collection("users").document(uid).update("lastClaimedWeeklyCycle", cycleId);
                        }
                    });
        });
    }

    private int calculateTokensFromRank(int rank) {
        if (rank == 1) return 5;
        if (rank == 2) return 3;
        if (rank == 3) return 2;
        if (rank >= 4 && rank <= 10) return 1;
        return 0;
    }
    private String getPreviousWeeklyCycleId() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.WEEK_OF_YEAR, -1); // Vrati se jednu nedelju nazad
        return cal.get(Calendar.YEAR) + "_W" + cal.get(Calendar.WEEK_OF_YEAR);
    }
    private void applyReward(String uid, int tokens, String cycleId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.runTransaction(transaction -> {
            DocumentReference userRef = db.collection("users").document(uid);

            // Dodaj tokene
            transaction.update(userRef, "tokens", com.google.firebase.firestore.FieldValue.increment(tokens));
            // Obeleži ciklus kao preuzet
            transaction.update(userRef, "lastClaimedWeeklyCycle", cycleId);

            return null;
        }).addOnSuccessListener(aVoid -> {
            showRewardDialog(String.valueOf(tokens)); // Prikazujemo animaciju/dialog
        });
    }
    private void showRewardDialog(String cycleId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Nagrada za rang listu!");
        builder.setMessage("Završio se nedeljni ciklus. Proveravamo tvoju poziciju...");
        builder.setCancelable(false);
        builder.setPositiveButton("OK", (d, w) -> {
            // Označi kao preuzeto da ne iskače ponovo
            FirebaseFirestore.getInstance().collection("users")
                    .document(FirebaseAuth.getInstance().getCurrentUser().getUid())
                    .update("lastClaimedWeeklyCycle", cycleId);
            d.dismiss();
        });
        builder.show();
    }

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
        if (notificationsListener != null) notificationsListener.remove();
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

    @Override
    protected void onStop() {
        super.onStop();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(user.getUid())
                    .update("online", false);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(user.getUid())
                    .update("online", true);
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
            NotificationChannel invite = new NotificationChannel(
                    INVITE_CHANNEL_ID, "Game invites", NotificationManager.IMPORTANCE_HIGH);

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(chat);
            manager.createNotificationChannel(rank);
            manager.createNotificationChannel(reward);
            manager.createNotificationChannel(other);
            manager.createNotificationChannel(invite);

            android.util.Log.d("FIREBASE_NOTIF", "Kanali su uspešno registrovani u HomeActivity!");
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }

    private void startNotificationsListener() {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) return;

        // Pamtimo kad smo počeli da slušamo, da ne bismo pokazivali stare notifikacije
        long startTime = System.currentTimeMillis();

        notificationsListener = FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("notifications")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;

                    for (com.google.firebase.firestore.DocumentChange dc : snapshots.getDocumentChanges()) {
                        // Samo novi dokumenti (ne postojeći pri prvom učitavanju)
                        if (dc.getType() != com.google.firebase.firestore.DocumentChange.Type.ADDED) continue;

                        com.example.slagalica.models.NotificationModel notif =
                                dc.getDocument().toObject(com.example.slagalica.models.NotificationModel.class);

                        if (notif == null) continue;
                        if (notif.getTimestamp() < startTime) continue; // preskoci stare
                        if (notif.getIsRead()) continue; // preskoci vec procitane

                        // Odaberi kanal na osnovu tipa
                        String channelId;
                        if ("CHAT".equals(notif.getType())) channelId = CHAT_CHANNEL_ID;
                        else if ("RANK".equals(notif.getType())) channelId = RANK_CHANNEL_ID;
                        else if ("INVITE".equals(notif.getType())) channelId = INVITE_CHANNEL_ID;
                        else if ("REWARD".equals(notif.getType()) || "REWARDS".equals(notif.getType())) channelId = REWARD_CHANNEL_ID;
                        else channelId = OTHER_CHANNEL_ID;

                        NotificationHelper.showNotification(
                                getApplicationContext(),
                                channelId,
                                (int) System.currentTimeMillis(),
                                notif.getTitle(),
                                notif.getMessage()
                        );
                    }
                });
    }
}
