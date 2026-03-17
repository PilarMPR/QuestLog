package com.questlog.app;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import java.util.Calendar;

public class NotificationReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String action = intent.getAction();

        // Re-schedule alarms after reboot
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            rescheduleOnBoot(ctx);
            return;
        }

        if (!"com.questlog.NOTIFY".equals(action)) return;

        int    notifId = intent.getIntExtra("notif_id", 1);
        String title   = intent.getStringExtra("title");
        String body    = intent.getStringExtra("body");
        if (title == null) title = "⚔️ QuestLog";
        if (body  == null) body  = "Tienes misiones pendientes.";

        // Build and send notification
        NotificationCompat.Builder builder =
            new NotificationCompat.Builder(ctx, MainActivity.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setVibrate(new long[]{0, 250, 100, 250});

        // Tap notification → open app
        Intent openApp = new Intent(ctx, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(
            ctx, notifId, openApp,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        builder.setContentIntent(pi);

        NotificationManager nm =
            (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(notifId, builder.build());

        // If it was an exact daily alarm (not repeating), re-schedule for tomorrow
        boolean repeating = intent.getBooleanExtra("repeating", false);
        if (!repeating && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            rescheduleDaily(ctx, intent, notifId);
        }
    }

    // Re-schedule a daily exact alarm for tomorrow (needed for Android M+ exact alarms)
    private void rescheduleDaily(Context ctx, Intent intent, int notifId) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);

        // Recover time from saved prefs or just add 24h
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, 1);

        Intent newIntent = new Intent(ctx, NotificationReceiver.class);
        newIntent.setAction("com.questlog.NOTIFY");
        newIntent.putExtras(intent);

        PendingIntent pi = PendingIntent.getBroadcast(
            ctx, notifId, newIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
        }
    }

    // After device reboot, re-schedule default notifications
    private void rescheduleOnBoot(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);

        String[][] defaults = {
            {"morning", "⚔️ QuestLog", "¡Tus misiones del día te esperan!", "8",  "0"},
            {"midday",  "🗡 QuestLog",  "¿Cómo va el progreso?",             "13", "0"},
            {"evening", "🌙 QuestLog",  "Hora de revisar el día.",            "21", "0"},
        };

        for (String[] n : defaults) {
            Intent intent = new Intent(ctx, NotificationReceiver.class);
            intent.setAction("com.questlog.NOTIFY");
            intent.putExtra("notif_id", n[0].hashCode());
            intent.putExtra("title",    n[1]);
            intent.putExtra("body",     n[2]);
            intent.putExtra("id_str",   n[0]);

            PendingIntent pi = PendingIntent.getBroadcast(
                ctx, n[0].hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(n[3]));
            cal.set(Calendar.MINUTE,      Integer.parseInt(n[4]));
            cal.set(Calendar.SECOND, 0);
            if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
                cal.add(Calendar.DAY_OF_YEAR, 1);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
            } else {
                am.setRepeating(
                    AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(),
                    AlarmManager.INTERVAL_DAY, pi);
            }
        }
    }
}
