package androidx.test.runner.intent;
public final class IntentMonitorRegistry {
    private static IntentMonitor m;
    public static synchronized IntentMonitor getInstance() { return m; }
    public static synchronized void registerInstance(IntentMonitor x) { m = x; }
}
