package io.github.guangying;

import android.app.Application;
import android.webkit.WebView;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // 壁纸在单独的进程里运行，每个进程的 WebView 需要各自的数据目录
        String name = Application.getProcessName();
        if (name != null && name.endsWith(":wall")) WebView.setDataDirectorySuffix("wall");
    }
}
