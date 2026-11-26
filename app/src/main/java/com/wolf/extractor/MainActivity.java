package com.wolf.extractor;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "WOLF-EXTRACTOR";
    private static final String WOLF_URL = "https://app.wolf.live/mna";

    private static final String WOLF_EMAIL = "mm";
    private static final String WOLF_PASSWORD = "mm";

    private WebView webView;
    private TextView tvStatus;
    private Handler handler = new Handler(Looper.getMainLooper());

    private String v3APIToken = null;
    private String appCheckToken = null;
    private String deviceToken = null;

    private boolean autoMode = false;
    private boolean uploadStarted = false;
    private boolean loginAttempted = false;
    private int checkAttempts = 0;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AutoScheduler.schedule(this);

        requestOverlayPermissionIfNeeded();
        requestNotificationPermissionIfNeeded();
        requestBatteryOptimizationExemption();
        requestExactAlarmPermission();
        requestStoragePermission(); // طلب صلاحية الملفات

        Intent intent = getIntent();
        autoMode = intent != null && intent.getBooleanExtra("auto_mode", false);
        Log.d(TAG, "onCreate — autoMode = " + autoMode);

        webView = findViewById(R.id.webview);
        tvStatus = findViewById(R.id.tvStatus);
        Button btnExtract = findViewById(R.id.btnExtract);
        Button btnUpload = findViewById(R.id.btnUpload);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUserAgentString(
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        );

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                setStatus("تم التحميل: " + url);
                handler.postDelayed(MainActivity.this::checkAndProceed, 5000);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage cm) {
                Log.d(TAG, "JS: " + cm.message());
                return true;
            }
        });

        webView.addJavascriptInterface(new TokenBridge(), "WolfBridge");

        btnExtract.setOnClickListener(v -> {
            checkAttempts = 0;
            checkAndProceed();
        });
        btnUpload.setOnClickListener(v -> saveTokensLocally());

        setStatus(autoMode ? "🔄 وضع تلقائي — جاري الفتح..." : "جاري فتح WOLF...");
        webView.loadUrl(WOLF_URL);

        if (autoMode) {
            handler.postDelayed(() -> {
                Log.d(TAG, "Auto timeout — closing");
                finish();
            }, 120_000);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        boolean newAutoMode = intent != null && intent.getBooleanExtra("auto_mode", false);
        if (newAutoMode) {
            autoMode = true;
            uploadStarted = false;
            loginAttempted = false;
            checkAttempts = 0;
            v3APIToken = null;
            appCheckToken = null;
            deviceToken = null;

            setStatus("🔄 وضع تلقائي — إعادة الفحص...");
            webView.reload();
        }
    }

    private class TokenBridge {
        @JavascriptInterface
        public void sendTokens(String v3, String appCheck, String device) {
            v3APIToken = v3;
            appCheckToken = appCheck;
            deviceToken = device;

            handler.post(() -> {
                setStatus(
                    "v3: " + (v3 != null ? "✅" : "❌") +
                    " | appCheck: " + (appCheck != null ? "✅" : "❌") +
                    " | device: " + (device != null ? "✅" : "❌")
                );

                if (!uploadStarted && v3 != null && appCheck != null) {
                    uploadStarted = true;
                    handler.postDelayed(MainActivity.this::saveTokensLocally, 1500);
                }
            });
        }

        @JavascriptInterface
        public void log(String message) {
            Log.d(TAG, "JS-LOG: " + message);
        }
    }

    private void checkAndProceed() {
        checkAttempts++;

        String js = "(function() {" +
            "try {" +
            "  var v3 = localStorage.getItem('v3APIToken');" +
            "  var appCheck = localStorage.getItem('appCheckToken');" +
            "  var device = localStorage.getItem('deviceToken');" +
            "  WolfBridge.sendTokens(v3, appCheck, device);" +
            "} catch (e) {" +
            "  WolfBridge.log('Error: ' + e.message);" +
            "}" +
            "})();";

        webView.evaluateJavascript(js, value -> Log.d(TAG, "Check: " + value));

        if (checkAttempts >= 2 && v3APIToken == null && !loginAttempted) {
            loginAttempted = true;
            performAutoLogin();
        }
    }

    private void performAutoLogin() {
        setStatus("🔐 محاولة تسجيل دخول...");
        String js = "(function() {" +
            "try {" +
            "  var legacy = document.querySelector('#legacy-link');" +
            "  if (legacy) { legacy.click(); return 'legacy-clicked'; }" +
            "  var banner = document.querySelector('div.MuiAlert-message');" +
            "  if (banner) { banner.click(); return 'banner-clicked'; }" +
            "  return 'nothing';" +
            "} catch (e) { return 'error'; }" +
            "})();";
        webView.evaluateJavascript(js, v -> handler.postDelayed(this::fillCredentials, 3000));
    }

    private void fillCredentials() {
        setStatus("✍️ كتابة البيانات...");
        String js = "(function() {" +
            "try {" +
            "  var email = document.querySelector('input[type=\"email\"]');" +
            "  var pass = document.querySelector('input[type=\"password\"]');" +
            "  if (!email || !pass) return 'fields-missing';" +
            "  var setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;" +
            "  setter.call(email, '" + WOLF_EMAIL + "');" +
            "  email.dispatchEvent(new Event('input', { bubbles: true }));" +
            "  email.dispatchEvent(new Event('change', { bubbles: true }));" +
            "  setter.call(pass, '" + WOLF_PASSWORD + "');" +
            "  pass.dispatchEvent(new Event('input', { bubbles: true }));" +
            "  pass.dispatchEvent(new Event('change', { bubbles: true }));" +
            "  return 'filled';" +
            "} catch (e) { return 'error'; }" +
            "})();";
        webView.evaluateJavascript(js, v -> handler.postDelayed(this::clickSubmit, 2000));
    }

    private void clickSubmit() {
        setStatus("🖱️ النقر على زر الدخول...");
        String js = "(function() {" +
            "try {" +
            "  var btn = document.querySelector('button[aria-label=\"تسجيل الدخول\"]')" +
            "         || document.querySelector('button[type=\"submit\"]');" +
            "  if (btn) { btn.click(); return 'clicked'; }" +
            "  return 'not-found';" +
            "} catch (e) { return 'error'; }" +
            "})();";
        webView.evaluateJavascript(js, v -> handler.postDelayed(() -> {
            checkAttempts = 0;
            checkAndProceed();
        }, 8000));
    }

    // ============================================================
    // الحفظ المحلي بدلاً من GitHub
    // ============================================================
    private void saveTokensLocally() {
        if (v3APIToken == null || appCheckToken == null) {
            Toast.makeText(this, "الرموز غير جاهزة", Toast.LENGTH_SHORT).show();
            if (autoMode) handler.postDelayed(this::finish, 3000);
            return;
        }

        setStatus("💾 جاري الحفظ...");

        new Thread(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("v3APIToken", v3APIToken);
                json.put("appCheckToken", appCheckToken);
                json.put("deviceToken", deviceToken != null ? deviceToken : "");
                json.put("updatedAt", new java.util.Date().toString());

                // الحفظ في مجلد Download ليتمكن Termux من قراءته
                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File file = new File(downloadsDir, "wolftokens.json");
                
                FileWriter writer = new FileWriter(file);
                writer.write(json.toString(2));
                writer.close();

                handler.post(() -> {
                    setStatus("✅ تم الحفظ في: Download/wolf_tokens.json");
                    if (autoMode) handler.postDelayed(this::finish, 2000);
                });
            } catch (Exception e) {
                Log.e(TAG, "Save error", e);
                handler.post(() -> {
                    setStatus("❌ فشل الحفظ: " + e.getMessage());
                    if (autoMode) handler.postDelayed(this::finish, 3000);
                });
            }
        }).start();
    }

    private void setStatus(String text) {
        handler.post(() -> tvStatus.setText(text));
    }

    // ============================================================
    // الأذونات
    // ============================================================
    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }
    }

    private void requestOverlayPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        if (!Settings.canDrawOverlays(this)) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) { Log.e(TAG, "overlay perm", e); }
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 100);
            }
        }
    }

    private void requestBatteryOptimizationExemption() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
                if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                }
            }
        } catch (Exception e) { Log.e(TAG, "battery perm", e); }
    }

    private void requestExactAlarmPermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
                if (am != null && !am.canScheduleExactAlarms()) {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                }
            }
        } catch (Exception e) { Log.e(TAG, "exact alarm", e); }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack() && !autoMode) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
