package com.markdave.sinomatakaw;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    static final int GAME_PORT = 8787;

    private WebView web;
    private RelayServer relay;
    private LanBridge lan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.parseColor("#5B3A93"));
        lan = new LanBridge(this);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "Android");
        setContentView(web);
        web.loadUrl("file:///android_asset/www/index.html");
    }

    /** Methods the game page can call as Android.xxx(). They run off the UI thread. */
    class Bridge {
        @JavascriptInterface
        public String startHost(String name) {
            stopHostInternal();
            try {
                relay = new RelayServer(GAME_PORT);
                relay.start();
            } catch (Exception e) {
                relay = null;
            }
            lan.startBroadcast(name == null || name.trim().isEmpty() ? "Game" : name.trim(), GAME_PORT);
            return lan.getIp();
        }

        @JavascriptInterface
        public void stopHost() { stopHostInternal(); }

        @JavascriptInterface
        public void startScan() { lan.startScan(); }

        @JavascriptInterface
        public void stopScan() { lan.stopScan(); }

        @JavascriptInterface
        public String getGames() { return lan.getGames(); }

        @JavascriptInterface
        public String getIp() { return lan.getIp(); }
    }

    private void stopHostInternal() {
        lan.stopBroadcast();
        if (relay != null) {
            try { relay.stop(500); } catch (Exception ignored) { }
            relay = null;
        }
    }

    @Override
    public void onBackPressed() {
        // Keep the game (and the Wi-Fi room, if hosting) alive instead of closing.
        moveTaskToBack(true);
    }

    @Override
    protected void onDestroy() {
        stopHostInternal();
        lan.stopScan();
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
