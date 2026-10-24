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
public class GalleryRenderTest {
    static Bitmap painting(int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setShader(new LinearGradient(0, 0, 0, h, 0xFF9FB8D8, 0xFFD8B070, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF4A5A48);
        Path m = new Path();
        m.moveTo(0, h); m.lineTo(0, h * .6f); m.lineTo(w * .35f, h * .35f); m.lineTo(w * .7f, h * .62f); m.lineTo(w, h * .5f); m.lineTo(w, h); m.close();
        c.drawPath(m, p);
        return b;
    }

    @Test public void gallery() throws Exception {
        WallArt.texDir = new File("../../app/src/main/assets/tex");
        for (String[] d : new String[][]{{"r", "1548", "968", "1.5"}, {"l", "968", "1548", "0.66"}}) {
            Photo p = WidgetRenderTest.photo("gallery", "west", "light", 0xFFF0A080, 0xFF3A2D2A);
            int w = Integer.parseInt(d[1]), h = Integer.parseInt(d[2]);
            p.w = 1500; p.h = 1000;
            p.lx = 0.78f; p.ly = 0.12f; p.lk = 0.6f; p.hl = 0xFFFFE4C0; p.sh = 0xFF1E1A14;
            Bitmap art = painting(1500, 1000);
            Bitmap blur = Bitmap.createScaledBitmap(art, 32, 24, true);
            Bitmap wall = WallArt.compose(art, blur, p, d[0], w, h);
            Png.save(wall, "wall_gallery_" + w + "x" + h);
        }
        Photo q = WidgetRenderTest.photo("minimal", "nature", "light", 0xFFF0A080, 0xFF3A2D2A);
        Bitmap full = WidgetRenderTest.wall(1548, 968, 0xFF35486B, 0xFFB9603A);
        Png.save(WallArt.compose(full, null, q, "tl", 1548, 968), "wall_plain");
    }
}
