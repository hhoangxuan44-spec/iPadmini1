package io.github.guangying.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/** 一张壁纸：图片地址、中文说明，以及构建时分析出来的日历主题（见 build/theme.py）。 */
public final class Photo {
    public String id;                 // 唯一标识（中等尺寸图片的路径）
    public String large, blur;        // photos/l/…  photos/b/…
    public int w, h;
    public String kind = "nature";    // nature east west city china sky animal
    public String title = "", sub = "", desc = "", artist = "", license = "";
    public String poem = "", poemSrc = "";
    public String tags = "";          // 以 / 连接的季节、时段、主题标签
    public String style = "glass";    // minimal glass magazine band poster paper gallery vpoem
    public String pos = "br";         // 构建时认为最空的位置
    public String ink = "light";
    public int accent = 0xFFFFD60A;
    public int bg = 0xFF111111;
    /** 清透鲜亮程度 0-100（构建时计算）：暗调、灰调、单色偏低 */
    public int v = 0;
    /** 画面里的光（构建时分析）：光源位置 0-1、方向性、高光色、阴影色 */
    public float lx = 0.32f, ly = 0.08f, lk = 0.45f;
    public int hl = 0xFFFFF0DC, sh = 0xFF10101A;
    /** 各位置的「繁忙度百分位」（0 最安静）和亮度（0-255），位置：tl tr bl br l r b */
    public final Map<String, int[]> quiet = new HashMap<>();

    public boolean has(String tag) {
        return ("/" + tags + "/").contains("/" + tag + "/");
    }

    static Photo parse(JSONObject o) throws JSONException {
        Photo p = new Photo();
        p.id = o.optString("m");
        p.large = o.optString("l", p.id);
        p.blur = o.optString("b", "");
        p.w = o.optInt("w", 4);
        p.h = o.optInt("h", 3);
        p.kind = o.optString("kind", p.kind);
        p.v = o.optInt("v", 0);
        p.title = o.optString("title");
        p.sub = o.optString("sub");
        p.desc = o.optString("desc");
        p.artist = o.optString("artist");
        p.license = o.optString("license");
        JSONArray poem = o.optJSONArray("poem");
        if (poem != null && poem.length() > 1) {
            p.poem = poem.optString(0);
            p.poemSrc = poem.optString(1);
        }
        JSONArray tags = o.optJSONArray("tags");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; tags != null && i < tags.length(); i++) {
            if (i > 0) sb.append('/');
            sb.append(tags.optString(i));
        }
        p.tags = sb.toString();
        JSONObject t = o.optJSONObject("t");
        if (t != null) {
            p.style = t.optString("style", p.style);
            p.pos = t.optString("pos", p.pos);
            p.ink = t.optString("ink", p.ink);
            p.accent = color(t.optString("accent"), p.accent);
            p.bg = color(t.optString("bg"), p.bg);
        }
        JSONArray lt = o.optJSONArray("L");
        if (lt != null && lt.length() >= 5) {
            p.lx = (float) lt.optDouble(0, p.lx);
            p.ly = (float) lt.optDouble(1, p.ly);
            p.lk = (float) lt.optDouble(2, p.lk);
            p.hl = color(lt.optString(3), p.hl);
            p.sh = color(lt.optString(4), p.sh);
        }
        JSONObject q = o.optJSONObject("q");
        if (q != null) {
            for (String k : new String[]{"tl", "tr", "bl", "br", "l", "r", "b"}) {
                JSONArray a = q.optJSONArray(k);
                if (a != null && a.length() > 1) p.quiet.put(k, new int[]{a.optInt(0), a.optInt(1)});
            }
        }
        return p;
    }

    private static int color(String hex, int dflt) {
        if (hex == null || hex.length() != 7 || hex.charAt(0) != '#') return dflt;
        try {
            return 0xFF000000 | Integer.parseInt(hex.substring(1), 16);
        } catch (NumberFormatException e) {
            return dflt;
        }
    }
}
