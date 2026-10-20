package io.github.guangying.render;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Paint;
import android.graphics.Shader;

import java.util.Random;

/** 极细的颗粒：纸张的纤维感、玻璃和色块上的细腻噪点（同时避免渐变出现色带）。 */
final class Texture {
    private Texture() {}

    private static Bitmap noise;

    private static synchronized Bitmap noise() {
        if (noise == null) {
            int n = 192;
            int[] px = new int[n * n];
            Random r = new Random(11);
            for (int i = 0; i < px.length; i++) {
                int a = (int) (Math.abs(r.nextGaussian()) * 70);
                if (a > 255) a = 255;
                px[i] = r.nextBoolean() ? (a << 24) | 0xFFFFFF : a << 24;
            }
            noise = Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888);
        }
        return noise;
    }

    /** strength：0-1，颗粒的浓淡（纸张约 0.5，玻璃约 0.25） */
    static Paint grain(float strength, float scale) {
        Paint p = new Paint();
        BitmapShader s = new BitmapShader(noise(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.setScale(scale, scale);
        s.setLocalMatrix(m);
        p.setShader(s);
        p.setAlpha(Math.round(255 * Math.max(0, Math.min(1, strength))));
        return p;
    }
}
