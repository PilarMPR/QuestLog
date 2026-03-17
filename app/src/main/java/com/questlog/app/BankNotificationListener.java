package com.questlog.app;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BankNotificationListener
 *
 * Listens to push notifications from bank apps and subscription services,
 * parses them and forwards structured data to the WebView via JS bridge.
 *
 * Requires: android.permission.BIND_NOTIFICATION_LISTENER_SERVICE
 * User must enable manually in: Settings → Apps → Special app access → Notification access
 */
public class BankNotificationListener extends NotificationListenerService {

    private static final String TAG = "BankNotifListener";

    // ── Callback interface for MainActivity ──────────────────────
    public interface BankEventCallback {
        void onBankTransaction(String json);
        void onSubscriptionAlert(String json);
    }

    static BankEventCallback sCallback;

    public static void setCallback(BankEventCallback cb) {
        sCallback = cb;
    }

    // ── Known bank package names ─────────────────────────────────
    private static final Set<String> BANK_PACKAGES = new HashSet<>(Arrays.asList(
        "com.caixabank.mobile.hora",       // CaixaBank
        "es.lacaixa.mobile.android.newwapicon",
        "com.imagin.app",                  // Imagin
        "es.evobanco.bancamovil",          // EVO Banco
        "com.bbva.bbvacontigo",            // BBVA
        "es.santander.app",               // Santander
        "es.bancosabadell.android",       // Sabadell
        "com.ing.banking",                // ING
        "com.bankinter.launcher",         // Bankinter
        "com.unicaja.movil",              // Unicaja
        "com.revolut.revolut",            // Revolut
        "com.n26.b2c",                    // N26
        "com.wise",                       // Wise/Transferwise
        "com.paypal.android.p2pmobile"    // PayPal
    ));

    // Friendly names for known banks
    private static final Map<String, String> BANK_NAMES = new HashMap<String, String>() {{
        put("com.caixabank.mobile.hora",               "CaixaBank");
        put("es.lacaixa.mobile.android.newwapicon",    "CaixaBank");
        put("com.imagin.app",                          "Imagin");
        put("com.bbva.bbvacontigo",                    "BBVA");
        put("es.santander.app",                       "Santander");
        put("es.bancosabadell.android",               "Sabadell");
        put("com.ing.banking",                        "ING");
        put("com.bankinter.launcher",                 "Bankinter");
        put("com.unicaja.movil",                      "Unicaja");
        put("com.revolut.revolut",                    "Revolut");
        put("com.n26.b2c",                            "N26");
        put("com.wise",                               "Wise");
        put("com.paypal.android.p2pmobile",           "PayPal");
    }};

    // ── Known subscription package names ────────────────────────
    private static final Map<String, String> SUBSCRIPTION_PACKAGES = new HashMap<String, String>() {{
        put("com.netflix.mediaclient",     "Netflix");
        put("com.spotify.music",           "Spotify");
        put("com.amazon.venezia",          "Amazon Prime");
        put("com.amazon.avod.thirdpartyclient", "Amazon Prime Video");
        put("com.google.android.play.games", "Google Play");
        put("com.android.vending",         "Google Play");
        put("com.apple.android.music",     "Apple Music");
        put("tv.twitch.android.app",       "Twitch");
        put("com.discord",                 "Discord Nitro");
        put("com.microsoft.teams",         "Microsoft 365");
        put("com.adobe.reader",            "Adobe");
        put("com.dropbox.android",         "Dropbox");
        put("com.hbo.hbonow",              "HBO Max");
        put("com.disney.disneyplus",       "Disney+");
        put("es.dazn.app",                 "DAZN");
        put("com.mojang.minecraftpe",      "Minecraft");
        put("com.xbox.mobile",             "Xbox Game Pass");
        put("com.playstation.remoteplay",  "PlayStation Plus");
    }};

    // ── Regex patterns for Spanish bank notification text ────────

