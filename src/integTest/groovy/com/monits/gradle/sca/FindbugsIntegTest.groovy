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
import com.monits.gradle.sca.io.TestFile
import org.gradle.testkit.runner.BuildResult
import org.gradle.util.GradleVersion
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.FAILED
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.not
import static org.junit.Assert.assertThat

/**
 * Integration test of Findbugs tasks.
 */
class FindbugsIntegTest extends AbstractPluginIntegTestFixture {
    @SuppressWarnings('MethodName')
    @Unroll('Findbugs #findbugsVersion should run when using gradle #version')
    void 'findbugs is run'() {
        given:
        writeBuildFile()
        useEmptySuppressionFilter()
        goodCode()

        when:
        BuildResult result = gradleRunner()
            .withGradleVersion(version)
            .build()

        then:
        if (GradleVersion.version(version) >= GradleVersion.version('2.5')) {
            // Executed task capture is only available in Gradle 2.5+
            result.task(taskName()).outcome == SUCCESS
        }

        // Make sure report exists and was using the expected tool version
        reportFile().exists()
        reportFile().assertContents(containsString("<BugCollection version=\"$findbugsVersion\""))

        // Plugins should be automatically added and enabled
        reportFile().assertContents(containsString('<Plugin id="com.mebigfatguy.fbcontrib" enabled="true"/>'))
        reportFile().assertContents(containsString('<Plugin id="jp.co.worksap.oss.findbugs" enabled="true"/>'))

        where:
        version << ['2.3', '2.4', '2.7', '2.10', GradleVersion.current().version]
        findbugsVersion = ToolVersions.findbugsVersion
    }

    @SuppressWarnings('MethodName')
    void 'findbugs download remote suppression config'() {
        given:
        writeBuildFile()
        goodCode()

        when:
        BuildResult result = gradleRunner()
                .build()

        then:
        result.task(taskName()).outcome == SUCCESS

        // The config must exist
        file('config/findbugs/excludeFilter-main.xml').exists()
        file('config/findbugs/excludeFilter-androidTest.xml').exists()

        // Make sure checkstyle report exists
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
        result.task(':downloadFindbugsExcludeFilterAndroidTest').outcome == FAILED
        assertThat(result.output, containsString('Running in offline mode, but there is no cached version'))
    }

    @SuppressWarnings('MethodName')
    void 'running offline with a cached file passes but warns'() {
        given:
        writeBuildFile()
        writeEmptySuppressionFilter('main')
        writeEmptySuppressionFilter('test')
        writeEmptySuppressionFilter('testDebug')
        writeEmptySuppressionFilter('testRelease')
        writeEmptySuppressionFilter('androidTest')
        writeEmptySuppressionFilter('debug')
        writeEmptySuppressionFilter('release')
        goodCode()

        when:
        BuildResult result = gradleRunner()
                .withArguments('check', '--stacktrace', '--offline')
                .build()

        then:
        result.task(':downloadFindbugsExcludeFilterAndroidTest').outcome == SUCCESS
        result.task(':downloadFindbugsExcludeFilterMain').outcome == SUCCESS
        result.task(':downloadFindbugsExcludeFilterDebug').outcome == SUCCESS
        result.task(':downloadFindbugsExcludeFilterRelease').outcome == SUCCESS
        result.task(':downloadFindbugsExcludeFilterTest').outcome == SUCCESS
        result.task(':downloadFindbugsExcludeFilterTestDebug').outcome == SUCCESS
        result.task(':downloadFindbugsExcludeFilterTestRelease').outcome == SUCCESS
        assertThat(result.output, containsString('Running in offline mode. Using a possibly outdated version of'))
    }

    @SuppressWarnings(['MethodName', 'LineLength'])
    void 'Findbugs-related annotations are available'() {
        given:
        writeBuildFile()
        writeEmptySuppressionFilter()
        file('src/main/java/com/monits/ClassA.java') << '''
            package com.monits;

            import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
            import javax.annotation.Nonnull;

            @SuppressFBWarnings(value = "MISSING_FIELD_IN_TO_STRING", justification = "doesn't provide meaningful information")
            public class ClassA {
                public boolean isFoo(@Nonnull Object arg) {
                    return true;
                }
            }
        '''

        when:
        BuildResult result = gradleRunner()
                .build()

        then:
        result.task(taskName()).outcome == SUCCESS

        // The report must exist, and not complain on missing classes from liba
        reportFile().exists()
        reportFile().assertContents(containsString('<Errors errors="0" missingClasses="0">'))
    }

