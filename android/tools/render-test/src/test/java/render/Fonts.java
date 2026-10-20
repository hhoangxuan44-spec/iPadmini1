package render;

import android.graphics.Typeface;
import java.io.File;

/** 测试用字体助手。渲染代码应接受注入的 Typeface，由测试通过这里取得。 */
public final class Fonts {
    private Fonts() {}

    public static synchronized Typeface cjk() {
        if (cjk == null) cjk = loadCjk();
        return cjk;
    }
    private static Typeface cjk;

    private static Typeface loadCjk() {
        String prop = System.getProperty("cjk.font");
        String[] cands = {
            prop,
            "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc"
        };
        for (String p : cands) {
            if (p == null || p.isEmpty() || p.startsWith("${")) continue;
            File f = new File(p);
            if (!f.isFile()) continue;
            try {
                Typeface t = new Typeface.Builder(f).setTtcIndex(0).build();
                if (t != null) return t;
            } catch (Throwable ignored) {}
            Typeface t = Typeface.createFromFile(f);
            if (t != null) return t;
        }
        throw new IllegalStateException("找不到 CJK 字体，请 -Dcjk.font=/path/to/font.ttf");
    }

    /** 自定义 TTF（仅数字子集，src/test/resources/testfonts/digits.ttf），经 Typeface.createFromFile 加载。 */
    public static Typeface digits() {
        File f = new File("src/test/resources/testfonts/digits.ttf");
        return Typeface.createFromFile(f);
    }
}
