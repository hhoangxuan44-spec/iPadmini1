package io.github.guangying.render;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
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

    /** 测试用：没有 AssetManager 时从这个目录读材质贴图 */
    public static java.io.File texDir;

    private static Bitmap tex(android.content.res.AssetManager am, String name) {
        if (am == null) return texDir == null ? null : BitmapFactory.decodeFile(new java.io.File(texDir, name).getPath());
        try (java.io.InputStream in = am.open("tex/" + name)) {
            return BitmapFactory.decodeStream(in);
        } catch (java.io.IOException e) {
            return null;
        }
    }

    private static Paint tile(Bitmap b, float alpha, float scale) {
        Paint p = new Paint();
        if (b == null) return null;
        BitmapShader sh = new BitmapShader(b, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.setScale(scale, scale);
        sh.setLocalMatrix(m);
        p.setShader(sh);
        p.setAlpha(Math.round(255 * alpha));
        return p;
    }

    /** 四周略暗、再盖一层极细的胶片颗粒：照片更像「冲印出来的」 */
    private static void finish(Canvas c, int w, int h, Photo p, android.content.res.AssetManager am, boolean grain) {
        Paint v = new Paint();
        float r = (float) Math.hypot(w, h) * 0.62f;
        v.setShader(new RadialGradient(w * 0.5f, h * 0.46f, r, new int[]{0x00000000, 0x00000000, Look.withAlpha(p.sh, 0.34f)},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, v);
        if (grain) {
            Paint g = tile(tex(am, "grain.png"), 0.3f, Math.max(1f, Math.min(w, h) / 900f));
            if (g != null) c.drawRect(0, 0, w, h, g);
        }
    }

    public static Bitmap compose(Bitmap full, Bitmap blur, Photo p, String userPos, int w, int h) {
        return compose(full, blur, p, userPos, w, h, null);
    }

    /**
     * 合成一整屏壁纸。普通照片铺满；画作（gallery 风格）完整挂在灰泥墙上：射灯、胡桃木线脚画框、卡纸斜切、玻璃反光、
     * 落在墙上的影子都随这张画的光来；留出一侧给桌面组件。
     * userPos：日历组件所在位置（tl tr bl br l r），画作会让到另一侧。am 提供材质贴图，可为 null。
     */
    public static Bitmap compose(Bitmap full, Bitmap blur, Photo p, String userPos, int w, int h,
                                 android.content.res.AssetManager am) {
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(0xFF000000);
        if (!p.style.equals("gallery")) {
            cover(c, full, w, h, 1f);
            finish(c, w, h, p, am, true);
            return out;
        }
        // 墙面：同一幅画的模糊放大 + 灰泥
        c.drawColor(p.bg);
        cover(c, blur != null ? blur : full, w, h, 1.08f);
        Paint shade = new Paint();
        shade.setColor(p.bg);
        shade.setAlpha(97);                                    // 0.38
        c.drawRect(0, 0, w, h, shade);
        Paint plaster = tile(tex(am, "plaster.png"), 0.9f, Math.max(1f, Math.min(w, h) / 800f));
        if (plaster != null) c.drawRect(0, 0, w, h, plaster);
        boolean calLeft = userPos.endsWith("l");

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
        float mat = 1.6f * rem, frame = 0.62f * rem, pad = 2 * (mat + frame);
        float ratio = p.w / (float) p.h;
        float iw = Math.min(aw - pad, (ah - pad) * ratio), ih = iw / ratio;
        float ax = x0 + (aw - iw - pad) / 2f, ay = y0 + (ah - ih - pad) / 2f;     // 画框外沿左上角
        float fw = iw + pad, fh = ih + pad;

        // 光：从画的中心看向光源
        float cxn = (ax + fw / 2f) / w, cyn = (ay + fh / 2f) / h;
        float vx = p.lx - cxn, vy = Math.min(p.ly, 0.6f) - cyn - 0.3f;
        float len = (float) Math.sqrt(vx * vx + vy * vy);
        float dx = len < 0.07f ? -0.45f : vx / len, dy = len < 0.07f ? -0.89f : vy / len;

        // 射灯：从画的上方打下来，颜色取这张照片的高光色，偏暖
        int warm = Look.mix(p.hl, 0xFFFFE2B4, 0.55f);
        Paint spot = new Paint();
        c.save();
        c.translate(ax + fw / 2f, -0.08f * h);
        c.scale(w * 0.62f * 0.5f, h * 0.78f);
        spot.setShader(new RadialGradient(0, 0, 1f, new int[]{Look.withAlpha(warm, 0.3f), Look.withAlpha(warm, 0.08f), Look.withAlpha(warm, 0f)},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(0, 0, 1f, spot);
        c.restore();
        finish(c, w, h, p, am, false);

        // 画框的影子：贴墙的接触阴影 + 顺光方向落在墙上的影子
        Paint sh = new Paint(Paint.ANTI_ALIAS_FLAG);
        sh.setColor(0xFF000000);
        sh.setShadowLayer(0.5f * rem, -dx * 0.35f * rem, (-dy * 0.35f + 0.2f) * rem, Look.withAlpha(p.sh, 0.55f));
        c.drawRect(ax, ay, ax + fw, ay + fh, sh);
        sh.setShadowLayer(1.8f * rem, -dx * 1.1f * rem, (-dy * 1.1f + 0.7f) * rem, Look.withAlpha(p.sh, 0.55f));
        c.drawRect(ax, ay, ax + fw, ay + fh, sh);
        sh.setShadowLayer(4.2f * rem, 0, 2.4f * rem, 0x8C000000);
        c.drawRect(ax, ay, ax + fw, ay + fh, sh);

        // 线脚：胡桃木
        Paint fr = tile(tex(am, "wood.png"), 1f, Math.max(1f, rem / 12f));
        if (fr == null) {
            fr = new Paint();
            fr.setColor(0xFF3B281A);
        }
        c.drawRect(ax, ay, ax + fw, ay + fh, fr);
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE);
        float px = Math.max(1f, rem * 0.07f);
        line.setStrokeWidth(px);
        line.setColor(0xB3000000);
        c.drawRect(ax + px / 2, ay + px / 2, ax + fw - px / 2, ay + fh - px / 2, line);
        // 受光一侧的斜面高光、背光一侧的暗面
        Paint bev = new Paint(Paint.ANTI_ALIAS_FLAG);
        bev.setStyle(Paint.Style.STROKE);
        bev.setStrokeWidth(px * 1.4f);
        int litC = Look.withAlpha(Look.mix(0xFFFFFFFF, p.hl, 0.4f), 0.5f);
        bev.setColor(litC);
        float o = px * 1.6f;
        if (dy < -0.2f) c.drawLine(ax + o, ay + o, ax + fw - o, ay + o, bev);
        if (dx < -0.2f) c.drawLine(ax + o, ay + o, ax + o, ay + fh - o, bev);
        bev.setColor(0x73000000);
        if (dy > 0.2f) c.drawLine(ax + o, ay + fh - o, ax + fw - o, ay + fh - o, bev);
        if (dx > 0.2f) c.drawLine(ax + fw - o, ay + o, ax + fw - o, ay + fh - o, bev);

        // 卡纸：白卡 + 纸纤维，外沿一圈鎏金细线
        float mx = ax + frame, my = ay + frame, mw = fw - 2 * frame, mh = fh - 2 * frame;
        Paint gold = new Paint();
        gold.setColor(0xFFA8803F);
        c.drawRect(mx - rem * 0.1f, my - rem * 0.1f, mx + mw + rem * 0.1f, my + mh + rem * 0.1f, gold);
        gold.setColor(0xB3140C06);
        c.drawRect(mx - rem * 0.17f, my - rem * 0.17f, mx - rem * 0.1f, my + mh + rem * 0.17f, gold);
        Paint mt = new Paint();
        mt.setColor(0xFFF3EEE3);
        c.drawRect(mx, my, mx + mw, my + mh, mt);
        Paint mp = tile(tex(am, "paper.png"), 0.55f, Math.max(1f, rem / 16f));
        if (mp != null) c.drawRect(mx, my, mx + mw, my + mh, mp);

        // 画面：卡纸斜切露出的白芯、卡纸压在画上的影子
        RectF img = new RectF(ax + frame + mat, ay + frame + mat, ax + fw - frame - mat, ay + fh - frame - mat);
        Paint core = new Paint();
        core.setColor(0xFFFBF8F0);
        c.drawRect(img.left - rem * 0.13f, img.top - rem * 0.13f, img.right + rem * 0.13f, img.bottom + rem * 0.13f, core);
        Paint ip = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        c.drawBitmap(full, null, img, ip);
        c.save();
        c.clipRect(img);
        Paint ish = new Paint();
        ish.setColor(0xFF000000);
        ish.setShadowLayer(0.5f * rem, -dx * 0.14f * rem, -dy * 0.14f * rem, 0x73000000);
        android.graphics.Path ring = new android.graphics.Path();
        ring.addRect(img.left - 3 * rem, img.top - 3 * rem, img.right + 3 * rem, img.bottom + 3 * rem, android.graphics.Path.Direction.CW);
        ring.addRect(img, android.graphics.Path.Direction.CCW);
        c.drawPath(ring, ish);
        // 玻璃反光：一道斜着的淡光带
        Paint glare = new Paint();
        float ang = (float) Math.atan2(dy, dx) + (float) Math.PI / 2f;
        float gx = (float) Math.cos(ang), gy = (float) Math.sin(ang);
        float cx0 = (img.left + img.right) / 2, cy0 = (img.top + img.bottom) / 2, span = Math.max(img.width(), img.height());
        int gw = Look.withAlpha(Look.mix(0xFFFFFFFF, p.hl, 0.4f), 0.07f + 0.05f * p.lk);
        glare.setShader(new LinearGradient(cx0 - gx * span * 0.5f, cy0 - gy * span * 0.5f, cx0 + gx * span * 0.5f, cy0 + gy * span * 0.5f,
                new int[]{0x00FFFFFF, 0x00FFFFFF, gw, 0x00FFFFFF, 0x00FFFFFF, Look.withAlpha(gw, 0.5f), 0x00FFFFFF},
                new float[]{0f, 0.28f, 0.38f, 0.47f, 0.6f, 0.68f, 0.76f}, Shader.TileMode.CLAMP));
        c.drawRect(img, glare);
        c.restore();
        Paint edge = new Paint();
        edge.setStyle(Paint.Style.STROKE);
        edge.setColor(0x33000000);
        edge.setStrokeWidth(Math.max(1f, rem * 0.07f));
        c.drawRect(img, edge);
        return out;
    }
}
