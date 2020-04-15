/*
 * Copyright 2010-2017 Monits S.A.
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
package com.monits.gradle.sca.performance.fixture

import com.monits.gradle.sca.performance.categories.AndroidScenario
import com.monits.gradle.sca.performance.categories.JavaScenario
import com.monits.gradle.sca.performance.metrics.PerformanceRunner
import groovy.transform.CompileDynamic
import org.gradle.util.GradleVersion
import org.junit.experimental.categories.Category
import org.junit.Assume
import spock.lang.Shared
import spock.lang.Unroll

/**
 * Base specification to test a single analysis report.
*/
@CompileDynamic
abstract class AbstractPluginPerfTestFixture extends AbstractPerfTestFixture {

    @Shared
    private PerformanceRunner prevVersionBaselineJavaRunner

    @Shared
    private PerformanceRunner prevVersionBaselineAndroidRunner

    @Category(JavaScenario)
    @SuppressWarnings('MethodName')
    @Unroll('Java analysis performance with gradle #version')
    void 'analysis for java project'() {
        given:
        writeBuildFile(BASELINE_PLUGIN_VERSION)
        goodCode()

        // Generate baseline
        PerformanceRunner baselineRunner = new PerformanceRunner(GradleVersion.version(version))
        boolean ret = baselineRunner.exercise(gradleRunner().withGradleVersion(version))

        // Check if the old plugin was incompatible with the current setup
        Assume.assumeTrue(ret)

        // reset build file to use new development version
        writeBuildFile()

        when:
        PerformanceRunner perfRunner = new PerformanceRunner(GradleVersion.version(version))
        perfRunner.exercise(gradleRunner().withGradleVersion(version))

        then:
        perfRunner.assertVersionHasNotRegressed(baselineRunner)
        // If possible, check we have not regressed against the previous Gradle version either
        if (prevVersionBaselineJavaRunner != null) {
            perfRunner.assertVersionHasNotRegressed(prevVersionBaselineJavaRunner)
        }

        cleanup:
        // Keep baseline for next iteration
        prevVersionBaselineJavaRunner = baselineRunner

        where:
        version << TESTED_GRADLE_VERSIONS
    }

    @Category(AndroidScenario)
    @SuppressWarnings('MethodName')
    @Unroll('Android analysis performance with gradle #version')
    void 'analysis for android project'() {
        given:
        setupMultimoduleAndroidProject(androidVersionForGradle(version), BASELINE_PLUGIN_VERSION)

        // Generate baseline
        PerformanceRunner baselineRunner = new PerformanceRunner(GradleVersion.version(version))
        boolean ret = baselineRunner.exercise(gradleRunner().withGradleVersion(version))

        // Check if the old plugin was incompatible with the current setup
        Assume.assumeTrue(ret)

        // reset build file to use new development version
        setupMultimoduleAndroidProject(androidVersionForGradle(version))

        when:
        PerformanceRunner perfRunner = new PerformanceRunner(GradleVersion.version(version))
        perfRunner.exercise(gradleRunner().withGradleVersion(version))

        then:
        perfRunner.assertVersionHasNotRegressed(baselineRunner)
        // If possible, check we have not regressed against the previous Gradle version either
        if (prevVersionBaselineAndroidRunner != null) {
            perfRunner.assertVersionHasNotRegressed(prevVersionBaselineAndroidRunner)
        }

        cleanup:
        // Keep baseline for next iteration
        prevVersionBaselineAndroidRunner = baselineRunner

        where:
        version << TESTED_GRADLE_VERSIONS_FOR_ANDROID
    }
}