    @SuppressWarnings('MethodName')
    @Unroll('Android classes are available when using android gradle plugin #androidVersion and gradle #gradleVersion')
    void 'Android generated classes are available'() {
        given:
        writeBuildFile()
        writeEmptySuppressionFilter()
        file('src/main/res/values/strings.xml') <<
            '''<?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <string name="greeting">Hey there!</string>
                </resources>
            '''
        file('src/main/java/com/monits/ClassA.java') << '''
            package com.monits;

            import com.monits.staticCodeAnalysis.R;

            public class ClassA {
                public boolean isFoo() {
                    return R.string.greeting == 1;
                }
            }
        '''

        when:
        BuildResult result = gradleRunner()
                .build()

        then:
        result.task(taskName()).outcome == SUCCESS

        // The report must exist, and not complain on missing classes from liba
        reportFile().exists()
        reportFile().assertContents(containsString('<Errors errors="0" missingClasses="0">'))
    }

    @Unroll('Android classes are available when using android gradle plugin #androidVersion and gradle #gradleVersion')
    @SuppressWarnings('MethodName')
    void 'Android SDK classes are available'() {
        given:
        writeBuildFile('findbugs':true, androidVersion:androidVersion)
        writeEmptySuppressionFilter()
        file('src/main/java/com/monits/ClassA.java') << '''
            package com.monits;

            import android.view.View;

            public class ClassA {
                public boolean isFoo() {
                    return new View(null).callOnClick();
                }
            }
        '''

        when:
        BuildResult result = gradleRunner()
                .withGradleVersion(gradleVersion)
                .build()

        then:
        if (GradleVersion.version(gradleVersion) >= GradleVersion.version('2.5')) {
            result.task(taskName()).outcome == SUCCESS
        }

        // The report must exist, and not complain on missing classes from liba
        reportFile().exists()
        reportFile().assertContents(containsString('<Errors errors="0" missingClasses="0">'))

        where:
        // mockable android jar is available since 1.1.0
        androidVersion << ['1.1.3', '1.2.3', '1.3.1', '1.5.0', '2.0.0', '2.1.0']
        gradleVersion = androidVersion < '1.5.0' ? '2.9' : GradleVersion.current().version
    }

    @SuppressWarnings(['MethodName', 'LineLength'])
    void 'multimodule project has all classes'() {
        given:
        writeBuildFile().renameTo(file('liba/build.gradle'))
        file('src/main/AndroidManifest.xml').renameTo(file('liba/src/main/AndroidManifest.xml'))
        file('src').deleteDir()

        writeBuildFile().renameTo(file('libb/build.gradle'))
        file('src/main/AndroidManifest.xml').renameTo(file('libb/src/main/AndroidManifest.xml'))
        file('src').deleteDir()

        file('libb/build.gradle') << '''
            dependencies {
                compile project(':liba')
            }
        '''

        file('settings.gradle') << '''
            include ':liba', ':libb'
        '''
        file('build.gradle') // empty root build.gradle

        file('liba/src/main/java/liba/ClassA.java') <<
                'package liba; public class ClassA { public boolean isFoo(Object arg) { return true; } }'
        file('libb/src/main/java/libb/ClassB.java') <<
                'package libb; import liba.ClassA; public class ClassB { public boolean isFoo(Object arg) { ClassA a = new ClassA(); return a.isFoo(arg); } }'

        when:
        BuildResult result = gradleRunner()
                .build()

        then:
        result.task(':libb' + taskName()).outcome == SUCCESS

        // The report must exist, and not complain on missing classes from liba
        TestFile finbugsReport = file('libb/' + reportFileName(null))
        finbugsReport.exists()
        finbugsReport.assertContents(not(containsString('<MissingClass>liba.ClassA</MissingClass>')))

        // make sure nothing is reported
        finbugsReport.assertContents(containsString('<Errors errors="0" missingClasses="0">'))
    }

    @Override
    String reportFileName(final String sourceSet) {
        "build/reports/findbugs/findbugs${sourceSet ? "-${sourceSet}" : '-main'}.xml"
    }

    @Override
    String taskName() {
        ':findbugs'
    }

    @Override
    String toolName() {
        'findbugs'
    }

    void useEmptySuppressionFilter() {
        writeEmptySuppressionFilter()

        buildScriptFile() << '''
            staticCodeAnalysis {
                findbugsExclude = 'config/findbugs/excludeFilter.xml'
            }
        '''
    }

    TestFile writeEmptySuppressionFilter(final String sourceSet = null) {
        file("config/findbugs/excludeFilter${sourceSet ? "-${sourceSet}" : ''}.xml") << '''
            <FindBugsFilter>
            </FindBugsFilter>
        ''' as TestFile
    }

    @Override
    TestFile writeBuildFile(toolsConfig) {
        // FIXME : Right now findbugs works only on Android projects
        writeAndroidManifest()

        buildScriptFile() << """
            buildscript {
                dependencies {
                    classpath 'com.android.tools.build:gradle:${toolsConfig.get('androidVersion', '2.1.0')}'
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

                lintOptions {
                    abortOnError false
                }
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
