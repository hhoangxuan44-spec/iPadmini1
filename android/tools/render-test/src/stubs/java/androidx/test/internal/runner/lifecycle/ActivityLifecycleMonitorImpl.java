package androidx.test.internal.runner.lifecycle;
import android.app.Activity;
import androidx.test.runner.lifecycle.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
public class ActivityLifecycleMonitorImpl implements ActivityLifecycleMonitor {
    private final CopyOnWriteArrayList<ActivityLifecycleCallback> cbs = new CopyOnWriteArrayList<>();
    private final Map<Activity, Stage> stages = new WeakHashMap<>();
    public void addLifecycleCallback(ActivityLifecycleCallback c) { cbs.add(c); }
    public void removeLifecycleCallback(ActivityLifecycleCallback c) { cbs.remove(c); }
    public synchronized Collection<Activity> getActivitiesInStage(Stage s) {
        List<Activity> r = new ArrayList<>();
        for (Map.Entry<Activity, Stage> e : stages.entrySet()) if (e.getValue() == s) r.add(e.getKey());
        return r;
    }
    public synchronized Stage getLifecycleStageOf(Activity a) { return stages.get(a); }
    public void signalLifecycleChange(Stage stage, Activity a) {
        synchronized (this) { stages.put(a, stage); }
        for (ActivityLifecycleCallback c : cbs) c.onActivityLifecycleChanged(a, stage);
    }
}
