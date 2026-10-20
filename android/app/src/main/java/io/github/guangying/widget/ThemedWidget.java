package io.github.guangying.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Build;
import android.util.ArrayMap;
import android.util.SizeF;
import android.widget.RemoteViews;

import java.util.List;
import java.util.Map;

import io.github.guangying.render.Look;

/** 月历、画意组件的共同部分：画位图、点一下换下一张壁纸。 */
public abstract class ThemedWidget extends AppWidgetProvider {
    public static final String ACTION_NEXT = "io.github.guangying.ACTION_NEXT";

    abstract int kind();

    /**
     * 画好位图交给桌面。位图用 fitCenter 显示：就算桌面实际给的格子和估计的大小略有出入，
     * 也只会留一点边，不会把字拉伸变形。Android 12 以上桌面会给出可能的几种尺寸，每种各画一张，桌面挑最合适的。
     */
    static void update(Context ctx, AppWidgetManager mgr, int id, int kind, Look look) {
        Bitmap backdrop = Widgets.backdrop(ctx, look, kind);
        List<float[]> sizes = Widgets.sizesDp(ctx, mgr, id);
        float density = ctx.getResources().getDisplayMetrics().density;
        if (Build.VERSION.SDK_INT >= 31 && sizes.size() > 1) {
            Map<SizeF, RemoteViews> map = new ArrayMap<>();
            for (float[] s : sizes) {
                int[] px = Widgets.fitPx(s[0] * density, s[1] * density, 2.6e6f);
                map.put(new SizeF(s[0], s[1]), Cards.build(ctx, look, kind, Widgets.render(ctx, kind, look, px[0], px[1], backdrop, true), backdrop));
            }
            mgr.updateAppWidget(id, new RemoteViews(map));
        } else {
            float[] s = sizes.get(0);
            int[] px = Widgets.fitPx(s[0] * density, s[1] * density, 3.2e6f);
            mgr.updateAppWidget(id, Cards.build(ctx, look, kind, Widgets.render(ctx, kind, look, px[0], px[1], backdrop, true), backdrop));
        }
    }

    @Override
    public void onUpdate(final Context ctx, final AppWidgetManager mgr, final int[] ids) {
        Widgets.async(ctx, goAsync(), new Runnable() {
            @Override
            public void run() {
                Look look = Widgets.look(ctx);
                for (int id : ids) update(ctx, mgr, id, kind(), look);
            }
        });
    }

    @Override
    public void onAppWidgetOptionsChanged(final Context ctx, final AppWidgetManager mgr, final int id, Bundle newOptions) {
        Widgets.async(ctx, goAsync(), new Runnable() {
            @Override
            public void run() {
                update(ctx, mgr, id, kind(), Widgets.look(ctx));
            }
        });
    }

    @Override
    public void onEnabled(Context ctx) {
        // 第一次放上桌面：确保图库已经下载并选好了当前图片
        io.github.guangying.Wall.advanceIfEmpty(ctx);
    }
}
