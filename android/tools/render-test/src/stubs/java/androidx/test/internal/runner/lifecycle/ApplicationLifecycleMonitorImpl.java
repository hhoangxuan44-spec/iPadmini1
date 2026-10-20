package androidx.test.internal.runner.lifecycle;
import androidx.test.runner.lifecycle.*;
import java.util.concurrent.CopyOnWriteArrayList;
public class ApplicationLifecycleMonitorImpl implements ApplicationLifecycleMonitor {
    private final CopyOnWriteArrayList<ApplicationLifecycleCallback> cbs = new CopyOnWriteArrayList<>();
    public void addLifecycleCallback(ApplicationLifecycleCallback c) { cbs.add(c); }
    public void removeLifecycleCallback(ApplicationLifecycleCallback c) { cbs.remove(c); }
    public void signalLifecycleChange(android.app.Application app, ApplicationStage stage) {
        for (ApplicationLifecycleCallback c : cbs) c.onApplicationLifecycleChanged(app, stage);
    }
}
