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
package com.monits.gradle.sca

import com.monits.gradle.sca.fixture.AbstractIntegTestFixture
import com.monits.gradle.sca.io.TestFile
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.GradleVersion
import org.gradle.util.VersionNumber
import spock.lang.Unroll
import spock.util.environment.Jvm

import static org.gradle.testkit.runner.TaskOutcome.FAILED
import static org.gradle.testkit.runner.TaskOutcome.SKIPPED
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE
import static org.hamcrest.CoreMatchers.containsString
import static org.junit.Assert.assertThat

/**
 * Integration test of Android Lint tasks.
 */
class AndroidLintIntegTest extends AbstractIntegTestFixture {

    static final List<String> ANDROID_PLUGIN_VERSIONS = (['1.1.3', '1.2.3', '1.3.1', '1.5.0', '2.0.0', '2.1.3'] +
        (Jvm.current.java8Compatible ? ['2.2.3', '2.3.3', '3.0.0-beta4'] : [])).asImmutable()

    @SuppressWarnings('MethodName')
    @Unroll('AndroidLint should run when using gradle #version')
    void 'androidLint is run'() {
        given:
        writeAndroidBuildFile(androidVersion)
        useSimpleAndroidLintConfig()
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
        version << ['2.3', '2.4', '2.7', '2.10', '2.14.1'] +
            (Jvm.current.java8Compatible ? ['3.0', '3.1'] : [])
        androidVersion = GradleVersion.version(version) < GradleVersion.version('3.0') ?
            DEFAULT_ANDROID_VERSION : '2.2.0'
    }

    @SuppressWarnings('MethodName')
    @Unroll('AndroidLint should be resolved before linting for plugin version #androidVersion')
    void 'androidLint is resolved'() {
        given:
        writeAndroidBuildFile(androidVersion)
        useSimpleAndroidLintConfig()
        writeAndroidManifest()
        goodCode()

        when:
        BuildResult result = gradleRunner()
                .withGradleVersion(gradleVersion)
                // plugin version 1.1.x failed to compile tests if assemble was not called beforehand
                .withArguments('assemble', 'check', '--stacktrace')
                .build()

        then:
        result.task(taskName()).outcome == SUCCESS
        result.task(':resolveAndroidLint').outcome == SUCCESS
        result.task(':cleanupAndroidLint').outcome == SUCCESS

        where:
        androidVersion << ANDROID_PLUGIN_VERSIONS
        gradleVersion = gradleVersionForAndroid(androidVersion)
    }

    @SuppressWarnings('MethodName')
    @Unroll('AndroidLint re-run is up-to-date when using plugin version #androidVersion')
    void 'rerun is up-to-date'() {
        given:
        writeAndroidBuildFile(androidVersion)
        useSimpleAndroidLintConfig()
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
        androidVersion << ANDROID_PLUGIN_VERSIONS
        gradleVersion = gradleVersionForAndroid(androidVersion)
    }

    @SuppressWarnings('MethodName')
    @Unroll('AndroidLint is skipped when disabled and using plugin version #androidVersion')
    void 'task is skipped if disabled'() {
        given:
        writeAndroidBuildFile([(ANDROID_VERSION):androidVersion])
        writeAndroidManifest()
        goodCode()

        when:
        BuildResult result = gradleRunner()
            .withGradleVersion(gradleVersion)
            // plugin version 1.1.x failed to compile tests if assemble was not called beforehand
            .withArguments('assemble', 'check', '--stacktrace')
            .build()

        then:
        // no task should be configured
        result.task(taskName()).outcome == SKIPPED
        result.task(':resolveAndroidLint').outcome == SKIPPED
        result.task(':cleanupAndroidLint').outcome == SKIPPED

        // Make sure the report doesn't exist
        !reportFile().exists()

        where:
        androidVersion << ANDROID_PLUGIN_VERSIONS
        gradleVersion = gradleVersionForAndroid(androidVersion)
    }

