package io.github.guangying;

import android.service.dreams.DreamService;
import android.webkit.WebView;

/** 屏幕保护程序：充电或放在支架上时全屏显示月历，碰一下屏幕就退出。 */
public class CalendarDream extends DreamService {
    private WebView web;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);
        show();
    }

    private void show() {
        if (web != null) web.destroy();
        web = Site.newWebView(this, this::show);
        setContentView(web);
        web.loadUrl(Site.url(this, "dream", 0, 0));
    }

    @Override
    public void onDetachedFromWindow() {
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDetachedFromWindow();
    }
}
