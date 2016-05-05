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
import org.gradle.util.GradleVersion
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

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

    @Override
    String reportFileName(final String sourceSet) {
        // Sourceset names are only taken into account when using Android plugin 2.+
        'build/outputs/lint-results.xml'
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
