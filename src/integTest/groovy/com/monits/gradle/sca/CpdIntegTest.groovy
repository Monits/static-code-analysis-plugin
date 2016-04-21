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

import com.monits.gradle.sca.fixture.AbstractPluginIntegTestFixture
import org.gradle.util.GradleVersion
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class CpdIntegTest extends AbstractPluginIntegTestFixture {
    @Unroll("CPD should run when using gradle #version")
    def "CPD is run"() {
        given:
        writeBuildFile()
        goodCode()

        when:
        def result = gradleRunner()
            .withGradleVersion(version)
            .build()

        then:
        if (GradleVersion.version(version) >= GradleVersion.version('2.5')) {
            // Executed task capture is only available in Gradle 2.5+
            result.task(taskName()).outcome == SUCCESS
        }

        // Make sure report exists
        reportFile().exists()

        where:
        version << ['2.3', '2.4', '2.8', '2.10', GradleVersion.current().version]
    }

    def "CPD runs if there is no code"() {
        given:
        writeBuildFile()

        when:
        def result = gradleRunner().build()

        then:
        result.task(taskName()).outcome == SUCCESS

        // The report should not exist
        !reportFile().exists()
    }

    String reportFileName() {
        'build/reports/pmd/cpd.xml'
    }

    String taskName() {
        ":cpd"
    }

    String toolName() {
        'cpd'
    }
}
