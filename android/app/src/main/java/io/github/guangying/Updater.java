package io.github.guangying;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 自动更新：App 自己下载新版安装包并安装，不用再去浏览器里下载、找文件、点开。
 * 系统仍会弹一次安装确认（第一次还要允许光影安装应用），这一步 App 跳不过。
 */
public class Updater extends BroadcastReceiver {
    private static final String ACTION = "io.github.guangying.INSTALL_RESULT";

    public interface Progress {
        /** 主线程回调：0–100 是下载进度，-1 表示正在安装 */
        void progress(int percent);

        /** 主线程回调：失败原因 */
        void failed(String why);
    }

    public static boolean canInstall(Context c) {
        return c.getPackageManager().canRequestPackageInstalls();
    }

    public static void downloadAndInstall(Context ctx, String url, Progress cb) {
        Context c = ctx.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            String err;
            try {
                File apk = new File(c.getCacheDir(), "update.apk");
                download(url, apk, p -> main.post(() -> cb.progress(p)));
                main.post(() -> cb.progress(-1));
                err = install(c, apk);
            } catch (Exception e) {
                CrashLog.note(c, "自动更新", e);
                err = "下载失败：" + e.getMessage();
            }
            if (err != null) {
                String why = err;
                main.post(() -> cb.failed(why));
            }
        }, "guangying-updater").start();
    }

    private interface IntCallback {
        void call(int v);
    }

    private static void download(String url, File to, IntCallback progress) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url + "?t=" + System.currentTimeMillis()).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        try {
            if (conn.getResponseCode() != 200) throw new IOException("HTTP " + conn.getResponseCode());
            long total = conn.getContentLengthLong();
            try (InputStream in = conn.getInputStream(); OutputStream out = new FileOutputStream(to)) {
                byte[] buf = new byte[64 * 1024];
                long done = 0;
                int last = -1;
                for (int n; (n = in.read(buf)) > 0; ) {
                    out.write(buf, 0, n);
                    done += n;
                    int p = total > 0 ? (int) (done * 100 / total) : 0;
                    if (p != last) {
                        last = p;
                        progress.call(p);
                    }
                }
            }
        } finally {
            conn.disconnect();
        }
    }

    /** 检查是光影的新版本后交给系统安装；返回 null 表示已提交，等系统弹确认。 */
    private static String install(Context c, File apk) throws IOException, PackageManager.NameNotFoundException {
        PackageManager pm = c.getPackageManager();
        PackageInfo info = pm.getPackageArchiveInfo(apk.getPath(), 0);
        if (info == null) return "下载的文件不是完整的安装包，请稍后重试";
        if (!c.getPackageName().equals(info.packageName)) return "下载的不是光影的安装包";
        long current = pm.getPackageInfo(c.getPackageName(), 0).getLongVersionCode();
        if (info.getLongVersionCode() <= current) return "已经是最新版本";

        PackageInstaller installer = pm.getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(c.getPackageName());
        int id = installer.createSession(params);
        try (PackageInstaller.Session s = installer.openSession(id)) {
            try (InputStream in = new FileInputStream(apk); OutputStream out = s.openWrite("base.apk", 0, apk.length())) {
                byte[] buf = new byte[64 * 1024];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                s.fsync(out);
            }
            Intent result = new Intent(c, Updater.class).setAction(ACTION);
            PendingIntent pi = PendingIntent.getBroadcast(c, id, result,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            s.commit(pi.getIntentSender());
        } finally {
            //noinspection ResultOfMethodCallIgnored
            apk.delete();
        }
        return null;
    }

    @Override
    public void onReceive(Context c, Intent i) {
        int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @SuppressWarnings("deprecation") // 带类型的版本要 Android 13，App 最低支持 11
            Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) c.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } else if (status != PackageInstaller.STATUS_SUCCESS) {
            String msg = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            Toast.makeText(c, "更新没有完成" + (msg == null ? "" : "：" + msg), Toast.LENGTH_LONG).show();
        }
    }
}
