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

    /** 在后台线程调用：把当前图片设成系统壁纸 */
    public static boolean apply(Context ctx) {
        try {
            Photo p = Wall.current(ctx);
            if (p == null) return false;
            File src = Site.photoFile(ctx, p);
            if (src == null) return false;
            DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
            int w = Math.max(dm.widthPixels, dm.heightPixels) > 3000 ? dm.widthPixels / 2 : dm.widthPixels;
            int h = Math.max(dm.widthPixels, dm.heightPixels) > 3000 ? dm.heightPixels / 2 : dm.heightPixels;
            Bitmap full = WallArt.decode(src, w, h);
            if (full == null) return false;
            Bitmap blur = null;
            if (p.style.equals("gallery") && !p.blur.isEmpty() && Site.ensure(ctx, p.blur)) {
                blur = WallArt.decode(io.github.guangying.data.Library.file(ctx, p.blur), 64, 64);
            }
            Bitmap out = WallArt.compose(full, blur, p, new Prefs(ctx).position(), w, h, ctx.getAssets());
            WallpaperManager wm = WallpaperManager.getInstance(ctx);
            wm.setBitmap(out, null, true, WallpaperManager.FLAG_SYSTEM);
            return true;
        } catch (IOException | RuntimeException e) {
            CrashLog.note(ctx, "设置静态壁纸", e);
            return false;
        }
    }

    private static PendingIntent tick(Context ctx) {
        Intent i = new Intent(ctx, Tick.class).setAction("io.github.guangying.STATIC_TICK");
        return PendingIntent.getBroadcast(ctx, 7, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** 开始 / 重新安排下一次换壁纸 */
    public static void schedule(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long at = System.currentTimeMillis() + Math.max(1, new Prefs(ctx).intervalMinutes()) * 60000L;
        am.setAndAllowWhileIdle(AlarmManager.RTC, at, tick(ctx));
    }

    public static void cancel(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(tick(ctx));
    }

    /** 打开兼容模式：马上换一次，并开始定时 */
    public static void enable(Context ctx) {
        new Prefs(ctx).setStaticMode(true);
        schedule(ctx);
        Wall.advance(ctx);
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
            schedule(app);
            if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) return;
            Wall.advance(app);                 // 换图后 Wall 会自动把新图设成壁纸
        }
    }
}
