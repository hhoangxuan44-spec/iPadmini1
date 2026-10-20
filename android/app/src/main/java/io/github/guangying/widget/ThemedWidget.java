package io.github.guangying.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.RemoteViews;

import io.github.guangying.R;
import io.github.guangying.render.Look;

/** 月历、画意组件的共同部分：画位图、点一下换下一张壁纸。 */
public abstract class ThemedWidget extends AppWidgetProvider {
    public static final String ACTION_NEXT = "io.github.guangying.ACTION_NEXT";

    abstract int kind();

    static void update(Context ctx, AppWidgetManager mgr, int id, int kind, Look look) {
        int[] sz = Widgets.sizeOf(ctx, mgr, id);
        Bitmap bm = Widgets.render(ctx, kind, look, sz[0], sz[1]);
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_image);
        rv.setImageViewBitmap(R.id.image, bm);
        Intent next = new Intent(ctx, ControlReceiver.class).setAction(ACTION_NEXT);
        rv.setOnClickPendingIntent(R.id.image,
                PendingIntent.getBroadcast(ctx, 0, next, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        mgr.updateAppWidget(id, rv);
    }

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        Look look = Widgets.look(ctx);
        for (int id : ids) update(ctx, mgr, id, kind(), look);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context ctx, AppWidgetManager mgr, int id, Bundle newOptions) {
        update(ctx, mgr, id, kind(), Widgets.look(ctx));
    }

    @Override
    public void onEnabled(Context ctx) {
        // 第一次放上桌面：确保图库已经下载并选好了当前图片
        io.github.guangying.Wall.advanceIfEmpty(ctx);
    }
}
