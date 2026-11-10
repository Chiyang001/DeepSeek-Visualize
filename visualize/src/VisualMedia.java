package local.visualize;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class VisualMedia {
    private static final int WRAP = LinearLayout.LayoutParams.WRAP_CONTENT;
    private static final int IMAGE_BYTES = 4 * 1024 * 1024;
    private static final int READ_TIMEOUT = 6000;
    private static final int LONG_EDGE = 1280;
    private VisualMedia() {}

    private static final class ImageStore {
        private static final ThreadPoolExecutor DOWNLOADS = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(8));
        private static final LruCache<String, Bitmap> IMAGES = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
            @Override protected int sizeOf(String url, Bitmap bitmap) { return bitmap.getAllocationByteCount(); }
        };
        private static final Handler UI = new Handler(Looper.getMainLooper());

        static { DOWNLOADS.allowCoreThreadTimeOut(true); }
    }

    static View create(VisualCard card, JSONObject item) {
        LinearLayout panel = card.section(card.surface, card.border);
        String label = item.optString("label");
        if (!label.isEmpty()) panel.addView(card.text(label, 15, card.ink, true));
        switch (item.optString("type")) {
            case "emoji":
                JSONArray emojis = item.optJSONArray("entries");
                boolean compact = "inline".equals(item.optString("style"));
                for (int i = 0; i < emojis.length(); i++) {
                    card.append(panel, emojiTile(card, emojis.optJSONObject(i), compact), WRAP, 8);
                }
                break;
            case "gallery":
                JSONArray images = item.optJSONArray("entries");
                for (int i = 0; i < images.length(); i++) {
                    card.append(panel, new RemoteImage(card, images.optJSONObject(i)), WRAP, 10);
                }
                break;
            default:
                card.append(panel, new RemoteImage(card, item), WRAP, 7);
                break;
        }
        return panel;
    }

    private static View emojiTile(VisualCard card, JSONObject entry, boolean compact) {
        LinearLayout row = new LinearLayout(card.getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(card.dp(compact ? 2 : 8), card.dp(6), card.dp(8), card.dp(6));
        if (!compact) row.setBackground(card.shape(card.panel, 12, 0));
        String emoji = entry.optString("emoji");
        TextView tile = card.text(emoji, compact ? 20 : 29, card.ink, false);
        tile.setGravity(Gravity.CENTER);
        tile.setMinHeight(card.dp(compact ? 34 : 53));
        tile.setMinWidth(card.dp(compact ? 38 : 56));
        tile.setMaxWidth(card.dp(120));
        if (!compact) tile.setBackground(card.shape(card.surface, 10, 0));
        row.addView(tile, new LinearLayout.LayoutParams(WRAP, WRAP));

        LinearLayout copy = new LinearLayout(card.getContext());
        copy.setOrientation(LinearLayout.VERTICAL);
        String label = entry.optString("label");
        if (!label.isEmpty()) copy.addView(card.text(label, 14, card.ink, true));
        String detail = entry.optString("detail");
        if (!detail.isEmpty()) card.append(copy, card.text(detail, 13, card.secondary, false), WRAP, 2);
        if (copy.getChildCount() != 0) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, WRAP, 1);
            params.setMarginStart(card.dp(10));
            row.addView(copy, params);
        }
        return row;
    }

    private static final class RemoteImage extends LinearLayout {
        private final VisualCard card;
        private final Button load;
        private final TextView status;
        private final ImageView image;
        private final URI uri;
        private final String key;

        RemoteImage(VisualCard card, JSONObject entry) {
            super(card.getContext());
            this.card = card;
            uri = URI.create(entry.optString("url"));
            key = uri.toASCIIString();
            setOrientation(VERTICAL);
            setPadding(card.dp(9), card.dp(8), card.dp(9), card.dp(9));
            setBackground(card.shape(card.panel, 12, 0));

            image = new ImageView(getContext());
            image.setAdjustViewBounds(true);
            image.setMaxHeight(card.dp(360));
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setBackground(card.shape(card.surface, 10, 0));
            image.setClipToOutline(true);
            image.setContentDescription(entry.optString("alt"));
            image.setVisibility(GONE);
            card.append(this, image, WRAP, 0);

            status = card.text(entry.optString("alt"), 13, card.ink, true);
            card.append(this, status, WRAP, 4);
            load = card.button(card.local("加载图片", "Load image"), false);
            load.setContentDescription(card.local("加载来自 ", "Load image from ") + uri.getHost());
            load.setOnClickListener(view -> load());
            card.append(this, load, WRAP, 8);
            card.append(this, card.text(uri.getHost(), 11, card.secondary, false), WRAP, 3);
            String caption = entry.optString("caption");
            if (!caption.isEmpty()) card.append(this, card.text(caption, 13, card.secondary, false), WRAP, 6);
            Bitmap cached = ImageStore.IMAGES.get(key);
            if (cached != null) display(cached);
        }

        private void load() {
            Bitmap cached = ImageStore.IMAGES.get(key);
            if (cached != null) {
                display(cached);
                return;
            }
            load.setEnabled(false);
            load.setText(card.local("正在加载图片", "Loading image"));
            try {
                ImageStore.DOWNLOADS.execute(() -> {
                    Bitmap bitmap = null;
                    try {
                        bitmap = download(card.getContext(), uri);
                        ImageStore.IMAGES.put(key, bitmap);
                    } catch (IOException | RuntimeException unavailable) {
                        // The alt text and caption stay readable if the source is unavailable.
                    }
                    Bitmap result = bitmap;
                    ImageStore.UI.post(() -> {
                        if (result == null) showError();
                        else display(result);
                    });
                });
            } catch (RejectedExecutionException busy) {
                showError();
            }
        }

        private void showError() {
            load.setText(card.local("图片无法加载 · 重试", "Couldn't load image · Retry"));
            load.setEnabled(true);
        }

        private void display(Bitmap bitmap) {
            image.setImageBitmap(bitmap);
            image.setVisibility(VISIBLE);
            status.setVisibility(GONE);
            load.setVisibility(GONE);
        }
    }

    static void validate(JSONObject item) throws JSONException {
        String type = item.getString("type");
        if ("image".equals(type)) {
            validateImage(item);
            return;
        }
        JSONArray entries = VisualSpec.array(item.get("entries"), 1, "emoji".equals(type) ? 8 : 4);
        if ("emoji".equals(type) && item.has("style")) {
            String style = VisualSpec.string(item.get("style"));
            VisualSpec.require("inline".equals(style) || "sticker".equals(style));
        }
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.getJSONObject(i);
            if ("emoji".equals(type)) {
                String glyph = VisualSpec.string(entry.get("emoji"));
                VisualSpec.require(!glyph.trim().isEmpty() && glyph.codePointCount(0, glyph.length()) <= 24);
                if (entry.has("label")) VisualSpec.string(entry.get("label"));
                if (entry.has("detail")) VisualSpec.string(entry.get("detail"));
            } else validateImage(entry);
        }
    }

    private static void validateImage(JSONObject entry) throws JSONException {
        Object value = entry.get("url");
        VisualSpec.require(value instanceof String && ((String) value).length() <= 2048);
        try {
            URI uri = new URI((String) value);
            String host = uri.getHost();
            VisualSpec.require("https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443) && publicHostname(host));
        } catch (URISyntaxException invalid) {
            throw new IllegalArgumentException("Invalid image address");
        }
        VisualSpec.require(!VisualSpec.string(entry.get("alt")).trim().isEmpty());
        if (entry.has("caption")) VisualSpec.string(entry.get("caption"));
    }

    private static boolean publicHostname(String hostname) {
        if (hostname == null) return false;
        String host = hostname.toLowerCase(Locale.ROOT);
        return host.length() <= 253 && host.matches("(?:[a-z0-9-]+\\.)+[a-z][a-z0-9-]*")
                && !host.endsWith(".localhost") && !host.endsWith(".local") && !host.endsWith(".home.arpa")
                && !host.endsWith(".internal") && !host.endsWith(".onion");
    }

    private static boolean usableAddress(InetAddress address, boolean throughVpn) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        int a = bytes[0] & 255, b = bytes[1] & 255, c = bytes[2] & 255;
        if (bytes.length == 16) {
            return (a & 0xe0) == 0x20 && (a != 0x20 || b != 0x02)
                    && (a != 0x20 || b != 0x01 || c != 0x00)
                    && (a != 0x20 || b != 0x01 || c != 0x0d || (bytes[3] & 255) != 0xb8);
        }
        return a > 0 && a < 224 && !(a == 100 && b >= 64 && b < 128)
                && !(a == 192 && b == 0 && (c == 0 || c == 2)) && !(a == 192 && b == 88 && c == 99)
                && !(a == 198 && (!throughVpn && (b == 18 || b == 19) || b == 51 && c == 100))
                && !(a == 203 && b == 0 && c == 113);
    }

    private static Bitmap download(Context context, URI uri) throws IOException {
        ConnectivityManager connectivity = context.getSystemService(ConnectivityManager.class);
        Network active = connectivity == null ? null : connectivity.getActiveNetwork();
        NetworkCapabilities capabilities = active == null ? null : connectivity.getNetworkCapabilities(active);
        Network vpn = capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ? active : null;
        String host = uri.getHost();
        Set<InetAddress> addresses = new HashSet<>();
        InetAddress[] resolved = vpn == null ? InetAddress.getAllByName(host) : vpn.getAllByName(host);
        for (InetAddress address : resolved) {
            // A user-selected VPN may use 198.18.0.0/15 as a synthetic DNS range.
            if (!usableAddress(address, vpn != null)) throw new IOException("Non-public image endpoint");
            addresses.add(address);
        }
        HttpsURLConnection connection = (HttpsURLConnection) (vpn == null
                ? uri.toURL().openConnection(Proxy.NO_PROXY) : vpn.openConnection(uri.toURL(), Proxy.NO_PROXY));
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(READ_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);
        connection.setUseCaches(false);
        connection.setRequestProperty("User-Agent", "DeepSeek-Visualize/2.7");
        connection.setRequestProperty("Accept", "image/png,image/jpeg,image/webp,image/gif,image/bmp");
        connection.setRequestProperty("Accept-Encoding", "identity");
        connection.setSSLSocketFactory(new PublicImageTls(connection.getSSLSocketFactory(), host, addresses, vpn));
        long deadline = SystemClock.elapsedRealtime() + 18000;
        try {
            if (connection.getResponseCode() != 200 || !isRaster(connection.getContentType())
                    || connection.getContentLengthLong() > IMAGE_BYTES) {
                throw new IOException("Unsupported image response");
            }
            try (InputStream stream = connection.getInputStream()) {
                ByteArrayOutputStream data = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                for (int read; (read = stream.read(buffer)) != -1; ) {
                    if (SystemClock.elapsedRealtime() > deadline || read > IMAGE_BYTES - data.size()) {
                        throw new IOException("Image download limit exceeded");
                    }
                    data.write(buffer, 0, read);
                }
                return decode(data.toByteArray());
            }
        } finally {
            connection.disconnect();
        }
    }

    private static boolean isRaster(String type) {
        if (type == null) return false;
        switch (type.split(";", 2)[0].trim().toLowerCase(Locale.ROOT)) {
            case "image/png": case "image/jpeg": case "image/webp": case "image/gif":
            case "image/bmp": case "image/x-ms-bmp":
                return true;
            default:
                return false;
        }
    }

    private static Bitmap decode(byte[] data) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);
        int width = options.outWidth, height = options.outHeight;
        if (width <= 0 || height <= 0 || width > 10000 || height > 10000 || (long) width * height > 40000000
                || !isRaster(options.outMimeType)) throw new IOException("Image dimensions or format rejected");
        options.inJustDecodeBounds = false;
        options.inSampleSize = 1;
        while ((Math.max(width, height) + options.inSampleSize - 1) / options.inSampleSize > LONG_EDGE) {
            options.inSampleSize *= 2;
        }
        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
        if (bitmap == null) throw new IOException("Image decode failed");
        return bitmap;
    }

    private static final class PublicImageTls extends SSLSocketFactory {
        private final SSLSocketFactory delegate;
        private final String hostname;
        private final Set<InetAddress> publicEndpoints;
        private final Network vpn;

        PublicImageTls(SSLSocketFactory delegate, String hostname, Set<InetAddress> publicEndpoints, Network vpn) {
            this.delegate = delegate;
            this.hostname = hostname;
            this.publicEndpoints = publicEndpoints;
            this.vpn = vpn;
        }

        @Override public String[] getDefaultCipherSuites() { return delegate.getDefaultCipherSuites(); }
        @Override public String[] getSupportedCipherSuites() { return delegate.getSupportedCipherSuites(); }

        @Override public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
            if (!hostname.equalsIgnoreCase(host) || port != 443 || !publicEndpoints.contains(socket.getInetAddress())) {
                throw new IOException("Image TLS endpoint changed");
            }
            return delegate.createSocket(socket, host, port, autoClose);
        }

        @Override public Socket createSocket(String host, int port) throws IOException {
            return createSocket(host, port, null, 0);
        }

        @Override public Socket createSocket(String host, int port, InetAddress local, int localPort) throws IOException {
            if (!hostname.equalsIgnoreCase(host)) throw new IOException("Image TLS hostname changed");
            return connect(publicEndpoints.iterator().next(), port, local, localPort);
        }

        @Override public Socket createSocket(InetAddress host, int port) throws IOException {
            return connect(host, port, null, 0);
        }

        @Override public Socket createSocket(InetAddress host, int port, InetAddress local, int localPort)
                throws IOException {
            return connect(host, port, local, localPort);
        }

        private Socket connect(InetAddress host, int port, InetAddress local, int localPort) throws IOException {
            if (port != 443 || !publicEndpoints.contains(host)) throw new IOException("Image TLS endpoint changed");
            Socket socket = vpn == null ? new Socket(Proxy.NO_PROXY) : vpn.getSocketFactory().createSocket();
            try {
                if (local != null || localPort != 0) socket.bind(new InetSocketAddress(local, localPort));
                socket.connect(new InetSocketAddress(host, port), READ_TIMEOUT);
                return createSocket(socket, hostname, port, true);
            } catch (IOException | RuntimeException invalid) {
                socket.close();
                throw invalid;
            }
        }
    }
}
