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
import org.gradle.testkit.runner.BuildResult
import org.gradle.util.GradleVersion
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.FAILED
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.is
import static org.hamcrest.core.IsNot.not
import static org.junit.Assert.assertThat

/**
 * Integration test of PMD tasks.
 */
class PmdIntegTest extends AbstractPerSourceSetPluginIntegTestFixture {
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
        version << TESTED_GRADLE_VERSIONS
        pmdVersion = GradleVersion.version(version) < ToolVersions.GRADLE_VERSION_PMD ?
                ToolVersions.BACKWARDS_PMD_TOOL_VERSION : ToolVersions.LATEST_PMD_TOOL_VERSION
    }

    @SuppressWarnings('MethodName')
    void 'pmd configures auxclasspath for Java'() {
        given:
        writeBuildFile()
        buildScriptFile() << '''
            afterEvaluate {
                Task pmdTask = project.tasks.getByPath(':pmdMain')
                pmdTask.doLast {
                    if (!classpath.empty) {
                        println "Auxclasspath is configured for main " + classpath.asPath
                    }
                }

                Task pmdTestTask = project.tasks.getByPath(':pmdTest')
                pmdTestTask.doLast {
                    if (!classpath.empty) {
                        println "Auxclasspath is configured for test " + classpath.asPath
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
        assertThat('main classes are in classpath',
            (result.output =~ /Auxclasspath is configured for main .*\/classes\/main/) as boolean, is(true))
        assertThat('test classes are not in main classpath',
            (result.output =~ /Auxclasspath is configured for main .*\/classes\/test/) as boolean, is(false))
        assertThat('junit is not in main classpath',
            (result.output =~ /Auxclasspath is configured for main .*\/junit\//) as boolean, is(false))
        assertThat('main classes are in test classpath',
            (result.output =~ /Auxclasspath is configured for test .*\/classes\/main/) as boolean, is(true))
        assertThat('test classes are in test classpath',
            (result.output =~ /Auxclasspath is configured for test .*\/classes\/test/) as boolean, is(true))
        assertThat('junit is in test classpath',
            (result.output =~ /Auxclasspath is configured for test .*\/junit\//) as boolean, is(true))

        // Make sure pmd report exists
        reportFile().exists()
        reportFile('test').exists()
    }

    @SuppressWarnings('MethodName')
    void 'pmd configures auxclasspath for Android'() {
        given:
        writeAndroidBuildFile()
        writeAndroidManifest()
        buildScriptFile() << '''
            afterEvaluate {
                Task configPmdTask = project.tasks.getByPath(':configureClasspathForPmdMain')
                configPmdTask.doLast {
                    Task pmdTask = project.tasks.getByPath(':pmdMain')
                    if (!pmdTask.classpath.empty) {
                        println "Auxclasspath is configured for main " + pmdTask.classpath.asPath
                    }
                }

                Task configTestPmdTask = project.tasks.getByPath(':configureClasspathForPmdTest')
                configTestPmdTask.doLast {
                    Task pmdTask = project.tasks.getByPath(':pmdTest')
                    if (!pmdTask.classpath.empty) {
                        println "Auxclasspath is configured for test " + pmdTask.classpath.asPath
                    }
                }
            }
        '''
        goodCode()

        when:
        BuildResult result = gradleRunner()
            .withGradleVersion('2.8') // property is available since 2.8
            .build()

        then:
        // The classpath must be configured, and not empty
        assertThat('main classes are in classpath',
            (result.output =~ /Auxclasspath is configured for main .*\/debug\//) as boolean, is(true))
        // on android we don't discriminate test / compile
        assertThat('test classes are in main classpath',
            (result.output =~ /Auxclasspath is configured for main .*\/test\/debug/) as boolean, is(true))
        // on android we don't discriminate test / compile
        assertThat('junit is in main classpath',
            (result.output =~ /Auxclasspath is configured for main .*\/junit\//) as boolean, is(true))
        assertThat('main classes are in test classpath',
            (result.output =~ /Auxclasspath is configured for test .*\/debug\//) as boolean, is(true))
        assertThat('test classes are in test classpath',
            (result.output =~ /Auxclasspath is configured for test .*\/test\/debug/) as boolean, is(true))
        assertThat('junit is in test classpath',
            (result.output =~ /Auxclasspath is configured for test .*\/junit\//) as boolean, is(true))

        // Make sure pmd report exists
        reportFile().exists()
        reportFile('test').exists()
    }

    @SuppressWarnings('MethodName')
    void 'compile is run before pmd'() {
        given:
        writeBuildFile()
        goodCode()

        when:
        BuildResult result = gradleRunner()
            .build()

        then:
        result.task(taskName()).outcome == SUCCESS
        result.task(':compileJava').outcome == SUCCESS
        result.task(':compileTestJava').outcome == SUCCESS
    }

    @SuppressWarnings('MethodName')
    void 'dsl allows to override rules per sourceset'() {
        given:
        writeBuildFile() << '''
            staticCodeAnalysis {
                sourceSetConfig {
                    test {
                        pmdRules = ['test-pmd.xml']
                    }
                }
            }
        '''
        file('test-pmd.xml') <<
            '''<?xml version="1.0"?>
            <ruleset name="Monits Java ruleset"
                    xmlns="http://pmd.sf.net/ruleset/1.0.0"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="http://pmd.sf.net/ruleset/1.0.0 http://pmd.sf.net/ruleset_xml_schema.xsd"
                    xsi:noNamespaceSchemaLocation="http://pmd.sf.net/ruleset_xml_schema.xsd">

                <rule ref="rulesets/java/comments.xml/CommentDefaultAccessModifier" />
            </ruleset>
        '''
        goodCode()
        // Class with CommentDefaultAccessModifier violation
        file('src/main/java/com/monits/BadPmd.java') <<
            'package com.monits; public class BadPmd { boolean isFoo(Object arg) { return true; } }'

        when:
        BuildResult result = gradleRunner()
                .build()

        then:
        result.task(taskName()).outcome == SUCCESS

        // Make sure PMD reports exist
        reportFile().exists()
        reportFile('test').exists()

        // But results should differ in spite of being very similar code
        reportFile().assertContents(containsString('<violation '))
        reportFile('test').assertContents(not(containsString('<violation ')))
    }

    @SuppressWarnings('MethodName')
    void 'pmd download remote config for Java'() {
        given:
        writeBuildFile()
        goodCode()

        when:
        BuildResult result = gradleRunner()
            .build()

        then:
        result.task(taskName()).outcome == SUCCESS

        // The config must exist, but  only for Java projects
        file('config/pmd/pmd-main-pmd.xml').exists()
        file('config/pmd/pmd-test-pmd.xml').exists()
        !file('config/pmd/pmd-main-pmd-android.xml').exists()
        !file('config/pmd/pmd-test-pmd-android.xml').exists()

        // Make sure PMD report exists
        reportFile().exists()
    }

    @SuppressWarnings('MethodName')
    void 'pmd download remote config for Android'() {
        given:
        writeAndroidBuildFile()
        writeAndroidManifest()
        goodCode()

        when:
        BuildResult result = gradleRunner()
            .withGradleVersion(gradleVersionForAndroid(DEFAULT_ANDROID_VERSION))
            .build()

        then:
        result.task(taskName()).outcome == SUCCESS

        // The config must exist
        file('config/pmd/pmd-main-pmd.xml').exists()
        file('config/pmd/pmd-main-pmd-android.xml').exists()
        file('config/pmd/pmd-test-pmd.xml').exists()
        file('config/pmd/pmd-test-pmd-android.xml').exists()

        // Make sure PMD report exists
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
        result.task(':downloadPmdXmlMainPmd').outcome == FAILED
        assertThat(result.output, containsString('Running in offline mode, but there is no cached version'))
    }

    @SuppressWarnings('MethodName')
    void 'running offline with a cached file passes but warns'() {
        given:
        writeBuildFile()
        writeAlmostEmptyPmdConfig('main-pmd')
        writeAlmostEmptyPmdConfig('main-pmd-android')
        writeAlmostEmptyPmdConfig('test-pmd')
        writeAlmostEmptyPmdConfig('test-pmd-android')
        goodCode()

        when:
        BuildResult result = gradleRunner()
            .withArguments('check', '--stacktrace', '--offline')
            .build()

        then:
        result.task(':downloadPmdXmlMainPmd').outcome == SUCCESS
        result.task(':downloadPmdXmlTestPmd').outcome == SUCCESS
        assertThat(result.output, containsString('Running in offline mode. Using a possibly outdated version of'))
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

    TestFile writeAlmostEmptyPmdConfig(final String fileClassifier = null) {
        file("config/pmd/pmd${fileClassifier ? "-${fileClassifier}" : ''}.xml") <<
            '''<?xml version="1.0" encoding="UTF-8"?>
            <ruleset name="Monits Test ruleset"
                xmlns="http://pmd.sf.net/ruleset/1.0.0"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsi:schemaLocation="http://pmd.sf.net/ruleset/1.0.0 http://pmd.sf.net/ruleset_xml_schema.xsd"
                xsi:noNamespaceSchemaLocation="http://pmd.sf.net/ruleset_xml_schema.xsd">

                <rule ref="rulesets/java/basic.xml" />
                <rule ref="rulesets/java/android.xml" />
            </ruleset>
        ''' as TestFile
    }
}
