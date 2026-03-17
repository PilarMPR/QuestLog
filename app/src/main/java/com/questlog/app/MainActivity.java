package com.questlog.app;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.Calendar;

public class MainActivity extends AppCompatActivity {

    public static final String CHANNEL_ID     = "questlog_channel";
    public static final String CHANNEL_NAME   = "QuestLog";
    private static final int   NOTIF_PERM_REQ = 1001;

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        createNotificationChannel();
        requestNotificationPermission();

        webView = findViewById(R.id.webview);
        setupWebView();
        webView.loadUrl("file:///android_asset/index.html");

        // Register callback: when bank notifications arrive → push to WebView JS
        BankNotificationListener.setCallback(new BankNotificationListener.BankEventCallback() {
            @Override
            public void onBankTransaction(final String json) {
                runOnUiThread(() -> {
                    if (webView != null) {
                        webView.evaluateJavascript(
                            "if(typeof onBankTransaction==='function') onBankTransaction(" + json + ");",
                            null
                        );
                    }
                });
            }

            @Override
            public void onSubscriptionAlert(final String json) {
                runOnUiThread(() -> {
                    if (webView != null) {
                        webView.evaluateJavascript(
                            "if(typeof onSubscriptionAlert==='function') onSubscriptionAlert(" + json + ");",
                            null
                        );
                    }
                });
            }
        });
    }

    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setDatabaseEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setTextZoom(100);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new JsBridge(this), "Android");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT
            );
            ch.setDescription("Recordatorios de QuestLog");
            ch.enableVibration(true);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIF_PERM_REQ);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    // ══════════════════════════════════════════════════════════
    //  JavaScript Bridge
    // ══════════════════════════════════════════════════════════
    public class JsBridge {
        private final Context ctx;
        JsBridge(Context c) { ctx = c; }

        @JavascriptInterface
        public void scheduleDaily(String id, String title, String body, int hour, int minute) {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(ctx, NotificationReceiver.class);
            intent.setAction("com.questlog.NOTIFY");
            intent.putExtra("notif_id", id.hashCode());
            intent.putExtra("title", title);
            intent.putExtra("body", body);
            intent.putExtra("id_str", id);
            PendingIntent pi = PendingIntent.getBroadcast(ctx, id.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, hour);
            cal.set(Calendar.MINUTE, minute);
            cal.set(Calendar.SECOND, 0);
            if (cal.getTimeInMillis() <= System.currentTimeMillis())
                cal.add(Calendar.DAY_OF_YEAR, 1);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
            else
                am.setRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pi);
        }

        @JavascriptInterface
        public void scheduleRepeating(String id, String title, String body, long intervalMs) {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(ctx, NotificationReceiver.class);
            intent.setAction("com.questlog.NOTIFY");
            intent.putExtra("notif_id", id.hashCode());
            intent.putExtra("title", title);
            intent.putExtra("body", body);
            intent.putExtra("id_str", id);
            intent.putExtra("interval_ms", intervalMs);
            intent.putExtra("repeating", true);
            PendingIntent pi = PendingIntent.getBroadcast(ctx, id.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.setRepeating(AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + intervalMs, intervalMs, pi);
        }

        @JavascriptInterface
        public void cancelNotification(String id) {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(ctx, NotificationReceiver.class);
            intent.setAction("com.questlog.NOTIFY");
            PendingIntent pi = PendingIntent.getBroadcast(ctx, id.hashCode(), intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (pi != null) { am.cancel(pi); pi.cancel(); }
            ((NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE))
                .cancel(id.hashCode());
        }

        @JavascriptInterface
        public void sendTestNotification() {
            Intent intent = new Intent(ctx, NotificationReceiver.class);
            intent.setAction("com.questlog.NOTIFY");
            intent.putExtra("notif_id", 9999);
            intent.putExtra("title", "⚔️ QuestLog — Test");
            intent.putExtra("body", "Las notificaciones funcionan correctamente.");
            ctx.sendBroadcast(intent);
        }

        @JavascriptInterface
        public String getDeviceInfo() {
            return Build.MANUFACTURER + " " + Build.MODEL + " (Android " + Build.VERSION.RELEASE + ")";
        }

        @JavascriptInterface
        public boolean isAndroid() { return true; }

        // ── Bank notification bridge ──────────────────────────

        /** True if the user granted notification listener access */
        @JavascriptInterface
        public boolean isBankListenerEnabled() {
            return BankNotificationListener.isPermissionGranted(ctx);
        }

        /** Opens system settings screen to grant notification listener access */
        @JavascriptInterface
        public void requestBankListenerPermission() {
            // JavascriptInterface runs on background thread — must switch to UI thread
            // before calling startActivity, otherwise it silently fails on many devices.
            runOnUiThread(() -> BankNotificationListener.openPermissionSettings(ctx));
        }

        /** Simulate a bank transaction for dev mode testing */
        @JavascriptInterface
        public void simulateBankTransaction(String json) {
            if (BankNotificationListener.sCallback != null)
                BankNotificationListener.sCallback.onBankTransaction(json);
        }

        /**
         * Schedule a one-time notification at an exact epoch timestamp (ms).
         * Used for subscription renewal alerts.
         */
        @JavascriptInterface
        public void scheduleOnce(String id, String title, String body, long epochMs) {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(ctx, NotificationReceiver.class);
            intent.setAction("com.questlog.NOTIFY");
            intent.putExtra("notif_id", id.hashCode());
            intent.putExtra("title", title);
            intent.putExtra("body", body);
            intent.putExtra("id_str", id);
            PendingIntent pi = PendingIntent.getBroadcast(ctx, id.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMs, pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, epochMs, pi);
            }
        }

        /** Simulate a subscription alert for dev mode testing */
        @JavascriptInterface
        public void simulateSubscriptionAlert(String json) {
            if (BankNotificationListener.sCallback != null)
                BankNotificationListener.sCallback.onSubscriptionAlert(json);
        }
    }
}