    // CaixaBank / Imagin: "Pago de 34,50€ en AMAZON" / "Compra 12,00 € en MERCADONA"
    private static final Pattern PAT_CAIXA_EXPENSE = Pattern.compile(
        "(?:Pago|Compra|Cargo|Retirada|Transferencia enviada)\\s+(?:de\\s+)?([\\d]+[,.]\\d{2})\\s*€?\\s+(?:en|a)\\s+(.+)",
        Pattern.CASE_INSENSITIVE
    );

    // CaixaBank income: "Ingreso de 1.800,00€ de NOMINA"
    private static final Pattern PAT_CAIXA_INCOME = Pattern.compile(
        "(?:Ingreso|Transferencia recibida|Abono)\\s+(?:de\\s+)?([\\d.,]+)\\s*€?\\s*(?:de|recibido)?\\s*(.*)",
        Pattern.CASE_INSENSITIVE
    );

    // Generic: amount with € anywhere in text
    private static final Pattern PAT_AMOUNT_GENERIC = Pattern.compile(
        "([\\d]+[,.]\\d{2})\\s*€"
    );

    // Subscription renewal keywords
    private static final Pattern PAT_SUBSCRIPTION = Pattern.compile(
        "(?:renovación|renovado|suscripción|subscription|renewed|renewal|cobro|cargo periódico|pago mensual|pago anual)",
        Pattern.CASE_INSENSITIVE
    );

    // ── Main callback ────────────────────────────────────────────
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;

        String pkg = sbn.getPackageName();
        Notification notif = sbn.getNotification();
        if (notif == null) return;

        Bundle extras = notif.extras;
        if (extras == null) return;

        String title = extras.getString(Notification.EXTRA_TITLE, "");
        String text  = extras.getCharSequence(Notification.EXTRA_TEXT,  "").toString();
        String big   = extras.getCharSequence(Notification.EXTRA_BIG_TEXT, "").toString();
        String full  = title + " " + (big.isEmpty() ? text : big);

        Log.d(TAG, "Notif from [" + pkg + "]: " + full.trim());

