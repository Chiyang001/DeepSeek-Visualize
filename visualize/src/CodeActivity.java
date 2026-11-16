package local.visualize;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.MimeTypeMap;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** A private HTML preview and Storage Access Framework export host. */
public final class CodeActivity extends Activity {
    private File source;
    private WebView web;
    private boolean preview;
    private Button save;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String token = getIntent().getStringExtra("token");
        if (token == null || !token.matches("code-[a-zA-Z0-9-]+\\.utf8")) { finish(); return; }
        source = new File(new File(getCacheDir(), "code-files"), token);
        if (!source.isFile()) { message("代码缓存已失效，请返回对话重试"); finish(); return; }
        preview = getIntent().getBooleanExtra("preview", false);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        // Respect system bars even when the host APK targets edge-to-edge Android.
        page.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        if (!preview) {
            LinearLayout toolbar = new LinearLayout(this);
            Button back = new Button(this);
            back.setText("返回");
            back.setOnClickListener(v -> finish());
            toolbar.addView(back);
            save = new Button(this);
            save.setText("保存代码文件");
            save.setOnClickListener(v -> save());
            toolbar.addView(save);
            page.addView(toolbar);
        }
        setContentView(page);
        if (preview) {
            web = new WebView(this);
            WebSettings settings = web.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setAllowFileAccess(false);
            settings.setAllowContentAccess(false);
            settings.setAllowFileAccessFromFileURLs(false);
            settings.setAllowUniversalAccessFromFileURLs(false);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            settings.setSupportMultipleWindows(false);
            settings.setJavaScriptCanOpenWindowsAutomatically(false);
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);
            web.setWebViewClient(new WebViewClient() {
                @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    // Keep generated pages from launching apps or navigating to private files.
                    return !allowed(request.getUrl());
                }
                @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    return !allowed(Uri.parse(url));
                }
                private boolean allowed(Uri uri) {
                    return "https".equals(uri.getScheme()) || "http".equals(uri.getScheme())
                            || "about".equals(uri.getScheme());
                }
            });
            page.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
            try (FileInputStream in = new FileInputStream(source);
                 ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) != -1) bytes.write(buffer, 0, n);
                // A null origin isolates preview scripts from the signed-in app's web origins.
                web.loadDataWithBaseURL(null, new String(bytes.toByteArray(), StandardCharsets.UTF_8),
                        "text/html", "UTF-8", null);
            } catch (Exception failure) { message("无法读取 HTML，请返回重试"); finish(); }
        } else if (state == null) save();
    }

    private void save() {
        String ext = getIntent().getStringExtra("extension");
        if (ext == null || !ext.matches("[a-z0-9]{1,12}")) ext = "txt";
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        // Match the device's own extension table so its document provider keeps the suffix.
        // For unregistered source extensions, use a type without a default .bin/.txt suffix.
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        // Android associates .ts with MPEG transport streams, not TypeScript source.
        if ("ts".equals(ext)) mime = null;
        intent.setType(mime != null ? mime : "text/x-source-" + ext);
        intent.putExtra(Intent.EXTRA_TITLE, "deepseek-" + System.currentTimeMillis() + "." + ext);
        try { startActivityForResult(intent, 1); }
        catch (Exception failure) { message("无法打开系统文件保存器"); }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 1) return;
        if (result != RESULT_OK || data == null || data.getData() == null) {
            if (!preview) finish();
            return;
        }
        Uri destination = data.getData();
        save.setEnabled(false);
        new Thread(() -> {
            boolean succeeded = false;
            try (FileInputStream in = new FileInputStream(source);
                 OutputStream out = getContentResolver().openOutputStream(destination, "wt")) {
                if (out == null) throw new java.io.IOException("No output stream");
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                out.flush();
                succeeded = true;
            } catch (Exception failure) { /* Report failure on the UI thread; permit retry. */ }
            final boolean ok = succeeded;
            runOnUiThread(() -> {
                message(ok ? "代码文件已保存" : "保存失败，请重新选择位置");
                save.setEnabled(true);
                if (ok && !preview) finish();
            });
        }, "code-export").start();
    }

    private void message(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }

    @Override protected void onDestroy() {
        if (web != null) { web.stopLoading(); web.destroy(); web = null; }
        super.onDestroy();
    }
}
