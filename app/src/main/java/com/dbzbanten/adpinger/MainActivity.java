package com.dbzbanten.adpinger;

import android.app.Activity;
import android.content.*;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private EditText githubUrl;
    private TextView status, logView, currentUrl, deviceMode;
    private WebView webView;
    private static final long WEBVIEW_REFRESH_MS = 5 * 60 * 1000L;
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override public void run() {
            if (webView != null && webView.getUrl() != null && !webView.getUrl().isEmpty()) {
                webView.reload();
                status.setText("WebView otomatis refresh — setiap 5 menit");
            }
            refreshHandler.postDelayed(this, WEBVIEW_REFRESH_MS);
        }
    };
    private BroadcastReceiver urlReceiver;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        githubUrl = findViewById(R.id.githubUrl);
        status = findViewById(R.id.status);
        logView = findViewById(R.id.logView);
        currentUrl = findViewById(R.id.currentUrl);
        deviceMode = findViewById(R.id.deviceMode);
        deviceMode.setText(RootUtils.modeLabel());
        webView = findViewById(R.id.webView);

        githubUrl.setText(RemoteConfigStore.getUrl(this));
        githubUrl.setHint("Raw GitHub URL daftar URL");
        githubUrl.setEnabled(false);
        githubUrl.setFocusable(false);
        githubUrl.setFocusableInTouchMode(false);
        githubUrl.setClickable(false);
        githubUrl.setLongClickable(false);
        githubUrl.setTextIsSelectable(false);
        setupWebView();
        refreshHandler.postDelayed(refreshRunnable, WEBVIEW_REFRESH_MS);

        findViewById(R.id.saveRefresh).setOnClickListener(v -> refreshRemote());
        findViewById(R.id.start).setOnClickListener(v -> startPinger());
        findViewById(R.id.stop).setOnClickListener(v -> stopPinger());
        findViewById(R.id.showLog).setOnClickListener(v -> loadLog());
        findViewById(R.id.randomUrl).setOnClickListener(v -> showRandomUrl());

        urlReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (PingerService.ACTION_REFRESH.equals(intent.getAction())) {
                    if (webView != null && webView.getUrl() != null && !webView.getUrl().isEmpty()) {
                        webView.reload();
                        status.setText("WebView otomatis refresh — setiap 5 menit");
                    }
                    return;
                }
                if (PingerService.ACTION_STOP.equals(intent.getAction())) {
                    getSharedPreferences("adpinger",0)
                            .edit().remove("pending_url").apply();
                    status.setText("Auto WebView STOP");
                    return;
                }

                String u = intent.getStringExtra(PingerService.EXTRA_URL);
                if (u != null && !u.isEmpty()) {
                    getSharedPreferences("adpinger",0)
                            .edit().remove("pending_url").apply();
                    currentUrl.setText("AUTO RANDOM: " + u);
                    status.setText("URL dipilih otomatis. Membuka di WebView...");
                    loadAdUrl(u);
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(PingerService.ACTION_SHOW_URL);
        filter.addAction(PingerService.ACTION_REFRESH);
        filter.addAction(PingerService.ACTION_STOP);
        registerReceiver(urlReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        loadPendingUrl(getIntent());
        loadLog();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        loadPendingUrl(intent);
    }

    private void loadPendingUrl(Intent intent) {
        String u = intent == null ? null : intent.getStringExtra(PingerService.EXTRA_URL);
        if (u == null || u.isEmpty()) {
            u = getSharedPreferences("adpinger",0).getString("pending_url", "");
        }
        if (u != null && !u.isEmpty()) {
            getSharedPreferences("adpinger",0).edit().remove("pending_url").apply();
            currentUrl.setText("AUTO RANDOM: " + u);
            status.setText("URL otomatis. Membuka di WebView...");
            loadAdUrl(u);
        }
    }

    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        CookieManager.getInstance().setAcceptCookie(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
                status.setText("Memuat: " + url);
            }
            @Override public void onPageFinished(WebView view, String url) {
                status.setText("WebView aktif: HTTP/halaman selesai dimuat");
                loadLog();
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
    }

    private void loadAdUrl(String u) {
        runOnUiThread(() -> {
            currentUrl.setText(u);
            webView.loadUrl(u);
        });
    }

    private void refreshRemote() {
        final String u = githubUrl.getText().toString().trim();
        if (u.isEmpty()) { status.setText("Masukkan URL Raw GitHub"); return; }
        RemoteConfigStore.setUrl(this, u);
        status.setText("Mengambil konfigurasi...");
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                int n = RemoteUrlConfig.fetch(u).size();
                runOnUiThread(() -> status.setText("Konfigurasi OK: " + n + " URL"));
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("Gagal: " + e.getMessage()));
            }
        });
    }

    private void startPinger() {
        getSharedPreferences("adpinger",0).edit().putInt("interval", 5).apply();
        Intent i = new Intent(this, PingerService.class).setAction("START");
        if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        status.setText("Auto WebView START — refresh setiap 5 menit");
    }

    private void stopPinger() {
        getSharedPreferences("adpinger",0)
                .edit().remove("pending_url").putBoolean("enabled", false).apply();

        Intent i = new Intent(this, PingerService.class).setAction("STOP");
        startService(i);

        status.setText("Auto WebView STOP");
    }

    private void showRandomUrl() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                List<String> urls = RemoteUrlConfig.fetch(RemoteConfigStore.getUrl(this));
                if (urls.isEmpty()) throw new IOException("Daftar URL kosong");
                String u = urls.get(new Random().nextInt(urls.size()));
                runOnUiThread(() -> loadAdUrl(u));
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("Random URL gagal: " + e.getMessage()));
            }
        });
    }

    private void loadLog() {
        try {
            File f = new File(getFilesDir(), "adpinger.log");
            if (!f.exists()) { logView.setText("Belum ada log."); return; }
            FileInputStream in = new FileInputStream(f);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096]; int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            in.close();
            logView.setText(new String(out.toByteArray(), StandardCharsets.UTF_8));
        } catch(Exception e) { logView.setText("Log error: " + e.getMessage()); }
    }

    @Override protected void onDestroy() {
        if (urlReceiver != null) unregisterReceiver(urlReceiver);
        refreshHandler.removeCallbacks(refreshRunnable);
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
