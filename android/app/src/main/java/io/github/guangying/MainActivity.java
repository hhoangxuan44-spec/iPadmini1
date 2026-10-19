package io.github.guangying;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowInsets;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** 主界面：一个实时预览，加上设为壁纸、全屏摆件、屏保、下载图片几个按钮。 */
public class MainActivity extends Activity {
    private WebView preview;
    private TextView status, downloadBtn;
    private float dp;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        dp = getResources().getDisplayMetrics().density;

        boolean wide = getResources().getConfiguration().screenWidthDp > getResources().getConfiguration().screenHeightDp;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        int pad = px(32);
        root.setPadding(pad, pad, pad, pad);
        // 内容铺满全屏（Android 15 起的默认做法），四周留出状态栏和导航条的位置
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(pad + i.left, pad + i.top, pad + i.right, pad + i.bottom);
            return WindowInsets.CONSUMED;
        });

        // 实时预览（和壁纸是同一个网页）
        FrameLayout frame = new FrameLayout(this);
        frame.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), px(18));
            }
        });
        frame.setClipToOutline(true);
        preview = Site.newWebView(this, this::recreate);
        frame.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        preview.loadUrl(Site.url(this, "full", 0, 0));

        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setPadding(wide ? px(36) : 0, wide ? 0 : px(28), 0, 0);

        TextView title = text("光影月历", 34, Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        side.addView(title);
        TextView sub = text("名画与风景轮播，日历随每张图变换颜色与样式", 15, 0xFF8E8E93);
        sub.setPadding(0, px(6), 0, px(26));
        side.addView(sub);

        side.addView(button("设为桌面动态壁纸", true, v -> setWallpaper()));
        side.addView(hint("设好后桌面背景就是月历。日历会避开顶部状态栏和底部 Dock，桌面第一页少放些图标效果最好。"));
        side.addView(button("全屏摆件模式", false, v -> startActivity(new Intent(this, FullActivity.class))));
        side.addView(hint("像 iPad 上那样全屏常亮。轻点换下一张，左右滑看其他月份，返回键退出。"));
        side.addView(button("设为屏幕保护程序", false, v -> openDreamSettings()));
        side.addView(hint("在系统屏保设置里选「光影月历」，充电时自动显示，碰一下屏幕就回到桌面。"));
        downloadBtn = button("下载全部图片（离线可用）", false, v -> download());
        side.addView(downloadBtn);
        status = hint("");
        side.addView(status);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(side);
        if (wide) {
            root.addView(frame, new LinearLayout.LayoutParams(0, -1, 1.55f));
            root.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1f));
        } else {
            int w = getResources().getDisplayMetrics().widthPixels - 2 * pad;
            root.addView(frame, new LinearLayout.LayoutParams(-1, Math.round(w * 0.625f)));
            root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        }
        setContentView(root);
        updateStatus();

        // 连着 Wi-Fi 时自动在后台把图片存到本地
        if (Site.unmetered(this)) download();
    }

    private void setWallpaper() {
        Intent i = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
        i.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, new ComponentName(this, CalendarWallpaper.class));
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));
            } catch (ActivityNotFoundException e2) {
                Toast.makeText(this, "请到 设置 → 壁纸 里选择「光影月历」", Toast.LENGTH_LONG).show();
            }
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

    private TextView hint(String s) {
        TextView t = text(s, 13, 0xFF8E8E93);
        t.setPadding(px(4), px(8), px(4), px(20));
        t.setLineSpacing(0, 1.25f);
        return t;
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
        preview.onResume();
        Site.js(preview, "WallApp.resume()");
    }

    @Override
    protected void onPause() {
        Site.js(preview, "WallApp.pause()");
        preview.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        preview.destroy();
        super.onDestroy();
    }
}
