package render;

import android.graphics.*;
import io.github.guangying.data.Photo;
import io.github.guangying.render.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.util.Calendar;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WidgetRenderTest {
    static io.github.guangying.render.Fonts fonts() {
        Typeface cjk = render.Fonts.cjk();
        String[] n = {"extralight", "light", "regular", "medium", "semibold"};
        Typeface[] t = new Typeface[5];
        for (int i = 0; i < 5; i++)
            t[i] = Typeface.createFromFile(new File("../../app/src/main/assets/fonts/inter-" + n[i] + ".ttf"));
        return new io.github.guangying.render.Fonts(t[0], t[1], t[2], t[3], t[4], cjk, cjk);
    }

    static Photo photo(String style, String kind, String ink, int accent, int bg) {
        Photo p = new Photo();
        p.id = "t";
        p.style = style;
        p.kind = kind;
        p.ink = ink;
        p.accent = accent;
        p.bg = bg;
        p.title = "神奈川冲浪里";
        p.sub = "葛饰北斋 · 约 1831 年";
        p.poem = "海日生残夜，江春入旧年。";
        p.poemSrc = "王湾《次北固山下》";
        p.desc = "巨浪卷起泡沫的指爪，三条运货小船在浪谷中颠簸，远处的富士山安静得像一个小小的三角形。画面里的蓝色是当时刚从欧洲传入的普鲁士蓝。";
        p.artist = "Katsushika Hokusai";
        p.license = "公有领域";
        return p;
    }

    /** 一张彩色渐变，当作壁纸 */
    static Bitmap wall(int w, int h, int c1, int c2) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setShader(new LinearGradient(0, 0, w, h, c1, c2, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        Paint m = new Paint(Paint.ANTI_ALIAS_FLAG);
        m.setColor(0xFFFFE8C0);
        c.drawCircle(w * 0.62f, h * 0.3f, h * 0.12f, m);
        m.setColor(0x55000000);
        Path mt = new Path();
        mt.moveTo(0, h); mt.lineTo(0, h * 0.7f); mt.lineTo(w * 0.3f, h * 0.45f); mt.lineTo(w * 0.55f, h * 0.7f);
        mt.lineTo(w * 0.8f, h * 0.5f); mt.lineTo(w, h * 0.75f); mt.lineTo(w, h); mt.close();
        c.drawPath(mt, m);
        return b;
    }

    @Test public void allStyles() throws Exception {
        io.github.guangying.render.Fonts f = fonts();
        String[][] defs = {
            {"minimal", "nature", "light", "tl"}, {"minimal", "nature", "dark", "tr"},
            {"glass", "nature", "light", "br"}, {"magazine", "nature", "light", "r"},
            {"band", "nature", "light", "bl"}, {"paper", "east", "dark", "tl"},
            {"poster", "sky", "light", "tr"}, {"gallery", "west", "light", "r"}, {"vpoem", "east", "dark", "l"},
        };
        int[][] sizes = {{560, 800}, {980, 440}, {640, 640}, {420, 900}, {900, 300}};
        Calendar now = Calendar.getInstance();
        now.clear();
        now.set(2026, Calendar.OCTOBER, 19, 7, 18);
        for (String[] d : defs) {
            int accent = d[0].equals("paper") || d[0].equals("vpoem") ? 0xFFB23A2E : d[2].equals("light") ? 0xFFF0A080 : 0xFF7A3A22;
            int bg = d[0].equals("paper") || d[0].equals("vpoem") ? 0xFFF1EADA : d[0].equals("band") ? 0xFF2A3350 : 0xFF3A2D2A;
            Photo p = photo(d[0], d[1], d[2], accent, bg);
            Look look = Look.of(p, d[3]);
            int tag = 0;
            for (int[] s : sizes) {
                Bitmap w = wall(s[0], s[1], 0xFF35486B, 0xFFB9603A);
                CalendarArt.Spec spec = new CalendarArt.Spec(s[0], s[1], look, now);
                spec.backdrop = Bitmap.createScaledBitmap(w, Math.max(2, s[0] / 24), Math.max(2, s[1] / 24), true);
                spec.motto = new String[]{"知者不惑，仁者不忧，勇者不惧。", "《论语》"};
                Bitmap cal = CalendarArt.render(f, spec);
                new Canvas(w).drawBitmap(cal, 0, 0, null);
                Png.save(w, "cal_" + d[0] + "_" + d[3] + "_" + s[0] + "x" + s[1]);
                Bitmap w2 = wall(s[0], s[1], 0xFF35486B, 0xFFB9603A);
                new Canvas(w2).drawBitmap(StoryArt.render(f, s[0], s[1], look, Bitmap.createScaledBitmap(w2, Math.max(2, s[0] / 24), Math.max(2, s[1] / 24), true)), 0, 0, null);
                Png.save(w2, "story_" + d[0] + "_" + d[3] + "_" + s[0] + "x" + s[1]);
            }
        }
    }

    @Test public void parts() throws Exception {
        io.github.guangying.render.Fonts f = fonts();
        Calendar now = Calendar.getInstance();
        now.clear();
        now.set(2026, Calendar.OCTOBER, 19, 7, 18);
        String[][] defs = {
            {"minimal", "nature", "light", "tl"}, {"glass", "nature", "light", "br"}, {"magazine", "nature", "light", "r"},
            {"band", "nature", "light", "bl"}, {"paper", "east", "dark", "tl"}, {"poster", "sky", "light", "tr"},
            {"gallery", "west", "light", "r"}, {"vpoem", "east", "dark", "l"},
        };
        String[] names = {"full", "clock", "week", "quote"};
        int[][] sizes = {{520, 520}, {800, 320}};
        for (String[] d : defs) {
            int accent = d[0].equals("paper") || d[0].equals("vpoem") ? 0xFFB23A2E : d[2].equals("light") ? 0xFFF0A080 : 0xFF7A3A22;
            int bg = d[0].equals("paper") || d[0].equals("vpoem") ? 0xFFF1EADA : d[0].equals("band") ? 0xFF2A3350 : 0xFF3A2D2A;
            Look look = Look.of(photo(d[0], d[1], d[2], accent, bg), d[3]);
            for (int part = 0; part < 4; part++) {
                for (int[] sz : sizes) {
                    Bitmap w = wall(sz[0], sz[1], 0xFF35486B, 0xFFB9603A);
                    CalendarArt.Spec spec = new CalendarArt.Spec(sz[0], sz[1], look, now);
                    spec.part = part;
                    spec.backdrop = Bitmap.createScaledBitmap(w, Math.max(2, sz[0] / 24), Math.max(2, sz[1] / 24), true);
                    spec.motto = new String[]{"知者不惑，仁者不忧，勇者不惧。", "《论语》"};
                    new Canvas(w).drawBitmap(CalendarArt.render(f, spec), 0, 0, null);
                    Png.save(w, "part_" + names[part] + "_" + d[0] + "_" + sz[0] + "x" + sz[1]);
                }
            }
        }
    }

    /** 生成桌面组件选择器里的预览图（res/drawable-nodpi） */
    @Test public void previews() throws Exception {
        io.github.guangying.render.Fonts f = fonts();
        Calendar now = Calendar.getInstance();
        now.clear();
        now.set(2026, Calendar.OCTOBER, 19, 7, 18);
        Photo p = photo("glass", "nature", "light", 0xFFF0A080, 0xFF3A2D2A);
        Look look = Look.of(p, "tl");
        File dir = new File("../../app/src/main/res/drawable-nodpi");
        dir.mkdirs();
        int[][] sz = {{520, 640}, {520, 360}};
        String[] names = {"preview_calendar", "preview_story"};
        for (int i = 0; i < 2; i++) {
            Bitmap w = wall(sz[i][0], sz[i][1], 0xFF35486B, 0xFFB9603A);
            Bitmap art;
            if (i == 0) {
                CalendarArt.Spec spec = new CalendarArt.Spec(sz[i][0], sz[i][1], look, now);
                spec.motto = new String[]{"知者不惑，仁者不忧，勇者不惧。", "《论语》"};
                spec.backdrop = Bitmap.createScaledBitmap(w, 22, 27, true);
                art = CalendarArt.render(f, spec);
            } else {
                art = StoryArt.render(f, sz[i][0], sz[i][1], look, Bitmap.createScaledBitmap(w, 22, 15, true));
            }
            new Canvas(w).drawBitmap(art, 0, 0, null);
            try (java.io.FileOutputStream o = new java.io.FileOutputStream(new File(dir, names[i] + ".png"))) {
                w.compress(Bitmap.CompressFormat.PNG, 100, o);
            }
        }
    }

    @Test public void previews2() throws Exception {
        io.github.guangying.render.Fonts f = fonts();
        Calendar now = Calendar.getInstance();
        now.clear();
        now.set(2026, Calendar.OCTOBER, 19, 7, 18);
        Photo p = photo("glass", "nature", "light", 0xFFF0A080, 0xFF3A2D2A);
        Look look = Look.of(p, "tl");
        File dir = new File("../../app/src/main/res/drawable-nodpi");
        dir.mkdirs();
        int[] parts = {CalendarArt.CLOCK, CalendarArt.WEEK, CalendarArt.QUOTE};
        String[] names = {"preview_clock", "preview_week", "preview_quote"};
        for (int i = 0; i < 3; i++) {
            int w = 520, h = i == 1 ? 260 : 300;
            Bitmap bg = wall(w, h, 0xFF35486B, 0xFFB9603A);
            CalendarArt.Spec spec = new CalendarArt.Spec(w, h, look, now);
            spec.part = parts[i];
            spec.backdrop = Bitmap.createScaledBitmap(bg, 22, 14, true);
            spec.motto = new String[]{"知者不惑，仁者不忧，勇者不惧。", "《论语》"};
            new Canvas(bg).drawBitmap(CalendarArt.render(f, spec), 0, 0, null);
            try (java.io.FileOutputStream o = new java.io.FileOutputStream(new File(dir, names[i] + ".png"))) {
                bg.compress(Bitmap.CompressFormat.PNG, 100, o);
            }
        }
    }

    @Test public void gallery() throws Exception {
        for (int[] s : new int[][]{{1600, 1000}, {900, 1400}}) {
            for (String pos : new String[]{"tl", "br"}) {
                Photo p = photo("gallery", "west", "light", 0xFFF0A080, 0xFF3A2D2A);
                p.w = 1500; p.h = 1000;
                Bitmap art = wall(1500, 1000, 0xFF35486B, 0xFFB9603A);
                Bitmap blur = wall(60, 40, 0xFF35486B, 0xFFB9603A);
                Bitmap frame = io.github.guangying.render.WallArt.compose(art, blur, p, pos, s[0], s[1]);
                Png.save(frame, "wall_gallery_" + pos + "_" + s[0] + "x" + s[1]);
            }
        }
    }
}
