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
package com.monits.gradle.sca.performance.fixture

import com.monits.gradle.sca.performance.io.TestFile
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.GradleVersion
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.util.environment.Jvm

/**
 * Base specification for integration testing of a gradle plugin.
*/
@CompileStatic
abstract class AbstractPerfTestFixture extends Specification {
    // A sample of gradle versions to be considered in general testing - don't test anything below 2.8, it spams stdout
    static final List<String> TESTED_GRADLE_VERSIONS = ['2.8', '2.14.1', GradleVersion.current().version].asImmutable()
    @SuppressWarnings(['DuplicateStringLiteral', 'UnnecessaryCast'])
    static final List<String> TESTED_GRADLE_VERSIONS_FOR_ANDROID = (['2.8', '2.14.1'] +
        (Jvm.current.java8Compatible ? [GradleVersion.current().version] : [] as List<String>))
        .takeRight(2).asImmutable()
    static final String BASELINE_PLUGIN_VERSION = '"com.monits:static-code-analysis-plugin:2.4.2"'

    static final int NUMBER_OF_CLASSES_TO_ANALYZE = 100

    private static final String ANDROID_1_5_0 = '1.5.0'
    static final String DEFAULT_ANDROID_VERSION = ANDROID_1_5_0
    protected static final String ANDROID_VERSION = 'androidVersion'
    static final String LIBA_DIRNAME = 'liba/'
    static final String LIBB_DIRNAME = 'libb/'
    static final String LIBA_PATH = ':liba'
    static final String LIBB_PATH = ':libb'
    static final String ANDROID_MANIFEST_PATH = 'src/main/AndroidManifest.xml'
    static final String BUILD_GRADLE_FILENAME = 'build.gradle'

    @Rule
    final TemporaryFolder testProjectDir = new TemporaryFolder()

    String pluginClasspathString

