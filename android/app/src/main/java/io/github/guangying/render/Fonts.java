package io.github.guangying.render;

import android.content.res.AssetManager;
import android.graphics.Typeface;

/**
 * 字体：数字用内置的 Inter Display（和网页一致），中文用系统字体。
 * 内置字体只含数字和英文，中文自动回退到系统字体。
 */
public final class Fonts {
    private final Typeface[] inter = new Typeface[5];   // 200 300 400 500 600
    private final Typeface sans, serif;

    public Fonts(Typeface extralight, Typeface light, Typeface regular, Typeface medium, Typeface semibold,
                 Typeface sans, Typeface serif) {
        inter[0] = extralight;
        inter[1] = light;
        inter[2] = regular;
        inter[3] = medium;
        inter[4] = semibold;
        this.sans = sans;
        this.serif = serif;
    }

    public static Fonts fromAssets(AssetManager am) {
        String[] names = {"extralight", "light", "regular", "medium", "semibold"};
        Typeface[] t = new Typeface[5];
        for (int i = 0; i < 5; i++) {
            try {
                t[i] = Typeface.createFromAsset(am, "fonts/inter-" + names[i] + ".ttf");
            } catch (RuntimeException e) {
                t[i] = Typeface.DEFAULT;
            }
        }
        return new Fonts(t[0], t[1], t[2], t[3], t[4], Typeface.DEFAULT, Typeface.SERIF);
    }

    private static int idx(int weight) {
        return weight <= 200 ? 0 : weight <= 300 ? 1 : weight <= 400 ? 2 : weight <= 500 ? 3 : 4;
    }

    /** 数字与英文（Inter） */
    public Typeface num(int weight) {
        return inter[idx(weight)];
    }

    /** 中文无衬线 */
    public Typeface sans(int weight) {
        return Typeface.create(sans, weight, false);
    }

    /** 中文宋体（系统没有宋体时回退到默认字体） */
    public Typeface serif(int weight) {
        return Typeface.create(serif, weight, false);
    }

    /** 衬线数字（宣纸、诗笺风格） */
    public Typeface serifNum(int weight) {
        return Typeface.create(serif, weight, false);
    }
}
