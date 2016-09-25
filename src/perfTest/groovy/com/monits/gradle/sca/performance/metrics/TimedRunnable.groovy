package com.monits.gradle.sca.performance.metrics

import groovy.transform.CompileStatic
import org.joda.time.DateTime
import org.joda.time.Duration

import java.util.concurrent.TimeUnit

/**
 * A helper for runnables that need to be timed.
 */
@CompileStatic
final class TimedRunnable {

    private TimedRunnable() {
        throw new AssertionError('Cannot instantiate this class' as Object)
    }

    @SuppressWarnings('CatchException')
    static ExecutionMetrics run(final Runnable runnable) {
        ExecutionMetrics result = new ExecutionMetrics()
        result.start = DateTime.now()
        long startNanos = System.nanoTime()
        try {
            runnable.run()
        } catch (Exception e) {
            result.exception = e
        }
        result.end = DateTime.now()
        long endNanos = System.nanoTime()
        result.totalTime = Duration.millis(TimeUnit.NANOSECONDS.toMillis(endNanos - startNanos))
        result
    }
}
