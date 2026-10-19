package io.github.guangying;

import android.app.Presentation;
import android.app.WallpaperManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Bundle;
import android.os.SystemClock;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.SurfaceHolder;
import android.webkit.WebView;

/**
 * 动态壁纸。
 *
 * 壁纸服务本身没法直接放 WebView，所以建一块只属于本 App 的虚拟屏幕，输出到壁纸的画面上，
 * 再在这块虚拟屏幕上显示月历网页。
 */
public class CalendarWallpaper extends WallpaperService {

    @Override
    public Engine onCreateEngine() {
        return new WallEngine();
    }

    private class WallEngine extends Engine {
        private VirtualDisplay display;
        private Presentation presentation;
        private WebView web;
        private int width, height;
        private long lastTap;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            setOffsetNotificationsEnabled(false);
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            super.onSurfaceChanged(holder, format, w, h);
            int dpi = getResources().getDisplayMetrics().densityDpi;
            if (display == null) {
                start(holder, w, h);
            } else {
                // 横竖屏切换：虚拟屏幕跟着改尺寸，网页会收到 resize 重新排版
                if (w != width || h != height) display.resize(w, h, dpi);
                display.setSurface(holder.getSurface());
            }
            width = w;
            height = h;
        }

        private void start(SurfaceHolder holder, int w, int h) {
            DisplayManager dm = (DisplayManager) getSystemService(DISPLAY_SERVICE);
            int dpi = getResources().getDisplayMetrics().densityDpi;
            try {
                display = dm.createVirtualDisplay("guangying-wallpaper", w, h, dpi, holder.getSurface(), 0);
                presentation = new Presentation(CalendarWallpaper.this, display.getDisplay());
                web = Site.newWebView(presentation.getContext(), this::rebuild);
                presentation.setContentView(web);
                presentation.show();
                // 上面让开状态栏，下面让开导航条和桌面 Dock
                int top = Site.systemDimen(CalendarWallpaper.this, "status_bar_height");
                int bottom = Site.systemDimen(CalendarWallpaper.this, "navigation_bar_height")
                        + Math.round(96 * getResources().getDisplayMetrics().density);
                web.loadUrl(Site.url(CalendarWallpaper.this, "wall", top, bottom));
                if (!isVisible()) pause();
            } catch (RuntimeException e) {
                Log.e("Guangying", "无法创建壁纸画面", e);
                stop();
                drawMessage(holder, "这台设备暂时无法显示动态壁纸，请改用「全屏摆件模式」");
            }
        }

        private void rebuild() {
            stop();
            SurfaceHolder holder = getSurfaceHolder();
            if (holder.getSurface() != null && holder.getSurface().isValid() && width > 0) {
                start(holder, width, height);
            }
        }

        private void stop() {
            if (presentation != null) {
                try {
                    presentation.dismiss();
                } catch (RuntimeException ignored) {
                }
                presentation = null;
            }
            if (web != null) {
                web.destroy();
                web = null;
            }
            if (display != null) {
                display.release();
                display = null;
            }
        }

        private void drawMessage(SurfaceHolder holder, String text) {
            Canvas c = null;
            try {
                c = holder.lockCanvas();
                if (c == null) return;
                c.drawColor(Color.BLACK);
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setColor(0xFF999999);
                p.setTextSize(18 * getResources().getDisplayMetrics().density);
                p.setTextAlign(Paint.Align.CENTER);
                c.drawText(text, c.getWidth() / 2f, c.getHeight() / 2f, p);
            } finally {
                if (c != null) holder.unlockCanvasAndPost(c);
            }
        }

        private void pause() {
            if (web == null) return;
            Site.js(web, "WallApp.pause()");
            web.onPause();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            if (web == null) return;
            // 被应用挡住或熄屏时暂停；回到桌面时继续，离上次换图超过 5 分钟就马上换一张
            if (visible) {
                web.onResume();
                Site.js(web, "WallApp.resume()");
            } else {
                pause();
            }
        }

        @Override
        public Bundle onCommand(String action, int x, int y, int z, Bundle extras, boolean resultRequested) {
            // 在桌面空白处双击：换下一张（取决于桌面 App 是否转发点击）
            if (WallpaperManager.COMMAND_TAP.equals(action)) {
                long now = SystemClock.uptimeMillis();
                if (now - lastTap < 400) {
                    Site.js(web, "WallApp.next()");
                    lastTap = 0;
                } else {
                    lastTap = now;
                }
            }
            return null;
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            stop();
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onDestroy() {
            stop();
            super.onDestroy();
        }
    }
}
