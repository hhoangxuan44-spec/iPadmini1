package io.github.guangying.render;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import io.github.guangying.core.Lunar;

/**
 * 月历组件：把网页版的八种日历风格用 Canvas 画成一张位图，桌面组件直接显示它。
 * 版式跟随组件的大小：竖长的组件上下排（时钟在上、月历在下），扁长的组件左右排。
 */
public final class CalendarArt {
    private CalendarArt() {}

    public static final String[] MONTH_EN = {"January", "February", "March", "April", "May", "June", "July",
            "August", "September", "October", "November", "December"};
    public static final String[] NUM_CN = {"一", "二", "三", "四", "五", "六", "七", "八", "九", "十", "十一", "十二"};
    static final String[] WEEK_CN = {"星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六"};
    static final String[] WEEK_SHORT = {"日", "一", "二", "三", "四", "五", "六"};
    private static final Map<String, String> FEST_SHORT = new HashMap<>();

    static {
        String[] kv = {"劳动节", "劳动", "国庆节", "国庆", "儿童节", "儿童", "教师节", "教师", "妇女节", "妇女", "青年节", "青年",
                "建党节", "建党", "建军节", "建军", "植树节", "植树", "情人节", "情人", "愚人节", "愚人", "平安夜", "平安夜",
                "圣诞节", "圣诞", "母亲节", "母亲", "父亲节", "父亲", "感恩节", "感恩", "元宵节", "元宵", "端午节", "端午",
                "中秋节", "中秋", "重阳节", "重阳", "腊八节", "腊八", "中元节", "中元"};
        for (int i = 0; i < kv.length; i += 2) FEST_SHORT.put(kv[i], kv[i + 1]);
    }

    /** 要画什么：组件大小（像素）、这张图的风格、日期与时间。 */
    public static final class Spec {
        public int w, h;
        public Look look;
        public Calendar now;
        public int viewYear, viewMonth;       // 月历显示的月份（月份从 0 起）
        public String[] motto;                // {句子, 出处}，可为 null
        public boolean use24h = true;

        public Spec(int w, int h, Look look, Calendar now) {
            this.w = w;
            this.h = h;
            this.look = look;
            this.now = now;
            this.viewYear = now.get(Calendar.YEAR);
            this.viewMonth = now.get(Calendar.MONTH);
        }
    }

    // ------------------------------------------------------------ 各风格的尺寸（单位 rem，和 css/wall.css 对应）

    private static final int NONE = 0, GLASS = 1, PAPER = 2, BAND = 3, PLAQUE = 4, RULED = 5;

    static final class P {
        int card = NONE;
        float inner = 18.6f;                              // 内容宽度
        float grid = -1;                                  // 月历宽度（默认等于内容宽度）
        float padL, padT, padR, padB;
        float margin = 0.6f;                              // 卡片外留给阴影的边
        float radius;
        float timeSize = 4.2f;
        int timeWeight = 200;
        boolean timeSerif;
        boolean lunar = true, motto = true, head = true, bigMonth, poster, vpoem, stretch, scrim, fillWidth;
        float cell = 2.2f, gridHead = 1.5f, numBox = 1.5f, numSize = 0.9f, subSize = 0.56f, subLine = 0.7f;
        boolean sub = true, serifGrid;
        float gap = 1.5f;                                 // 时钟与月历之间
        int rule;                                         // 1 时钟与月历之间有细线，2 时钟上方有细线
        float leftCol = 15.5f;                            // 左右排版时左列的宽度
    }

    static P params(Look l) {
        P p = paramsOf(l);
        if (p.grid < 0) p.grid = p.inner;
        return p;
    }

