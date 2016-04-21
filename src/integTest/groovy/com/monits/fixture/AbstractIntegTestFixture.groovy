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
package com.monits.fixture

import com.monits.io.TestFile
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

abstract class AbstractIntegTestFixture extends Specification {

    @Rule
    final TemporaryFolder testProjectDir = new TemporaryFolder();

    String pluginClasspathString

    def setup() {
        // We do it this way to support all versions of gradle in our tests, since we care about backwards comaptibility
        def pluginClasspathResource = getClass().classLoader.findResource("plugin-classpath.txt")
        if (pluginClasspathResource == null) {
            throw new IllegalStateException("Did not find plugin classpath resource, run `testClasses` build task.")
        }

        def pluginClasspath = pluginClasspathResource.readLines().collect { new File(it) }
        pluginClasspathString = pluginClasspath
                .collect { it.absolutePath } // get absolute paths
                .findAll { !it.contains(".gradle${File.separator}wrapper${File.separator}dists${File.separator}") }
                .collect { it.replace('\\', '\\\\') } // escape backslashes in Windows paths
                .collect { "'$it'" }
                .join(", ")
    }

    def gradleRunner() {
        GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments('check', '--stacktrace')
    }

    def file(path) {
        def f = new File(testProjectDir.root, path)
        f.parentFile.mkdirs()
        return new TestFile(f)
    }

    def goodCode(int numberOfClasses = 1) {
        1.upto(numberOfClasses) {
            file("src/main/java/com/monits/Class${it}.java") << "package com.monits; public class Class${it} { public boolean isFoo(Object arg) { return true; } }"
            file("src/test/java/com/monits/Class${it}Test.java") << "package com.monits; public class Class${it}Test { public boolean isFoo(Object arg) { return true; } }"
        }
    }

    def writeBuildFile() {
        def configMap = new LinkedHashMap<>();
        configMap.put(toolName(), true);
        writeBuildFile(configMap)
    }

    def writeBuildFile(toolsConfig) {
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
        """
    }

    def buildScriptFile() {
        file('build.gradle')
    }

    def reportFile() {
        file(reportFileName())
    }

    abstract String reportFileName()

    abstract String taskName()

    abstract String toolName()
}
