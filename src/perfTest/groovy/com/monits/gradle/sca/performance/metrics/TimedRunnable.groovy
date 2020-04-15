/*
 * Copyright 2010-2020 Monits S.A.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
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
