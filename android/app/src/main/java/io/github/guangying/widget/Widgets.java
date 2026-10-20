package io.github.guangying.widget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.DisplayMetrics;

import android.util.SizeF;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import io.github.guangying.Wall;
import io.github.guangying.core.Mottos;
import io.github.guangying.data.Photo;
import io.github.guangying.data.Prefs;
import io.github.guangying.render.CalendarArt;
import io.github.guangying.render.Fonts;
import io.github.guangying.render.Look;
import io.github.guangying.render.StoryArt;

/** 桌面组件：按当前图片的风格画出位图，交给桌面显示。 */
public final class Widgets {
    private Widgets() {}

    public static final int CALENDAR = 0, STORY = 1, CLOCK = 2, WEEK = 3, QUOTE = 4;
    static final Class<?>[] PROVIDERS = {CalendarWidget.class, StoryWidget.class, ClockWidget.class, WeekWidget.class,
            QuoteWidget.class};
    private static Fonts fonts;

    public static synchronized Fonts fonts(Context ctx) {
        if (fonts == null) fonts = Fonts.fromAssets(ctx.getApplicationContext().getAssets());
        return fonts;
    }

    /** 没有图库（第一次使用、还没联网）时用的样子 */
    public static Photo fallback() {
        Photo p = new Photo();
        p.id = "";
        p.style = "glass";
        p.title = "光影月历";
        p.sub = "第一次使用请先联网";
        p.desc = "联网打开一次光影月历，会下载图库，之后这里会显示每张壁纸的标题、诗句和描述。";
        return p;
    }

    public static Look look(Context ctx) {
        Photo p = Wall.current(ctx);
        return Look.of(p != null ? p : fallback(), new Prefs(ctx).position());
    }

    public static Bitmap render(Context ctx, int kind, Look look, int w, int h, Bitmap backdrop, boolean contentOnly) {
        Fonts f = fonts(ctx);
        if (kind == STORY) return StoryArt.render(f, w, h, look, backdrop, contentOnly);
        Calendar now = Calendar.getInstance();
        CalendarArt.Spec spec = new CalendarArt.Spec(w, h, look, now);
        spec.backdrop = backdrop;
        spec.contentOnly = contentOnly;
        spec.part = kind == CLOCK ? CalendarArt.CLOCK : kind == WEEK ? CalendarArt.WEEK : kind == QUOTE ? CalendarArt.QUOTE
                : CalendarArt.FULL;
        spec.motto = Mottos.forDay(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH));
        return CalendarArt.render(f, spec);
    }

    /**
     * 组件背后那一块壁纸（已模糊），让玻璃风格有真正的磨砂质感。
     * 组件在桌面上的确切位置拿不到，按用户选的位置估计——模糊之后差一点也看不出来。
     */
    public static Bitmap backdrop(Context ctx, Look look, int kind) {
        Photo p = look.photo;
        if (p == null || p.blur == null || p.blur.isEmpty()) return null;
        java.io.File f = io.github.guangying.data.Library.file(ctx, p.blur);
        if (!f.isFile()) return null;
        Bitmap blur = io.github.guangying.render.WallArt.decode(f, 64, 64);
        if (blur == null) return null;
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        boolean land = dm.widthPixels >= dm.heightPixels;
        int rw = 256, rh = Math.round(rw * (land ? 0.625f : 1.6f));
        Bitmap ref = Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(ref);
        float sc = Math.max(rw / (float) blur.getWidth(), rh / (float) blur.getHeight()) * 1.08f;
        float dw = blur.getWidth() * sc, dh = blur.getHeight() * sc;
        android.graphics.Paint pt = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
        c.drawBitmap(blur, null, new android.graphics.RectF((rw - dw) / 2, (rh - dh) / 2, (rw + dw) / 2, (rh + dh) / 2), pt);
        float[] r = io.github.guangying.render.Preview.rects(look.pos)[kind];
        int x = Math.max(0, Math.round(r[0] * rw)), y = Math.max(0, Math.round(r[1] * rh));
        int w = Math.min(rw - x, Math.round(r[2] * rw)), h = Math.min(rh - y, Math.round(r[3] * rh));
        return Bitmap.createBitmap(ref, x, y, Math.max(1, w), Math.max(1, h));
    }

    /** 桌面可能给这个组件的尺寸（dp）：Android 12 起桌面会给出一组，之前只有当前横竖屏下的最小、最大值。至多返回 3 个，最大的在前。 */
    @SuppressWarnings("deprecation")
    public static List<float[]> sizesDp(Context ctx, AppWidgetManager mgr, int id) {
        Bundle o = mgr.getAppWidgetOptions(id);
        List<float[]> out = new ArrayList<>();
        ArrayList<SizeF> sizes = o.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES);
        if (sizes != null) {
            for (SizeF s : sizes) if (s.getWidth() > 20 && s.getHeight() > 20) out.add(new float[]{s.getWidth(), s.getHeight()});
        }
        if (out.isEmpty()) {
            boolean land = ctx.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
            float w = o.getInt(land ? AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH : AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
            float h = o.getInt(land ? AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT : AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
            out.add(new float[]{w > 20 ? w : 300, h > 20 ? h : 300});
        }
        Collections.sort(out, (a, b) -> Float.compare(b[0] * b[1], a[0] * a[1]));
        return out.size() > 3 ? new ArrayList<>(out.subList(0, 3)) : out;
    }

    /** 像素大小，太大时按比例缩小（桌面对远程位图的内存有上限） */
    public static int[] fitPx(float w, float h, float cap) {
        if (w * h > cap) {
            float k = (float) Math.sqrt(cap / (w * h));
            w *= k;
            h *= k;
        }
        return new int[]{Math.max(1, Math.round(w)), Math.max(1, Math.round(h))};
    }

    /** 刷新全部月历、画意组件 */
    public static void refreshAll(Context ctx) {
        Context app = ctx.getApplicationContext();
        AppWidgetManager mgr = AppWidgetManager.getInstance(app);
        Look look = null;
        for (int kind = 0; kind < PROVIDERS.length; kind++) {
            Class<?> cls = PROVIDERS[kind];
            int[] ids = mgr.getAppWidgetIds(new ComponentName(app, cls));
            if (ids.length == 0) continue;
            if (look == null) look = look(app);
            for (int id : ids) ThemedWidget.update(app, mgr, id, kind, look);
        }
    }
}
