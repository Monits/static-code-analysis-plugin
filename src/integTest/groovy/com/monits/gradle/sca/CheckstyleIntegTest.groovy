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
import static org.hamcrest.CoreMatchers.containsString

class CheckstyleIntegTest extends AbstractPluginIntegTestFixture {
    @Unroll("Checkstyle #checkstyleVersion should run when using gradle #version")
    def "Checkstyle is run"() {
        given:
        writeBuildFile()
        writeEmptyCheckstyleConfig()
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

        // Make sure report exists and was using the expected tool version
        reportFile().exists()
        reportFile().assertContents(containsString("<checkstyle version=\"$checkstyleVersion\""))

        where:
        version << ['2.3', '2.4', '2.7', '2.10', GradleVersion.current().version]
        checkstyleVersion = GradleVersion.version(version) < StaticCodeAnalysisPlugin.GRADLE_VERSION_CHECKSTYLE ?
                StaticCodeAnalysisPlugin.BACKWARDS_CHECKSTYLE_VERSION : StaticCodeAnalysisPlugin.LATEST_CHECKSTYLE_VERSION
    }

    def "Checkstyle download remote config"() {
        given:
        writeBuildFile()
        goodCode()

        when:
        def result = gradleRunner()
                .build()

        then:
        result.task(taskName()).outcome == SUCCESS

        // The config must exist
        file('config/checkstyle/checkstyle.xml').exists()

        // Make sure checkstyle report exists
        reportFile().exists()
    }

    String reportFileName() {
        'build/reports/checkstyle/checkstyle.xml'
    }

    String taskName() {
        ":checkstyle"
    }

    String toolName() {
        'checkstyle'
    }

    def writeEmptyCheckstyleConfig() {
        file('config/checkstyle.xml') <<
        """<?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE module PUBLIC "-//Puppy Crawl//DTD Check Configuration 1.3//EN" "http://www.puppycrawl.com/dtds/configuration_1_3.dtd">
            <module name="Checker">
                <property name="severity" value="warning"/>
                <module name="TreeWalker">
                    <module name="OneTopLevelClass"/>
                </module>
            </module>
        """

        buildScriptFile() << """
            staticCodeAnalysis {
                checkstyleRules = "config/checkstyle.xml"
            }
        """
    }
}
