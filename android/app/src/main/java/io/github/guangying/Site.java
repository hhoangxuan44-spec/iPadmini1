package io.github.guangying;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.util.Log;
import android.view.View;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 月历网页的加载与本地缓存。
 *
 * 网页本身（html / js / css / 数据）每次先联网取最新的，取不到就用上次存下的；
 * 图片和字体只要存过就直接用本地文件。所以联网用过一次之后，断网也能完整运行，
 * 网站更新后 App 也会自动用上新版本。
 */
public final class Site {
    public static final String BASE = "https://hhoangxuan44-spec.github.io/iPadmini1/";
    private static final String TAG = "Guangying";
    private static final Pattern IMAGE = Pattern.compile("\"[lb]\":\"(photos/[^\"]+)\"");

    private Site() {}

    /** 打开月历的网址。insetTop / insetBottom 是要避开的状态栏与 Dock（像素）。 */
    public static String url(Context ctx, String mode, int insetTop, int insetBottom) {
        float d = ctx.getResources().getDisplayMetrics().density;
        return BASE + "index.html?app=" + mode
                + "&it=" + Math.round(insetTop / d) + "&ib=" + Math.round(insetBottom / d);
    }

    public static int systemDimen(Context ctx, String name) {
        Resources r = ctx.getResources();
        int id = r.getIdentifier(name, "dimen", "android");
        return id > 0 ? r.getDimensionPixelSize(id) : 0;
    }

