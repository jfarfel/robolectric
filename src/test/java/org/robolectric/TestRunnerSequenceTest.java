package org.robolectric;

import android.app.Application;
import org.junit.Test;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.InitializationError;
import org.robolectric.annotation.Config;
import org.robolectric.bytecode.Setup;
import org.robolectric.util.Transcript;

import java.io.File;
import java.lang.reflect.Method;

import static org.fest.assertions.api.Assertions.fail;
import static org.junit.Assert.assertTrue;
import static org.robolectric.util.TestUtil.resourceFile;

public class TestRunnerSequenceTest {
    public static class StateHolder {
        public static Transcript transcript;
    }

    @Test public void shouldRunThingsInTheRightOrder() throws Exception {
        StateHolder.transcript = new Transcript();
        assertNoFailures(run(new Runner(SimpleTest.class)));
        StateHolder.transcript.assertEventsSoFar(
                "configureShadows",
//                "resetStaticState", // no longer an overridable hook
//                "setupApplicationState", // no longer an overridable hook
                "createApplication",
                "onCreate",
                "beforeTest",
                "prepareTest",
                "TEST!",
                "onTerminate",
                "afterTest"
        );
    }

    @Test public void whenNoAppManifest_shouldRunThingsInTheRightOrder() throws Exception {
        StateHolder.transcript = new Transcript();
        assertNoFailures(run(new Runner(SimpleTest.class) {
            @Override protected AndroidManifest createAppManifest(File baseDir) {
                return null;
            }
        }));
        StateHolder.transcript.assertEventsSoFar(
                "configureShadows",
                "createApplication",
                "onCreate",
                "beforeTest",
                "prepareTest",
                "TEST!",
                "onTerminate",
                "afterTest"
        );
    }

    @Test public void shouldReleaseAllStateAfterClassSoWeDontLeakMemory() throws Exception {
        RobolectricTestRunner robolectricTestRunner = new Runner(SimpleTest.class);
        robolectricTestRunner.run(new RunNotifier());
        assertTrue(robolectricTestRunner.allStateIsCleared());
    }

    public static class SimpleTest {
        @Test public void shouldDoNothingMuch() throws Exception {
            StateHolder.transcript.add("TEST!");
        }
    }

    private Result run(Runner runner) throws InitializationError {
        RunNotifier notifier = new RunNotifier();
        Result result = new Result();
        notifier.addListener(result.createListener());
        runner.run(notifier);
        return result;
    }

    private void assertNoFailures(Result result) {
        if (!result.wasSuccessful()) {
            for (Failure failure : result.getFailures()) {
                fail(failure.getMessage(), failure.getException());
            }
        }
    }

    public static class Runner extends RobolectricTestRunner {
        public Runner(Class<?> testClass) throws InitializationError {
            super(testClass);
        }

        @Override public Setup createSetup() {
            return new Setup() {
                @Override public boolean shouldAcquire(String name) {
                    if (name.equals(StateHolder.class.getName())) return false;
                    return super.shouldAcquire(name);
                }
            };
        }

        @Override
        protected AndroidManifest createAppManifest(File baseDir) {
            return new AndroidManifest(resourceFile("TestAndroidManifest.xml"), resourceFile("res"), resourceFile("assets"));
        }

        @Override protected Class<? extends TestLifecycle> getTestLifecycleClass() {
            return MyTestLifecycle.class;
        }

        @Override protected void configureShadows(SdkEnvironment sdkEnvironment, Config config) {
            StateHolder.transcript.add("configureShadows");
            super.configureShadows(sdkEnvironment, config);
        }
    }

    public static class MyTestLifecycle extends DefaultTestLifecycle {
        @Override public Application createApplication(Method method, AndroidManifest appManifest) {
            StateHolder.transcript.add("createApplication");
            return new Application() {
                @Override public void onCreate() {
                    StateHolder.transcript.add("onCreate");
                }

                @Override public void onTerminate() {
                    StateHolder.transcript.add("onTerminate");
                }
            };
        }

        @Override public void prepareTest(Object test) {
            StateHolder.transcript.add("prepareTest");
        }

        @Override public void beforeTest(Method method) {
            StateHolder.transcript.add("beforeTest");
        }

        @Override public void afterTest(Method method) {
            StateHolder.transcript.add("afterTest");
        }
    }
}