    private static P paramsOf(Look l) {
        P p = new P();
        switch (l.style) {
            case "minimal":
                p.timeSize = 5f;
                p.scrim = true;
                break;
            case "poster":
                p.poster = true;
                p.inner = 26f;
                p.grid = 18.6f;
                p.head = false;
                p.scrim = true;
                p.gap = 1.2f;
                p.leftCol = 24f;
                break;
            case "glass":
                p.card = GLASS;
                p.padL = p.padR = 1.8f;
                p.padT = 1.7f;
                p.padB = 1.4f;
                p.radius = 1.8f;
                p.margin = 1.4f;
                break;
            case "magazine":
                p.inner = 16.4f;
                p.padL = p.padR = 2.8f;
                p.padT = 2.2f;
                p.padB = 2f;
                p.bigMonth = true;
                p.stretch = true;
                p.timeSize = 2.5f;
                p.timeWeight = 300;
                p.lunar = false;
                p.motto = false;
                p.cell = 2f;
                p.margin = 0;
                break;
            case "band":
                p.card = BAND;
                p.padL = p.padR = 2.8f;
                p.padT = 1.9f;
                p.padB = 1.8f;
                p.timeSize = 3.8f;
                p.head = false;
                p.cell = 1.85f;
                p.gridHead = 1.2f;
                p.numBox = 1.3f;
                p.numSize = 0.82f;
                p.subSize = 0.5f;
                p.subLine = 0.55f;
                p.fillWidth = true;
                p.margin = 0;
                p.gap = 2.6f;
                break;
            case "paper":
                p.card = PAPER;
                p.inner = 17.3f;
                p.padL = p.padR = 2.1f;
                p.padT = 2f;
                p.padB = 1.7f;
                p.radius = 0.3f;
                p.margin = 1.5f;
                p.timeSize = 2.9f;
                p.timeWeight = 400;
                p.timeSerif = true;
                p.motto = false;
                p.serifGrid = true;
                p.gap = 2.1f;
                p.rule = 1;
                break;
            case "gallery":
                p.card = PLAQUE;
                p.inner = 17.6f;
                p.padL = p.padR = 1.5f;
                p.padT = 1.4f;
                p.padB = 1.3f;
                p.radius = 1.2f;
                p.margin = 1.3f;
                p.timeSize = 3.4f;
                p.gap = 1.2f;
                break;
            default:                                      // vpoem 竖排诗笺
                p.card = RULED;
                p.inner = 12.8f;
                p.padL = p.padR = 1.4f;
                p.padT = 1.6f;
                p.padB = 1.3f;
                p.radius = 0.3f;
                p.margin = 1.5f;
                p.timeSize = 2.1f;
                p.timeWeight = 400;
                p.timeSerif = true;
                p.head = false;
                p.sub = false;
                p.cell = 1.6f;
                p.gridHead = 1.1f;
                p.numBox = 1.25f;
                p.numSize = 0.74f;
                p.lunar = false;
                p.motto = false;
                p.vpoem = true;
                p.stretch = true;
                p.serifGrid = true;
                p.gap = 0.7f;
                p.rule = 2;
                break;
        }
        return p;
    }

    // ------------------------------------------------------------ 入口

    public static Bitmap render(Fonts f, Spec s) {
        Bitmap bm = Bitmap.createBitmap(Math.max(1, s.w), Math.max(1, s.h), Bitmap.Config.ARGB_8888);
        draw(new Canvas(bm), f, s);
        return bm;
    }

    /** 选版式并绘制；返回实际使用的缩放（像素 / rem），供测试查看。 */
    public static float draw(Canvas canvas, Fonts f, Spec s) {
        P p = params(s.look);
        boolean poem = p.vpoem && hasPoem(s) && s.h >= 1.3f * s.w;
        float bestU = -1;
        boolean bestWide = false;
        for (int i = 0; i < 2; i++) {
            boolean wide = i == 1;
            if (wide && (p.bigMonth || poem)) continue;
            float[] sz = natural(f, s, p, wide, poem);
            float u = Math.min(s.w / (sz[0] + 2 * p.margin), s.h / (sz[1] + 2 * p.margin));
            if (u > bestU) {
                bestU = u;
                bestWide = wide;
            }
        }
        float[] sz = natural(f, s, p, bestWide, poem);
        float u = bestU;
        float totalW = s.w / u, totalH = s.h / u;
        float cardW = sz[0], cardH = sz[1];
        if (p.fillWidth) cardW = Math.max(cardW, totalW);
        if (p.stretch) cardH = Math.max(cardH, totalH - 2 * p.margin);
        float x0 = s.look.leftAligned() ? p.margin : totalW - p.margin - cardW;
        if (p.fillWidth) x0 = 0;
        float y0 = s.look.pos.startsWith("t") ? p.margin : s.look.pos.startsWith("b") ? totalH - p.margin - cardH
                : (totalH - cardH) / 2f;
        if (p.stretch) y0 = p.margin;
        Pen pen = new Pen(canvas, u);
        paintCard(pen, s, p, x0, y0, cardW, cardH);
        float innerW = cardW - p.padL - p.padR, innerH = cardH - p.padT - p.padB;
        content(pen, f, s, p, bestWide, poem, x0 + p.padL, y0 + p.padT, innerW, innerH);
        paintOver(pen, f, s, p, x0, y0, cardW, cardH);
        return u;
    }

