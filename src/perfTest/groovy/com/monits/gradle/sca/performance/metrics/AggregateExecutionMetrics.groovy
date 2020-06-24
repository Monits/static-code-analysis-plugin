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

    @Override
    String toString() {
        return "AggregateExecutionMetrics{ raw: " +
            "${totalTime}, dataseries: ${totalTime.toString()} }"
    }
}
