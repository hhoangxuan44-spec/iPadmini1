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

/** 近似桌面组件的图层叠放：底色 → 贴图 → 受光 → 边缘 → 文字位图（和 widget/Cards 的顺序一致） */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LayerRenderTest {
    static Bitmap tile(String name) { return BitmapFactory.decodeFile(new File("../../app/src/main/assets/tex/" + name).getPath()); }

    @Test public void stack() throws Exception {
        io.github.guangying.render.Fonts f = WidgetRenderTest.fonts();
        Calendar now = Calendar.getInstance(); now.clear(); now.set(2026, Calendar.OCTOBER, 24, 7, 40);
        Object[][] defs = {
            {"glass", "nature", "light", "tl", Lighting.GLASS, "grain.png", 0.55f, 0xE016181F},
            {"paper", "east", "dark", "tl", Lighting.PAPER, "paper.png", 1f, 0xFFF1EADA},
            {"band", "nature", "light", "bl", Lighting.BAND, "brushed.png", 1f, 0xF02A3350},
            {"gallery", "west", "light", "r", Lighting.PLAQUE, "plaster.png", 0.9f, 0xB83A2D2A},
        };
        int W = 560, H = 760;
        Bitmap sheet = Bitmap.createBitmap(W * 4, H, Bitmap.Config.ARGB_8888);
        Canvas sc = new Canvas(sheet);
        for (int i = 0; i < defs.length; i++) {
            Object[] d = defs[i];
            Photo p = WidgetRenderTest.photo((String) d[0], (String) d[1], (String) d[2], d[0].equals("paper") ? 0xFFB23A2E : 0xFFF0A080, (Integer) d[7]);
            p.lx = 0.7f; p.ly = 0.15f; p.lk = 0.7f; p.hl = 0xFFFFE0B8; p.sh = 0xFF201820;
            Look look = Look.of(p, (String) d[3]);
            Bitmap bg = WidgetRenderTest.wall(W, H, 0xFF35486B, 0xFFB9603A);
            Canvas c = new Canvas(bg);
            Bitmap tmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
            Canvas t = new Canvas(tmp);
            Paint fill = new Paint(); fill.setColor((Integer) d[7]); t.drawRect(0, 0, W, H, fill);
            Paint tp = new Paint(); tp.setShader(new BitmapShader(tile((String) d[5]), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)); tp.setAlpha(Math.round(255 * (Float) d[6]));
            t.drawRect(0, 0, W, H, tp);
            t.drawBitmap(Lighting.light(look, (Integer) d[4]), null, new Rect(0, 0, W, H), new Paint(Paint.FILTER_BITMAP_FLAG));
            t.drawBitmap(Lighting.rim(look, (Integer) d[4]), null, new Rect(0, 0, W, H), new Paint(Paint.FILTER_BITMAP_FLAG));
            CalendarArt.Spec spec = new CalendarArt.Spec(W, H, look, now);
            spec.contentOnly = true;
            spec.motto = new String[]{"知者不惑，仁者不忧，勇者不惧。", "《论语》"};
            t.drawBitmap(CalendarArt.render(f, spec), 0, 0, null);
            Path rr = new Path(); rr.addRoundRect(new RectF(30, 30, W - 30, H - 30), 40, 40, Path.Direction.CW);
            c.save(); c.clipPath(rr); c.drawBitmap(tmp, 0, 0, null); c.restore();
            sc.drawBitmap(bg, i * W, 0, null);
        }
        Png.save(sheet, "layers_sheet");
    }
}
