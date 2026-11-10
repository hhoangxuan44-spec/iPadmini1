package io.github.guangying;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.Calendar;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import io.github.guangying.data.Library;
import io.github.guangying.data.Photo;
import io.github.guangying.data.Prefs;
import io.github.guangying.widget.Widgets;

/**
 * 轮播的总控：决定当前是哪一张图，通知壁纸换图、通知桌面组件换样式。
 * 壁纸、组件、主界面在同一个进程里，直接调用即可。
 */
public final class Wall {
    private Wall() {}

    public interface Listener {
        /** 当前图片变了（主线程） */
        void onPhoto(Photo p);
    }

    private static final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final Random rnd = new Random();
    /** 提前选好并下载好的下一张：到时间换图不用再等下载 */
    private static volatile Photo prefetched;

    public static void listen(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    public static void unlisten(Listener l) {
        listeners.remove(l);
    }

    /** 当前图片；还没有选过就现选一张。图库还没下载时返回 null。 */
    public static Photo current(Context ctx) {
        List<Photo> all = Library.load(ctx);
        if (all.isEmpty()) return null;
        Prefs pr = new Prefs(ctx);
        Photo p = Library.find(all, pr.currentId());
        return p != null ? p : pick(ctx, all, pr, true);
    }

    private static Photo pick(Context ctx, List<Photo> all, Prefs pr, boolean save) {
        boolean offline = !Site.online(ctx);
        Photo p = Library.pick(ctx, all, pr.recent(), Calendar.getInstance(), pr.position(), offline, rnd);
        if (p == null) p = Library.pick(ctx, all, pr.recent(), Calendar.getInstance(), pr.position(), false, rnd);
        if (p != null && save) pr.setCurrent(p.id, System.currentTimeMillis());
        return p;
    }

    /**
     * 选出下一张但不保存（兼容模式用：壁纸设成功之后才 commit）。
     * 优先用预取好的；其次只在已经存到本地的图片里挑；本地一张都没有才挑需要下载的。
     */
    static Photo chooseNext(Context app) {
        List<Photo> all = Library.load(app);
        if (all.isEmpty()) {
            Site.imageList(app);
            all = Library.load(app);
        }
        if (all.isEmpty()) return null;
        Prefs pr = new Prefs(app);
        Photo pre = prefetched;
        prefetched = null;
        if (pre != null && Library.find(all, pre.id) != null && new java.io.File(Site.root(app), pre.large).isFile()) return pre;
        Photo p = Library.pick(app, all, pr.recent(), Calendar.getInstance(), pr.position(), true, rnd);
        if (p == null) p = Library.pick(app, all, pr.recent(), Calendar.getInstance(), pr.position(), false, rnd);
        return p;
    }

    /** 把 p 记为当前图片，并通知壁纸、主界面和桌面组件 */
    static void commit(final Context app, final Photo p) {
        new Prefs(app).setCurrent(p.id, System.currentTimeMillis());
        main.post(new Runnable() {
            @Override
            public void run() {
                for (Listener l : listeners) l.onPhoto(p);
                Widgets.refreshAll(app);
            }
        });
    }

    static void prefetchNext(Context app) {
        List<Photo> all = Library.load(app);
        if (!all.isEmpty()) prefetch(app, all, new Prefs(app));
    }

    /** 换下一张（可在任何线程调用） */
    public static void advance(final Context ctx) {
        final Context app = ctx.getApplicationContext();
        if (new Prefs(app).staticMode()) {              // 兼容模式：先设壁纸，成功后才换当前图片
            StaticWall.changeAsync(app);
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<Photo> all = Library.load(app);
                if (all.isEmpty()) {
                    Site.imageList(app);                 // 第一次使用：先取图库清单
                    all = Library.load(app);
                }
                if (all.isEmpty()) return;
                Prefs pr = new Prefs(app);
                Photo pre = prefetched;
                prefetched = null;
                final Photo p;
                if (pre != null && Library.find(all, pre.id) != null && new java.io.File(Site.root(app), pre.large).isFile()) {
                    p = pre;
                    pr.setCurrent(p.id, System.currentTimeMillis());
                } else {
                    p = pick(app, all, pr, true);
                }
                if (p == null) return;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        for (Listener l : listeners) l.onPhoto(p);
                        Widgets.refreshAll(app);
                    }
                });
                prefetch(app, all, pr);
            }
        }, "guangying-next").start();
    }

    /** 趁现在有网，把下一张图（和玻璃、画廊用的模糊小图）先下好 */
    private static void prefetch(Context app, List<Photo> all, Prefs pr) {
        try {
            if (!Site.online(app)) return;
            Photo n = Library.pick(app, all, pr.recent(), Calendar.getInstance(), pr.position(), false, rnd);
            if (n == null) return;
            if (Site.fetchWithRetry(app, n.large, 3) && !n.blur.isEmpty()) Site.fetchWithRetry(app, n.blur, 2);
            prefetched = n;
        } catch (RuntimeException ignored) {
            // 预取失败不影响正常换图
        }
    }

    /** 主题或模式改了：丢掉按旧设置预取的下一张，马上按新设置换一张 */
    public static void reselect(Context ctx) {
        prefetched = null;
        advance(ctx);
    }

    /** 还没有当前图片（刚安装）时选一张并刷新组件 */
    public static void advanceIfEmpty(Context ctx) {
        if (new Prefs(ctx).currentId().isEmpty()) advance(ctx);
    }

    /** 重新通知当前图片（组件位置改了、刚启动等） */
    public static void refresh(Context ctx) {
        Widgets.refreshAll(ctx.getApplicationContext());
    }
}
