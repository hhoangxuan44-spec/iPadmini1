package io.github.guangying;

import android.app.Activity;
import android.app.WallpaperManager;
import android.appwidget.AppWidgetManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import io.github.guangying.data.Photo;
import io.github.guangying.data.Prefs;
import io.github.guangying.render.Preview;
import io.github.guangying.widget.CalendarWidget;
import io.github.guangying.widget.ClockWidget;
import io.github.guangying.widget.QuoteWidget;
import io.github.guangying.widget.TimerWidgetProvider;
import io.github.guangying.widget.WeekWidget;
import io.github.guangying.widget.StoryWidget;
import io.github.guangying.widget.Widgets;

/** 主界面：预览、设为壁纸、添加桌面组件、选择组件位置、摆件模式、屏保、下载图片。 */
public class MainActivity extends Activity implements Wall.Listener {
    private ImageView preview;
    private TextView status, caption;
    private final TextView[] chips = new TextView[Prefs.POSITIONS.length];
    private float dp;
    private Prefs prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private int pw, ph;
    private boolean rendering;
    private final Runnable minute = new Runnable() {
        @Override
        public void run() {
            renderPreview();
            main.postDelayed(this, 60000);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        dp = getResources().getDisplayMetrics().density;
        prefs = new Prefs(this);

        boolean wide = getResources().getConfiguration().screenWidthDp > getResources().getConfiguration().screenHeightDp;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        final int pad = px(32);
        root.setPadding(pad, pad, pad, pad);
        // 内容铺满全屏（Android 15 起的默认做法），四周留出状态栏和导航条的位置
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(pad + i.left, pad + i.top, pad + i.right, pad + i.bottom);
            return WindowInsets.CONSUMED;
        });

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(0xFF111111);
        frame.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), px(18));
            }
        });
        frame.setClipToOutline(true);
        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_XY);
        preview.setOnClickListener(v -> next());
        frame.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        caption = text("", 13, 0xFF8E8E93);
        caption.setPadding(px(4), px(10), px(4), 0);
        left.addView(frame, new LinearLayout.LayoutParams(-1, wide ? 0 : px(0), 1f));
        left.addView(caption);

        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setPadding(wide ? px(36) : 0, wide ? 0 : px(24), 0, px(24));

        final String crash = CrashLog.read(this);
        if (!crash.isEmpty()) {
            TextView ct = text("上次出错的记录（点这里复制，发给我就能修）：\n\n" + crash.substring(0, Math.min(crash.length(), 1500)), 11, 0xFFFFB4A8);
            ct.setPadding(px(12), px(10), px(12), px(10));
            GradientDrawable cb = new GradientDrawable();
            cb.setCornerRadius(px(10));
            cb.setColor(0xFF2A1512);
            ct.setBackground(cb);
            ct.setOnClickListener(v -> {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(android.content.ClipData.newPlainText("crash", crash));
                CrashLog.clear(this);
                Toast.makeText(this, "已复制，记录已清除", Toast.LENGTH_SHORT).show();
                ct.setVisibility(View.GONE);
            });
            side.addView(ct);
            side.addView(gap(14));
        }
        final TextView update = text("", 14, 0xFF0A0A0A);
        update.setVisibility(View.GONE);
        update.setGravity(Gravity.CENTER);
        update.setPadding(px(12), px(12), px(12), px(12));
        GradientDrawable ub = new GradientDrawable();
        ub.setCornerRadius(px(12));
        ub.setColor(0xFFFFD60A);
        update.setBackground(ub);
        side.addView(update);
        checkUpdate(update);

        TextView title = text("光影月历", 34, Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        side.addView(title);
        TextView sub = text("壁纸只放图片，日历做成桌面组件，样式随每张图变化", 15, 0xFF8E8E93);
        sub.setPadding(0, px(6), 0, px(22));
        side.addView(sub);

        side.addView(button("1  设为桌面动态壁纸", true, v -> setWallpaper()));
        side.addView(gap(10));
        staticBtn = button("", false, v -> {
            if (prefs.staticMode()) {
                StaticWall.disable(this);
                updateStaticButton();
            } else {
                StaticWall.enable(this);
                Toast.makeText(this, "已开启，正在设置壁纸…", Toast.LENGTH_LONG).show();
                updateStaticButton();
            }
        });
        updateStaticButton();
        side.addView(staticBtn);
        staticStatus = hint("");
        side.addView(staticStatus);
        side.addView(button("电池与自启动设置（防止换壁纸被系统关掉）", false, v -> {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + getPackageName())));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, "请到 设置 → 应用 → 光影月历 里设置", Toast.LENGTH_LONG).show();
            }
        }));
        side.addView(hint("兼容模式靠定时任务换壁纸。请在打开的页面里把电池设为「不限制」、允许自启动，否则系统省电时壁纸会停在旧图上。"));
        side.addView(text("2  添加桌面组件", 15, Color.WHITE));
        side.addView(gap(8));
        Object[][] widgets = {{"月历", CalendarWidget.class}, {"画意", StoryWidget.class}, {"时钟", ClockWidget.class},
                {"本周", WeekWidget.class}, {"一言", QuoteWidget.class}, {"定时器", TimerWidgetProvider.class}};
        for (int r = 0; r < 2; r++) {
            LinearLayout row = new LinearLayout(this);
            for (int c = 0; c < 3; c++) {
                final Object[] wd = widgets[r * 3 + c];
                TextView b = button((String) wd[0], false, v -> pin((Class<?>) wd[1]));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
                if (c > 0) lp.leftMargin = px(8);
                row.addView(b, lp);
            }
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
            rl.bottomMargin = px(8);
            side.addView(row, rl);
        }
        side.addView(hint("月历、时钟、本周、一言、画意的样式都随壁纸变化；拉大组件时卡片跟着变宽变高。点一下组件，壁纸换下一张。定时器：点数字换时长，−5/+5 微调。如果这个桌面不支持一键添加，就长按桌面空白处 → 组件 → 光影月历。"));

        side.addView(text("3  日历组件放在桌面的哪里", 15, Color.WHITE));
        LinearLayout posRow = new LinearLayout(this);
        posRow.setPadding(0, px(10), 0, 0);
        for (int i = 0; i < chips.length; i++) {
            final String pos = Prefs.POSITIONS[i];
            chips[i] = chip(Prefs.POSITION_NAMES[i], v -> {
                prefs.setPosition(pos);
                updateChips();
                Wall.refresh(this);
                renderPreview();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.rightMargin = px(6);
            posRow.addView(chips[i], lp);
        }
        side.addView(posRow);
        side.addView(hint("壁纸会优先挑这个位置画面安静的图片，文字深浅也按这里的明暗决定。"));
        updateChips();

        side.addView(text("4  想看哪些图片", 15, Color.WHITE));
        for (int r = 0; r < 2; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setPadding(0, px(10), 0, 0);
            for (int c = 0; c < 3; c++) {
                final int idx = r * 3 + c;
                themeChips[idx] = chip(Prefs.THEMES[idx][0], v -> toggleTheme(idx));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
                lp.rightMargin = px(6);
                row.addView(themeChips[idx], lp);
            }
            side.addView(row);
        }
        themeHint = hint("");
        side.addView(themeHint);

        side.addView(text("5  换图模式", 15, Color.WHITE));
        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setPadding(0, px(10), 0, 0);
        for (int i = 0; i < modeChips.length; i++) {
            final String m = Prefs.MODES[i];
            modeChips[i] = chip(Prefs.MODE_NAMES[i], v -> {
                prefs.setMode(m);
                updateThemeChips();
                Wall.reselect(this);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.rightMargin = px(6);
            modeRow.addView(modeChips[i], lp);
        }
        side.addView(modeRow);
        modeHint = hint("");
        side.addView(modeHint);
        updateThemeChips();

        final TextView motionBtn = button("", false, null);
        motionBtn.setOnClickListener(v -> {
            prefs.setMotion(!prefs.motion());
            motionBtn.setText(prefs.motion() ? "壁纸缓慢推拉与视差：开" : "壁纸缓慢推拉与视差：关");
        });
        motionBtn.setText(prefs.motion() ? "壁纸缓慢推拉与视差：开" : "壁纸缓慢推拉与视差：关");
        side.addView(motionBtn);
        side.addView(hint("壁纸在 5 分钟里极缓慢地推近，翻桌面页面时跟着平移，让图片和桌面图标之间有远近感；怕费电可以关掉。"));
        side.addView(button("换下一张", false, v -> next()));
        side.addView(gap(10));
        side.addView(button("全屏摆件模式", false, v -> startActivity(new Intent(this, FullActivity.class))));
        side.addView(hint("像 iPad 上那样全屏常亮，不需要桌面组件。轻点换图，左右滑看其他月份，返回键退出。"));
        side.addView(button("设为屏幕保护程序", false, v -> openDreamSettings()));
        side.addView(hint("充电时自动全屏显示月历，碰一下屏幕回到桌面。"));
        side.addView(button("下载全部图片（离线可用）", false, v -> download()));
        status = hint("");
        side.addView(status);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(side);
        if (wide) {
            root.addView(left, new LinearLayout.LayoutParams(0, -1, 1.6f));
            root.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1f));
        } else {
            root.addView(left, new LinearLayout.LayoutParams(-1, px(300)));
            root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        }
        setContentView(root);
        frame.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != pw || b - t != ph) {
                pw = r - l;
                ph = b - t;
                renderPreview();
            }
        });
        updateStatus();
        if (Site.unmetered(this)) download();
    }

    /** 检查有没有新版 App：网站上的 version.json 比当前装的新，就显示一条提示，点一下下载安装包 */
    private void checkUpdate(final TextView banner) {
        new Thread(() -> {
            try {
                byte[] data = Site.download(Site.BASE + "version.json?t=" + System.currentTimeMillis(), 6000);
                if (data == null) return;
                org.json.JSONObject o = new org.json.JSONObject(new String(data, "UTF-8"));
                long remote = o.optLong("versionCode", 0);
                long local = getPackageManager().getPackageInfo(getPackageName(), 0).getLongVersionCode();
                final String apk = Site.BASE + o.optString("apk", "guangying.apk");
                if (remote > local) {
                    runOnUiThread(() -> {
                        if (isDestroyed()) return;
                        banner.setText("有新版本（第 " + remote + " 版，当前第 " + local + " 版）——点这里下载，装好就是最新的");
                        banner.setVisibility(View.VISIBLE);
                        banner.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(apk))));
                    });
                }
            } catch (Exception ignored) {
                // 没网或网站上没有版本文件，就不提示
            }
        }, "guangying-update").start();
    }

    private void next() {
        Wall.advance(this);
    }

    @Override
    public void onPhoto(Photo p) {
        updateStaticButton();
        renderPreview();
    }

    private void renderPreview() {
        if (pw <= 0 || ph <= 0 || rendering) return;
        rendering = true;
        final int w = pw, h = ph;
        new Thread(() -> {
            Bitmap bm = null;
            Photo p = null;
            try {
                bm = Preview.render(getApplicationContext(), w, h);
                p = Wall.current(getApplicationContext());
            } catch (RuntimeException ignored) {
            }
            final Bitmap result = bm;
            final Photo photo = p;
            runOnUiThread(() -> {
                rendering = false;
                if (isDestroyed()) return;
                if (result != null) preview.setImageBitmap(result);
                caption.setText(photo == null ? "第一次使用，联网后会自动下载图片"
                        : photo.title + "  ·  " + photo.sub + "  ·  风格：" + styleName(photo.style));
            });
        }, "guangying-preview").start();
    }

    private static String styleName(String s) {
        switch (s) {
            case "minimal": return "极简";
            case "glass": return "玻璃";
            case "magazine": return "杂志";
            case "band": return "色带";
            case "poster": return "海报";
            case "paper": return "宣纸";
            case "gallery": return "画廊";
            case "vpoem": return "诗笺";
            default: return s;
        }
    }

    private final TextView[] themeChips = new TextView[Prefs.THEMES.length];
    private final TextView[] modeChips = new TextView[Prefs.MODES.length];
    private TextView themeHint, modeHint;

    private void toggleTheme(int idx) {
        java.util.Set<String> kinds = prefs.kinds();
        String[] t = Prefs.THEMES[idx];
        boolean on = kinds.contains(t[1]);
        for (int i = 1; i < t.length; i++) {
            if (on) kinds.remove(t[i]); else kinds.add(t[i]);
        }
        if (kinds.isEmpty()) {
            Toast.makeText(this, "至少留一类", Toast.LENGTH_SHORT).show();
            return;
        }
        prefs.setKinds(kinds);
        updateThemeChips();
        Wall.reselect(this);
    }

    private void updateThemeChips() {
        java.util.Set<String> kinds = prefs.kinds();
        boolean art = "art".equals(prefs.mode());
        for (int i = 0; i < themeChips.length; i++) {
            paintChip(themeChips[i], kinds.contains(Prefs.THEMES[i][1]), art);
        }
        themeHint.setText(art ? "「名画」模式只看绘画，这里的选择暂时不起作用。"
                : "点一下选上或取消，选好马上换一张。离线时本地没有符合的图片，会暂时不按主题挑。");
        String mode = prefs.mode();
        for (int i = 0; i < modeChips.length; i++) {
            boolean on = Prefs.MODES[i].equals(mode);
            paintChip(modeChips[i], on, false);
            if (on) modeHint.setText(Prefs.MODE_HINTS[i]);
        }
    }

    private void paintChip(TextView chip, boolean on, boolean dimmed) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(12));
        bg.setColor(on ? Color.WHITE : 0xFF1C1C1E);
        chip.setBackground(bg);
        chip.setTextColor(on ? Color.BLACK : Color.WHITE);
        chip.setAlpha(dimmed ? 0.4f : 1f);
    }

    private void updateChips() {
        String cur = prefs.position();
        for (int i = 0; i < chips.length; i++) {
            boolean on = Prefs.POSITIONS[i].equals(cur);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(px(12));
            bg.setColor(on ? Color.WHITE : 0xFF1C1C1E);
            chips[i].setBackground(bg);
            chips[i].setTextColor(on ? Color.BLACK : Color.WHITE);
        }
    }

    private void setWallpaper() {
        if (!StaticWall.liveSupported(this)) {
            offerStatic("这台设备的系统没有动态壁纸功能（壁纸设置里只有静态壁纸）。");
            return;
        }
        Intent i = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
        i.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, new ComponentName(this, CalendarWallpaper.class));
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));
            } catch (ActivityNotFoundException e2) {
                offerStatic("系统里找不到动态壁纸的设置入口。");
            }
        }
    }

    /** 没有动态壁纸入口时：改用兼容模式（App 自己定时设置静态壁纸） */
    private void offerStatic(String why) {
        new android.app.AlertDialog.Builder(this)
                .setTitle("改用兼容模式？")
                .setMessage(why + "\n\n兼容模式：App 每隔几分钟自己把图片设成系统壁纸（没有淡入淡出和缓慢推拉），"
                        + "桌面组件、应景选图、画廊装裱都照常。")
                .setPositiveButton("开启兼容模式", (d, w) -> {
                    StaticWall.enable(this);
                    Toast.makeText(this, "已开启，正在设置壁纸…", Toast.LENGTH_LONG).show();
                    updateStaticButton();
                })
                .setNegativeButton("不用", null)
                .show();
    }

    private TextView staticBtn, staticStatus;

    private void updateStaticButton() {
        if (staticBtn == null) return;
        staticBtn.setText(prefs.staticMode() ? "兼容模式（静态壁纸定时换）：开" : "兼容模式（静态壁纸定时换）：关");
        if (staticStatus == null) return;
        String base = "系统壁纸设置里找不到动态壁纸时用：App 每隔几分钟自己把图片设成系统静态壁纸。";
        if (prefs.staticMode() && prefs.staticTriedAt() > 0) {
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("M月d日 HH:mm", java.util.Locale.CHINA);
            String note = prefs.staticNote();
            if (prefs.staticLastOk()) {
                base += "\n上次换壁纸：" + f.format(new java.util.Date(prefs.staticOkAt())) + " 成功" + (note.isEmpty() ? "" : "（" + note + "）");
            } else {
                base += "\n上次换壁纸：" + f.format(new java.util.Date(prefs.staticTriedAt())) + " 失败——" + note + "，两分钟后重试"
                        + (prefs.staticOkAt() > 0 ? "；最近一次成功是 " + f.format(new java.util.Date(prefs.staticOkAt())) : "");
            }
        }
        staticStatus.setText(base);
    }

    private void pin(Class<?> provider) {
        AppWidgetManager m = AppWidgetManager.getInstance(this);
        if (m.isRequestPinAppWidgetSupported()) {
            m.requestPinAppWidget(new ComponentName(this, provider), null, null);
        } else {
            Toast.makeText(this, "这个桌面不支持一键添加：长按桌面空白处 → 组件 → 光影月历", Toast.LENGTH_LONG).show();
        }
    }

    private void openDreamSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_DREAM_SETTINGS));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "这台平板没有屏保设置，可以用「全屏摆件模式」代替", Toast.LENGTH_LONG).show();
        }
    }

    private void download() {
        if (Site.isDownloading()) return;
        status.setText("正在读取图片列表…");
        Site.downloadAll(getApplicationContext(), (done, total, finished) -> runOnUiThread(() -> {
            if (isDestroyed()) return;
            if (total == 0) status.setText("暂时连不上网站，联网后再试");
            else if (!finished) status.setText("正在下载图片 " + done + " / " + total);
            else if (done >= total) status.setText("全部 " + total + " 张图片已存到本地，断网也能完整轮播");
            else status.setText("已存 " + done + " / " + total + " 张，网络中断了，可以稍后再点一次");
            if (finished) renderPreview();
        }));
    }

    private void updateStatus() {
        new Thread(() -> {
            java.util.List<String> list = Site.imageList(getApplicationContext());
            int n = Site.countCached(getApplicationContext(), list);
            runOnUiThread(() -> {
                if (isDestroyed() || Site.isDownloading()) return;
                if (list.isEmpty()) status.setText("第一次使用请先联网");
                else status.setText("已存 " + n + " / " + list.size() + " 张图片到本地（未存的图片联网时照常显示）");
                renderPreview();
            });
        }).start();
    }

    // ------------------------------------------------------------ 界面小工具

    private int px(float v) {
        return Math.round(v * dp);
    }

    private TextView text(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    private View gap(int dpv) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, px(dpv)));
        return v;
    }

    private TextView hint(String s) {
        TextView t = text(s, 13, 0xFF8E8E93);
        t.setPadding(px(4), px(8), px(4), px(20));
        t.setLineSpacing(0, 1.25f);
        return t;
    }

    private TextView chip(String s, View.OnClickListener l) {
        TextView b = text(s, 14, Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setPadding(px(4), px(10), px(4), px(10));
        b.setOnClickListener(l);
        return b;
    }

    private TextView button(String s, boolean primary, View.OnClickListener l) {
        TextView b = text(s, 17, primary ? Color.BLACK : Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setPadding(px(20), px(15), px(20), px(15));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(14));
        bg.setColor(primary ? Color.WHITE : 0xFF1C1C1E);
        b.setBackground(bg);
        b.setOnClickListener(l);
        b.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return b;
    }

    @Override
    protected void onResume() {
        super.onResume();
        StaticWall.ensureFresh(this);
        updateStaticButton();
        Wall.listen(this);
        main.postDelayed(minute, 60000);
        renderPreview();
        Widgets.refreshAll(this);
    }

    @Override
    protected void onPause() {
        Wall.unlisten(this);
        main.removeCallbacks(minute);
        super.onPause();
    }
}
