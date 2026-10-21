package io.github.guangying;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

import java.io.File;
import java.util.Calendar;
import java.util.List;

import io.github.guangying.data.Library;
import io.github.guangying.data.Photo;
import io.github.guangying.data.Prefs;
import io.github.guangying.render.WallArt;
import io.github.guangying.widget.Widgets;

/**
 * 动态壁纸：只负责图片——定时换图、交叉淡入淡出。日历和说明在上层的桌面组件里，
 * 样式随当前图片变化（见 widget 包）。
 */
public class CalendarWallpaper extends WallpaperService {

    @Override
    public Engine onCreateEngine() {
        return new WallEngine();
    }

    private class WallEngine extends Engine implements Wall.Listener {
        private static final int FADE_MS = 1500, FRAME_MS = 33;
        private final Handler main = new Handler(Looper.getMainLooper());
        private Prefs prefs;
        private int width, height;
        private boolean visible;
        private Bitmap cur, nxt;
        private long fadeStart;
        private String loadingId = "", curId = "";
        private boolean registered;

        private final Runnable advanceTask = new Runnable() {
            @Override
            public void run() {
                Wall.advance(CalendarWallpaper.this);
            }
        };
        private final Runnable frame = new Runnable() {
            @Override
            public void run() {
                drawFrame();
            }
        };
        private final BroadcastReceiver tick = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                Widgets.refreshAll(CalendarWallpaper.this);
                draw();                                  // 夜间调暗随时间变化
            }
        };

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            setTouchEventsEnabled(false);
            prefs = new Prefs(CalendarWallpaper.this);
            Wall.listen(this);
            if (Site.unmetered(CalendarWallpaper.this) && !Site.isDownloading()) {
                Site.downloadAll(getApplicationContext(), new Site.Progress() {
                    @Override
                    public void update(int done, int total, boolean finished) {
                    }
                });
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            super.onSurfaceChanged(holder, format, w, h);
            if (w != width || h != height) {
                width = w;
                height = h;
                cur = nxt = null;
                curId = loadingId = "";
                showCurrent(false);
            } else {
                draw();
            }
        }

        @Override
        public void onVisibilityChanged(boolean v) {
            super.onVisibilityChanged(v);
            visible = v;
            main.removeCallbacks(advanceTask);
            if (v) {
                if (!registered) {
                    registerReceiver(tick, new IntentFilter(Intent.ACTION_TIME_TICK));
                    registered = true;
                }
                Widgets.refreshAll(CalendarWallpaper.this);
                showCurrent(false);
                scheduleAdvance();
                draw();
                syncLibrary();
            } else if (registered) {
                unregisterReceiver(tick);
                registered = false;
            }
        }

        /** 图库有更新：每隔半天（联网时）刷新一次图库清单；连着 Wi-Fi 时顺便把新图片存下来 */
        private void syncLibrary() {
            final Context app = getApplicationContext();
            long now = System.currentTimeMillis();
            if (now - prefs.lastSync() < 12 * 3600000L || !Site.online(app)) return;
            prefs.setLastSync(now);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Site.imageList(app);                         // 只下载清单，很小
                    if (Site.unmetered(app) && !Site.isDownloading()) {
                        Site.downloadAll(app, new Site.Progress() {
                            @Override
                            public void update(int done, int total, boolean finished) {
                            }
                        });
                    }
                }
            }, "guangying-sync").start();
        }

        /** 过了切换间隔就换一张，否则等到时间再换 */
        private void scheduleAdvance() {
            long interval = prefs.intervalMinutes() * 60000L;
            long left = interval - (System.currentTimeMillis() - prefs.shownAt());
            main.removeCallbacks(advanceTask);
            main.postDelayed(advanceTask, Math.max(left, 500));
        }

        @Override
        public void onPhoto(Photo p) {
            scheduleAdvance();
            if (visible || cur == null) load(p, true);
        }

        /** 显示当前图片（没有就让总控选一张） */
        private void showCurrent(final boolean animate) {
            if (width == 0) return;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final Photo p = Wall.current(CalendarWallpaper.this);
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            if (p == null) Wall.advance(CalendarWallpaper.this);
                            else if (!p.id.equals(curId)) load(p, animate);
                        }
                    });
                }
            }, "guangying-show").start();
        }

        private void load(final Photo p, final boolean animate) {
            if (width == 0 || p.id.equals(loadingId) || p.id.equals(curId)) return;
            loadingId = p.id;
            final int w = width, h = height;
            final String pos = prefs.position();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Context ctx = CalendarWallpaper.this;
                    Bitmap frame = null;
                    if (Site.ensure(ctx, p.large)) {
                        Bitmap full = WallArt.decode(Library.file(ctx, p.large), w, h);
                        Bitmap blur = null;
                        if (p.style.equals("gallery") && !p.blur.isEmpty() && Site.ensure(ctx, p.blur)) {
                            blur = WallArt.decode(Library.file(ctx, p.blur), 64, 64);
                        }
                        if (full != null) frame = WallArt.compose(full, blur, p, pos, w, h);
                    }
                    final Bitmap result = frame;
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            if (!p.id.equals(loadingId) || w != width || h != height) return;
                            loadingId = "";
                            if (result == null) {                 // 下载不了：一分钟后再试，试不成就换别的
                                main.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        Wall.advance(CalendarWallpaper.this);
                                    }
                                }, 60000);
                                return;
                            }
                            curId = p.id;
                            if (cur == null || !animate || !visible) {
                                cur = result;
                                nxt = null;
                                draw();
                            } else {
                                nxt = result;
                                fadeStart = System.currentTimeMillis();
                                drawFrame();
                            }
                        }
                    });
                }
            }, "guangying-load").start();
        }

        private void drawFrame() {
            main.removeCallbacks(frame);
            boolean more = draw();
            if (more) main.postDelayed(frame, FRAME_MS);
        }

        /** 画一帧；返回淡入淡出是否还没结束 */
        private boolean draw() {
            SurfaceHolder holder = getSurfaceHolder();
            Canvas c = null;
            boolean fading = false;
            try {
                c = holder.lockCanvas();
                if (c == null) return false;
                c.drawColor(0xFF000000);
                if (cur != null) c.drawBitmap(cur, 0, 0, null);
                if (nxt != null) {
                    float t = Math.min(1f, (System.currentTimeMillis() - fadeStart) / (float) FADE_MS);
                    float e = t * t * (3 - 2 * t);
                    Paint p = new Paint();
                    p.setAlpha(Math.round(255 * e));
                    c.drawBitmap(nxt, 0, 0, p);
                    fading = t < 1f;
                }
                if (night()) {
                    Paint d = new Paint();
                    d.setColor(0x66000000);
                    c.drawRect(0, 0, c.getWidth(), c.getHeight(), d);
                }
            } finally {
                if (c != null) holder.unlockCanvasAndPost(c);
            }
            if (nxt != null && !fading) {
                cur = nxt;
                nxt = null;
            }
            return fading;
        }

        private boolean night() {
            if (!prefs.nightDim()) return false;
            int hr = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            return hr >= 23 || hr < 7;
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            main.removeCallbacksAndMessages(null);
            cur = nxt = null;
            curId = loadingId = "";
            width = height = 0;
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onDestroy() {
            main.removeCallbacksAndMessages(null);
            Wall.unlisten(this);
            if (registered) {
                unregisterReceiver(tick);
                registered = false;
            }
            super.onDestroy();
        }
    }
}
