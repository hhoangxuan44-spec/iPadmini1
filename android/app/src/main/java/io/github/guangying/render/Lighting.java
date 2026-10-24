package io.github.guangying.render;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;

/**
 * 按照片的光给卡片上光：生成两张小位图，桌面组件把它们拉伸铺满整个卡片——
 * 受光层（渐变、高光斑）和边缘层（受光一侧的边缘亮、背光一侧的边缘暗）。
 * 渐变和柔和的边缘拉伸了也看不出来，所以不依赖桌面报告的组件大小。
 */
public final class Lighting {
    private Lighting() {}

    public static final int GLASS = 0, PAPER = 1, BAND = 2, PLAQUE = 3;
    private static final int N = 128;

    private static int a(int color, float alpha) {
        return Look.withAlpha(color, alpha);
    }

    private static int white(int hl) {
        return android.graphics.Color.rgb(
                (android.graphics.Color.red(hl) + 255) / 2, (android.graphics.Color.green(hl) + 255) / 2,
                (android.graphics.Color.blue(hl) + 255) / 2);
    }

    /** 受光：整块卡片的明暗和一团高光 */
    public static Bitmap light(Look L, int material) {
        Bitmap bm = Bitmap.createBitmap(N, N, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        float[] d = L.lightDir();
        float k = L.photo.lk;
        int hw = white(L.photo.hl), sh = L.photo.sh;
        // 渐变从受光一侧指向背光一侧
        float gx0 = N / 2f + d[0] * N / 2f, gy0 = N / 2f + d[1] * N / 2f, gx1 = N - gx0, gy1 = N - gy0;
        int[] cols;
        float[] pos;
        switch (material) {
            case PAPER:
                cols = new int[]{a(0xFFFFFAEC, 0.38f), a(0xFFFFFAEC, 0f), a(Look.mix(0xFF785834, sh, 0.25f), 0.13f)};
                pos = new float[]{0f, 0.46f, 1f};
                break;
            case BAND:
                cols = new int[]{a(0xFFFFFFFF, 0.14f), a(0xFFFFFFFF, 0.03f), a(0xFF000000, 0.16f)};
                pos = new float[]{0f, 0.5f, 1f};
                gx0 = N / 2f; gy0 = 0; gx1 = N / 2f; gy1 = N;
                break;
            case PLAQUE:
                cols = new int[]{a(hw, 0.14f), a(hw, 0.02f), a(sh, 0.3f)};
                pos = new float[]{0f, 0.5f, 1f};
                break;
            default:
                if (L.light) {
                    cols = new int[]{a(hw, 0.3f), a(hw, 0.06f), a(sh, 0.12f), a(sh, 0.34f)};
                    pos = new float[]{0f, 0.4f, 0.62f, 1f};
                } else {
                    cols = new int[]{0x9EFFFFFF, 0x5CFFFFFF, a(Look.mix(0xFFFFFFFF, sh, 0.25f), 0.3f)};
                    pos = new float[]{0f, 0.55f, 1f};
                }
        }
        Paint p = new Paint();
        p.setShader(new LinearGradient(gx0, gy0, gx1, gy1, cols, pos, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, N, N, p);
        // 受光一侧的高光斑
        if (material != BAND) {
            float hx = N * (0.5f + d[0] * 0.62f), hy = N * (0.5f + d[1] * 0.62f);
            int base = material == PAPER ? 0xFFFFFCF0 : hw;
            float al = material == PAPER ? 0.12f + 0.16f * k : (L.light ? 0.3f : 0.5f) * (0.4f + k);
            Paint rp = new Paint();
            rp.setShader(new RadialGradient(hx, hy, N * 0.58f, a(base, al), a(base, 0f), Shader.TileMode.CLAMP));
            c.drawRect(0, 0, N, N, rp);
        }
        return bm;
    }

    /** 边缘：朝光的边缘有一道亮边，背光的边缘有一道暗边（卡片的厚度） */
    public static Bitmap rim(Look L, int material) {
        int n = 160;
        Bitmap bm = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        float[] d = L.lightDir();
        float k = L.photo.lk;
        int hw = white(L.photo.hl), sh = L.photo.sh;
        float lit = material == PAPER ? 0.85f : 0.42f + 0.4f * k;
        int litColor = material == PAPER ? 0xFFFFFDF4 : hw;
        int darkColor = material == PAPER ? 0xFF8C6C46 : sh;
        float darkA = material == PAPER ? 0.3f : (L.light ? 0.45f : 0.25f);
        float edge = n * (material == PAPER ? 0.06f : 0.09f);
        // 四条边：法线与光的夹角决定亮暗
        float[][] normals = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};          // 上 下 左 右
        for (int i = 0; i < 4; i++) {
            float dot = normals[i][0] * d[0] + normals[i][1] * d[1];
            int col;
            float alpha;
            if (dot > 0) {
                col = litColor;
                alpha = lit * dot;
            } else {
                col = darkColor;
                alpha = darkA * -dot * 0.8f;
            }
            if (alpha < 0.02f) continue;
            float x0 = 0, y0 = 0, x1 = 0, y1 = 0, rx = 0, ry = 0, rw = n, rh = n;
            switch (i) {
                case 0: x1 = 0; y1 = edge; rh = edge; break;
                case 1: y0 = n; y1 = n - edge; ry = n - edge; rh = edge; break;
                case 2: x1 = edge; rw = edge; break;
                default: x0 = n; x1 = n - edge; rx = n - edge; rw = edge; break;
            }
            Paint p = new Paint();
            p.setShader(new LinearGradient(x0, y0, x1, y1, a(col, Math.min(1f, alpha)), a(col, 0f), Shader.TileMode.CLAMP));
            c.drawRect(rx, ry, rx + rw, ry + rh, p);
        }
        return bm;
    }
}
