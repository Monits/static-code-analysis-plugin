package com.monits.gradle.sca.performance.metrics

import groovy.transform.CompileStatic
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.GradleVersion

/**
 * A helper to run performance analysis
 */
@CompileStatic
class PerformanceRunner {
    // To give us < 0.3% odds of a falsely identified regression.
    // https://en.wikipedia.org/wiki/Standard_deviation#Rules_for_normally_distributed_data
    private static final BigDecimal NUM_STANDARD_ERRORS_FROM_MEAN = 3.0G
    // We want to ignore regressions of less than 2% over the baseline.
    private static final BigDecimal MINIMUM_REGRESSION_PERCENTAGE = 0.02G

    private static final int WARM_UP_ITERATIONS = 3
    private static final int MEASURE_ITERATIONS = 10

    private static final long SLEEP_AFTER_RUN_MS = 500L
    private static final long SLEEP_AFTER_WARM_UP_MS = 5000L

    private static final int PERCENT = 100

    private final GradleVersion version

    private final AggregateExecutionMetrics results = new AggregateExecutionMetrics()

    PerformanceRunner(final GradleVersion version) {
        this.version = version
    }

    void exercise(final GradleRunner runner) {
        GradleRunner cleanRunner = GradleRunner.create()
            .withGradleVersion(version.version)
            .withProjectDir(runner.projectDir)
            .withArguments('clean')

        // warm up
        for (int i = 0; i < WARM_UP_ITERATIONS; i++) {
            runner.build()
            cleanRunner.build()
            sleep(SLEEP_AFTER_RUN_MS)
        }

        println 'Warm up is done'
        sleep(SLEEP_AFTER_WARM_UP_MS)

        // measure
        for (int i = 0; i < MEASURE_ITERATIONS; i++) {
            results.add(TimedRunnable.run { runner.build() })
            cleanRunner.build()
            sleep(SLEEP_AFTER_RUN_MS)
            println "Iteration ${i+1} / ${MEASURE_ITERATIONS} is done."
        }
    }

    void assertVersionHasNotRegressed(final PerformanceRunner baseline) {
        baseline.assertEveryBuildSucceeds()
        assertEveryBuildSucceeds()

        if (results.totalTime.average - baseline.results.totalTime.average > maxExecutionTimeRegression) {
            throw new AssertionError('New version is slower' as Object)
        }

        // We are on par or faster, check for informational purposes
        if (baseline.results.totalTime.average - results.totalTime.average > baseline.maxExecutionTimeRegression) {
            println "We are actually faster than old plugin uwing ${baseline.version} under ${version} " +
                "by ${baseline.results.totalTime.average / results.totalTime.average * PERCENT - PERCENT}%"
        } else {
            println "We are on par with old plugin using ${baseline.version} under ${version}"
        }
    }

    private void assertEveryBuildSucceeds() {
        assert results.failures.empty : 'Some builds have failed.'
    }

    private BigDecimal getMaxExecutionTimeRegression() {
        BigDecimal allowedPercentageRegression = results.totalTime.average *
            MINIMUM_REGRESSION_PERCENTAGE
        BigDecimal allowedStatisticalRegression = results.totalTime.standardErrorOfMean *
            NUM_STANDARD_ERRORS_FROM_MEAN

        (allowedStatisticalRegression > allowedPercentageRegression) ?
            allowedStatisticalRegression : allowedPercentageRegression
    }
}
