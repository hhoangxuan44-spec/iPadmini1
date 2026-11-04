package io.github.guangying;

import android.content.ComponentName;

import java.lang.reflect.Method;

/**
 * 用 adb 的 shell 身份把「光影」设成系统的动态壁纸。
 *
 * 有些厂商的系统把动态壁纸的选择界面删掉了，App 自己没法设置；但系统底层仍支持，
 * 而 adb shell 持有 SET_WALLPAPER_COMPONENT 权限，可以直接调用系统的壁纸服务。
 * 运行（平板连上电脑并开启 USB 调试）：
 *
 *   adb shell 'CLASSPATH=$(pm path io.github.guangying | cut -d: -f2) app_process /system/bin io.github.guangying.ShellSetWallpaper'
 *
 * 可选参数：组件名（默认 io.github.guangying/.CalendarWallpaper）。设好后一直有效，恢复出厂设置才需要重来。
 */
public final class ShellSetWallpaper {
    private ShellSetWallpaper() {}

    public static void main(String[] args) {
        String comp = args.length > 0 ? args[0] : "io.github.guangying/.CalendarWallpaper";
        ComponentName cn = ComponentName.unflattenFromString(comp);
        if (cn == null) {
            System.out.println("组件名不对：" + comp);
            System.exit(2);
            return;
        }
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            Object binder = sm.getMethod("getService", String.class).invoke(null, "wallpaper");
            Class<?> stub = Class.forName("android.app.IWallpaperManager$Stub");
            Object svc = stub.getMethod("asInterface", Class.forName("android.os.IBinder")).invoke(null, binder);
            if (svc == null) {
                System.out.println("找不到系统的壁纸服务");
                System.exit(3);
                return;
            }
            // 不同系统版本的方法签名不一样，按名字和参数个数找，参数依次是：组件、调用者包名、壁纸类型(1=主屏)、用户 id
            Method target = null;
            for (Method m : svc.getClass().getMethods()) {
                if (m.getName().equals("setWallpaperComponentChecked")) target = m;
            }
            if (target != null) {
                Object[] a = new Object[target.getParameterTypes().length];
                int ints = 0;
                for (int i = 0; i < a.length; i++) {
                    Class<?> t = target.getParameterTypes()[i];
                    if (t == ComponentName.class) a[i] = cn;
                    else if (t == String.class) a[i] = "com.android.shell";
                    else if (t == int.class) a[i] = ints++ == 0 && a.length == 4 ? 1 : 0;   // 有 4 个参数时第一个 int 是壁纸类型
                    else a[i] = null;
                }
                Object r = target.invoke(svc, a);
                System.out.println("setWallpaperComponentChecked -> " + r);
            } else {
                Method old = svc.getClass().getMethod("setWallpaperComponent", ComponentName.class);
                old.invoke(svc, cn);
                System.out.println("setWallpaperComponent 已调用");
            }
            System.out.println("已设置：" + comp + "。回到桌面看看。");
        } catch (Throwable t) {
            Throwable c = t.getCause() != null ? t.getCause() : t;
            System.out.println("设置失败：" + c);
            System.exit(1);
        }
    }
}
