package com.example.slagalica.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.slagalica.R;
import com.example.slagalica.activities.HomeActivity;
import com.example.slagalica.helper.NotificationHelper;
import com.example.slagalica.models.NotificationType;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class ChatNotificationService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        if (!"chat".equals(remoteMessage.getData().get("type"))) return;

        String senderId = remoteMessage.getData().get("senderId");
        String sender   = remoteMessage.getData().get("sender");
        String text     = remoteMessage.getData().get("text");

        String currentUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (currentUid == null || currentUid.equals(senderId)) return; // ne obaveštavaj sebe

        // 1) Uvek upiši u istoriju notifikacija (postojeća kolekcija koju NotificationFragment sluša)
        java.util.Map<String, Object> notif = new java.util.HashMap<>();
        notif.put("title", sender);
        notif.put("message", text);
        notif.put("timestamp", System.currentTimeMillis());
        notif.put("isRead", false);
        notif.put("type", NotificationType.CHAT.name()); // "CHAT"

        String TAG = "MyFirebaseMessaging";

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(currentUid)
                .collection("notifications")
                .add(notif)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Notifikacija sačuvana!");
                    Log.d(TAG, "Document ID: " + documentReference.getId());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Greška pri čuvanju notifikacije", e);
                });

        // 2) Sistemska notifikacija SAMO ako igrač nije u aplikaciji
        boolean appInForeground = androidx.lifecycle.ProcessLifecycleOwner.get()
                .getLifecycle().getCurrentState()
                .isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED);

        if (!appInForeground) {
            NotificationHelper.showNotification(
                    getApplicationContext(),
                    HomeActivity.CHAT_CHANNEL_ID,
                    (int) System.currentTimeMillis(),
                    sender,
                    text
            );
        }
    }

    private void showChatNotification(String sender, String text) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        String channelId = "chat_channel";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, "Čet poruke", NotificationManager.IMPORTANCE_HIGH);
            manager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_profile)
                .setContentTitle(sender)
                .setContentText(text)
                .setAutoCancel(true);

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    @Override
    public void onNewToken(String token) {
        // Sačuvaj novi FCM token u Firestore
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid != null) {
            FirebaseFirestore.getInstance().collection("users")
                    .document(uid)
                    .update("fcmToken", token);
        }
    }
}