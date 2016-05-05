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

import org.gradle.testkit.runner.BuildResult

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
/**
 * Base specification to test a single analysis report.
*/
abstract class AbstractPerSourceSetPluginIntegTestFixture extends AbstractPluginIntegTestFixture {

    @SuppressWarnings('MethodName')
    void 'multimodule android project runs all tasks'() {
        given:
        setupMultimoduleAndroidProject()

        when:
        BuildResult result = gradleRunner()
                .withArguments(toolName()) // use the meta-task
                .build()

        then:
        result.task(':liba' + taskName()).outcome == SUCCESS
        result.task(':liba' + taskName() + 'Main').outcome == SUCCESS
        result.task(':liba' + taskName() + 'Test').outcome == SUCCESS
        result.task(':libb' + taskName()).outcome == SUCCESS
        result.task(':libb' + taskName() + 'Main').outcome == SUCCESS
        result.task(':libb' + taskName() + 'Test').outcome == SUCCESS

        // The reports must exist
        file('libb/' + reportFileName(null)).exists()
        file('libb/' + reportFileName('test')).exists()
        file('liba/' + reportFileName(null)).exists()
        file('liba/' + reportFileName('test')).exists()
    }
}
