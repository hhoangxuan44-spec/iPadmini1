package io.github.guangying.render;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.io.File;

import io.github.guangying.data.Photo;

/** 壁纸层：只有图片，没有日历（日历在上层的桌面组件里）。 */
public final class WallArt {
    private WallArt() {}

    /** 解码图片，只要能盖满 w×h 就尽量缩小，省内存。 */
    public static Bitmap decode(File f, int w, int h) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        if (o.outWidth <= 0) return null;
        int sample = 1;
        while (o.outWidth / (sample * 2) >= w && o.outHeight / (sample * 2) >= h) sample *= 2;
        o.inJustDecodeBounds = false;
        o.inSampleSize = sample;
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeFile(f.getPath(), o);
    }

    private static void cover(Canvas c, Bitmap b, int w, int h, float zoom) {
        float s = Math.max(w / (float) b.getWidth(), h / (float) b.getHeight()) * zoom;
        float dw = b.getWidth() * s, dh = b.getHeight() * s;
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        c.drawBitmap(b, null, new RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f), p);
    }

    /**
     * 合成一整屏壁纸。普通照片铺满；画作（gallery 风格）完整挂在模糊的墙面上，留出一侧给桌面组件。
     * userPos：日历组件所在位置（tl tr bl br l r），画作会让到另一侧。
     */
    public static Bitmap compose(Bitmap full, Bitmap blur, Photo p, String userPos, int w, int h) {
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(0xFF000000);
        if (!p.style.equals("gallery")) {
            cover(c, full, w, h, 1f);
            return out;
        }
        // 墙面：同一幅画的模糊放大
        c.drawColor(p.bg);
        cover(c, blur != null ? blur : full, w, h, 1.08f);
        Paint shade = new Paint();
        shade.setColor(p.bg);
        shade.setAlpha(97);                                    // 0.38
        c.drawRect(0, 0, w, h, shade);
        boolean calLeft = userPos.endsWith("l");
        Paint spot = new Paint();
        float sx = (calLeft ? 0.62f : 0.38f) * w;
        spot.setShader(new RadialGradient(sx, 0, Math.max(w, h) * 0.72f, 0x29FFF0DC, 0x00FFF0DC, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, spot);

        boolean portrait = h > w * 1.05f;
        float rem = 16f * (portrait ? (w < 600 ? w / 470f : Math.min(w / 768f, h / 1024f)) : Math.min(w / 1024f, h / 768f));
        float x0, y0, aw, ah;
        if (portrait) {
            boolean calTop = userPos.startsWith("t");
            x0 = 1.6f * rem;
            aw = w - 3.2f * rem;
            ah = h * 0.5f;
            y0 = calTop ? h - ah - 2f * rem : 2f * rem;
        } else {
            float side = 24.5f * rem;
            x0 = calLeft ? side : 2.6f * rem;
            y0 = 2.6f * rem;
            aw = w - side - 2.6f * rem;
            ah = h - 5.2f * rem;
        }
        float mat = 1.5f * rem, frame = 0.45f * rem, pad = 2 * (mat + frame);
        float ratio = p.w / (float) p.h;
        float iw = Math.min(aw - pad, (ah - pad) * ratio), ih = iw / ratio;
        float ax = x0 + (aw - iw - pad) / 2f, ay = y0 + (ah - ih - pad) / 2f;     // 画框外沿左上角
        float fw = iw + pad, fh = ih + pad;

        // 画框的投影
        Paint sh = new Paint(Paint.ANTI_ALIAS_FLAG);
        sh.setColor(0xFF000000);
        sh.setShadowLayer(2.4f * rem, 0, 1.4f * rem, 0xB8000000);
        c.drawRect(ax, ay, ax + fw, ay + fh, sh);
        Paint fr = new Paint();
        fr.setColor(0xFF2B241E);
        c.drawRect(ax, ay, ax + fw, ay + fh, fr);
        Paint mt = new Paint();
        mt.setColor(0xFFF3EEE3);
        c.drawRect(ax + frame, ay + frame, ax + fw - frame, ay + fh - frame, mt);
        RectF img = new RectF(ax + frame + mat, ay + frame + mat, ax + fw - frame - mat, ay + fh - frame - mat);
        Paint ip = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        c.drawBitmap(full, null, img, ip);
        Paint edge = new Paint();
        edge.setStyle(Paint.Style.STROKE);
        edge.setColor(0x1F000000);
        edge.setStrokeWidth(Math.max(1f, rem * 0.07f));
        c.drawRect(img, edge);
        return out;
    }
}
