package com.android.cts.jtech;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.theonionsarewatching.yapchik.Yapchik;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "JtechPrefs";
    private static final String PREF_SCREEN_SIZE = "screen_size";
    private static final String PREF_FIRST_LAUNCH = "first_launch";
    private static final String BASE_URL = "https://forums.jtechforums.org/dumb";
    private static final int NOTIFICATION_PERMISSION_CODE = 1001;
    private static final int FILE_CHOOSER_CODE = 1002;
    private static final int STORAGE_PERMISSION_CODE = 1003;

    private WebView webView;
    private ValueCallback<Uri[]> fileChooserCallback;
    private boolean useFullscreen = true; // Default to fullscreen
    private String pendingDownloadUrl;
    private String pendingDownloadContentDisposition;
    private String pendingDownloadMimetype;
    private String softkeysScript;
    private final Yapchik.StateListener softkeyStateListener = active -> syncNativeSoftkeys();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Check preference and set theme BEFORE super.onCreate()
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isFirstLaunch = prefs.getBoolean(PREF_FIRST_LAUNCH, true);

        if (!isFirstLaunch) {
            // Load saved preference and set theme
            String screenSize = prefs.getString(PREF_SCREEN_SIZE, "small");
            useFullscreen = screenSize.equals("small");
            setTheme(useFullscreen ? R.style.AppTheme : R.style.AppTheme_Normal);
        } else {
            // First launch - use normal theme for dialog
            setTheme(R.style.AppTheme_Normal);
        }

        super.onCreate(savedInstanceState);

        // Request notification permission (Android 13+)
        requestNotificationPermission();

        if (isFirstLaunch) {
            // First launch - show screen size selection dialog
            showScreenSizeDialog(prefs);
        } else {
            // Not first launch - setup UI normally
            setupUI();
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_CODE) {
            // Permission granted or denied - service will work either way on older Android
            // On Android 13+ without permission, notifications won't show but service still runs
        } else if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (pendingDownloadUrl != null) {
                    startDownload(pendingDownloadUrl, pendingDownloadContentDisposition, pendingDownloadMimetype);
                }
            } else {
                JtechSoftkeys.message(this, "Storage permission required for downloads");
            }
            pendingDownloadUrl = null;
            pendingDownloadContentDisposition = null;
            pendingDownloadMimetype = null;
        }
    }

    private void showScreenSizeDialog(SharedPreferences prefs) {
        // Create custom layout with buttons
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(30, 30, 30, 30);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // Convert dp to pixels for button size
        int widthPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 200, getResources().getDisplayMetrics());
        int heightPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 45, getResources().getDisplayMetrics());

        // Small Screen button
        Button smallButton = new Button(this);
        smallButton.setText("Small Screen (no notch)");
        smallButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        smallButton.setTextColor(0xFFFFFFFF); // White text
        smallButton.setFocusable(true);
        smallButton.setFocusableInTouchMode(true);
        smallButton.setBackgroundResource(R.drawable.button_selector);
        LinearLayout.LayoutParams smallButtonParams = new LinearLayout.LayoutParams(widthPx, heightPx);
        smallButtonParams.setMargins(0, 0, 0, 15);
        smallButton.setLayoutParams(smallButtonParams);
        smallButton.setOnClickListener(v -> {
            prefs.edit()
                .putString(PREF_SCREEN_SIZE, "small")
                .putBoolean(PREF_FIRST_LAUNCH, false)
                .apply();
            recreate();
        });

        // Normal Screen button
        Button normalButton = new Button(this);
        normalButton.setText("Normal Screen (has notch)");
        normalButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        normalButton.setTextColor(0xFFFFFFFF); // White text
        normalButton.setFocusable(true);
        normalButton.setFocusableInTouchMode(true);
        normalButton.setBackgroundResource(R.drawable.button_selector);
        LinearLayout.LayoutParams normalButtonParams = new LinearLayout.LayoutParams(widthPx, heightPx);
        normalButton.setLayoutParams(normalButtonParams);
        normalButton.setOnClickListener(v -> {
            prefs.edit()
                .putString(PREF_SCREEN_SIZE, "normal")
                .putBoolean(PREF_FIRST_LAUNCH, false)
                .apply();
            recreate();
        });

        layout.addView(smallButton);
        layout.addView(normalButton);

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.CompactDialog)
            .setTitle("Screen Type")
            .setView(layout)
            .setCancelable(false)
            .create();

        dialog.show();

        // Force focus on first button to show outline immediately
        smallButton.setFocusableInTouchMode(true);
        smallButton.requestFocusFromTouch();
    }

    private void setupUI() {
        if (!useFullscreen) {
            // Normal mode with action bar
            // Clear any fullscreen flags
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

            // Hide title text and icon in action bar
            if (getActionBar() != null) {
                getActionBar().setDisplayShowTitleEnabled(false);
                getActionBar().setDisplayShowHomeEnabled(false);
            }

            // Create container with fitsSystemWindows to position below action bar
            FrameLayout container = new FrameLayout(this);
            container.setFitsSystemWindows(true);
            container.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ));

            webView = new WebView(this);
            webView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ));

            container.addView(webView);
            setContentView(softkeyRoot(container));
        } else {
            // Fullscreen mode
            webView = new WebView(this);
            setContentView(softkeyRoot(webView));
            hideSystemUI();
        }

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);

        // Add JavaScript interface for push notifications
        webView.addJavascriptInterface(new PushInterface(), "PushBridge");

        // Hardware soft keys: the page's soft-key bar is mirrored into the native one.
        webView.addJavascriptInterface(new SoftkeyInterface(), "SoftkeyBridge");
        JtechSoftkeys.addStateListener(softkeyStateListener);

        webView.setWebViewClient(new WebViewClient() {
            private final List<String> allowedDomains = Arrays.asList(
                "jtechforums.org",
                "forums.jtechforums.org",
                "drive.usercontent.google.com",
                "drive.google.com",
                "dropbox.com",
                "github.com",
                "release-assets.githubusercontent.com"
            );

            private boolean isAllowed(String url) {
                String host = Uri.parse(url).getHost();
                if (host == null) return false;
                host = host.toLowerCase();
                for (String domain : allowedDomains) {
                    if (host.equals(domain) || host.equals("www." + domain)
                            || host.endsWith("." + domain)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (isAllowed(url)) {
                    return false;
                }
                JtechSoftkeys.message(MainActivity.this, "Not allowed");
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (isForumApp(url)) {
                    injectSoftkeys();
                } else {
                    JtechSoftkeys.clear(MainActivity.this);
                }
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                    FileChooserParams params) {
                if (fileChooserCallback != null) {
                    fileChooserCallback.onReceiveValue(null);
                }
                fileChooserCallback = callback;
                Intent intent = params.createIntent();
                startActivityForResult(intent, FILE_CHOOSER_CODE);
                return true;
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                    && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                pendingDownloadUrl = url;
                pendingDownloadContentDisposition = contentDisposition;
                pendingDownloadMimetype = mimetype;
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_CODE);
                return;
            }
            startDownload(url, contentDisposition, mimetype);
        });

        // Check if opened from notification
        String openUrl = getIntent().getStringExtra("open_url");
        if (openUrl != null && !openUrl.isEmpty()) {
            webView.loadUrl(openUrl);
        } else {
            webView.loadUrl(BASE_URL);
        }

        // Start notification service if already configured
        startPushServiceIfConfigured();
    }

    @Override
    protected void onDestroy() {
        JtechSoftkeys.removeStateListener(softkeyStateListener);
        super.onDestroy();
    }

    // ── Hardware soft keys ──────────────────────────────────────────────

    /**
     * The softkey engine pads the content view's first child to make room for its bar. A WebView
     * does not lay its page out inside its own padding, and a fitsSystemWindows container rewrites
     * its padding on every insets pass, so both screen modes sit inside this plain frame instead.
     */
    private FrameLayout softkeyRoot(View child) {
        FrameLayout root = new FrameLayout(this);
        root.addView(child, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        return root;
    }

    private static boolean isForumApp(String url) {
        return url != null && (url.equals(BASE_URL) || url.startsWith(BASE_URL + "/")
            || url.startsWith(BASE_URL + "?") || url.startsWith(BASE_URL + "#"));
    }

    private void injectSoftkeys() {
        if (webView == null) return;
        if (softkeysScript == null) {
            try (InputStream in = getAssets().open("softkeys.js")) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                softkeysScript = out.toString("UTF-8");
            } catch (IOException e) {
                return;
            }
        }
        webView.evaluateJavascript(softkeysScript, null);
        syncNativeSoftkeys();
    }

    /** Hide the page's own bar while the native one is active, so there is exactly one. */
    private void syncNativeSoftkeys() {
        if (webView == null || !isForumApp(webView.getUrl())) return;
        webView.evaluateJavascript(JtechSoftkeys.isActive()
            ? "document.documentElement.setAttribute('data-native-softkeys','')"
            : "document.documentElement.removeAttribute('data-native-softkeys')", null);
    }

    /** A native soft-key press clicks the page's own soft-key button, which runs its handler. */
    private void pressSoftkey(String which) {
        if (webView == null) return;
        if (!which.equals("left") && !which.equals("center") && !which.equals("right")) return;
        webView.evaluateJavascript("(function(){var b=document.querySelector('#softkeys [data-sk=\""
            + which + "\"]');if(b)b.click();})()", null);
    }

    /** Called from assets/softkeys.js. Its methods run on a WebView thread. */
    public class SoftkeyInterface {

        @JavascriptInterface
        public void onSoftkeys(String left, String center, String right, boolean light, String pref) {
            runOnUiThread(() -> {
                if (webView == null || !isForumApp(webView.getUrl())) return;
                JtechSoftkeys.setModeFromPage(MainActivity.this, pref);
                JtechSoftkeys.bind(MainActivity.this,
                    left != null ? left : "",
                    center != null ? center : "",
                    right != null ? right : "",
                    light, MainActivity.this::pressSoftkey);
            });
        }

        /** Whether the native bar is active (the page's own bar is then hidden). */
        @JavascriptInterface
        public boolean isActive() {
            return JtechSoftkeys.isActive();
        }

        /** Detect this phone's soft keys by pressing them (for non-standard keycodes). */
        @JavascriptInterface
        public void calibrate() {
            runOnUiThread(() -> JtechSoftkeys.calibrate(MainActivity.this));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_CODE) {
            if (fileChooserCallback != null) {
                Uri[] results = (resultCode == RESULT_OK && data != null)
                    ? new Uri[]{data.getData()} : null;
                fileChooserCallback.onReceiveValue(results);
                fileChooserCallback = null;
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String openUrl = intent.getStringExtra("open_url");
        if (openUrl != null && !openUrl.isEmpty() && webView != null) {
            webView.loadUrl(openUrl);
        }
    }

    private void startPushServiceIfConfigured() {
        String topic = PushService.getTopic(this);
        if (topic != null && !topic.isEmpty()) {
            Intent serviceIntent = new Intent(this, PushService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && useFullscreen) {
            hideSystemUI();
        }
    }

    private void startDownload(String url, String contentDisposition, String mimetype) {
        // Pass null mimetype so guessFileName preserves the original extension
        // instead of remapping it (e.g. .apk -> .bin) based on MIME type
        String fileName = URLUtil.guessFileName(url, contentDisposition, null);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setMimeType(mimetype);
        String cookie = CookieManager.getInstance().getCookie(url);
        if (cookie != null) {
            request.addRequestHeader("Cookie", cookie);
        }
        // The forum's Cloudflare rules refuse .zip / .apk requests that carry no Referer
        // ("Attention Required" 403), and DownloadManager sends none, so attachments failed.
        String page = webView != null ? webView.getUrl() : null;
        if (page != null && page.startsWith("http")) {
            request.addRequestHeader("Referer", page);
        }
        request.setTitle(fileName);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
        DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        dm.enqueue(request);
        JtechSoftkeys.message(this, "Downloading " + fileName);
    }

    private void hideSystemUI() {
        if (webView != null) {
            webView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }
    }

    /**
     * Soft keys the native engine didn't take (it's off - Never or a touch phone - or the
     * slot is blank, or the key isn't in the calibrated layout) still reach the forum page, as the
     * keys it knows: F1 / F2 are its left / right soft keys, while WebView has no name for
     * SOFT_LEFT / SOFT_RIGHT, so the page never saw them. MENU is the left soft key on many
     * keypad phones. Left untranslated, MENU also opened the Activity's empty options panel: an
     * invisible window that took every key until BACK.
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        int translated =
            code == KeyEvent.KEYCODE_SOFT_LEFT || code == KeyEvent.KEYCODE_MENU ? KeyEvent.KEYCODE_F1
            : code == KeyEvent.KEYCODE_SOFT_RIGHT ? KeyEvent.KEYCODE_F2
            : 0;
        if (translated != 0 && webView != null && isForumApp(webView.getUrl())) {
            webView.dispatchKeyEvent(new KeyEvent(
                event.getDownTime(), event.getEventTime(), event.getAction(), translated,
                event.getRepeatCount(), event.getMetaState()));
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    /** No options menu: an empty one shows as an invisible panel that swallows every key. */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return false;
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    /**
     * JavaScript interface for push notification registration.
     * Call from JS: PushBridge.registerPush(server, topic)
     */
    public class PushInterface {

        @JavascriptInterface
        public String getDeviceId() {
            // Generate or retrieve a stable device ID
            String deviceId = getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getString("device_id", null);
            if (deviceId == null) {
                deviceId = UUID.randomUUID().toString();
                getSharedPreferences("app_prefs", MODE_PRIVATE)
                    .edit()
                    .putString("device_id", deviceId)
                    .apply();
            }
            return deviceId;
        }

        @JavascriptInterface
        public String getTopic() {
            return PushService.getTopic(MainActivity.this);
        }

        @JavascriptInterface
        public String getServer() {
            return PushService.getServer(MainActivity.this);
        }

        @JavascriptInterface
        public void registerPush(String server, String topic) {
            PushService.configure(MainActivity.this, server, topic);

            // Start the service
            Intent serviceIntent = new Intent(MainActivity.this, PushService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        }

        @JavascriptInterface
        public void unregisterPush() {
            // Stop service
            Intent serviceIntent = new Intent(MainActivity.this, PushService.class);
            serviceIntent.setAction("STOP");
            startService(serviceIntent);

            // Clear config
            PushService.configure(MainActivity.this, "", "");
        }

        @JavascriptInterface
        public boolean isRegistered() {
            String topic = PushService.getTopic(MainActivity.this);
            return topic != null && !topic.isEmpty();
        }

        @JavascriptInterface
        public boolean isNativeApp() {
            return true;
        }
    }
}
