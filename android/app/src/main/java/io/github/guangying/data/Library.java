package io.github.guangying.data;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;

import io.github.guangying.Site;
import io.github.guangying.core.Lunar;

/** 图库：读取网站上的 photos.js，并按季节、时段、节日和日历位置挑下一张。 */
public final class Library {
    private static List<Photo> cache;
    private static long cacheStamp = -1;

    private Library() {}

    public static synchronized List<Photo> load(Context ctx) {
        File f = new File(Site.root(ctx), "data/photos.js");
        if (!f.isFile()) return new ArrayList<>();
        if (cache != null && cacheStamp == f.lastModified()) return cache;
        List<Photo> out = new ArrayList<>();
        try {
            String text = read(f);
            JSONArray arr = new JSONArray(text.substring(text.indexOf('['), text.lastIndexOf(']') + 1));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Photo p = Photo.parse(o);
                if (p.id != null && !p.id.isEmpty()) out.add(p);
            }
        } catch (Exception e) {
            return cache != null ? cache : new ArrayList<Photo>();
        }
        cache = out;
        cacheStamp = f.lastModified();
        return out;
    }

    public static Photo find(List<Photo> all, String id) {
        for (Photo p : all) if (p.id.equals(id)) return p;
        return null;
    }

    private static String read(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toString("UTF-8");
        }
    }

    public static File file(Context ctx, String path) {
        return new File(Site.root(ctx), path);
    }

    // ------------------------------------------------------------ 应景选图

    /** 季节、时段、节日：和网页版（js/wall.js 的 scene / weight）保持一致。 */
    static final class Scene {
        String season, part;
        List<String> fest = new ArrayList<>();
    }

    static Scene scene(Calendar now) {
        Scene s = new Scene();
        int m = now.get(Calendar.MONTH) + 1;
        double h = now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE) / 60.0;
        s.season = m >= 3 && m <= 5 ? "spring" : m >= 6 && m <= 8 ? "summer" : m >= 9 && m <= 11 ? "autumn" : "winter";
        s.part = (h >= 19 || h < 5) ? "night" : h < 8.5 ? "dawn" : h >= 16.5 ? "dusk" : "day";
        Lunar.Day l = Lunar.info(now.get(Calendar.YEAR), m, now.get(Calendar.DAY_OF_MONTH)).lunar;
        if (!l.isLeap) {
            if (l.month == 8 && l.day >= 13 && l.day <= 17) s.fest.add("moon");
            if ((l.month == 12 && l.day >= 23) || (l.month == 1 && l.day <= 15)) s.fest.add("festive");
            if (l.month == 1 && l.day >= 13 && l.day <= 15) s.fest.add("moon");
            if (l.month == 7 && l.day >= 5 && l.day <= 7) s.fest.add("stars");
            if (l.month == 9 && l.day >= 7 && l.day <= 9) s.fest.add("mountain");
        }
        return s;
    }

    static double weight(Photo p, Scene c, String pos) {
        double w = 1;
        if (p.has("spring") || p.has("summer") || p.has("autumn") || p.has("winter")) w *= p.has(c.season) ? 3 : 0.35;
        boolean nightly = p.has("night") || p.has("stars");
        if (c.part.equals("night")) {
            if (nightly || p.has("moon")) w *= 3; else if (p.has("dawn")) w *= 0.5;
        } else {
            if (nightly) w *= 0.3;
            if (c.part.equals("dawn") && p.has("dawn")) w *= 2.5;
            if (c.part.equals("dusk") && p.has("dusk")) w *= 2.5;
        }
        for (String f : c.fest) if (p.has(f)) w *= 6;
        // 日历组件所在的位置画面安静的图片优先；画廊装裱可以自己安排位置，不受影响
        int[] q = p.quiet.get(pos);
        if (q != null && !p.style.equals("gallery")) w *= q[0] <= 40 ? 2.5 : q[0] <= 60 ? 1.2 : 0.35;
        return w;
    }

    /** 加权随机挑一张（最近出现过的先不选）。cachedOnly：只在已经存到本地的图片里挑。 */
    public static Photo pick(Context ctx, List<Photo> all, List<String> recent, Calendar now, String pos,
                             boolean cachedOnly, Random rnd) {
        Scene c = scene(now);
        Photo best = null;
        double bestKey = -1;
        for (int pass = 0; pass < 2 && best == null; pass++) {
            for (Photo p : all) {
                if (cachedOnly && !file(ctx, p.large).isFile()) continue;
                if (pass == 0 && recent.contains(p.id) && all.size() > recent.size() + 1) continue;
                double key = Math.pow(rnd.nextDouble(), 1.0 / weight(p, c, pos));
                if (key > bestKey) {
                    bestKey = key;
                    best = p;
                }
            }
        }
        return best;
    }
}
