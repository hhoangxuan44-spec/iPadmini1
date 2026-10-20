package androidx.test.runner.lifecycle;
public interface ApplicationLifecycleMonitor {
    void addLifecycleCallback(ApplicationLifecycleCallback c);
    void removeLifecycleCallback(ApplicationLifecycleCallback c);
}
