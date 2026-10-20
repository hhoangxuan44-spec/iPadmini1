package io.github.guangying.widget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import io.github.guangying.Wall;

/** 点桌面组件：换下一张壁纸。 */
public class ControlReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (ThemedWidget.ACTION_NEXT.equals(intent.getAction())) Wall.advance(ctx);
    }
}
