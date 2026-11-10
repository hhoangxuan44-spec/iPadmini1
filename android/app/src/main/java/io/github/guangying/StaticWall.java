package io.github.guangying;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.util.DisplayMetrics;

import java.io.File;
import java.io.IOException;

import io.github.guangying.data.Photo;
import io.github.guangying.data.Prefs;
import io.github.guangying.render.WallArt;

/**
 * 兼容模式：有些系统（定制的掌机、平板 ROM）的壁纸设置里只有静态壁纸，没有动态壁纸入口。
 * 这时由 App 自己把当前图片合成好、设成系统的静态壁纸，并每隔几分钟换一张。
 * 没有淡入淡出和缓慢推拉，其余（桌面组件随图片变样式、应景选图、画廊装裱）都一样。
 */
public final class StaticWall {
    private StaticWall() {}

    /** 系统是否带动态壁纸功能 */
    public static boolean liveSupported(Context ctx) {
        return ctx.getPackageManager().hasSystemFeature(android.content.pm.PackageManager.FEATURE_LIVE_WALLPAPER);
    }

    /** 把这张图合成好、设成系统壁纸。桌面设成功就算成功；锁屏单独设，失败不影响桌面。 */
    static boolean apply(Context ctx, Photo p) {
        Prefs pr = new Prefs(ctx);
        try {
            File src = Site.photoFile(ctx, p);
            if (src == null) {
                pr.setStaticResult(false, "图片还没下载到本地");
                return false;
            }
            DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
            int w = Math.max(dm.widthPixels, dm.heightPixels) > 3000 ? dm.widthPixels / 2 : dm.widthPixels;
            int h = Math.max(dm.widthPixels, dm.heightPixels) > 3000 ? dm.heightPixels / 2 : dm.heightPixels;
            Bitmap full = WallArt.decode(src, w, h);
            if (full == null) {
                pr.setStaticResult(false, "图片文件打不开");
                return false;
            }
            Bitmap blur = null;
            if (p.style.equals("gallery") && !p.blur.isEmpty() && io.github.guangying.data.Library.file(ctx, p.blur).isFile()) {
                blur = WallArt.decode(io.github.guangying.data.Library.file(ctx, p.blur), 64, 64);
            }
            Bitmap out = WallArt.compose(full, blur, p, pr.position(), w, h, ctx.getAssets());
            WallpaperManager wm = WallpaperManager.getInstance(ctx);
            wm.setBitmap(out, null, true, WallpaperManager.FLAG_SYSTEM);
            String note = "";
            try {
                // 锁屏也跟着换（只设桌面时，系统会把旧图留作单独的锁屏壁纸）。有的系统不让后台改锁屏：失败就只换桌面
                wm.setBitmap(out, null, true, WallpaperManager.FLAG_LOCK);
            } catch (IOException | RuntimeException e) {
                note = "锁屏壁纸没换成：" + e.getClass().getSimpleName();
            }
            pr.setStaticResult(true, note);
            return true;
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            CrashLog.note(ctx, "设置静态壁纸", e);
            pr.setStaticResult(false, e.getClass().getSimpleName() + (e.getMessage() == null ? "" : "：" + e.getMessage()));
            return false;
        }
    }

    private static final Object LOCK = new Object();

    /**
     * 换下一张（在后台线程调用）。先把新图设成壁纸，成功了才把「当前图片」指到新图并通知组件——
     * 这样壁纸和组件永远对得上；失败时保持原样，两分钟后重试。
     */
    public static boolean change(Context ctx) {
        Context app = ctx.getApplicationContext();
        synchronized (LOCK) {
            Photo p = Wall.chooseNext(app);
            boolean ok = p != null && apply(app, p);
            if (p == null) new Prefs(app).setStaticResult(false, "图库还没下载（需要先联网打开一次）");
            if (ok) Wall.commit(app, p);
            scheduleIn(app, ok ? Math.max(1, new Prefs(app).intervalMinutes()) * 60000L : 2 * 60000L);
            return ok;
        }
    }

    public static void changeAsync(final Context ctx) {
        final Context app = ctx.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                change(app);
                Wall.prefetchNext(app);
            }
        }, "guangying-static").start();
    }

    /**
     * 保底检查：闹钟可能被系统的省电策略取消或推迟。每次 App、组件被唤起时调用——
     * 发现早该换图却没换，就马上补换一次并重新登记闹钟。
     */
    public static void ensureFresh(Context ctx) {
        Context app = ctx.getApplicationContext();
        Prefs pr = new Prefs(app);
        if (!pr.staticMode()) return;
        long interval = Math.max(1, pr.intervalMinutes()) * 60000L;
        long since = System.currentTimeMillis() - pr.staticTriedAt();
        if (since > interval + 90000L || since < 0) changeAsync(app);
        else scheduleIn(app, Math.max(15000L, interval - since));
    }

    private static PendingIntent tick(Context ctx) {
        Intent i = new Intent(ctx, Tick.class).setAction("io.github.guangying.STATIC_TICK");
        return PendingIntent.getBroadcast(ctx, 7, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** 开始 / 重新安排下一次换壁纸 */
    public static void schedule(Context ctx) {
        scheduleIn(ctx, Math.max(1, new Prefs(ctx).intervalMinutes()) * 60000L);
    }

    private static void scheduleIn(Context ctx, long delayMs) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delayMs, tick(ctx));
    }

    public static void cancel(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(tick(ctx));
    }

    /** 打开兼容模式：马上换一次，并开始定时 */
    public static void enable(Context ctx) {
        new Prefs(ctx).setStaticMode(true);
        changeAsync(ctx);
    }

    public static void disable(Context ctx) {
        new Prefs(ctx).setStaticMode(false);
        cancel(ctx);
    }

    /** 闹钟到点、开机、升级后触发 */
    public static final class Tick extends BroadcastReceiver {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            final Context app = ctx.getApplicationContext();
            if (!new Prefs(app).staticMode()) return;
            if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
                ensureFresh(app);
                return;
            }
            // goAsync：告诉系统这条广播还没处理完，换图做完之前别把进程冻结或回收
            final PendingResult pr = goAsync();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        change(app);
                    } finally {
                        pr.finish();
                    }
                    Wall.prefetchNext(app);          // 下一张提前下好：换图本身不依赖网络
                }
            }, "guangying-static").start();
        }
    }
}
