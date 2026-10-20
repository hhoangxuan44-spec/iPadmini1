package androidx.test.platform.app;
import android.app.Instrumentation; import android.os.Bundle;
/** 最小桩：androidx.test:monitor 只发布在 Google Maven(本环境不可达)。 */
public final class InstrumentationRegistry {
    private InstrumentationRegistry() {}
    private static Instrumentation inst; private static Bundle args;
    public static void registerInstance(Instrumentation i, Bundle a) { inst = i; args = a; }
    public static Instrumentation getInstrumentation() { return inst; }
    public static Bundle getArguments() { return args; }
}
