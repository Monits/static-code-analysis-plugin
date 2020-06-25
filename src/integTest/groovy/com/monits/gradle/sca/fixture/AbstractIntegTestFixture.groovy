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

import com.monits.gradle.sca.io.TestFile
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.internal.DefaultGradleRunner
import org.gradle.util.GradleVersion
import org.gradle.util.VersionNumber
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.util.environment.Jvm

/**
 * Base specification for integration testing of a gradle plugin.
*/
@CompileStatic
abstract class AbstractIntegTestFixture extends Specification {

    // A sample of gradle versions to be considered in general testing
    static final List<String> TESTED_GRADLE_VERSIONS = ['5.6.4', '6.0', GradleVersion.current().version]
    static final List<String> ANDROID_PLUGIN_VERSIONS = ['3.3.0', '3.4.0', '3.5.3', '3.6.0', '4.0.0'].asImmutable()

    static final String DEFAULT_ANDROID_VERSION = '3.4.0'
    protected static final String ANDROID_VERSION = 'androidVersion'
    static final String LIBA_DIRNAME = 'liba/'
    static final String LIBB_DIRNAME = 'libb/'
    static final String LIBA_PATH = ':liba'
    static final String LIBB_PATH = ':libb'
    static final String ANDROID_MANIFEST_PATH = 'src/main/AndroidManifest.xml'
    static final String BUILD_GRADLE_FILENAME = 'build.gradle'
    private static final String TARGET_ANDROID_VERSION = Jvm.current.java8Compatible ? '25' : '23'
    private static final String DEFAULT_ANDROID_PACKAGE = 'com.monits.staticCodeAnalysis'

    private static final String GRADLE_COMPATIBLE_WITH_AGP_3_3 = '5.6.4'

    @Rule
    final TemporaryFolder testProjectDir = new TemporaryFolder()

    String pluginClasspathString

