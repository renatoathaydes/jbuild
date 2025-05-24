package jbuild.extension.runner;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class WorkingDirLockTest {

    private final WorkingDirLock lock = WorkingDirLock.TESTER;

    @Test
    void canEnterWorkingDirSafely() throws InterruptedException {
        // enter "foo" dir
        lock.enter("foo");

        var latch1 = createLatchAndEnterDir("bar");

        // cannot enter "bar" dir for now
        assertThat(latch1.await(100L, TimeUnit.MILLISECONDS)).isFalse();

        var latch2 = createLatchAndEnterDir("zort");

        // cannot enter "zort" dir for now
        assertThat(latch2.await(100L, TimeUnit.MILLISECONDS)).isFalse();

        // leave "foo" dir
        var leftFoo = lock.leave("foo");

        // should've entered "bar" immediately
        assertThat(latch1.await(10L, TimeUnit.MILLISECONDS)).isTrue();

        assertThat(leftFoo).isTrue();

        // if we try to leave the wrong directory, we will be notified of it and nothing else happens
        assertThat(lock.leave("zort")).isFalse();

        // cannot enter "zort" dir for now
        assertThat(latch2.await(100L, TimeUnit.MILLISECONDS)).isFalse();

        // leave "bar" dir
        var leftBar = lock.leave("bar");

        // should've entered "zort" immediately
        assertThat(latch2.await(10L, TimeUnit.MILLISECONDS)).isTrue();

        assertThat(leftBar).isFalse();

        assertThat(lock.leave("zort")).isTrue();
        assertThat(lock.leave("zort")).isFalse();
    }

    @Test
    void canOnlyLeaveDirAfterAllCallersLeft() throws InterruptedException {
        // enter "foo" dir
        lock.enter("foo");

        var latch1 = createLatchAndEnterDir("foo");
        var latch2 = createLatchAndEnterDir("foo");
        var latch3 = createLatchAndEnterDir("bar");
        var latch4 = createLatchAndEnterDir("foo");

        // should've entered "foo" as many times as needed
        assertThat(latch1.await(10L, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(latch2.await(10L, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(latch4.await(10L, TimeUnit.MILLISECONDS)).isTrue();

        // but cannot enter "bar" dir for now
        assertThat(latch3.await(100L, TimeUnit.MILLISECONDS)).isFalse();

        // leave "foo" dir
        assertThat(lock.leave("foo")).isTrue();

        // cannot enter "bar" dir yet because there are other actions still running
        assertThat(latch3.await(100L, TimeUnit.MILLISECONDS)).isFalse();

        // leave "foo" dir
        assertThat(lock.leave("foo")).isTrue();

        // cannot enter "bar" dir yet because there are other actions still running
        assertThat(latch3.await(100L, TimeUnit.MILLISECONDS)).isFalse();

        // leave "foo" dir
        assertThat(lock.leave("foo")).isTrue();

        // cannot enter "bar" dir yet because there are other actions still running
        assertThat(latch3.await(100L, TimeUnit.MILLISECONDS)).isFalse();

        // leave "foo" dir
        assertThat(lock.leave("foo")).isTrue();

        // can enter "bar" now as all actions in "foo" have released their locks
        assertThat(latch3.await(100L, TimeUnit.MILLISECONDS)).isTrue();

        assertThat(lock.leave("bar")).isTrue();
        assertThat(lock.leave("foo")).isFalse();
        assertThat(lock.leave("bar")).isFalse();
    }

    private CountDownLatch createLatchAndEnterDir(String dir) {
        var latch = new CountDownLatch(1);
        new Thread(() -> {
            lock.enter(dir);
            latch.countDown();
        }).start();
        return latch;
    }
}
