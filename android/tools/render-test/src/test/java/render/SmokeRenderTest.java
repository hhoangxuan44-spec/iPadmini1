package render;

import static org.junit.Assert.*;

import android.graphics.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SmokeRenderTest {

    private static int countNonBg(Bitmap b, int bg) {
        int n = 0;
        for (int y = 0; y < b.getHeight(); y++)
            for (int x = 0; x < b.getWidth(); x++) if (b.getPixel(x, y) != bg) n++;
        return n;
    }

    @Test public void shapes() throws Exception {
        Bitmap b = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(0xFF202830);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(40, 40, 760, 360, 0xFFFF7043, 0xFF3949AB, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(40, 40, 760, 360), 48, 48, p);
        p.setShader(null);
        p.setColor(0xFFFFFFFF);
        p.setTextSize(120);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setShadowLayer(14, 6, 8, 0x99000000);
        c.drawText("Hello 12:34", 80, 240, p);
        p.clearShadowLayer();
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(6); p.setColor(0xFF80CBC4);
        Path path = new Path(); path.moveTo(60, 500); path.cubicTo(250, 380, 450, 620, 740, 480);
        c.drawPath(path, p);
        Png.save(b, "smoke_shapes");
        assertEquals(800, b.getWidth());
        assertTrue(countNonBg(b, 0xFF202830) > 100000);
        assertNotEquals(b.getPixel(60, 60), b.getPixel(740, 340)); // gradient varies
    }

    @Test public void chineseText() throws Exception {
        Bitmap b = Bitmap.createBitmap(800, 300, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(0xFFFFFFFF);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF000000);
        p.setTypeface(Fonts.cjk());
        p.setTextSize(72);
        c.drawText("光阴 今日宜出行", 30, 110, p);
        p.setTextSize(48);
        c.drawText("农历九月初八 星期三", 30, 200, p);
        // 无字形(豆腐块)对不同汉字会画出同一形状；真实字形则 "光" 与 "阴" 像素不同
        Bitmap a = glyph("光"), d = glyph("阴");
        assertFalse("glyphs identical -> tofu", a.sameAs(d));
        Png.save(b, "smoke_cjk");
    }

    private Bitmap glyph(String s) {
        Bitmap b = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b); c.drawColor(0xFFFFFFFF);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setTypeface(Fonts.cjk()); p.setTextSize(80);
        c.drawText(s, 5, 80, p);
        return b;
    }

    @Test public void customTtfDigits() throws Exception {
        Bitmap b = Bitmap.createBitmap(800, 260, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(0xFF000000);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF00E676);
        p.setTypeface(Fonts.digits());
        p.setTextSize(110);
        c.drawText("0123456789", 20, 105, p);
        p.setColor(0xFFFFC107);
        c.drawText("21:45.30", 20, 230, p);
        Png.save(b, "smoke_digits");
        assertTrue(countNonBg(b, 0xFF000000) > 5000);
    }
}
