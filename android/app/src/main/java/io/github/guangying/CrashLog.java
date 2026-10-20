package io.github.guangying;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 出错记录：闪退或组件绘制出错时把原因存下来，下次打开 App 能看到、能复制发给我。 */
public final class CrashLog {
    private CrashLog() {}

    private static File file(Context c) {
        return new File(c.getApplicationContext().getFilesDir(), "crash.txt");
    }

    public static void install(final Context app) {
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                note(app, "闪退 · 线程 " + t.getName(), e);
                if (prev != null) prev.uncaughtException(t, e);
            }
        });
    }

    /** 记录一次出错（最多保留最近约 12KB） */
    public static synchronized void note(Context c, String where, Throwable t) {
        try {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            String stamp = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(new Date());
            String old = read(c);
            String text = stamp + "  " + where + "\n" + sw + "\n" + old;
            if (text.length() > 12000) text = text.substring(0, 12000);
            try (FileOutputStream out = new FileOutputStream(file(c))) {
                out.write(text.getBytes("UTF-8"));
            }
        } catch (Throwable ignored) {
            // 记录失败就算了，不能因为记日志再出错
        }
    }

    public static synchronized String read(Context c) {
        File f = file(c);
        if (!f.isFile()) return "";
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toString("UTF-8");
        } catch (Throwable e) {
            return "";
        }
    }

    public static synchronized void clear(Context c) {
        file(c).delete();
    }
}
