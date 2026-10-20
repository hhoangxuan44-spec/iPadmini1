package io.github.guangying.render;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;

import io.github.guangying.data.Photo;

/** 「画意」组件：这张图的标题、出处、诗句和几句描述，风格与月历组件一致。 */
public final class StoryArt {
    private StoryArt() {}

    public static Bitmap render(Fonts f, int w, int h, Look look, Bitmap backdrop, boolean contentOnly) {
        Bitmap bm = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888);
        draw(new Canvas(bm), f, w, h, look, backdrop, contentOnly);
        return bm;
    }

    private static CalendarArt.P params(Look l) {
        CalendarArt.P p = CalendarArt.params(l);
        switch (l.style) {
            case "minimal":
            case "poster":
                p.inner = 21f;
                break;
            case "glass":
                p.inner = 18.8f;
                p.padL = p.padR = 1.6f;
                p.padT = 1.3f;
                p.padB = 1.2f;
                p.radius = 1.5f;
                break;
            case "band":
                p.inner = 26f;
                p.fillWidth = false;
                p.margin = 0.6f;
                break;
            case "gallery":
                p.inner = 17f;
                p.padL = p.padR = 1.1f;
                p.padT = 1f;
                p.padB = 0.9f;
                p.radius = 0.3f;
                break;
            case "vpoem":
                p.card = 2;
                p.inner = 17.3f;
                p.padL = p.padR = 2.1f;
                p.padT = 2f;
                p.padB = 1.7f;
                p.stretch = false;
                break;
            default:
                break;
        }
        p.stretch = false;
        return p;
    }

    /** descLines：描述最多显示几行（<=0 不限）。返回内容高度（rem）。 */
    private static float content(Pen pen, Fonts f, Look L, CalendarArt.P p, float x, float y, float w, int descLines) {
        Photo ph = L.photo;
        boolean gallery = L.style.equals("gallery");
        boolean paper = L.style.equals("paper") || L.style.equals("vpoem");
        int ink = gallery ? 0xFF2A2420 : L.ink();
        if (p.card == 0) {
            if (L.light) pen.shadow(0.55f, 0.06f, 0x8C000000);
            else pen.shadow(0.8f, 0f, 0x99FFFFFF);
        } else {
            pen.noShadow();
        }
        Typeface titleFace = paper ? f.serif(700) : gallery ? f.serif(700) : f.sans(600);
        float tsz = paper ? 1.4f : gallery ? 1.1f : L.style.equals("glass") ? 1.15f : 1.3f;
        float tsp = paper ? 0.08f : 0.04f;
        float cy = y;
        cy += pen.paragraph(ph.title, x, cy, w, tsz * 1.35f, titleFace, tsz, ink, 1f, tsp, 2);
        if (!ph.sub.isEmpty()) {
            cy += 0.3f;
            int subColor = gallery ? 0xFF8A5A3C : L.accent;
            pen.text(ph.sub, x, cy, 1.0f, f.sans(600), 0.74f, subColor, 1f, 0.08f, Pen.LEFT);
            cy += 1.0f;
        }
        if (!ph.poem.isEmpty()) {
            cy += 0.6f;
            float psz = L.style.equals("band") ? 0.9f : gallery ? 0.86f : 0.98f;
            cy += pen.paragraph(ph.poem, x, cy, w, psz * 1.55f, f.serif(400), psz, ink, 1f, 0.08f, 3);
            cy += 0.1f;
            pen.text(ph.poemSrc, x, cy, 0.9f, f.sans(400), 0.62f, ink, 0.62f, 0.12f, Pen.LEFT);
            cy += 0.9f;
        }
        if (!ph.desc.isEmpty()) {
            cy += 0.6f;
            float dsz = gallery ? 0.74f : paper ? 0.84f : L.style.equals("glass") || L.style.equals("band") || L.style.equals("magazine") ? 0.8f : 0.86f;
            float dlh = gallery ? 1.6f : paper ? 1.75f : 1.7f;
            cy += pen.paragraph(ph.desc, x, cy, w, dsz * dlh, paper ? f.serif(400) : f.sans(400), dsz, ink, 0.92f, 0.02f,
                    descLines);
        }
        if (!ph.artist.isEmpty()) {
            cy += 0.55f;
            String credit = "© " + ph.artist + (ph.license.isEmpty() ? "" : " · " + ph.license);
            Typeface cf = f.sans(400);
            while (credit.length() > 4 && pen.width(credit, cf, 0.58f, 0) > w) credit = credit.substring(0, credit.length() - 2) + "…";
            pen.text(credit, x, cy, 0.8f, cf, 0.58f, ink, 0.5f, 0, Pen.LEFT);
            cy += 0.8f;
        }
        return cy - y;
    }

    public static float draw(Canvas canvas, Fonts f, int w, int h, Look L, Bitmap backdrop, boolean contentOnly) {
        CalendarArt.P p = params(L);
        float natInner = p.inner;
        boolean fill = p.card != 0;
        // 组件拉宽时文字行变长、拉高时行数变多：在一系列宽度里挑一个，让字能排得最大
        float bestU = -1, bestInner = natInner;
        int lines = 0;
        for (float iw = natInner * 0.85f; iw <= natInner * 2.4f; iw += 0.75f) {
            float ih = content(new Pen(null, 1), f, L, p, 0, 0, iw, 0);
            float cw = iw + p.padL + p.padR, ch = ih + p.padT + p.padB;
            float u = Math.min(w / (cw + 2 * p.margin), h / (ch + 2 * p.margin));
            if (u > bestU * 1.002f) {
                bestU = u;
                bestInner = iw;
            }
        }
        float innerW = bestInner, u = bestU, cardW, cardH;
        // 描述尽量完整；组件太矮时才减少行数，保证字不会缩得太小
        for (int tries = 0; tries < 10; tries++) {
            float ih = content(new Pen(null, 1), f, L, p, 0, 0, innerW, lines);
            cardW = innerW + p.padL + p.padR;
            cardH = ih + p.padT + p.padB;
            u = Math.min(w / (cardW + 2 * p.margin), h / (cardH + 2 * p.margin));
            float wu = w / (cardW + 2 * p.margin);
            if (u >= wu * 0.72f || lines == 2) break;
            lines = lines == 0 ? Math.max(2, descLinesOf(f, L, p, innerW) - 1) : Math.max(2, lines - 1);
        }
        float ih = content(new Pen(null, 1), f, L, p, 0, 0, innerW, lines);
        cardW = innerW + p.padL + p.padR;
        cardH = ih + p.padT + p.padB;
        float totalW = w / u, totalH = h / u;
        float x0, y0, cw = cardW, chh = cardH;
        if (fill) {
            // 有卡片的风格：卡片铺满整个组件，内容居中
            cw = Math.max(cardW, totalW - 2 * p.margin);
            chh = Math.max(cardH, totalH - 2 * p.margin);
            if (L.style.equals("band")) {
                cw = Math.max(cardW, totalW);
                chh = Math.min(chh, cardH * 1.5f);
            }
            x0 = L.style.equals("band") ? 0 : (totalW - cw) / 2f;
            y0 = (totalH - chh) / 2f;
        } else {
            x0 = L.leftAligned() ? p.margin : totalW - p.margin - cardW;
            y0 = L.pos.startsWith("t") ? p.margin : L.pos.startsWith("b") ? totalH - p.margin - cardH : (totalH - cardH) / 2f;
        }
        Pen pen = new Pen(canvas, u);
        if (contentOnly && fill) {
            // 卡片由原生布局铺满
        } else if (L.style.equals("gallery")) {
            CalendarArt.cardShadow(pen, x0, y0, cw, chh, p.radius, 1.6f, 0.9f, 0x80000000);
            Paint base = pen.paint();
            base.setColor(0xF0FAF6EE);
            CalendarArt.rrect(canvas, u, x0, y0, cw, chh, p.radius, base);
        } else {
            CalendarArt.Spec spec = new CalendarArt.Spec(w, h, L, java.util.Calendar.getInstance());
            spec.backdrop = backdrop;
            CalendarArt.paintCard(pen, spec, p, x0, y0, cw, chh);
        }
        float cx = x0 + (cw - cardW) / 2f + p.padL, cy = y0 + (chh - cardH) / 2f + p.padT;
        content(pen, f, L, p, cx, cy, innerW, lines);
        return u;
    }

    private static int descLinesOf(Fonts f, Look L, CalendarArt.P p, float w) {
        return Math.max(2, new Pen(null, 1).lineCount(L.photo.desc, w, f.sans(400), 0.8f, 0.02f, 0));
    }
}
