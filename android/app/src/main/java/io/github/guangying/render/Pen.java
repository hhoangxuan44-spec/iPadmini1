package io.github.guangying.render;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;

import java.util.ArrayList;
import java.util.List;

/**
 * 画笔：所有尺寸用「rem」（设计稿里 1rem = 16px），乘上 u 得到像素。
 * canvas 为 null 时只测量不绘制，用来先算出内容有多高。
 */
public final class Pen {
    public static final int LEFT = 0, CENTER = 1, RIGHT = 2;

    public final Canvas c;
    public final float u;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private float shR, shDy;
    private int shColor;

    public Pen(Canvas c, float u) {
        this.c = c;
        this.u = u;
    }

    public Paint paint() {
        paint.reset();
        paint.setAntiAlias(true);
        paint.setSubpixelText(true);
        paint.setLinearText(true);
        return paint;
    }

    public void shadow(float radiusRem, float dyRem, int color) {
        shR = radiusRem;
        shDy = dyRem;
        shColor = color;
    }

    public void noShadow() {
        shR = 0;
    }

    private Paint textPaint(Typeface tf, float size, int color, float alpha, float spacing) {
        Paint p = paint();
        p.setTypeface(tf);
        p.setTextSize(size * u);
        p.setLetterSpacing(spacing);
        p.setColor(Look.withAlpha(color, alpha));
        if (shR > 0) p.setShadowLayer(shR * u, 0, shDy * u, shColor);
        return p;
    }

    public float width(String s, Typeface tf, float size, float spacing) {
        Paint p = paint();
        p.setTypeface(tf);
        p.setTextSize(100f);               // 用大字号测量再换算：字号太小时系统会把字宽取整
        p.setLetterSpacing(spacing);
        return p.measureText(s) * size / 100f;
    }

    /** 画单行文字：x 是左 / 中 / 右边界，top 是行盒顶部，lineH 是行高；返回文字宽度（rem）。 */
    public float text(String s, float x, float top, float lineH, Typeface tf, float size, int color, float alpha,
                      float spacing, int align) {
        float w = width(s, tf, size, spacing);
        if (c == null || s.isEmpty()) return w;
        Paint p = textPaint(tf, size, color, alpha, spacing);
        Paint.FontMetrics fm = p.getFontMetrics();
        float base = (top + lineH / 2f) * u - (fm.ascent + fm.descent) / 2f;
        float left = align == LEFT ? x : align == CENTER ? x - w / 2 : x - w;
        // 字间距会在最后一个字后面多留一份，右对齐、居中时扣掉
        if (spacing != 0 && align != LEFT) left += spacing * size * (align == RIGHT ? 1 : 0.5f);
        c.drawText(s, left * u, base, p);
        return w;
    }

    /** 数字专用：按数字的高度（而不是整行高度）垂直居中，大号数字看起来才居中。 */
    public float digits(String s, float x, float top, float lineH, Typeface tf, float size, int color, float alpha,
                        float spacing, int align) {
        float w = width(s, tf, size, spacing);
        if (c == null || s.isEmpty()) return w;
        Paint p = textPaint(tf, size, color, alpha, spacing);
        float base = (top + lineH / 2f + 0.36f * size) * u;
        float left = align == LEFT ? x : align == CENTER ? x - w / 2 : x - w;
        if (spacing != 0 && align != LEFT) left += spacing * size * (align == RIGHT ? 1 : 0.5f);
        c.drawText(s, left * u, base, p);
        return w;
    }

    /** 自动换行的段落：返回高度（rem）。maxLines <= 0 不限行数，超出时末行加省略号。 */
    public float paragraph(String s, float x, float top, float width, float lineH, Typeface tf, float size,
                           int color, float alpha, float spacing, int maxLines) {
        List<String> lines = wrap(s, width, tf, size, spacing, maxLines);
        for (int i = 0; i < lines.size(); i++) {
            text(lines.get(i), x, top + i * lineH, lineH, tf, size, color, alpha, spacing, LEFT);
        }
        return lines.size() * lineH;
    }

    public int lineCount(String s, float width, Typeface tf, float size, float spacing, int maxLines) {
        return wrap(s, width, tf, size, spacing, maxLines).size();
    }

    private static boolean noStart(char ch) {
        return "，。、；：！？）》」』”’,.;:!?)]}".indexOf(ch) >= 0;
    }

    private static boolean wordChar(char ch) {
        return ch < 128 && (Character.isLetterOrDigit(ch) || ch == '\'' || ch == '-' || ch == '.');
    }

    List<String> wrap(String s, float width, Typeface tf, float size, float spacing, int maxLines) {
        List<String> out = new ArrayList<>();
        if (s == null || s.isEmpty()) return out;
        // 切成「单位」：连续的英文数字算一个，其他每个字一个
        List<String> units = new ArrayList<>();
        for (int i = 0; i < s.length(); ) {
            int j = i + 1;
            if (wordChar(s.charAt(i))) while (j < s.length() && wordChar(s.charAt(j))) j++;
            units.add(s.substring(i, j));
            i = j;
        }
        StringBuilder line = new StringBuilder();
        for (String un : units) {
            String t = line + un;
            if (line.length() > 0 && width(t, tf, size, spacing) > width && !noStart(un.charAt(0))) {
                out.add(line.toString());
                line.setLength(0);
                if (un.equals(" ")) continue;
            }
            line.append(un);
        }
        if (line.length() > 0) out.add(line.toString());
        if (maxLines > 0 && out.size() > maxLines) {
            List<String> cut = new ArrayList<>(out.subList(0, maxLines));
            String last = cut.get(maxLines - 1);
            while (last.length() > 1 && width(last + "…", tf, size, spacing) > width) last = last.substring(0, last.length() - 1);
            cut.set(maxLines - 1, last + "…");
            return cut;
        }
        return out;
    }
}
