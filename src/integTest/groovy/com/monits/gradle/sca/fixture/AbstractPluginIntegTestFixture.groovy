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
package com.monits.gradle.sca.fixture

import groovy.transform.CompileDynamic
import org.gradle.testkit.runner.BuildResult

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

/**
 * Base specification to test a single analysis report.
*/
@CompileDynamic
abstract class AbstractPluginIntegTestFixture extends AbstractIntegTestFixture {

    @SuppressWarnings('MethodName')
    void 'tool not configured if disabled'() {
        given:
        writeBuildFile(any:false) // just make sure to call the proper method
        goodCode()

        when:
        BuildResult result = gradleRunner().build()

        then:
        // no task should be configured
        result.task(taskName()) == null

        // Make sure the report doesn't exist
        !reportFile().exists()
    }

    @SuppressWarnings('MethodName')
    void 'rerun is up-to-date'() {
        given:
        writeBuildFile()
        goodCode()

        when:
        BuildResult firstRun = gradleRunner().build()
        BuildResult secondRun = gradleRunner().build()

        then:
        firstRun.task(taskName()).outcome == SUCCESS
        secondRun.task(taskName()).outcome == UP_TO_DATE

        // Make sure the report exist
        reportFile().exists()
    }
}