    @SuppressWarnings('MethodName')
    void 'Android downloads remote config'() {
        given:
        writeAndroidBuildFile(DEFAULT_ANDROID_VERSION)
        writeAndroidManifest()
        goodCode()

        when:
        BuildResult result = gradleRunner()
            .withGradleVersion(gradleVersionForAndroid(DEFAULT_ANDROID_VERSION))
            .build()

        then:
        result.task(taskName()).outcome == SUCCESS

        String projectName = testProjectDir.root.name

        // The config must exist
        file("config/android/android-lint-${projectName}.xml").exists()

        // Make sure android report exists
        reportFile().exists()
    }

    @SuppressWarnings('MethodName')
    void 'running offline fails download'() {
        given:
        writeAndroidBuildFile(DEFAULT_ANDROID_VERSION)
        writeAndroidManifest()
        goodCode()

        when:
        BuildResult result = gradleRunner()
            .withGradleVersion(gradleVersionForAndroid(DEFAULT_ANDROID_VERSION))
            .withArguments('check', '--stacktrace', '--offline')
            .buildAndFail()

        then:
        result.task(':downloadAndroidLintConfig').outcome == FAILED
        assertThat(result.output, containsString('Running in offline mode, but there is no cached version'))
    }

    @SuppressWarnings('MethodName')
    void 'running offline with a cached file passes but warns'() {
        given:
        writeAndroidBuildFile(DEFAULT_ANDROID_VERSION)
        writeAndroidManifest()

        String projectName = testProjectDir.root.name
        writeSimpleAndroidLintConfig(projectName)

        goodCode()

        when:
        BuildResult result = gradleRunner()
            .withGradleVersion(gradleVersionForAndroid(DEFAULT_ANDROID_VERSION))
            .withArguments('check', '--stacktrace', '--offline')
            .build()

        then:
        result.task(':downloadAndroidLintConfig').outcome == SUCCESS
        assertThat(result.output, containsString('Running in offline mode. Using a possibly outdated version of'))
    }

    @SuppressWarnings('MethodName')
    void 'fails when error found and ignoreErrors is false'() {
        given:
        setupProjectWithViolations(false)

        when:
        BuildResult result = gradleRunner()
            .withGradleVersion(gradleVersionForAndroid(DEFAULT_ANDROID_VERSION))
            .buildAndFail()

        then:
        // Make sure task didn't fail
        result.task(taskName()).outcome == FAILED

        // Make sure the report exist
        reportFile().exists()
    }

    @SuppressWarnings('MethodName')
    void 'does not fail when error found and ignoreErrors is true'() {
        given:
        setupProjectWithViolations(true)

        when:
        BuildResult result = gradleRunner()
            .withGradleVersion(gradleVersionForAndroid(DEFAULT_ANDROID_VERSION))
            .build()

        then:
        // Make sure task didn't fail
        result.task(taskName()).outcome == SUCCESS

        // Make sure the report exist
        reportFile().exists()
    }

    @Override
    String reportFileName(final String buildType) {
        // Sourceset names are not taken into account
        'build/reports/android/lint-results.xml'
    }

    @Override
    String taskName() {
        ':lint'
    }

    @Override
    String toolName() {
        'androidLint'
    }

    TestFile writeSimpleAndroidLintConfig(final String project = null) {
        file("config/android/android-lint${project ? "-${project}" : ''}.xml") <<
            '''<?xml version="1.0" encoding="UTF-8"?>
            <lint>
                <issue id="InvalidPackage" severity="warning" />
            </lint>
        ''' as TestFile
    }

    @SuppressWarnings('GStringExpressionWithinString')
    void useSimpleAndroidLintConfig() {
        writeSimpleAndroidLintConfig()

        buildScriptFile() << '''
            staticCodeAnalysis {
                androidLintConfig = "${project.rootDir}/config/android/android-lint.xml"
            }
        '''
    }

    void setupProjectWithViolations(final boolean ignoreErrors) {
        writeAndroidManifest()

        writeAndroidBuildFile() << """
            staticCodeAnalysis {
                ignoreErrors = ${ignoreErrors}
            }

            // Treat everything as an error
            android {
                lintOptions {
                    warningsAsErrors true
                }
            }
        """

        goodCode()
    }
}
