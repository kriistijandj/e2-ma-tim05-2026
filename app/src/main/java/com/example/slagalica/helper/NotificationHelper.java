package com.example.slagalica.helper;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.slagalica.R;

public class NotificationHelper {

    public  static void showNotification(
            Context context,
            String channelId,
            int id,
            String title,
            String message) {

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true);
        android.util.Log.d("TEST_NOTIF", "Channel: " + channelId);
        NotificationManagerCompat manager =
                NotificationManagerCompat.from(context);

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            android.util.Log.e("TEST_NOTIF", "NEMA DOZVOLE");
            return;
        }
        manager.notify(id, builder.build());
    }
}