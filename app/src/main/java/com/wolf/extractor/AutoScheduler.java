package com.wolf.extractor;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class AutoScheduler {

    private static final String TAG = "WOLF-AUTO";
    private static final int REQUEST_CODE = 1001;

    // ★★★ اختبار: 5 دقائق — بعد النجاح غيّرها إلى 12 ساعة ★★★
    private static final long INTERVAL_MS = 12 * 60 * 60 * 1000L;
    // private static final long INTERVAL_MS = 12 * 60 * 60 * 1000L;

    public static void schedule(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            Log.e(TAG, "❌ AlarmManager null");
            return;
        }

        Intent intent = new Intent(context, AutoReceiver.class);
        intent.setAction("com.wolf.extractor.AUTO_TRIGGER");

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pi = PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags);
        long triggerAt = System.currentTimeMillis() + INTERVAL_MS;
        long mins = INTERVAL_MS / 60000;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
                Log.d(TAG, "✅ Alarm scheduled in " + mins + " min");
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi);
                Log.d(TAG, "✅ Alarm scheduled in " + mins + " min");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "❌ Permission denied", e);
            am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        }
    }

    public static void cancel(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, AutoReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pi = PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags);
        am.cancel(pi);
    }
                      }
