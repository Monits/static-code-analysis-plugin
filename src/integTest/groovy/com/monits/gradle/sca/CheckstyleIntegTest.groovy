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

import com.monits.gradle.sca.fixture.AbstractPerSourceSetPluginIntegTestFixture
import com.monits.gradle.sca.io.TestFile
import groovy.transform.CompileDynamic
import org.gradle.api.JavaVersion
import org.gradle.testkit.runner.BuildResult
import org.gradle.util.GradleVersion
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.FAILED
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.core.IsNot.not
import static org.junit.Assert.assertThat

/**
 * Integration test of Checkstyle tasks.
*/
@CompileDynamic
class CheckstyleIntegTest extends AbstractPerSourceSetPluginIntegTestFixture {

    @SuppressWarnings('MethodName')
    @Unroll('Checkstyle #checkstyleVersion should run when using gradle #version')
    void 'checkstyle is run'() {
        given:
        writeBuildFile()
        useAlmostEmptyCheckstyleConfig()
        goodCode()

        when:
        BuildResult result = gradleRunner()
            .withGradleVersion(version)
            .build()

        then:
        if (GradleVersion.version(version) >= GradleVersion.version('2.5')) {
            // Executed task capture is only available in Gradle 2.5+
            result.task(taskName()).outcome == SUCCESS
        }

        // Execution output is only available in Gradle 2.8+
        if (GradleVersion.version(version) >= GradleVersion.version('2.8')) {
            // Check the proper message is logged
            if (GradleVersion.version(version) < ToolVersions.GRADLE_VERSION_CHECKSTYLE) {
                assertThat(result.output, containsString('Update the used Gradle version to'))
            } else if (JavaVersion.current() < JavaVersion.VERSION_1_8) {
                assertThat(result.output, containsString('Update the used Java version to'))
            }
        }

        // Make sure report exists and was using the expected tool version
        reportFile().exists()
        reportFile().assertContents(containsString("<checkstyle version=\"$checkstyleVersion\""))

        // Cache files must exist per sourceset
        if (checkstyleVersion == ToolVersions.LATEST_CHECKSTYLE_VERSION) {
            file('build/checkstyle-main.cache').exists()
            file('build/checkstyle-test.cache').exists()
        }

        // No HTML report should exist
        reportFile().parentFile.list { dir, name -> name.endsWith('.html') }.length == 0

        where:
        version << TESTED_GRADLE_VERSIONS
        checkstyleVersion = GradleVersion.version(version) < ToolVersions.GRADLE_VERSION_CHECKSTYLE ?
                ToolVersions.BACKWARDS_CHECKSTYLE_VERSION : (JavaVersion.current() < JavaVersion.VERSION_1_8 ?
                ToolVersions.LATEST_CHECKSTYLE_VERSION_JAVA_7 : ToolVersions.LATEST_CHECKSTYLE_VERSION)
    }

    @SuppressWarnings('MethodName')
    void 'checkstyle download remote config'() {
        given:
        writeBuildFile()
        goodCode()

        when:
        BuildResult result = gradleRunner()
                .build()

        then:
        result.task(taskName()).outcome == SUCCESS

        // The config must exist
        file('config/checkstyle/checkstyle-main.xml').exists()
        file('config/checkstyle/checkstyle-test.xml').exists()

        // Make sure checkstyle report exists
        reportFile().exists()
    }

    @SuppressWarnings('MethodName')
    void 'running offline fails download'() {
        given:
        writeBuildFile()
        goodCode()

        when:
        BuildResult result = gradleRunner()
            .withArguments('check', '--stacktrace', '--offline')
            .buildAndFail()

        then:
        result.task(':downloadCheckstyleXmlMain').outcome == FAILED
        assertThat(result.output, containsString('Running in offline mode, but there is no cached version'))
    }

    @SuppressWarnings('MethodName')
    void 'running offline with a cached file passes but warns'() {
        given:
        writeBuildFile()
        writeAlmostEmptyCheckstyleConfig('main')
        writeAlmostEmptyCheckstyleConfig('test')
        goodCode()

        when:
        BuildResult result = gradleRunner()
                .withArguments('check', '--stacktrace', '--offline')
                .build()

        then:
        result.task(':downloadCheckstyleXmlTest').outcome == SUCCESS
        result.task(':downloadCheckstyleXmlMain').outcome == SUCCESS
        result.task(taskName()).outcome == SUCCESS
        assertThat(result.output, containsString('Running in offline mode. Using a possibly outdated version of'))
    }

    @SuppressWarnings('MethodName')
    void 'dsl allows to override rules per sourceset'() {
        given:
        writeBuildFile() << '''
            |staticCodeAnalysis {
            |    sourceSetConfig {
            |        test {
            |            checkstyleRules = 'test-checkstyle.xml'
            |        }
            |    }
            |}
        '''.stripMargin()
        writeAlmostEmptyCheckstyleConfig('test').renameTo(file('test-checkstyle.xml'))
        goodCode()

        when:
        BuildResult result = gradleRunner()
                .build()

        then:
        result.task(taskName()).outcome == SUCCESS

        // The config for other sourcesets must exist
        file('config/checkstyle/checkstyle-main.xml').exists()

        // Make sure checkstyle reports exist
        reportFile().exists()
        reportFile('test').exists()

        // But results should differ in spite of being very similar code
        reportFile().assertContents(containsString('<error '))
        reportFile('test').assertContents(not(containsString('<error ')))
    }

    @Override
    String reportFileName(final String sourceSet) {
        "build/reports/checkstyle/checkstyle${sourceSet ? "-${sourceSet}" : '-main'}.xml"
    }

    @Override
    String taskName() {
        ':checkstyle'
    }

    @Override
    String toolName() {
        'checkstyle'
    }

    TestFile writeAlmostEmptyCheckstyleConfig(final String sourceSet = null) {
        file("config/checkstyle/checkstyle${sourceSet ? "-${sourceSet}" : ''}.xml") << '''\
            |<?xml version="1.0" encoding="UTF-8"?>
            |<!DOCTYPE module PUBLIC "-//Puppy Crawl//DTD Check Configuration 1.3//EN"
            |    "http://www.puppycrawl.com/dtds/configuration_1_3.dtd">
            |<module name="Checker">
            |    <property name="severity" value="warning"/>
            |    <module name="TreeWalker">
            |        <module name="OneTopLevelClass"/>
            |    </module>
            |</module>
        '''.stripMargin() as TestFile
    }

    @SuppressWarnings('GStringExpressionWithinString')
    void useAlmostEmptyCheckstyleConfig() {
        writeAlmostEmptyCheckstyleConfig()

        buildScriptFile() << '''
            |staticCodeAnalysis {
            |    checkstyleRules = "${project.rootDir}/config/checkstyle/checkstyle.xml"
            |}
        '''.stripMargin()
    }
}
