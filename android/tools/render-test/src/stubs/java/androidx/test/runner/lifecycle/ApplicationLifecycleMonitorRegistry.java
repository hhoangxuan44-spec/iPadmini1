package androidx.test.runner.lifecycle;
public final class ApplicationLifecycleMonitorRegistry {
    private static ApplicationLifecycleMonitor m;
    public static synchronized ApplicationLifecycleMonitor getInstance() { return m; }
    public static synchronized void registerInstance(ApplicationLifecycleMonitor x) { m = x; }
}