    /** 内容自然尺寸（卡片宽、高，rem） */
    private static float[] natural(Fonts f, Spec s, P p, boolean wide, boolean poem) {
        Pen m = new Pen(null, 1);
        float innerW = wide ? p.leftCol + p.gap + p.grid : p.inner;
        if (p.fillWidth && !wide) innerW = p.inner;
        float innerH = content(m, f, s, p, wide, poem, 0, 0, innerW, -1);
        return new float[]{innerW + p.padL + p.padR, innerH + p.padT + p.padB};
    }

    private static boolean hasPoem(Spec s) {
        return s.look.photo != null && s.look.photo.poem != null && !s.look.photo.poem.isEmpty();
    }

    // ------------------------------------------------------------ 内容

    /** h < 0：测量自然高度；否则 h 是可用高度（拉伸风格用它把时钟推到底部）。返回内容高度。 */
    private static float content(Pen pen, Fonts f, Spec s, P p, boolean wide, boolean poem,
                                 float x, float y, float w, float h) {
        if (p.bigMonth) {
            float hm = monthBlock(pen, f, s, p, x, y, p.grid);
            float hc = clockBlock(pen, f, s, p, x, 0, w, true);          // 先量高度
            float cy = h < 0 ? y + hm + 0.8f : y + Math.max(hm + 0.8f, h - hc);
            clockBlock(pen, f, s, p, x, cy, w, false);
            return h < 0 ? hm + 0.8f + hc : h;
        }
        if (wide) {
            float lw = p.leftCol;
            float hc = clockBlock(pen, f, s, p, x, y, lw, false);
            float mx = x + (p.fillWidth ? Math.max(lw + p.gap, w - p.grid) : lw + p.gap);
            float hm = monthBlock(pen, f, s, p, mx, y, p.grid);
            return Math.max(hc, hm);
        }
        float cursor = 0;
        if (poem) {
            float hc = clockBlock(pen, f, s, p, x, 0, w, true);
            float hm = monthBlock(pen, f, s, p, x, 0, p.grid);
            float hp = h < 0 ? poemHeight(pen, f, s, p) : Math.max(4f, h - hc - hm - p.gap - 1.9f);
            poemBlock(pen, f, s, p, x, y, w, hp);
            cursor = hp + 1.0f;
            if (p.rule == 2) rule(pen, s, p, x, y + cursor, w);
            cursor += 0.9f;
        }
        float hc = clockBlock(pen, f, s, p, x, y + cursor, w, false);
        cursor += hc;
        if (p.rule == 1) rule(pen, s, p, x, y + cursor + p.gap / 2f, w);
        cursor += p.gap;
        float hm = monthBlock(pen, f, s, p, x, y + cursor, p.grid);
        return cursor + hm;
    }

    private static void rule(Pen pen, Spec s, P p, float x, float y, float w) {
        if (pen.c == null) return;
        Paint pt = pen.paint();
        pt.setColor(p.rule == 1 ? 0x33503220 : 0x4DB23A2E);
        pen.c.drawRect(x * pen.u, y * pen.u, (x + w) * pen.u, y * pen.u + Math.max(1f, 0.06f * pen.u), pt);
    }

    // ---------------------------------------------------------------- 时钟、日期、农历、每日一言

    private static Typeface numFace(Fonts f, P p, int weight) {
        return p.timeSerif ? f.serifNum(weight) : f.num(weight);
    }

    /** 时间：HH:MM，冒号略微上抬、淡一点 */
    private static float timeRow(Pen pen, Fonts f, Spec s, P p, float x, float y, float size, int weight, int align) {
        int hr = s.now.get(Calendar.HOUR_OF_DAY);
        if (!s.use24h) hr = hr % 12 == 0 ? 12 : hr % 12;
        String hh = s.use24h ? two(hr) : String.valueOf(hr), mm = two(s.now.get(Calendar.MINUTE));
        Typeface tf = numFace(f, p, weight);
        float sp = p.timeSerif ? 0 : -0.02f;
        int ink = s.look.ink();
        float wh = pen.width(hh, tf, size, sp), wc = pen.width(":", tf, size, sp), wm = pen.width(mm, tf, size, sp);
        float colonPad = 0.03f * size;
        float total = wh + wc + 2 * colonPad + wm;
        float x0 = align == Pen.RIGHT ? x - total : align == Pen.CENTER ? x - total / 2 : x;
        pen.digits(hh, x0, y, size, tf, size, ink, 1f, sp, Pen.LEFT);
        pen.digits(":", x0 + wh + colonPad, y - 0.06f * size, size, tf, size, ink, 0.85f, sp, Pen.LEFT);
        pen.digits(mm, x0 + wh + wc + 2 * colonPad, y, size, tf, size, ink, 1f, sp, Pen.LEFT);
        return size;
    }

