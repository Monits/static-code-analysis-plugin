package com.monits.gradle.sca.performance.metrics

import groovy.transform.CompileStatic
import org.joda.time.DateTime
import org.joda.time.Duration

/**
 * Value object for individual run statistics
 */
@CompileStatic
class ExecutionMetrics {
    DateTime start
    DateTime end
    Duration totalTime
    Throwable exception
}
