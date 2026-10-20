package io.github.guangying.render;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;

import io.github.guangying.Site;
import io.github.guangying.Wall;
import io.github.guangying.data.Library;
import io.github.guangying.data.Photo;
import io.github.guangying.data.Prefs;
import io.github.guangying.widget.Widgets;

/** 主界面的预览：壁纸 + 月历组件 + 画意组件，和真正放在桌面上时用的是同一套绘制。 */
public final class Preview {
    private Preview() {}

    /** 组件在预览里的位置（占整屏的比例）：{x, y, w, h}，依次为月历、画意 */
    private static float[][] rects(String pos) {
        switch (pos) {
            case "tr": return new float[][]{{.68f, .05f, .30f, .62f}, {.02f, .62f, .38f, .34f}};
            case "bl": return new float[][]{{.02f, .33f, .30f, .62f}, {.62f, .05f, .36f, .34f}};
            case "br": return new float[][]{{.68f, .33f, .30f, .62f}, {.02f, .05f, .36f, .34f}};
            case "l": return new float[][]{{.0f, .0f, .30f, 1f}, {.62f, .62f, .36f, .34f}};
            case "r": return new float[][]{{.70f, .0f, .30f, 1f}, {.02f, .62f, .36f, .34f}};
            default: return new float[][]{{.02f, .05f, .30f, .62f}, {.62f, .62f, .36f, .34f}};
        }
    }

    /** 在后台线程调用。图片还没下载好时用渐变色代替壁纸。 */
    public static Bitmap render(Context ctx, int w, int h) {
        Photo p = Wall.current(ctx);
        String pos = new Prefs(ctx).position();
        Bitmap out = null;
        if (p != null && Site.ensure(ctx, p.large)) {
            Bitmap full = WallArt.decode(Library.file(ctx, p.large), w, h);
            Bitmap blur = null;
            if (p.style.equals("gallery") && !p.blur.isEmpty() && Site.ensure(ctx, p.blur)) {
                blur = WallArt.decode(Library.file(ctx, p.blur), 64, 64);
            }
            if (full != null) out = WallArt.compose(full, blur, p, pos, w, h);
        }
        if (out == null) {
            out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Paint g = new Paint();
            g.setShader(new LinearGradient(0, 0, w, h, 0xFF2B3A55, 0xFF8A4B3A, Shader.TileMode.CLAMP));
            new Canvas(out).drawRect(0, 0, w, h, g);
        }
        Look look = Look.of(p != null ? p : Widgets.fallback(), pos);
        float[][] r = rects(pos);
        Canvas c = new Canvas(out);
        for (int kind = 0; kind < 2; kind++) {
            int rw = Math.round(r[kind][2] * w), rh = Math.round(r[kind][3] * h);
            Bitmap bm = Widgets.render(ctx, kind, look, rw, rh);
            c.drawBitmap(bm, Math.round(r[kind][0] * w), Math.round(r[kind][1] * h), null);
        }
        return out;
    }
}
