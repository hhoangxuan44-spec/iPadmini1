package io.github.guangying;

import android.app.Application;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CrashLog.install(this);
        // 进程每次被唤起（打开 App、组件刷新、闹钟、开机）都检查一次：兼容模式下该换的壁纸没换就补上
        try {
            StaticWall.ensureFresh(this);
        } catch (RuntimeException ignored) {
        }
    }
}