    @CompileDynamic
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
        ((DefaultGradleRunner) GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments('check', '--stacktrace'))
            // AGP sometimes runs out of metaspace running lint… this seems to fix it
            .withJvmArguments('-Dcom.sun.xml.bind.v2.bytecode.ClassTailor.noOptimize=true')
    }

    TestFile file(String path) {
        File f = new File(testProjectDir.root, path)
        f.parentFile.mkdirs()
        new TestFile(f)
    }

    void goodCode(int numberOfClasses = 1) {
        1.upto(numberOfClasses) {
            file("src/main/java/com/monits/Class${it}.java") <<
                "package com.monits; public class Class${it} { public boolean isFoo(Object arg) { return true; } }"
            file("src/test/java/com/monits/Class${it}Test.java") << """
                |package com.monits;
                |
                |import org.junit.Test;
                |import static org.junit.Assert.assertTrue;
                |
                |public class Class${it}Test extends Object {
                |    @Test
                |    public void isFoo() {
                |        assertTrue("It's not a foo", new Class${it}().isFoo(null));
                |    }
                |}""".stripMargin()
        }
    }

    TestFile writeBuildFile() {
        Map<String, Boolean> configMap = [:]
        configMap.put(toolName(), Boolean.TRUE)
        writeBuildFile(configMap)
    }

    TestFile writeBuildFile(Map<String, Boolean> toolsConfig) {
        buildScriptFile() << """
            |buildscript {
            |    dependencies {
            |        classpath files($pluginClasspathString)
            |    }
            |}
            |
            |repositories {
            |    mavenCentral()
            |}
            |
            |apply plugin: 'java'
            |apply plugin: 'com.monits.staticCodeAnalysis'
            |
            |dependencies {
            |    testCompile 'junit:junit:4.12'
            |}
        """.stripMargin() + staticCodeAnalysisConfig(toolsConfig) as TestFile
    }

    String staticCodeAnalysisConfig(Map<String, Boolean> toolsConfig) {
        """
            |// disable all other checks
            |staticCodeAnalysis {
            |    cpd = ${toolsConfig.get('cpd', false)}
            |    checkstyle = ${toolsConfig.get('checkstyle', false)}
            |    spotbugs = ${toolsConfig.get('spotbugs', false)}
            |    pmd = ${toolsConfig.get('pmd', false)}
            |    androidLint = ${toolsConfig.get('androidLint', false)}
            |}
        """.stripMargin()
    }

    TestFile writeAndroidBuildFile(final String androidVersion = DEFAULT_ANDROID_VERSION) {
        Map<String, Object> configMap = [:]
        configMap.put(toolName(), Boolean.TRUE)
        configMap.put(ANDROID_VERSION, androidVersion)
        writeAndroidBuildFile(configMap)
    }

    @CompileDynamic
    TestFile writeAndroidBuildFile(Map<String, Object> toolsConfig) {
        buildScriptFile() << """
            |buildscript {
            |    dependencies {
            |        classpath 'com.android.tools.build:gradle:' +
            |            '${toolsConfig.get(ANDROID_VERSION, DEFAULT_ANDROID_VERSION)}'
            |        classpath files($pluginClasspathString)
            |    }
            |
            |    repositories {
            |        jcenter()
            |        google()
            |    }
            |}
            |
            |repositories {
            |    jcenter()
            |    google()
            |}
            |
            |apply plugin: 'com.android.library'
            |apply plugin: 'com.monits.staticCodeAnalysis'
            |
            |dependencies {
            |    testCompile 'junit:junit:4.12'
            |}
        """.stripMargin() + staticCodeAnalysisConfig(toolsConfig) +
        """
            |android {
            |    compileSdkVersion ${TARGET_ANDROID_VERSION}
            |}
        """.stripMargin() as TestFile
    }

    TestFile writeAndroidManifest(final String packageName = DEFAULT_ANDROID_PACKAGE) {
        file(ANDROID_MANIFEST_PATH) << """
            |<manifest xmlns:android="http://schemas.android.com/apk/res/android"
            |    package="${packageName}"
            |    android:versionCode="1">
            |</manifest>
        """.stripMargin() as TestFile
    }

    @SuppressWarnings('FactoryMethodName')
    TestFile buildScriptFile() {
        file(BUILD_GRADLE_FILENAME)
    }

    TestFile reportFile(String sourceSet = '') {
        file(reportFileName(sourceSet))
    }

    abstract String reportFileName(String sourceSet)

    abstract String taskName()

    abstract String toolName()

    void setupMultimoduleAndroidProject(final String androidVersion = DEFAULT_ANDROID_VERSION) {
        setupAndroidSubProject(LIBA_DIRNAME, androidVersion)
        setupAndroidSubProject(LIBB_DIRNAME, androidVersion)

        setupSubmoduleContents()
    }

    void setupMixedMultimoduleAndroidProject(final String androidVersion = DEFAULT_ANDROID_VERSION) {
        setupJavaSubProject(LIBA_DIRNAME)
        setupAndroidSubProject(LIBB_DIRNAME, androidVersion)

        setupSubmoduleContents()
    }

    @SuppressWarnings('DuplicateNumberLiteral')
    String gradleVersionForAndroid(final String androidVersion) {
        VersionNumber androidVersionNumber = VersionNumber.parse(androidVersion)

        if (androidVersionNumber.major == 3) {
            if (androidVersionNumber.minor < 3) {
                throw new IllegalArgumentException("Version $androidVersion is unsupported")
            } else if (androidVersionNumber.minor == 3) {
                return GRADLE_COMPATIBLE_WITH_AGP_3_3
            }
        }

        GradleVersion.current().version
    }

    private void setupAndroidSubProject(final String dir, final String androidVersion = DEFAULT_ANDROID_VERSION) {
        writeAndroidBuildFile(androidVersion).renameTo(file(dir + BUILD_GRADLE_FILENAME))
        writeAndroidManifest(DEFAULT_ANDROID_PACKAGE + '.' + dir.replace('/', ''))
            .renameTo(file(dir + ANDROID_MANIFEST_PATH))
        file('src').deleteDir()
    }

    private void setupJavaSubProject(final String dir) {
        writeBuildFile().renameTo(file(dir + BUILD_GRADLE_FILENAME))
    }

    private void setupSubmoduleContents() {
        file(LIBB_DIRNAME + BUILD_GRADLE_FILENAME) << """
            |dependencies {
            |    compile project('${LIBA_PATH}')
            |}
        """.stripMargin()

        file('settings.gradle') << "include '${LIBA_PATH}', '${LIBB_PATH}'"

        file(BUILD_GRADLE_FILENAME).createNewFile() // empty root build.gradle

        file(LIBA_DIRNAME + 'src/main/java/liba/ClassA.java') <<
        'package liba; public class ClassA { public boolean isFoo(Object arg) { return true; } }'
        file(LIBA_DIRNAME + 'src/test/java/liba/ClassATest.java') <<
        'package liba; public class ClassATest { public boolean isFoo(Object arg) { return true; } }'
        file(LIBB_DIRNAME + 'src/main/java/libb/ClassB.java') <<
        'package libb; import liba.ClassA; public class ClassB { public boolean isFoo(Object arg) {' +
            ' ClassA a = new ClassA(); return a.isFoo(arg); } }'
        file(LIBB_DIRNAME + 'src/test/java/libb/ClassBTest.java') <<
        'package libb; public class ClassBTest { public boolean isFoo(Object arg) { return true; } }'
    }
}