    private static String two(int n) {
        return n < 10 ? "0" + n : "" + n;
    }

    /** 带「·」分隔的一行（分隔符淡一点） */
    private static float sepLine(Pen pen, Typeface tf, float size, float x, float top, float lh, int color, float alpha,
                                 float spacing, String... parts) {
        float cx = x;
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                cx += 0.4f * size;
                cx += pen.text("·", cx, top, lh, tf, size, color, alpha * 0.5f, spacing, Pen.LEFT);
                cx += 0.4f * size;
            }
            cx += pen.text(parts[i], cx, top, lh, tf, size, color, alpha, spacing, Pen.LEFT);
        }
        return cx - x;
    }

    private static float lunarLine(Pen pen, Fonts f, Spec s, P p, float x, float top, float size) {
        Calendar n = s.now;
        Lunar.Info info = Lunar.info(n.get(Calendar.YEAR), n.get(Calendar.MONTH) + 1, n.get(Calendar.DAY_OF_MONTH));
        Lunar.Day l = info.lunar;
        Typeface tf = p.serifGrid ? f.serif(400) : f.sans(400);
        float lh = 1.3f;
        float cx = x + sepLine(pen, tf, size, x, top, lh, s.look.ink(), 0.78f, 0.04f,
                "农历" + l.monthName + l.dayName, l.ganzhi + l.animal + "年");
        for (int i = 0; i < info.festivals.size() && i < 2; i++) {
            String t = info.festivals.get(i);
            float tw = pen.width(t, f.sans(700), 0.7f, 0.06f);
            float cw = tw + 1.1f;
            cx += 0.5f;
            if (pen.c != null) {
                Paint pt = pen.paint();
                pt.setColor(s.look.accent);
                float r = pen.u;
                pen.c.drawRoundRect(new RectF(cx * r, top * r, (cx + cw) * r, (top + 1.3f) * r), 0.65f * r, 0.65f * r, pt);
            }
            pen.text(t, cx + 0.55f, top, 1.3f, f.sans(700), 0.7f, s.look.onAccent(), 1f, 0.06f, Pen.LEFT);
            cx += cw;
        }
        return lh;
    }

    private static float mottoBlock(Pen pen, Fonts f, Spec s, P p, float x, float top, float w) {
        if (s.motto == null || s.motto.length < 2) return 0;
        float mw = Math.min(w, 18.5f);
        int ink = s.look.ink();
        float h = pen.paragraph(s.motto[0], x, top, mw, 1.24f, f.serif(400), 0.8f, ink, 0.88f, 0.05f, 3);
        pen.text(s.motto[1], x, top + h + 0.1f, 0.8f, f.sans(400), 0.58f, ink, 0.6f, 0.1f, Pen.LEFT);
        return h + 0.1f + 0.8f;
    }

    private static void shadowFor(Pen pen, P p, Look l) {
        if (p.card == NONE) {
            if (l.light) pen.shadow(0.55f, 0.06f, 0x8C000000);
            else pen.shadow(0.8f, 0f, 0x99FFFFFF);
        } else if (p.card == PLAQUE) {
            pen.shadow(0.5f, 0.06f, 0x66000000);
        } else {
            pen.noShadow();
        }
    }

    /** 时钟块。measure=true 时只返回高度，不画。 */
    private static float clockBlock(Pen pen, Fonts f, Spec s, P p, float x, float y, float w, boolean measure) {
        Pen pn = measure ? new Pen(null, 1) : pen;
        shadowFor(pn, p, s.look);
        Calendar n = s.now;
        int ink = s.look.ink();
        float cy = y;
        String date = (n.get(Calendar.MONTH) + 1) + "月" + n.get(Calendar.DAY_OF_MONTH) + "日";
        String week = WEEK_CN[n.get(Calendar.DAY_OF_WEEK) - 1];
        Typeface dateFace = p.serifGrid ? f.serif(500) : f.sans(500);

        if (p.poster) {
            float dayBox = 10.26f, size = 13.5f;
            String day = String.valueOf(n.get(Calendar.DAY_OF_MONTH));
            float dw = pn.digits(day, x - 0.6f, cy, dayBox, f.num(200), size, ink, 1f, -0.06f, Pen.LEFT);
            float mx = x - 0.6f + dw + 1.3f;
            float metaH = 1.9f + 0.35f + 1.0f + 0.35f + 1.3f + 0.6f + 1.7f;
            float my = cy + dayBox - 0.4f - metaH;
            pn.text(week, mx, my, 1.9f, f.sans(500), 1.5f, ink, 1f, 0.12f, Pen.LEFT);
            my += 1.9f + 0.35f;
            pn.text(MONTH_EN[n.get(Calendar.MONTH)].toUpperCase() + " " + n.get(Calendar.YEAR), mx, my, 1.0f,
                    f.num(600), 0.78f, ink, 1f, 0.32f, Pen.LEFT);
            my += 1.0f + 0.35f;
            lunarLine(pn, f, s, p, mx, my, 0.82f);
            my += 1.3f + 0.6f;
            timeRow(pn, f, s, p, mx, my, 1.7f, 300, Pen.LEFT);
            cy += dayBox;
            if (p.motto) {
                cy += 1.1f;
                cy += mottoBlock(pn, f, s, p, x, cy, w);
            }
            return cy - y;
        }

        timeRow(pn, f, s, p, x, cy, p.timeSize, p.timeWeight, Pen.LEFT);
        cy += p.timeSize;
        cy += p.vpoem ? 0.25f : p.bigMonth ? 0.35f : 0.55f;
        float dsz = p.vpoem ? 0.82f : 1.05f;
        sepLine(pn, dateFace, dsz, x, cy, dsz * 1.4f, ink, 1f, 0.04f, date, week);
        cy += dsz * 1.4f;
        if (p.lunar) {
            cy += 0.3f;
            cy += lunarLine(pn, f, s, p, x, cy, 0.82f);
        }
        if (p.motto) {
            cy += 0.75f;
            cy += mottoBlock(pn, f, s, p, x, cy, w);
        }
        return cy - y;
    }

    // ---------------------------------------------------------------- 月历

    private static float monthBlock(Pen pen, Fonts f, Spec s, P p, float x, float y, float w) {
        Look L = s.look;
        int ink = L.ink();
        shadowFor(pen, p, L);
        int m = s.viewMonth, yr = s.viewYear;
        float cy = y;
        if (p.head) {
            String num = String.valueOf(m + 1);
            if (p.bigMonth) {
                pen.digits(num, x - 0.3f, cy, 5.5f, f.num(200), 6.4f, ink, 1f, -0.04f, Pen.LEFT);
                cy += 5.5f + 0.45f;
                pen.text(MONTH_EN[m].toUpperCase(), x, cy, 1.15f, f.num(600), 0.95f, ink, 1f, 0.4f, Pen.LEFT);
                cy += 1.15f + 0.35f;
                pen.text(yr + " · " + NUM_CN[m] + "月", x, cy, 1.0f, f.sans(400), 0.74f, ink, 0.6f, 0.1f, Pen.LEFT);
                cy += 1.0f + 0.7f;
            } else {
                float nw = pen.digits(num, x, cy, 2.0f, numFace(f, p, 300), 2.0f, L.accent, 1f, 0, Pen.LEFT);
                float nx = x + nw + 0.7f;
                pen.text(MONTH_EN[m].toUpperCase(), nx, cy + 0.05f, 1.0f, f.num(600), 0.78f, L.accent, 1f, 0.26f, Pen.LEFT);
                pen.text(yr + " · " + NUM_CN[m] + "月", nx, cy + 1.05f, 0.95f, f.sans(400), 0.68f, ink, 0.6f, 0.1f, Pen.LEFT);
                cy += 2.0f + 0.55f;
            }
        }
        // 星期
        float cw = w / 7f;
        Typeface wk = p.serifGrid ? f.serif(600) : f.sans(600);
        for (int i = 0; i < 7; i++) {
            pen.text(WEEK_SHORT[i], x + cw * (i + 0.5f), cy, p.gridHead, wk, p.vpoem ? 0.58f : 0.66f, ink,
                    i == 0 || i == 6 ? 0.34f : 0.55f, 0, Pen.CENTER);
        }
        cy += p.gridHead;
        Calendar first = Calendar.getInstance();
        first.clear();
        first.set(yr, m, 1);
        int lead = first.get(Calendar.DAY_OF_WEEK) - 1;
        int days = first.getActualMaximum(Calendar.DAY_OF_MONTH);
        boolean thisMonth = yr == s.now.get(Calendar.YEAR) && m == s.now.get(Calendar.MONTH);
        int today = s.now.get(Calendar.DAY_OF_MONTH);
        int rows = (lead + days + 6) / 7;
        for (int d = 1; d <= days; d++) {
            int idx = lead + d - 1, col = idx % 7, row = idx / 7;
            float cx = x + cw * (col + 0.5f), top = cy + row * p.cell;
            boolean isToday = thisMonth && d == today;
            boolean weekend = col == 0 || col == 6;
            float a = isToday ? 1f : weekend ? 0.62f : 1f;
            Lunar.Info info = Lunar.info(yr, m + 1, d);
            boolean fest = !info.festivals.isEmpty();
            if (isToday && pen.c != null) {
                Paint pt = pen.paint();
                pt.setColor(L.accent);
                pen.c.drawCircle(cx * pen.u, (top + p.numBox / 2f) * pen.u, p.numBox / 2f * pen.u, pt);
            }
            int numColor = isToday ? L.onAccent() : ink;
            if (isToday) pen.noShadow(); else shadowFor(pen, p, L);
            pen.digits(String.valueOf(d), cx, top, p.numBox, numFace(f, p, isToday ? 600 : 500), p.numSize, numColor, a,
                    0, Pen.CENTER);
            if (p.sub) {
                String sub;
                if (fest) {
                    String fn = info.festivals.get(0);
                    sub = FEST_SHORT.containsKey(fn) ? FEST_SHORT.get(fn) : fn;
                } else {
                    sub = info.lunar.day == 1 ? info.lunar.monthName : info.lunar.dayName;
                }
                pen.text(sub, cx, top + p.numBox, p.subLine, fest ? f.sans(700) : f.sans(400), p.subSize,
                        fest ? L.accent : ink, fest ? a : 0.6f * a, 0, Pen.CENTER);
            }
        }
        return cy - y + rows * p.cell;
    }

    // ---------------------------------------------------------------- 竖排诗句（诗笺风格）

    private static String[] poemColumns(Spec s) {
        String title = s.look.photo.title.replaceAll("\\s*·\\s*", "·");
        String[] clauses = s.look.photo.poem.split("[，。；？！、：]");
        java.util.List<String> cols = new java.util.ArrayList<>();
        cols.add(title);
        for (String c : clauses) if (!c.isEmpty()) cols.add(c);
        String src = s.look.photo.poemSrc.replaceAll("[《》]", "·").replaceAll("·$", "");
        cols.add(src);
        return cols.toArray(new String[0]);
    }

    private static final float COL_W = 2.27f;

    private static float colHeight(String col, int kind) {
        float line = kind == 0 ? 1.35f : kind == 1 ? 1.85f : 1.0f;
        float h = 0;
        for (int i = 0; i < col.length(); i++) {
            char ch = col.charAt(i);
            h += ch == ' ' || ch == '·' ? line * 0.5f : line;
        }
        return h;
    }

    private static float poemHeight(Pen pen, Fonts f, Spec s, P p) {
        String[] cols = poemColumns(s);
        float h = 0;
        for (int i = 0; i < cols.length; i++) {
            int kind = i == 0 ? 0 : i == cols.length - 1 ? 2 : 1;
            h = Math.max(h, colHeight(cols[i], kind) + (kind == 2 ? 2.2f : 0));
        }
        return h + 0.3f;
    }

    private static void poemBlock(Pen pen, Fonts f, Spec s, P p, float x, float y, float w, float h) {
        if (pen.c == null) return;
        String[] cols = poemColumns(s);
        pen.c.save();
        pen.c.clipRect(x * pen.u, y * pen.u, (x + w) * pen.u, (y + h) * pen.u);
        float cx = x + w - COL_W / 2f;                   // 从右往左排
        int ink = s.look.ink();
        for (int i = 0; i < cols.length; i++) {
            int kind = i == 0 ? 0 : i == cols.length - 1 ? 2 : 1;
            float size = kind == 0 ? 1.0f : kind == 1 ? 1.45f : 0.72f, line = kind == 0 ? 1.35f : kind == 1 ? 1.85f : 1.0f;
            Typeface tf = f.serif(kind == 0 ? 700 : 400);
            int color = kind == 0 ? s.look.accent : ink;
            float alpha = kind == 2 ? 0.62f : 1f;
            float ty = y + 0.3f + (kind == 2 ? 2.2f : 0);
            float colX = kind == 0 ? cx - 0.3f : cx;
            for (int k = 0; k < cols[i].length(); k++) {
                char ch = cols[i].charAt(k);
                if (ch == ' ' || ch == '·') {
                    ty += line * 0.5f;
                    continue;
                }
                pen.text(String.valueOf(ch), colX, ty, line, tf, size, color, alpha, 0, Pen.CENTER);
                ty += line;
            }
            cx -= COL_W;
            if (cx < x - COL_W / 2f) break;
        }
        pen.c.restore();
    }

    // ---------------------------------------------------------------- 卡片与装饰

    static void rrect(Canvas c, float u, float x, float y, float w, float h, float r, Paint p) {
        c.drawRoundRect(new RectF(x * u, y * u, (x + w) * u, (y + h) * u), r * u, r * u, p);
    }

    /** 卡片外侧的柔和阴影（卡片内部不画，半透明卡片才不会发暗） */
    static void cardShadow(Pen pen, float x, float y, float w, float h, float r, float blur, float dy, int color) {
        Canvas c = pen.c;
        float u = pen.u;
        Path clip = new Path();
        clip.addRoundRect(new RectF(x * u, y * u, (x + w) * u, (y + h) * u), r * u, r * u, Path.Direction.CW);
        c.save();
        c.clipOutPath(clip);
        Paint p = pen.paint();
        p.setColor(0xFF000000);
        p.setShadowLayer(blur * u, 0, dy * u, color);
        rrect(c, u, x, y, w, h, r, p);
        c.restore();
    }

    static void paintCard(Pen pen, Spec s, P p, float x, float y, float w, float h) {
        Canvas c = pen.c;
        float u = pen.u;
        Look L = s.look;
        switch (p.card) {
            case GLASS: {
                cardShadow(pen, x, y, w, h, p.radius, 1.6f, 0.9f, 0x73000000);
                Paint base = pen.paint();
                base.setColor(L.light ? 0x6B14161E : 0x8CFFFFFF);
                rrect(c, u, x, y, w, h, p.radius, base);
                Paint tint = pen.paint();
                tint.setShader(new LinearGradient(x * u, y * u, (x + w) * u, (y + h) * u,
                        L.light ? new int[]{0x29FFFFFF, 0x2E14141A, 0x4214141A} : new int[]{0x8CFFFFFF, 0x52FFFFFF, 0x52FFFFFF},
                        new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
                rrect(c, u, x, y, w, h, p.radius, tint);
                break;
            }
            case PAPER:
            case RULED: {
                cardShadow(pen, x, y, w, h, p.radius, 2f, 1.1f, 0x8C000000);
                Paint base = pen.paint();
                base.setColor(L.bg);
                rrect(c, u, x, y, w, h, p.radius, base);
                Paint sheen = pen.paint();
                sheen.setShader(new LinearGradient(x * u, y * u, (x + w) * u, (y + h) * u,
                        new int[]{0x59FFFFFF, 0x00FFFFFF, 0x0F785A32}, new float[]{0f, 0.4f, 1f}, Shader.TileMode.CLAMP));
                rrect(c, u, x, y, w, h, p.radius, sheen);
                if (p.card == RULED) {                    // 红色竖线格
                    Paint ln = pen.paint();
                    ln.setColor(0x38B23A2E);
                    for (float lx = x + 0.9f + 2.2f; lx < x + w - 0.5f; lx += COL_W) {
                        c.drawRect(lx * u, (y + 0.5f) * u, lx * u + Math.max(1f, 0.07f * u), (y + h - 0.5f) * u, ln);
                    }
                }
                Paint bd = pen.paint();
                bd.setStyle(Paint.Style.STROKE);
                bd.setStrokeWidth(Math.max(1f, 0.07f * u));
                bd.setColor(p.card == RULED ? 0x59B23A2E : 0x47503220);
                float in = p.card == RULED ? 0.5f : 0.55f;
                c.drawRect((x + in) * u, (y + in) * u, (x + w - in) * u, (y + h - in) * u, bd);
                break;
            }
            case BAND: {
                Paint base = pen.paint();
                base.setColor(Look.withAlpha(L.bg, 0.9f));
                c.drawRect(x * u, y * u, (x + w) * u, (y + h) * u, base);
                Paint top = pen.paint();
                top.setColor(L.accent);
                c.drawRect(x * u, y * u, (x + w) * u, (y + 0.2f) * u, top);
                break;
            }
            case PLAQUE: {
                cardShadow(pen, x, y, w, h, p.radius, 1.6f, 0.8f, 0x73000000);
                Paint base = pen.paint();
                base.setColor(Look.withAlpha(L.bg, 0.66f));
                rrect(c, u, x, y, w, h, p.radius, base);
                Paint bd = pen.paint();
                bd.setStyle(Paint.Style.STROKE);
                bd.setStrokeWidth(Math.max(1f, 0.07f * u));
                bd.setColor(0x1FFFFFFF);
                rrect(c, u, x + 0.04f, y + 0.04f, w - 0.08f, h - 0.08f, p.radius, bd);
                break;
            }
            default: {
                if (L.style.equals("magazine")) {
                    boolean left = L.leftAligned();
                    float cw = c.getWidth();
                    float gx0 = left ? 0 : cw, gx1 = left ? (x + w) * u : x * u;
                    Paint g = pen.paint();
                    g.setShader(new LinearGradient(gx0, 0, gx1, 0,
                            L.light ? new int[]{0x7A000000, 0x3D000000, 0x00000000} : new int[]{0xB3FFFFFF, 0x66FFFFFF, 0x00FFFFFF},
                            new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
                    c.drawRect(left ? 0 : x * u, 0, left ? (x + w) * u : cw, c.getHeight(), g);
                } else if (p.scrim) {
                    float cx = x + w / 2f, cy = y + h / 2f, ax = w / 2f + 5.5f, ay = h / 2f + 4.5f;
                    c.save();
                    c.translate(cx * u, cy * u);
                    c.scale(ax * u, ay * u);
                    Paint g = pen.paint();
                    g.setShader(new RadialGradient(0, 0, 1f, L.light ? 0x3D000000 : 0x66FFFFFF,
                            L.light ? 0x00000000 : 0x00FFFFFF, Shader.TileMode.CLAMP));
                    c.drawCircle(0, 0, 1f, g);
                    c.restore();
                }
            }
        }
    }

    /** 盖在内容之上的装饰：玻璃的受光边缘、印章 */
    private static void paintOver(Pen pen, Fonts f, Spec s, P p, float x, float y, float w, float h) {
        Canvas c = pen.c;
        float u = pen.u;
        if (p.card == GLASS) {
            Paint bd = pen.paint();
            bd.setStyle(Paint.Style.STROKE);
            bd.setStrokeWidth(Math.max(1f, 0.07f * u));
            bd.setColor(0x38FFFFFF);
            rrect(c, u, x + 0.04f, y + 0.04f, w - 0.08f, h - 0.08f, p.radius, bd);
            Paint hi = pen.paint();
            hi.setStyle(Paint.Style.STROKE);
            hi.setStrokeWidth(Math.max(1.5f, 0.1f * u));
            hi.setShader(new LinearGradient(0, y * u, 0, (y + p.radius * 2) * u, 0x8CFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            rrect(c, u, x + 0.1f, y + 0.1f, w - 0.2f, h - 0.2f, p.radius, hi);
        }
        boolean seal = p.card == PAPER && s.look.kind.equals("east");
        if (seal) {
            float sw = 1.9f, sx = x + w - 1.8f - sw, sy = y + 1.8f;
            String txt = NUM_CN[s.viewMonth] + "月";
            float sh = txt.length() * 0.82f * 1.1f + 0.7f;
            c.save();
            c.rotate(-4f, (sx + sw / 2) * u, (sy + sh / 2) * u);
            Paint pt = pen.paint();
            pt.setColor(Look.withAlpha(0xFFB23A2E, 0.92f));
            rrect(c, u, sx, sy, sw, sh, 0.2f, pt);
            Paint bd = pen.paint();
            bd.setStyle(Paint.Style.STROKE);
            bd.setStrokeWidth(0.12f * u);
            bd.setColor(0x59F6EFE0);
            rrect(c, u, sx + 0.06f, sy + 0.06f, sw - 0.12f, sh - 0.12f, 0.15f, bd);
            pen.noShadow();
            for (int i = 0; i < txt.length(); i++) {
                pen.text(String.valueOf(txt.charAt(i)), sx + sw / 2, sy + 0.35f + i * 0.9f, 0.9f, f.serif(700), 0.82f,
                        0xFFF6EFE0, 1f, 0, Pen.CENTER);
            }
            c.restore();
        }
    }
}
