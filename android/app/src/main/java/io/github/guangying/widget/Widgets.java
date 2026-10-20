package io.github.guangying.widget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.DisplayMetrics;

import java.util.Calendar;

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

    public static final int CALENDAR = 0, STORY = 1;
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

    public static Bitmap render(Context ctx, int kind, Look look, int w, int h) {
        Fonts f = fonts(ctx);
        if (kind == STORY) return StoryArt.render(f, w, h, look);
        Calendar now = Calendar.getInstance();
        CalendarArt.Spec spec = new CalendarArt.Spec(w, h, look, now);
        spec.motto = Mottos.forDay(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH));
        return CalendarArt.render(f, spec);
    }

    /** 这个组件当前可用的像素大小（按当前横竖屏取桌面给的尺寸范围） */
    public static int[] sizeOf(Context ctx, AppWidgetManager mgr, int id) {
        Bundle o = mgr.getAppWidgetOptions(id);
        boolean land = ctx.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        int wDp = o.getInt(land ? AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH : AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
        int hDp = o.getInt(land ? AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT : AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
        if (wDp <= 0) wDp = 300;
        if (hDp <= 0) hDp = 300;
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        float w = wDp * dm.density, h = hDp * dm.density;
        // 桌面对远程位图的内存有上限，太大时按比例缩小（桌面会自己拉伸到组件大小）
        float cap = 3.2e6f;
        if (w * h > cap) {
            float k = (float) Math.sqrt(cap / (w * h));
            w *= k;
            h *= k;
        }
        return new int[]{Math.round(w), Math.round(h)};
    }

    /** 刷新全部月历、画意组件 */
    public static void refreshAll(Context ctx) {
        Context app = ctx.getApplicationContext();
        AppWidgetManager mgr = AppWidgetManager.getInstance(app);
        Look look = null;
        for (int kind = 0; kind < 2; kind++) {
            Class<?> cls = kind == CALENDAR ? CalendarWidget.class : StoryWidget.class;
            int[] ids = mgr.getAppWidgetIds(new ComponentName(app, cls));
            if (ids.length == 0) continue;
            if (look == null) look = look(app);
            for (int id : ids) ThemedWidget.update(app, mgr, id, kind, look);
        }
    }
}
