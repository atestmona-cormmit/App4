package com.wolf.extractor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class AutoReceiver extends BroadcastReceiver {

    private static final String TAG = "WOLF-AUTO";
    private static final String CHANNEL_ID = "wolf_auto";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "🔔 ⏰ ALARM FIRED!");

        try {
            notifyUser(context, "⏰ تشغيل تلقائي", "جاري فتح WOLF للرفع...");
        } catch (Exception e) {
            Log.e(TAG, "notify failed", e);
        }

        Intent activityIntent = new Intent(context, MainActivity.class);
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activityIntent.putExtra("auto_mode", true);

        try {
            context.startActivity(activityIntent);
            Log.d(TAG, "✅ MainActivity started with auto_mode");
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to start activity", e);
            notifyUser(context, "❌ فشل التشغيل", String.valueOf(e.getMessage()));
        }

        AutoScheduler.schedule(context);
    }

    private void notifyUser(Context ctx, String title, String text) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "WOLF Auto", NotificationManager.IMPORTANCE_HIGH);
            ch.enableVibration(true);
            nm.createNotificationChannel(ch);
        }

        Intent mainIntent = new Intent(ctx, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, mainIntent, flags);

        Notification n = new NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build();

        nm.notify((int) (System.currentTimeMillis() % 100000), n);
    }
          }
