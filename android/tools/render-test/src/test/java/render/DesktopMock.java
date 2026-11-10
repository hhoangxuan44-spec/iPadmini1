package render;

import android.graphics.*;
import io.github.guangying.data.Photo;
import io.github.guangying.render.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.nio.file.Files;
import java.util.Calendar;
import java.util.List;

/** 整屏效果图：壁纸 + 月历组件 + 画意组件，按桌面组件的图层顺序叠放（和 widget/Cards 一致）。场景图在 /tmp/mock。 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DesktopMock {
    static final int W = 1920, H = 1200;
    static Bitmap tile(String name) { return BitmapFactory.decodeFile(new File("../../app/src/main/assets/tex/" + name).getPath()); }

    static void widget(Canvas c, io.github.guangying.render.Fonts f, Look look, Bitmap wall, int kind, Rect r, Calendar now) {
        int w = r.width(), h = r.height();
        String st = look.style;
        Bitmap tmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas t = new Canvas(tmp);
        boolean card = st.equals("glass") || st.equals("paper") || st.equals("vpoem") || st.equals("gallery") || st.equals("band");
        float radius = st.equals("glass") ? 48 : st.equals("gallery") ? 24 : st.equals("band") ? 0 : 8;
        if (card) {
            if (st.equals("glass") || (st.equals("gallery") && kind == 0)) {
                Bitmap crop = Bitmap.createBitmap(wall, r.left, r.top, w, h);
                Bitmap small = Bitmap.createScaledBitmap(crop, Math.max(2, w / 40), Math.max(2, h / 40), true);
                Paint bp = new Paint(Paint.FILTER_BITMAP_FLAG);
                ColorMatrix cm = new ColorMatrix(); cm.setSaturation(1.4f); bp.setColorFilter(new ColorMatrixColorFilter(cm));
                t.drawBitmap(small, null, new Rect(0, 0, w, h), bp);
                Paint tint = new Paint(); tint.setColor(st.equals("glass") ? (look.light ? 0x40101218 : 0x66FFFFFF) : Look.withAlpha(look.bg, 0.72f));
                t.drawRect(0, 0, w, h, tint);
            } else {
                Paint fill = new Paint();
                fill.setColor(st.equals("band") ? Look.withAlpha(look.bg, 0.94f) : st.equals("gallery") ? 0xFFFAF6EE : look.bg);
                t.drawRect(0, 0, w, h, fill);
            }
            int mat = st.equals("glass") ? Lighting.GLASS : st.equals("band") ? Lighting.BAND : st.equals("gallery") && kind == 0 ? Lighting.PLAQUE : Lighting.PAPER;
            String tex = st.equals("glass") ? "grain.png" : st.equals("band") ? "brushed.png" : st.equals("gallery") && kind == 0 ? "plaster.png" : "paper.png";
            Paint tp = new Paint(); tp.setShader(new BitmapShader(tile(tex), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)); tp.setAlpha(st.equals("glass") ? 140 : st.equals("band") ? 150 : 255);
            t.drawRect(0, 0, w, h, tp);
            if (!(st.equals("gallery") && kind == 1)) t.drawBitmap(Lighting.light(look, mat), null, new Rect(0, 0, w, h), new Paint(Paint.FILTER_BITMAP_FLAG));
            if (!st.equals("band")) t.drawBitmap(Lighting.rim(look, mat), null, new Rect(0, 0, w, h), new Paint(Paint.FILTER_BITMAP_FLAG));
        }
        Bitmap content;
        if (kind == 1) content = StoryArt.render(f, w, h, look, null, true);
        else {
            CalendarArt.Spec spec = new CalendarArt.Spec(w, h, look, now);
            spec.contentOnly = true;
            spec.part = kind == 2 ? CalendarArt.CLOCK : kind == 3 ? CalendarArt.WEEK : kind == 4 ? CalendarArt.QUOTE : CalendarArt.FULL;
            spec.motto = new String[]{"不畏浮云遮望眼，自缘身在最高层。", "王安石《登飞来峰》"};
            content = CalendarArt.render(f, spec);
        }
        t.drawBitmap(content, 0, 0, null);
        if (card) {
            Paint sh = new Paint(Paint.ANTI_ALIAS_FLAG); sh.setColor(0x01000000); sh.setShadowLayer(40, 0, 18, 0x66000000);
            c.drawRoundRect(new RectF(r), radius, radius, sh);
        }
        Path rr = new Path(); rr.addRoundRect(new RectF(r), radius, radius, Path.Direction.CW);
        c.save(); c.clipPath(rr); c.drawBitmap(tmp, r.left, r.top, null); c.restore();
    }

    @Test public void desktop() throws Exception {
        WallArt.texDir = new File("../../app/src/main/assets/tex");
        io.github.guangying.render.Fonts f = WidgetRenderTest.fonts();
        Calendar now = Calendar.getInstance(); now.clear(); now.set(2026, Calendar.OCTOBER, 24, 7, 40);
        List<String> meta = Files.readAllLines(new File("/tmp/mock/meta.txt").toPath());
        String plan = System.getProperty("mock.plan", "0:glass,1:minimal,2:minimal,3:poster,4:paper,5:band,6:magazine");
        String[] titles = {"红霞山脉", "月下海面", "雪岭晴空", "落日沙丘", "林间晨雾", "极光夜", "暮色城市"};
        for (String item : plan.split(",")) {
            int i = Integer.parseInt(item.split(":")[0]);
            String style = item.split(":")[1];
            String[] m = meta.get(i).split("\\|");
            boolean paper = style.equals("paper") || style.equals("vpoem");
            Photo p = WidgetRenderTest.photo(style, paper ? "east" : "nature", "light", paper ? 0xFFB23A2E : Color.parseColor(m[6]),
                    paper ? 0xFFF1EADA : style.equals("band") ? Look.withLightness(Color.parseColor(m[6]), 0.22f) : 0xFF2A2420);
            p.title = titles[i]; p.sub = "冰岛 · 瓦特纳冰川国家公园";
            p.lx = Float.parseFloat(m[1]); p.ly = Float.parseFloat(m[2]); p.lk = Float.parseFloat(m[3]);
            p.hl = Color.parseColor(m[4]); p.sh = Color.parseColor(m[5]);
            int lum = Integer.parseInt(m[7]);
            p.quiet.put("tl", new int[]{20, lum}); p.quiet.put("l", new int[]{20, lum});
            p.w = W; p.h = H;
            Bitmap full = BitmapFactory.decodeFile("/tmp/mock/s" + i + ".png");
            Bitmap wall = WallArt.compose(full, null, p, "tl", W, H);
            Look look = Look.of(p, style.equals("magazine") || style.equals("vpoem") ? "l" : "tl");
            Canvas c = new Canvas(wall);
            Rect cal = style.equals("band") ? new Rect(0, 780, 1180, 1200) : style.equals("magazine") ? new Rect(0, 0, 560, 1200)
                    : style.equals("poster") ? new Rect(70, 70, 900, 800) : new Rect(70, 70, 640, 850);
            widget(c, f, look, wall.copy(Bitmap.Config.ARGB_8888, false), 0, cal, now);
            Rect story = style.equals("band") ? new Rect(1240, 60, 1860, 380) : new Rect(1230, 820, 1850, 1140);
            widget(c, f, look, wall.copy(Bitmap.Config.ARGB_8888, false), 1, story, now);
            Png.save(wall, System.getProperty("mock.tag", "desk") + "_" + i + "_" + style);
        }
    }
}
