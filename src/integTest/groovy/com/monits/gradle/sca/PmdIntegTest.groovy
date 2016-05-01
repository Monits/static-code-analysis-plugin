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
import org.gradle.testkit.runner.BuildResult
import org.gradle.util.GradleVersion
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.hamcrest.CoreMatchers.containsString
import static org.junit.Assert.assertThat

/**
 * Integration test of PMD tasks.
 */
class PmdIntegTest extends AbstractPluginIntegTestFixture {
    @SuppressWarnings('MethodName')
    @Unroll('PMD #pmdVersion should run when using gradle #version')
    void 'pmd is run'() {
        given:
        writeBuildFile()
        goodCode()

        when:
        BuildResult result = gradleRunner()
            .withGradleVersion(version)
            .build()

        then:
        if (GradleVersion.version(version) >= GradleVersion.version('2.5')) {
            // Executed task capture is only available in Gradle 2.5+
            result.task(taskName()).outcome == SUCCESS
            result.task(':pmdMain').outcome == SUCCESS
            result.task(':pmdTest').outcome == SUCCESS
        }

        // Make sure report exists and was using the expected tool version
        reportFile('main').exists()
        reportFile('main').assertContents(containsString("<pmd version=\"$pmdVersion\""))

        reportFile('test').exists()
        reportFile('test').assertContents(containsString("<pmd version=\"$pmdVersion\""))

        where:
        version << ['2.3', '2.4', '2.8', '2.10', GradleVersion.current().version]
        pmdVersion = GradleVersion.version(version) < ToolVersions.GRADLE_VERSION_PMD ?
                ToolVersions.BACKWARDS_PMD_TOOL_VERSION : ToolVersions.LATEST_PMD_TOOL_VERSION
    }

    @SuppressWarnings('MethodName')
    void 'pmd configures auxclasspath'() {
        given:
        writeBuildFile()
        buildScriptFile() << '''
            dependencies {
                // Add a dependency so there is something in the classpath
                testCompile 'junit:junit:4.12'
            }

            afterEvaluate {
                Task pmdTask = project.tasks.getByPath(':pmdTest');
                pmdTask << {
                    if (!classpath.empty) {
                        println "Auxclasspath is configured"
                    }
                }
            }
        '''
        goodCode()

        when:
        BuildResult result = gradleRunner()
                .withGradleVersion('2.8')
                .build()

        then:
        // The classpath must be configured, and not empty
        assertThat(result.output, containsString('Auxclasspath is configured'))

        // Make sure pmd report exists
        reportFile().exists()
    }

    @Override
    String reportFileName(final String sourceSet) {
        "build/reports/pmd/pmd${sourceSet ? "-${sourceSet}" : '-main'}.xml"
    }

    @Override
    String taskName() {
        ':pmd'
    }

    @Override
    String toolName() {
        'pmd'
    }
}
