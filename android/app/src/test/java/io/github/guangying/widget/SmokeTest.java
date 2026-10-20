package io.github.guangying.widget;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RemoteViews;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowAppWidgetManager;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

import io.github.guangying.CrashLog;
import io.github.guangying.MainActivity;
import io.github.guangying.R;
import io.github.guangying.Site;
import io.github.guangying.data.Library;
import io.github.guangying.data.Photo;
import io.github.guangying.data.Prefs;
import io.github.guangying.render.Look;

/**
 * 冒烟测试：在没有手机的情况下，把「会让 App 或桌面闪退」的东西提前跑一遍——
 * 每种风格 × 每种组件的布局能不能真的展开并应用、主界面能不能打开、组件刷新会不会抛异常。
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34)
public class SmokeTest {
    static final String[] STYLES = {"minimal", "glass", "magazine", "band", "paper", "poster", "gallery", "vpoem"};
    private Context ctx;

    @Before
    public void setUp() throws Exception {
        ctx = RuntimeEnvironment.getApplication();
        File site = Site.root(ctx);
        new File(site, "data").mkdirs();
        new File(site, "photos/l").mkdirs();
        new File(site, "photos/b").mkdirs();
        StringBuilder js = new StringBuilder("var PHOTOS = [");
        for (int i = 0; i < STYLES.length; i++) {
            String st = STYLES[i];
            String kind = i % 3 == 0 ? "east" : i % 3 == 1 ? "nature" : "west";
            if (i > 0) js.append(',');
            js.append("{\"w\":1500,\"h\":1000,\"m\":\"photos/m/t").append(i).append(".jpg\",\"l\":\"photos/l/t").append(i)
                    .append(".jpg\",\"b\":\"photos/b/t").append(i).append(".jpg\",\"kind\":\"").append(kind)
                    .append("\",\"title\":\"神奈川冲浪里 ").append(i).append("\",\"sub\":\"葛饰北斋 · 约 1831 年\",")
                    .append("\"desc\":\"巨浪卷起泡沫的指爪，三条运货小船在浪谷中颠簸，远处的富士山安静得像一个小小的三角形。\",")
                    .append("\"poem\":[\"海日生残夜，江春入旧年。\",\"王湾《次北固山下》\"],\"tags\":[\"winter\",\"night\"],")
                    .append("\"artist\":\"Hokusai\",\"license\":\"公有领域\",")
                    .append("\"t\":{\"style\":\"").append(st).append("\",\"pos\":\"tl\",\"ink\":\"")
                    .append(st.equals("paper") || st.equals("vpoem") ? "dark" : "light")
                    .append("\",\"accent\":\"#b23a2e\",\"bg\":\"#3a2d2a\"},")
                    .append("\"q\":{\"tl\":[10,60],\"tr\":[50,100],\"bl\":[30,80],\"br\":[70,90],\"l\":[20,70],\"r\":[60,95],\"b\":[40,50]}}");
            Bitmap b = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888);
            b.eraseColor(0xFF4466AA);
            for (String d : new String[]{"photos/l/t" + i + ".jpg", "photos/b/t" + i + ".jpg"}) {
                try (FileOutputStream o = new FileOutputStream(new File(site, d))) {
                    b.compress(Bitmap.CompressFormat.JPEG, 80, o);
                }
            }
        }
        js.append("];");
        try (FileOutputStream o = new FileOutputStream(new File(site, "data/photos.js"))) {
            o.write(js.toString().getBytes("UTF-8"));
        }
    }

    @Test
    public void everyStyleAndKindInflatesAndApplies() {
        List<Photo> all = Library.load(ctx);
        assertTrue("图库应该解析出 " + STYLES.length + " 张", all.size() == STYLES.length);
        for (Photo p : all) {
            for (String pos : new String[]{"tl", "br", "l"}) {
                Look look = Look.of(p, pos);
                for (int kind = 0; kind < Widgets.PROVIDERS.length; kind++) {
                    for (int[] sz : new int[][]{{600, 800}, {900, 300}, {300, 300}}) {
                        Bitmap backdrop = Widgets.backdrop(ctx, look, kind);
                        Bitmap content = Widgets.render(ctx, kind, look, sz[0], sz[1], backdrop, true);
                        assertNotNull(content);
                        RemoteViews rv = Cards.build(ctx, look, kind, content, backdrop);
                        View v = rv.apply(ctx, new FrameLayout(ctx));   // 真正展开布局、执行每一条设置
                        assertNotNull(v);
                        v.measure(View.MeasureSpec.makeMeasureSpec(sz[0], View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(sz[1], View.MeasureSpec.EXACTLY));
                        v.layout(0, 0, sz[0], sz[1]);
                        v.draw(new Canvas(Bitmap.createBitmap(sz[0], sz[1], Bitmap.Config.ARGB_8888)));
                    }
                }
            }
        }
    }

    @Test
    public void previewRenders() {
        Bitmap b = io.github.guangying.render.Preview.render(ctx, 1200, 750);
        assertNotNull(b);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void refreshingAllWidgetsDoesNotThrow() {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        ShadowAppWidgetManager sm = shadowOf(mgr);
        int[] ids = new int[Widgets.PROVIDERS.length];
        for (int i = 0; i < ids.length; i++) ids[i] = sm.createWidget((Class<? extends android.appwidget.AppWidgetProvider>) Widgets.PROVIDERS[i], R.layout.widget_image);
        Widgets.refreshNow(ctx);
        for (int id : ids) assertNotNull("组件 " + id + " 没有刷新出内容", sm.getViewFor(id));
        assertTrue("刷新组件时记录到了错误：\n" + CrashLog.read(ctx), CrashLog.read(ctx).isEmpty());
    }

    @Test
    public void timerWidgetUpdates() {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        ShadowAppWidgetManager sm = shadowOf(mgr);
        int id = sm.createWidget(TimerWidgetProvider.class, R.layout.timer_widget);
        TimerWidgetProvider p = new TimerWidgetProvider();
        p.onUpdate(ctx, mgr, new int[]{id});
        assertNotNull(sm.getViewFor(id));
        for (String a : new String[]{TimerWidgetProvider.ACTION_CYCLE, TimerWidgetProvider.ACTION_PLUS, TimerWidgetProvider.ACTION_TOGGLE,
                TimerWidgetProvider.ACTION_MINUS, TimerWidgetProvider.ACTION_DONE, TimerWidgetProvider.ACTION_RESET}) {
            p.onReceive(ctx, new android.content.Intent(a));
        }
    }

    @Test
    public void mainActivityLaunches() {
        Activity a = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible().get();
        assertNotNull(a);
    }

    @Test
    public void prefsAndPick() {
        Prefs pr = new Prefs(ctx);
        assertTrue(pr.position().length() > 0);
        assertNotNull(io.github.guangying.Wall.current(ctx));
    }
}
