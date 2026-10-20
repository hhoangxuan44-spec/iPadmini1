package io.github.guangying.render;

import android.graphics.Color;

import java.util.Arrays;

import io.github.guangying.data.Photo;

/** 一张图在桌面组件上的样子：风格、文字深浅、点缀色。 */
public final class Look {
    public final String style;
    public final boolean light;       // 浅色文字（压在深色背景上）
    public final int accent;
    public final int bg;              // 色带 / 纸张 / 画廊墙面的颜色
    public final String pos;          // 组件所在位置：靠左、靠右决定文字对齐
    public final String kind;
    public final Photo photo;

    private Look(Photo p, String pos, boolean light, int accent) {
        this.photo = p;
        this.style = p.style;
        this.kind = p.kind;
        this.bg = p.bg;
        this.pos = pos;
        this.light = light;
        this.accent = accent;
    }

    /** userPos：用户把日历组件放在桌面的哪个位置（tl tr bl br l r）。 */
    public static Look of(Photo p, String userPos) {
        boolean light = p.ink.equals("light");
        int accent = p.accent;
        String s = p.style;
        if (s.equals("minimal") || s.equals("poster") || s.equals("magazine") || s.equals("glass")) {
            String region = userPos;
            if (s.equals("magazine")) region = userPos.endsWith("r") ? "r" : "l";
            int[] q = p.quiet.get(region);
            if (q != null) light = q[1] <= (s.equals("glass") ? 165 : 150);
            accent = withLightness(p.accent, light ? 0.72f : 0.36f);
        } else if (s.equals("gallery")) {
            light = true;
            accent = withLightness(p.accent, 0.72f);
        }
        return new Look(p, userPos, light, accent);
    }

    /** 主文字颜色 */
    public int ink() {
        switch (style) {
            case "paper":
            case "vpoem":
                return 0xFF2A2420;
            case "gallery":
                return 0xFFF2EDE4;
            default:
                return light ? 0xFFFFFFFF : 0xFF1D1D1F;
        }
    }

    /** 文字放在点缀色上（节日小标签）时的颜色 */
    public int onAccent() {
        return style.equals("paper") || style.equals("vpoem") || !light ? 0xFFFFFFFF : 0xFF1D1D1F;
    }

    public boolean leftAligned() {
        return !pos.endsWith("r");
    }

    // ------------------------------------------------------------ 颜色工具

    public static int withAlpha(int color, float alpha) {
        return Color.argb(Math.round(255 * Math.max(0, Math.min(1, alpha)) * Color.alpha(color) / 255f),
                Color.red(color), Color.green(color), Color.blue(color));
    }

    public static int withLightness(int color, float l) {
        float[] hsl = rgbToHsl(color);
        return hslToRgb(hsl[0], hsl[1], l);
    }

    public static float rgbToHslL(int c) {
        return rgbToHsl(c)[2];
    }

    static float[] rgbToHsl(int c) {
        float r = Color.red(c) / 255f, g = Color.green(c) / 255f, b = Color.blue(c) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float l = (max + min) / 2f, h = 0, s = 0;
        float d = max - min;
        if (d > 1e-6f) {
            s = l > 0.5f ? d / (2f - max - min) : d / (max + min);
            if (max == r) h = (g - b) / d + (g < b ? 6 : 0);
            else if (max == g) h = (b - r) / d + 2;
            else h = (r - g) / d + 4;
            h /= 6f;
        }
        return new float[]{h, s, l};
    }

    static int hslToRgb(float h, float s, float l) {
        float r, g, b;
        if (s < 1e-6f) {
            r = g = b = l;
        } else {
            float q = l < 0.5f ? l * (1 + s) : l + s - l * s, p = 2 * l - q;
            r = hue(p, q, h + 1f / 3);
            g = hue(p, q, h);
            b = hue(p, q, h - 1f / 3);
        }
        return Color.rgb(Math.round(r * 255), Math.round(g * 255), Math.round(b * 255));
    }

    private static float hue(float p, float q, float t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1f / 6) return p + (q - p) * 6 * t;
        if (t < 1f / 2) return q;
        if (t < 2f / 3) return p + (q - p) * (2f / 3 - t) * 6;
        return p;
    }

    @Override
    public String toString() {
        return Arrays.asList(style, light ? "light" : "dark", pos).toString();
    }
}