    /** 网页出问题（渲染进程被系统回收）时调用 onGone，由调用方重建 WebView。 */
    public static WebView newWebView(Context ctx, final Runnable onGone) {
        final Context app = ctx.getApplicationContext();
        WebView w = new WebView(ctx);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setTextZoom(100);                       // 不受系统字体大小影响，排版和设计稿一致
        s.setSupportZoom(false);
        w.setBackgroundColor(Color.BLACK);
        w.setVerticalScrollBarEnabled(false);
        w.setHorizontalScrollBarEnabled(false);
        w.setOverScrollMode(View.OVER_SCROLL_NEVER);
        w.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                String u = req.getUrl().toString();
                if (!"GET".equals(req.getMethod()) || !u.startsWith(BASE)) return null;
                return fetch(app, u);
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                Log.w(TAG, "WebView 渲染进程退出，重建");
                if (onGone != null) view.post(onGone);
                return true;
            }
        });
        return w;
    }

    public static void js(WebView w, String code) {
        if (w != null) w.evaluateJavascript("window.WallApp && " + code, null);
    }

    // ------------------------------------------------------------ 缓存

    public static File root(Context ctx) {
        return new File(ctx.getFilesDir(), "site");
    }

    private static String pathOf(String url) {
        String p = url.substring(BASE.length());
        int q = p.indexOf('?');
        if (q >= 0) p = p.substring(0, q);
        q = p.indexOf('#');
        if (q >= 0) p = p.substring(0, q);
        return p.isEmpty() ? "index.html" : p;
    }

    private static boolean isStatic(String path) {
        return path.startsWith("photos/") || path.startsWith("fonts/") || path.startsWith("img/");
    }

    static WebResourceResponse fetch(Context ctx, String url) {
        String path = pathOf(url);
        if (path.contains("..")) return null;
        File f = new File(root(ctx), path);
        boolean stat = isStatic(path);
        if (stat && f.isFile()) return respond(path, f);

        byte[] data = download(BASE + path, stat ? 20000 : 6000);
        if (data != null) {
            save(f, data);
            return respond(path, new ByteArrayInputStream(data));
        }
        if (f.isFile()) return respond(path, f);
        if (path.equals("index.html")) {
            String html = "<!DOCTYPE html><html><head><meta charset='utf-8'>"
                    + "<meta http-equiv='refresh' content='30'></head>"
                    + "<body style='background:#000;color:#999;font:18px sans-serif;"
                    + "display:flex;align-items:center;justify-content:center;height:100vh;margin:0'>"
                    + "第一次使用需要联网，连上网络后会自动打开</body></html>";
            return respond(path, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
        }
        return null;
    }

    private static final Map<String, String> MIME = new HashMap<>();
    static {
        MIME.put("html", "text/html");
        MIME.put("js", "application/javascript");
        MIME.put("css", "text/css");
        MIME.put("jpg", "image/jpeg");
        MIME.put("png", "image/png");
        MIME.put("woff", "font/woff");
        MIME.put("webmanifest", "application/manifest+json");
        MIME.put("appcache", "text/cache-manifest");
    }

    private static WebResourceResponse respond(String path, File f) {
        try {
            return respond(path, new FileInputStream(f));
        } catch (IOException e) {
            return null;
        }
    }

    private static WebResourceResponse respond(String path, InputStream in) {
        String ext = path.substring(path.lastIndexOf('.') + 1);
        String mime = MIME.containsKey(ext) ? MIME.get(ext) : "application/octet-stream";
        boolean text = mime.startsWith("text/") || mime.endsWith("javascript");
        return new WebResourceResponse(mime, text ? "utf-8" : null, in);
    }

    public static byte[] download(String url, int timeout) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(timeout);
            c.setReadTimeout(timeout * 2);
            c.setUseCaches(false);
            if (c.getResponseCode() != 200) return null;
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            return out.toByteArray();
        } catch (IOException e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static final Set<String> inflight = new java.util.HashSet<>();

    /**
     * 下载一个文件到本地，支持断点续传：网络不稳时下到一半断了，下次接着下，不用从头来。
     * 先写到 .part，下完再改名；服务器不支持续传时自动从头下。
     */
    public static boolean fetchFile(Context ctx, String path, int timeout) {
        File f = new File(root(ctx), path);
        if (f.isFile()) return true;
        synchronized (inflight) {
            if (!inflight.add(path)) return waitFor(f);          // 别处正在下同一个文件，等它
        }
        try {
            File dir = f.getParentFile();
            if (dir != null && !dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) return false;
            File part = new File(f.getPath() + ".part");
            HttpURLConnection c = null;
            try {
                long have = part.isFile() ? part.length() : 0;
                c = (HttpURLConnection) new URL(BASE + path).openConnection();
                c.setConnectTimeout(timeout);
                c.setReadTimeout(timeout);
                c.setUseCaches(false);
                if (have > 0) c.setRequestProperty("Range", "bytes=" + have + "-");
                int code = c.getResponseCode();
                boolean append = code == 206 && have > 0;
                if (code == 416) {                                   // 已经下完或文件变了：重来
                    part.delete();
                    return false;
                }
                if (code != 200 && code != 206) return false;
                long total = c.getContentLengthLong();
                try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(part, append)) {
                    byte[] buf = new byte[16384];
                    int n;
                    long got = 0;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        got += n;
                    }
                    if (total >= 0 && got < total) return false;      // 没下完，留着 .part 下次续
                }
                if (!part.renameTo(f)) {
                    part.delete();
                    return false;
                }
                return true;
            } catch (IOException e) {
                return false;
            } finally {
                if (c != null) c.disconnect();
            }
        } finally {
            synchronized (inflight) {
                inflight.remove(path);
            }
        }
    }

    private static boolean waitFor(File f) {
        for (int i = 0; i < 60 && !f.isFile(); i++) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                return false;
            }
        }
        return f.isFile();
    }

    /** 带重试的下载：每次失败等一会儿再来，已经下好的部分不丢。 */
    public static boolean fetchWithRetry(Context ctx, String path, int attempts) {
        for (int i = 0; i < attempts; i++) {
            if (fetchFile(ctx, path, 15000)) return true;
            try {
                Thread.sleep(2000L << i);
            } catch (InterruptedException e) {
                return false;
            }
        }
        return false;
    }

    /** 确保某个文件在本地：没有就下载。返回是否可用。 */
    public static boolean ensure(Context ctx, String path) {
        return fetchWithRetry(ctx, path, 2);
    }

    /**
     * 这张图用哪个文件显示：大图（1920）存过就用大图；没存过，先用小得多的 1280 版立刻显示，
     * 同时在后台把大图下好，下次就是大图。网络慢时不会让壁纸干等。
     */
    public static File photoFile(Context ctx, io.github.guangying.data.Photo p) {
        File large = new File(root(ctx), p.large);
        if (large.isFile()) return large;
        File small = new File(root(ctx), p.id);
        if (small.isFile() || ensure(ctx, p.id)) {
            final Context app = ctx.getApplicationContext();
            final String path = p.large;
            if (!path.equals(p.id)) new Thread(new Runnable() {
                @Override
                public void run() {
                    fetchWithRetry(app, path, 3);
                }
            }, "guangying-large").start();
            return small;
        }
        return ensure(ctx, p.large) ? large : null;
    }

    /** 先写临时文件再改名：壁纸和主界面在两个进程里，可能同时写同一个文件。 */
    private static void save(File f, byte[] data) {
        File dir = f.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) return;
        File tmp = new File(dir, f.getName() + "." + android.os.Process.myPid() + "." + Thread.currentThread().getId() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(data);
        } catch (IOException e) {
            tmp.delete();
            return;
        }
        if (!tmp.renameTo(f)) tmp.delete();
    }

    // ------------------------------------------------------------ 离线下载全部图片

    /** 网站上所有图片（大图 + 玻璃效果用的模糊小图）。 */
    public static List<String> imageList(Context ctx) {
        File data = new File(root(ctx), "data/photos.js");
        byte[] fresh = download(BASE + "data/photos.js", 8000);
        if (fresh != null) save(data, fresh);
        List<String> out = new ArrayList<>();
        if (!data.isFile()) return out;
        String text;
        try (FileInputStream in = new FileInputStream(data)) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            text = b.toString("UTF-8");
        } catch (IOException e) {
            return out;
        }
        Set<String> seen = new LinkedHashSet<>();
        Matcher m = IMAGE.matcher(text);
        while (m.find()) seen.add(m.group(1));
        out.addAll(seen);
        return out;
    }

    public static int countCached(Context ctx, List<String> list) {
        int n = 0;
        for (String p : list) if (new File(root(ctx), p).isFile()) n++;
        return n;
    }

    public interface Progress {
        void update(int done, int total, boolean finished);
    }

    private static volatile boolean downloading = false;

    public static boolean isDownloading() {
        return downloading;
    }

    /** 在后台线程里下载所有还没存过的图片。 */
    public static void downloadAll(final Context ctx, final Progress cb) {
        if (downloading) return;
        downloading = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    List<String> list = imageList(ctx);
                    // 先下玻璃用的模糊小图（只有几 KB），再下大图
                    List<String> ordered = new ArrayList<>();
                    for (String p : list) if (p.startsWith("photos/b/")) ordered.add(p);
                    for (String p : list) if (!p.startsWith("photos/b/")) ordered.add(p);
                    int done = countCached(ctx, list), consecutive = 0;
                    cb.update(done, list.size(), false);
                    for (String p : ordered) {
                        if (new File(root(ctx), p).isFile()) continue;
                        if (!fetchWithRetry(ctx, p, 3)) {
                            if (++consecutive >= 4) break;      // 连着失败：多半断网了，等网络恢复再续
                            continue;
                        }
                        consecutive = 0;
                        done++;
                        cb.update(done, list.size(), false);
                    }
                    cb.update(done, list.size(), true);
                } finally {
                    downloading = false;
                }
            }
        }, "guangying-download").start();
    }

    public static boolean online(Context ctx) {
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return true;
        NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    public static boolean unmetered(Context ctx) {
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
}
