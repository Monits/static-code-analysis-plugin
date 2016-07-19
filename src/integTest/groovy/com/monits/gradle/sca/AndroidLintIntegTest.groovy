/*
 * Copyright 2010-2016 Monits S.A.
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
package com.monits.gradle.sca

import com.monits.gradle.sca.fixture.AbstractIntegTestFixture
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.GradleVersion
import org.gradle.util.VersionNumber
import spock.lang.Unroll
import spock.util.environment.Jvm

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE
/**
 * Integration test of Android Lint tasks.
 */
class AndroidLintIntegTest extends AbstractIntegTestFixture {
    @SuppressWarnings('MethodName')
    @Unroll('AndroidLint should run when using gradle #version')
    void 'androidLint is run'() {
        given:
        writeAndroidBuildFile()
        writeAndroidManifest()
        goodCode()

        when:
        BuildResult result = gradleRunner()
            .withGradleVersion(version)
            .build()

        then:
        if (GradleVersion.version(version) >= GradleVersion.version('2.5')) {
            // Executed task capture is only available in Gradle 2.5+
            result.task(taskName()).outcome == SUCCESS
            result.task(':resolveAndroidLint').outcome == SUCCESS
            result.task(':cleanupAndroidLint').outcome == SUCCESS
        }

        // Make sure report exists and was using the expected tool version
        reportFile().exists()

        where:
        version << ['2.3', '2.4', '2.7', '2.10', GradleVersion.current().version]
    }

    @SuppressWarnings('MethodName')
    @Unroll('AndroidLint re-run is up-to-date when using plugin version #androidVersion')
    void 'rerun is up-to-date'() {
        given:
        writeAndroidBuildFile(androidVersion)
        writeAndroidManifest()
        goodCode()

        when:
        GradleRunner gradleRunner = gradleRunner()
                .withGradleVersion(gradleVersion)
                // plugin version 1.1.x failed to compile tests if assemble was not called beforehand
                .withArguments('assemble', 'check', '--stacktrace')
        BuildResult firstRun = gradleRunner.build()
        BuildResult secondRun = gradleRunner.build()

        then:
        firstRun.task(taskName()).outcome == SUCCESS
        secondRun.task(taskName()).outcome == UP_TO_DATE

        // Make sure the report exist
        reportFile(VersionNumber.parse(androidVersion) >= VersionNumber.parse('2.0.0') ? 'debug' : null).exists()

        where:
        androidVersion << ['1.1.3', '1.2.3', '1.3.1', '1.5.0', '2.0.0', '2.1.2'] +
            (Jvm.current.java8Compatible ? ['2.2.0-alpha5'] : [])
        gradleVersion = VersionNumber.parse(androidVersion) < VersionNumber.parse('1.5.0') ?
            '2.9' : GradleVersion.current().version
    }

    @Override
    String reportFileName(final String buildType) {
        // Sourceset names are only taken into account when using Android plugin 2.+
        "build/outputs/lint-results${buildType ? '-' + buildType : ''}.xml"
    }

    @Override
    String taskName() {
        ':lint'
    }

    @Override
    String toolName() {
        'lint'
    }
}
