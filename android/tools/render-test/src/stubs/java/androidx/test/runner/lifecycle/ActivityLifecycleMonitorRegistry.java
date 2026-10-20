package androidx.test.runner.lifecycle;
public final class ActivityLifecycleMonitorRegistry {
    private static ActivityLifecycleMonitor m;
    public static synchronized ActivityLifecycleMonitor getInstance() { return m; }
    public static synchronized void registerInstance(ActivityLifecycleMonitor x) { m = x; }
}
