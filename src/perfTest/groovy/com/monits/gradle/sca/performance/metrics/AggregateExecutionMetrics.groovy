package com.monits.gradle.sca.performance.metrics

import groovy.transform.CompileStatic

/**
 * An aggregate over run metrics that allows to easily extract series of data.
 */
@CompileStatic
class AggregateExecutionMetrics {
    private final List<ExecutionMetrics> metrics = []

    void add(ExecutionMetrics em) {
        metrics.add(em)
    }

    DataSeries getTotalTime() {
        new DataSeries(metrics.collect { ExecutionMetrics it -> BigDecimal.valueOf(it.totalTime.millis) })
    }

    List<Throwable> getFailures() {
        metrics*.exception.findAll()
    }
}