        if (BANK_PACKAGES.contains(pkg)) {
            handleBankNotification(pkg, title, full.trim());
        } else if (SUBSCRIPTION_PACKAGES.containsKey(pkg)) {
            handleSubscriptionNotification(pkg, title, full.trim());
        }
    }

    // ── Bank notification handler ────────────────────────────────
    private void handleBankNotification(String pkg, String title, String fullText) {
        if (sCallback == null) return;

        String bankName = BANK_NAMES.getOrDefault(pkg, "Banco");

        // Try expense patterns first
        Matcher mExp = PAT_CAIXA_EXPENSE.matcher(fullText);
        if (mExp.find()) {
            String amountRaw = mExp.group(1).replace(".", "").replace(",", ".");
            String merchant  = mExp.group(2).trim();
            merchant = cleanMerchantName(merchant);

            try {
                JSONObject json = new JSONObject();
                json.put("type",     "expense");
                json.put("bank",     bankName);
                json.put("amount",   Double.parseDouble(amountRaw));
                json.put("merchant", merchant);
                json.put("raw",      fullText);
                json.put("pkg",      pkg);
                if (sCallback != null) sCallback.onBankTransaction(json.toString());
            } catch (Exception e) {
                Log.e(TAG, "JSON error (expense): " + e.getMessage());
            }
            return;
        }

        // Try income pattern
        Matcher mInc = PAT_CAIXA_INCOME.matcher(fullText);
        if (mInc.find()) {
            String amountRaw = mInc.group(1).replace(".", "").replace(",", ".");
            String source    = mInc.group(2).trim();

            try {
                JSONObject json = new JSONObject();
                json.put("type",   "income");
                json.put("bank",   bankName);
                json.put("amount", Double.parseDouble(amountRaw));
                json.put("source", source.isEmpty() ? "Ingreso" : source);
                json.put("raw",    fullText);
                json.put("pkg",    pkg);
                if (sCallback != null) sCallback.onBankTransaction(json.toString());
            } catch (Exception e) {
                Log.e(TAG, "JSON error (income): " + e.getMessage());
            }
            return;
        }

        // Generic fallback — extract any amount found
        Matcher mAmt = PAT_AMOUNT_GENERIC.matcher(fullText);
        if (mAmt.find()) {
            String amountRaw = mAmt.group(1).replace(".", "").replace(",", ".");
            boolean looksLikeIncome = fullText.toLowerCase().contains("ingreso")
                || fullText.toLowerCase().contains("recibida")
                || fullText.toLowerCase().contains("abono");

            try {
                JSONObject json = new JSONObject();
                json.put("type",     looksLikeIncome ? "income" : "expense");
                json.put("bank",     bankName);
                json.put("amount",   Double.parseDouble(amountRaw));
                json.put("merchant", looksLikeIncome ? "Ingreso" : "Comercio");
                json.put("raw",      fullText);
                json.put("pkg",      pkg);
                json.put("needsReview", true); // mark as uncertain parse
                if (sCallback != null) sCallback.onBankTransaction(json.toString());
            } catch (Exception e) {
                Log.e(TAG, "JSON error (generic): " + e.getMessage());
            }
        }
    }

    // ── Subscription notification handler ───────────────────────
    private void handleSubscriptionNotification(String pkg, String title, String fullText) {
        if (sCallback == null) return;

        String serviceName = SUBSCRIPTION_PACKAGES.getOrDefault(pkg, "Suscripción");

        // Only fire if it looks like a renewal/charge notification
        boolean isRenewal = PAT_SUBSCRIPTION.matcher(fullText).find()
            || PAT_AMOUNT_GENERIC.matcher(fullText).find()
            || title.toLowerCase().contains("renew")
            || title.toLowerCase().contains("renovac");

        if (!isRenewal) return;

        // Try to extract amount
        double amount = 0;
        Matcher mAmt = PAT_AMOUNT_GENERIC.matcher(fullText);
        if (mAmt.find()) {
            try {
                amount = Double.parseDouble(
                    mAmt.group(1).replace(".", "").replace(",", "."));
            } catch (Exception ignored) {}
        }

        try {
            JSONObject json = new JSONObject();
            json.put("service", serviceName);
            json.put("pkg",     pkg);
            json.put("amount",  amount);
            json.put("raw",     fullText);
            if (sCallback != null) sCallback.onSubscriptionAlert(json.toString());
        } catch (Exception e) {
            Log.e(TAG, "JSON error (subscription): " + e.getMessage());
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    /** Clean up merchant name — remove all-caps noise, trim */
    private String cleanMerchantName(String raw) {
        if (raw == null || raw.isEmpty()) return "Comercio";
        // Remove trailing punctuation and excess spaces
        raw = raw.replaceAll("[.]{2,}", "").trim();
        // Capitalize properly if all caps
        if (raw.equals(raw.toUpperCase()) && raw.length() > 3) {
            raw = raw.substring(0, 1).toUpperCase()
                + raw.substring(1).toLowerCase();
        }
        return raw.length() > 40 ? raw.substring(0, 40) : raw;
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // Not needed for our use case
    }

    // ── Static helpers for MainActivity ─────────────────────────

    /** Check if the user has granted notification listener access */
    public static boolean isPermissionGranted(Context ctx) {
        String flat = android.provider.Settings.Secure.getString(
            ctx.getContentResolver(),
            "enabled_notification_listeners"
        );
        if (flat == null || flat.isEmpty()) return false;
        ComponentName cn = new ComponentName(ctx, BankNotificationListener.class);
        return flat.contains(cn.flattenToString());
    }

    /** Open the system settings screen for notification listener access */
    public static void openPermissionSettings(Context ctx) {
        Intent intent = new Intent(
            "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"
        );
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(intent);
    }
}
