package render;

import android.graphics.Bitmap;
import java.io.File;
import java.io.FileOutputStream;

public final class Png {
    private Png() {}
    public static File save(Bitmap bmp, String name) throws Exception {
        File dir = new File(System.getProperty("render.out", "/tmp/render-out"));
        dir.mkdirs();
        File f = new File(dir, name.endsWith(".png") ? name : name + ".png");
        try (FileOutputStream o = new FileOutputStream(f)) {
            if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, o)) throw new IllegalStateException("PNG compress failed");
        }
        System.out.println("RENDER_OUT " + f.getAbsolutePath());
        return f;
    }
}
