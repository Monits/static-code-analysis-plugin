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
package com.monits.gradle.sca.fixture

import com.monits.gradle.sca.io.TestFile
import org.gradle.testkit.runner.BuildResult

import static org.apache.commons.lang3.StringUtils.capitalize
import static org.gradle.testkit.runner.TaskOutcome.FAILED
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

/**
 * Base specification to test a single analysis report.
*/
abstract class AbstractPerSourceSetPluginIntegTestFixture extends AbstractPluginIntegTestFixture {

    static final MAIN_SOURCESET = 'main'
    static final TEST_SOURCESET = 'test'
    static final ANDROID_TEST_SOURCESET = 'androidTest'

    @SuppressWarnings('MethodName')
    void 'multimodule android project runs all tasks'() {
        given:
        setupMultimoduleAndroidProject()

        when:
        BuildResult result = gradleRunner()
                .withArguments(toolName()) // use the meta-task
                .build()

        then:
        result.task(LIBA_PATH + taskName()).outcome == SUCCESS
        result.task(LIBA_PATH + taskName(MAIN_SOURCESET)).outcome == SUCCESS
        result.task(LIBA_PATH + taskName(TEST_SOURCESET)).outcome == SUCCESS
        result.task(LIBB_PATH + taskName()).outcome == SUCCESS
        result.task(LIBB_PATH + taskName(MAIN_SOURCESET)).outcome == SUCCESS
        result.task(LIBB_PATH + taskName(TEST_SOURCESET)).outcome == SUCCESS

        // On empty sourcesets, the task should be UP-TO-DATE
        result.task(LIBA_PATH + taskName(ANDROID_TEST_SOURCESET)).outcome == UP_TO_DATE
        result.task(LIBB_PATH + taskName(ANDROID_TEST_SOURCESET)).outcome == UP_TO_DATE

        // The reports must exist
        file(LIBA_DIRNAME + reportFileName(MAIN_SOURCESET)).exists()
        file(LIBA_DIRNAME + reportFileName(TEST_SOURCESET)).exists()
        file(LIBB_DIRNAME + reportFileName(MAIN_SOURCESET)).exists()
        file(LIBB_DIRNAME + reportFileName(TEST_SOURCESET)).exists()

        // On empty sourcesets, no report file is generated
        !file(LIBA_DIRNAME + reportFileName(ANDROID_TEST_SOURCESET)).exists()
        !file(LIBB_DIRNAME + reportFileName(ANDROID_TEST_SOURCESET)).exists()
    }

    @SuppressWarnings('MethodName')
    void 'fails when error found and ignoreErrors is false'() {
        given:
        setupProjectWithViolations(false)

        when:
        BuildResult result = gradleRunner().buildAndFail()

        then:
        // Make sure one of the two sourcesets' task failed
        result.task(taskName(MAIN_SOURCESET)).outcome == FAILED ||
            result.task(taskName(TEST_SOURCESET)).outcome == FAILED

        // Make sure at least one report exists
        reportFile(MAIN_SOURCESET).exists() || reportFile(TEST_SOURCESET).exists()
    }

    @SuppressWarnings('MethodName')
    void 'does not fail when error found and ignoreErrors is true'() {
        given:
        setupProjectWithViolations(true)

        when:
        BuildResult result = gradleRunner().build()

        then:
        // Make sure one of the two sourcesets' task failed
        result.task(taskName(MAIN_SOURCESET)).outcome == SUCCESS
        result.task(taskName(TEST_SOURCESET)).outcome == SUCCESS

        // Make sure the reports exist
        reportFile(MAIN_SOURCESET).exists()
        reportFile(TEST_SOURCESET).exists()
    }

    void setupProjectWithViolations(final boolean ignoreErrors) {
        writeBuildFile() << """
            staticCodeAnalysis {
                checkstyleRules = 'config/checkstyle/checkstyle.xml'
                ignoreErrors = ${ignoreErrors}
            }
        """
        /*
         Make sure Checkstyle's config is set to errors, warnings won't make it fail,
         see https://issues.gradle.org/browse/GRADLE-2888
          */
        file('config/checkstyle/checkstyle.xml') <<
            '''<?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE module PUBLIC "-//Puppy Crawl//DTD Check Configuration 1.3//EN"
                "http://www.puppycrawl.com/dtds/configuration_1_3.dtd">
            <module name="Checker">
                <property name="severity" value="error"/>
                <module name="TreeWalker">
                    <module name="FinalParameters">
                        <property name="tokens" value="METHOD_DEF,CTOR_DEF,LITERAL_CATCH,FOR_EACH_CLAUSE"/>
                    </module>
                </module>
            </module>
        ''' as TestFile
        goodCode()
    }

    // Get per-sourceset task
    String taskName(final String sourceSet) {
        taskName() + capitalize(sourceSet)
    }
}
