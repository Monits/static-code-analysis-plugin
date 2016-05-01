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
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

/**
 * Base specification for integration testing of a gradle plugin.
*/
abstract class AbstractIntegTestFixture extends Specification {

    @Rule
    final TemporaryFolder testProjectDir = new TemporaryFolder()

    String pluginClasspathString

    @SuppressWarnings('UnnecessaryCollectCall')
    void setup() {
        // We do it this way to support all versions of gradle in our tests, since we care about backwards comaptibility
        URL pluginClasspathResource = getClass().classLoader.findResource('plugin-classpath.txt')
        if (pluginClasspathResource == null) {
            throw new IllegalStateException('Did not find plugin classpath resource, run `testClasses` build task.')
        }

        Collection<File> pluginClasspath = pluginClasspathResource.readLines().collect { new File(it) }
        pluginClasspathString = pluginClasspath
                .collect { it.absolutePath } // get absolute paths
                .findAll { !it.contains(".gradle${File.separator}wrapper${File.separator}dists${File.separator}") }
                .collect { it.replace('\\', '\\\\') } // escape backslashes in Windows paths
                .collect { "'$it'" }
                .join(', ')
    }

    GradleRunner gradleRunner() {
        GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments('check', '--stacktrace')
    }

    TestFile file(path) {
        File f = new File(testProjectDir.root, path)
        f.parentFile.mkdirs()
        new TestFile(f)
    }

    void goodCode(int numberOfClasses = 1) {
        1.upto(numberOfClasses) {
            file("src/main/java/com/monits/Class${it}.java") <<
                "package com.monits; public class Class${it} { public boolean isFoo(Object arg) { return true; } }"
            file("src/test/java/com/monits/Class${it}Test.java") <<
                "package com.monits; public class Class${it}Test { public boolean isFoo(Object arg) { return true; } }"
        }
    }

    TestFile writeBuildFile() {
        Map<String, Boolean> configMap = [:]
        configMap.put(toolName(), Boolean.TRUE)
        writeBuildFile(configMap)
    }

    TestFile writeBuildFile(toolsConfig) {
        buildScriptFile() << """
            buildscript {
                dependencies {
                    classpath files($pluginClasspathString)
                }
            }

            repositories {
                mavenCentral()
            }

            apply plugin: 'java'
            apply plugin: 'com.monits.staticCodeAnalysis'

            // disable all other checks
            staticCodeAnalysis {
                cpd = ${toolsConfig.get('cpd', false)}
                checkstyle = ${toolsConfig.get('checkstyle', false)}
                findbugs = ${toolsConfig.get('findbugs', false)}
                pmd = ${toolsConfig.get('pmd', false)}
            }
        """ as TestFile
    }

    @SuppressWarnings('FactoryMethodName')
    TestFile buildScriptFile() {
        file('build.gradle')
    }

    TestFile reportFile(String sourceSet = '') {
        file(reportFileName(sourceSet))
    }

    abstract String reportFileName(String sourceSet)

    abstract String taskName()

    abstract String toolName()
}
