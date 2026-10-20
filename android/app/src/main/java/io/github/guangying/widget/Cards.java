package io.github.guangying.widget;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.RemoteViews;

import io.github.guangying.R;
import io.github.guangying.render.Look;

/**
 * 桌面组件的卡片：背景、质感、边框都用原生视图，由系统按组件的真实大小铺满，圆角裁切、紧贴边缘；
 * 文字内容是一张透明位图，在卡片中间等比例显示，不会被拉伸，也不会留出空边。
 */
final class Cards {
    private Cards() {}

    static RemoteViews build(Context ctx, Look L, int kind, Bitmap content, Bitmap backdrop) {
        boolean story = kind == Widgets.STORY;
        String st = L.style;
        int layout = R.layout.widget_image;
        if (st.equals("glass")) layout = R.layout.widget_card_r24;
        else if (st.equals("paper") || st.equals("vpoem")) layout = R.layout.widget_card_r4;
        else if (st.equals("gallery")) layout = R.layout.widget_card_r12;
        RemoteViews v = new RemoteViews(ctx.getPackageName(), layout);
        v.setImageViewBitmap(R.id.image, content);

        switch (st) {
            case "glass":
                if (backdrop != null) {
                    v.setViewVisibility(R.id.backdrop, View.VISIBLE);
                    v.setImageViewBitmap(R.id.backdrop, backdrop);
                    v.setViewVisibility(R.id.tint, View.VISIBLE);
                    v.setInt(R.id.tint, "setBackgroundColor", L.light ? 0x40101218 : 0x66FFFFFF);
                } else {
                    v.setViewVisibility(R.id.fill, View.VISIBLE);
                    v.setInt(R.id.fill, "setBackgroundColor", L.light ? 0xE016181F : 0xE6FFFFFF);
                }
                layer(v, R.id.sheen, L.light ? R.drawable.sheen_glass_light : R.drawable.sheen_glass_dark);
                grain(v, 0.09f);
                layer(v, R.id.border, R.drawable.border_glass);
                break;
            case "paper":
            case "vpoem":
                v.setViewVisibility(R.id.fill, View.VISIBLE);
                v.setInt(R.id.fill, "setBackgroundColor", L.bg);
                layer(v, R.id.sheen, R.drawable.sheen_paper);
                grain(v, 0.2f);
                layer(v, R.id.border, st.equals("vpoem") ? R.drawable.border_ruled : R.drawable.border_paper);
                break;
            case "gallery":
                if (story) {
                    v.setViewVisibility(R.id.fill, View.VISIBLE);
                    v.setInt(R.id.fill, "setBackgroundColor", 0xF0FAF6EE);
                } else {
                    if (backdrop != null) {
                        v.setViewVisibility(R.id.backdrop, View.VISIBLE);
                        v.setImageViewBitmap(R.id.backdrop, backdrop);
                    }
                    v.setViewVisibility(R.id.fill, View.VISIBLE);
                    v.setInt(R.id.fill, "setBackgroundColor", Look.withAlpha(L.bg, backdrop != null ? 0.72f : 0.8f));
                    grain(v, 0.1f);
                    layer(v, R.id.border, R.drawable.border_plaque);
                }
                break;
            case "band":
                v.setViewVisibility(R.id.fill, View.VISIBLE);
                v.setInt(R.id.fill, "setBackgroundColor", Look.withAlpha(L.bg, 0.94f));
                grain(v, 0.1f);
                v.setViewVisibility(R.id.line, View.VISIBLE);
                v.setInt(R.id.line, "setBackgroundColor", L.accent);
                break;
            case "magazine":
                {
                    boolean left = L.leftAligned();
                    int res = L.light ? (left ? R.drawable.scrim_left_dark : R.drawable.scrim_right_dark)
                            : (left ? R.drawable.scrim_left_light : R.drawable.scrim_right_light);
                    layer(v, R.id.scrim, res);
                }
                break;
            default:
                break;
        }
        // 点组件任何位置都换下一张壁纸
        Intent next = new Intent(ctx, ControlReceiver.class).setAction(ThemedWidget.ACTION_NEXT);
        v.setOnClickPendingIntent(R.id.root,
                PendingIntent.getBroadcast(ctx, 0, next, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return v;
    }

    private static void layer(RemoteViews v, int id, int drawable) {
        v.setViewVisibility(id, View.VISIBLE);
        v.setInt(id, "setBackgroundResource", drawable);
    }

    private static void grain(RemoteViews v, float alpha) {
        v.setViewVisibility(R.id.tex, View.VISIBLE);
        v.setFloat(R.id.tex, "setAlpha", alpha);
    }
}