    @CompileStatic(TypeCheckingMode.SKIP)
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
        File f = new File(testProjectDir.root.absolutePath + File.separator + path)
        f.parentFile.mkdirs()
        new TestFile(f)
    }

    void goodCode(final int numberOfClasses = NUMBER_OF_CLASSES_TO_ANALYZE) {
        1.upto(numberOfClasses) {
            file("src/main/java/com/monits/Class${it}.java") <<
                "package com.monits; public class Class${it} { public boolean isFoo(Object arg) { return true; } }"
            file("src/test/java/com/monits/Class${it}Test.java") << """
                package com.monits;

                import org.junit.Test;
                import static org.junit.Assert.assertTrue;

                public class Class${it}Test extends Object {
                    @Test
                    public void isFoo() {
                        assertTrue("It's not a foo", new Class${it}().isFoo(null));
                    }
                }"""
        }
    }

    TestFile writeBuildFile(final String pluginVersion = "files($pluginClasspathString)") {
        Map<String, Object> configMap = [:]
        configMap.put(toolName(), Boolean.TRUE)
        writeBuildFile(configMap, pluginVersion)
    }

    TestFile writeBuildFile(final Map<String, Object> toolsConfig, final String pluginVersion) {
        buildScriptFile() << """
            buildscript {
                repositories {
                    jcenter()
                }

                dependencies {
                    classpath $pluginVersion
                }
            }

            repositories {
                jcenter()
            }

            apply plugin: 'java'
            apply plugin: 'com.monits.staticCodeAnalysis'

            dependencies {
                testCompile 'junit:junit:4.12'
            }

        """ + staticCodeAnalysisConfig(toolsConfig) as TestFile
    }

    String staticCodeAnalysisConfig(final Map<String, Object> toolsConfig) {
        """
            // disable all other checks
            staticCodeAnalysis {
                cpd = ${toolsConfig.get('cpd', false)}
                checkstyle = ${toolsConfig.get('checkstyle', false)}
                findbugs = ${toolsConfig.get('findbugs', false)}
                pmd = ${toolsConfig.get('pmd', false)}
                androidLint = ${toolsConfig.get('androidLint', false)}
            }
        """
    }

    TestFile writeAndroidBuildFile(final String androidVersion,
                                   final String pluginVersion = "files($pluginClasspathString)") {
        Map<String, Object> configMap = [:]
        configMap.put(toolName(), Boolean.TRUE)
        configMap.put(ANDROID_VERSION, androidVersion)
        writeAndroidBuildFile(configMap, pluginVersion)
    }

    TestFile writeAndroidBuildFile(final Map<String, Object> toolsConfig, final String pluginVersion) {
        buildScriptFile() << """
            buildscript {
                dependencies {
                    classpath 'com.android.tools.build:gradle:' +
                        '${toolsConfig.get(ANDROID_VERSION, DEFAULT_ANDROID_VERSION)}'
                    classpath $pluginVersion
                }

                repositories {
                    jcenter()
                }
            }

            repositories {
                jcenter()
            }

            apply plugin: 'com.android.library'
            apply plugin: 'com.monits.staticCodeAnalysis'

            dependencies {
                testCompile 'junit:junit:4.12'
            }

        """ + staticCodeAnalysisConfig(toolsConfig) +
        '''
            android {
                compileSdkVersion 25
                buildToolsVersion "25.0.0"
            }
        ''' as TestFile
    }

    TestFile writeAndroidManifest(final String packageName) {
        file(ANDROID_MANIFEST_PATH) << """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="com.monits.staticCodeAnalysis.${packageName}"
                android:versionCode="1">
            </manifest>
        """ as TestFile
    }

    @SuppressWarnings('FactoryMethodName')
    TestFile buildScriptFile() {
        file(BUILD_GRADLE_FILENAME)
    }

    abstract String taskName()

    abstract String toolName()

    private void setupAndroidSubProject(final String packageName, final String dir, final String androidVersion,
                                        final String pluginVersion = "files($pluginClasspathString)") {
        writeAndroidBuildFile(androidVersion, pluginVersion).renameTo(file(dir + BUILD_GRADLE_FILENAME))
        writeAndroidManifest(packageName).renameTo(file(dir + ANDROID_MANIFEST_PATH))
        file('src').deleteDir()
    }

    void setupMultimoduleAndroidProject(final String androidVersion,
                                        final String pluginVersion = "files($pluginClasspathString)",
                                        final int numberOfClasses = NUMBER_OF_CLASSES_TO_ANALYZE) {
        setupAndroidSubProject('liba', LIBA_DIRNAME, androidVersion, pluginVersion)
        setupAndroidSubProject('libb', LIBB_DIRNAME, androidVersion, pluginVersion)

        file(LIBB_DIRNAME + BUILD_GRADLE_FILENAME) << """
            dependencies {
                compile project('${LIBA_PATH}')
            }
        """

        file('settings.gradle') << """
            include '${LIBA_PATH}', '${LIBB_PATH}'
        """
        file(BUILD_GRADLE_FILENAME).createNewFile() // empty root build.gradle

        1.upto(numberOfClasses) {
            file("${LIBA_DIRNAME}src/main/java/liba/ClassA${it}.java").text =
                "package liba; public class ClassA${it} { public boolean isFoo(Object arg) { return true; } }"
            file("${LIBA_DIRNAME}src/test/java/liba/ClassA${it}Test.java").text =
                "package liba; public class ClassA${it}Test { public boolean isFoo(Object arg) { return true; } }"
            file("${LIBB_DIRNAME}src/main/java/libb/ClassB${it}.java").text =
                "package libb; import liba.ClassA${it}; public class ClassB${it} { " +
                "public boolean isFoo(Object arg) { ClassA${it} a = new ClassA${it}(); return a.isFoo(arg); } }"
            file("${LIBB_DIRNAME}src/test/java/libb/ClassB${it}Test.java").text =
                "package libb; public class ClassB${it}Test { public boolean isFoo(Object arg) { return true; } }"
        }
    }

    @SuppressWarnings('DuplicateNumberLiteral')
    String androidVersionForGradle(final String gradleVersion) {
        GradleVersion.version(gradleVersion) < GradleVersion.version('3.0') ?
            DEFAULT_ANDROID_VERSION : '2.3.3'
    }
}
