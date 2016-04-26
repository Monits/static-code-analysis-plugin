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
import com.monits.gradle.sca.io.TestFile
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
            result.task(':resolveAndroidLint').outcome == SUCCESS
            result.task(':cleanupAndroidLint').outcome == SUCCESS
        }

        // Make sure report exists and was using the expected tool version
        reportFile().exists()

        where:
        version << ['2.3', '2.4', '2.7', '2.10', GradleVersion.current().version]
    }

    String reportFileName() {
        'build/outputs/lint-results.xml'
    }

    String taskName() {
        ':lint'
    }

    String toolName() {
        'androidLint'
    }

    TestFile writeBuildFile(toolsConfig) {
        // Android lint only exists on Android projects
        writeAndroidManifest()

        buildScriptFile() << """
            buildscript {
                dependencies {
                    classpath 'com.android.tools.build:gradle:1.5.0'
                    classpath files($pluginClasspathString)
                }

                repositories {
                    jcenter()
                }
            }

            repositories {
                mavenCentral()
            }

            apply plugin: 'com.android.library'
            apply plugin: 'com.monits.staticCodeAnalysis'

            // disable all other checks
            staticCodeAnalysis {
                cpd = ${toolsConfig.get('cpd', false)}
                checkstyle = ${toolsConfig.get('checkstyle', false)}
                findbugs = ${toolsConfig.get('findbugs', false)}
                pmd = ${toolsConfig.get('pmd', false)}
            }

            android {
                compileSdkVersion 23
                buildToolsVersion "23.0.2"
            }
        """ as TestFile
    }

    TestFile writeAndroidManifest() {
        file('src/main/AndroidManifest.xml') << '''
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="com.monits.staticCodeAnalysis"
                android:versionCode="1">
            </manifest>
        ''' as TestFile
    }
}
