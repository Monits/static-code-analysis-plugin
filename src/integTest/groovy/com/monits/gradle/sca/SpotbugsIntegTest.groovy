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
package com.monits.gradle.sca

import com.monits.gradle.sca.fixture.AbstractPerSourceSetPluginIntegTestFixture
import com.monits.gradle.sca.io.TestFile
import groovy.transform.CompileDynamic
import org.gradle.testkit.runner.BuildResult
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.FAILED
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.is
import static org.hamcrest.core.IsNot.not
import static org.hamcrest.text.MatchesPattern.matchesPattern
import static org.junit.Assert.assertThat

/**
 * Integration test of Spotbugs tasks.
 */
@CompileDynamic
class SpotbugsIntegTest extends AbstractPerSourceSetPluginIntegTestFixture {

    @SuppressWarnings('MethodName')
    @Unroll('Spotbugs #spotbugsVersion should run when using gradle #version')
    void 'spotbugs is run'() {
        given:
        writeBuildFile()
        useEmptySuppressionFilter()
        goodCode()

        when:
        BuildResult result = gradleRunner()
            .withGradleVersion(version)
            .build()

        then:
        result.task(taskName()).outcome == SUCCESS

        // Make sure report exists and was using the expected tool version
        reportFile().exists()
        reportFile().assertContents(containsString("<BugCollection version=\"$spotbugsVersion\""))

        // Plugins should be automatically added and enabled
        reportFile().assertContents(containsString('<Plugin id="com.mebigfatguy.fbcontrib" enabled="true"/>'))
        reportFile().assertContents(containsString('<Plugin id="jp.co.worksap.oss.findbugs" enabled="true"/>'))

        where:
        version << TESTED_GRADLE_VERSIONS
        spotbugsVersion = ToolVersions.spotbugsVersion
    }

    @SuppressWarnings('MethodName')
    void 'spotbugs downloads remote suppression config'() {
        given:
        writeBuildFile()
        // setup a remote config
        buildScriptFile() << '''
            |staticCodeAnalysis {
            |    spotbugsExclude = 'https://raw.githubusercontent.com/Monits/static-code-analysis-plugin/' +
            |        'staging/defaults/spotbugs/spotbugs-exclusions-android.xml'
            |}
        '''.stripMargin()
        goodCode()

        when:
        BuildResult result = gradleRunner()
                .build()

        then:
        result.task(taskName()).outcome == SUCCESS

        // The config must exist
        file('config/spotbugs/excludeFilter-main.xml').exists()
        file('config/spotbugs/excludeFilter-test.xml').exists()

        // Make sure spotbugs report exists
        reportFile().exists()
    }

    @SuppressWarnings('MethodName')
    void 'running offline fails download'() {
        given:
        writeBuildFile()
        // setup a remote config
        buildScriptFile() << '''
            |staticCodeAnalysis {
            |    spotbugsExclude = 'https://raw.githubusercontent.com/Monits/static-code-analysis-plugin/' +
            |        'staging/defaults/spotbugs/spotbugs-exclusions-android.xml'
            |}
        '''.stripMargin()
        goodCode()

        when:
        BuildResult result = gradleRunner()
                .withArguments('check', '--stacktrace', '--offline')
                .buildAndFail()

        then:
        result.task(':downloadSpotbugsExcludeFilterMain').outcome == FAILED
        assertThat(result.output, containsString('Running in offline mode, but there is no cached version'))
    }

    @SuppressWarnings('MethodName')
    void 'running offline with a cached file passes but warns'() {
        given:
        writeBuildFile()
        // setup a remote config
        buildScriptFile() << '''
            |staticCodeAnalysis {
            |    spotbugsExclude = 'https://raw.githubusercontent.com/Monits/static-code-analysis-plugin/' +
            |        'staging/defaults/spotbugs/spotbugs-exclusions-android.xml'
            |}
        '''.stripMargin()
        writeEmptySuppressionFilter('main')
        writeEmptySuppressionFilter('test')
        goodCode()

        when:
        BuildResult result = gradleRunner()
                .withArguments('check', '--stacktrace', '--offline')
                .build()

        then:
        result.task(':downloadSpotbugsExcludeFilterMain').outcome == SUCCESS
        result.task(':downloadSpotbugsExcludeFilterTest').outcome == SUCCESS
        assertThat(result.output, containsString('Running in offline mode. Using a possibly outdated version of'))
    }

    @SuppressWarnings(['MethodName', 'LineLength'])
    void 'Spotbugs-related annotations are available'() {
        given:
        writeBuildFile()
        useEmptySuppressionFilter()
        //noinspection LongLine
        file('src/main/java/com/monits/ClassA.java') << '''
            |package com.monits;
            |
            |import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
            |
            |@SuppressFBWarnings(value = "MISSING_FIELD_IN_TO_STRING", justification = "doesn't provide meaningful information")
            |public class ClassA {
            |    public boolean isFoo(Object arg) {
            |        return true;
            |    }
            |}
        '''.stripMargin()

        when:
        BuildResult result = gradleRunner()
                .build()

        then:
        result.task(taskName()).outcome == SUCCESS

        // The report must exist, and not complain on missing classes from liba
        reportFile().exists()
        reportFile().assertContents(containsString('<Errors errors="0" missingClasses="0">'))
    }

    @Unroll('Android generated classes are available when using android gradle plugin #androidVersion')
    @SuppressWarnings('MethodName')
    void 'Android generated classes are available'() {
        given:
        writeAndroidBuildFile(androidVersion)
        writeAndroidManifest()
        useEmptySuppressionFilter()
        file('src/main/res/values/strings.xml') << '''\
            |<?xml version="1.0" encoding="utf-8"?>
            |<resources>
            |    <string name="greeting">Hey there!</string>
            |</resources>
        '''.stripMargin()
        file('src/main/java/com/monits/ClassA.java') << '''
            |package com.monits;
            |
            |import com.monits.staticCodeAnalysis.R;
            |
            |public class ClassA {
            |    public boolean isFoo() {
            |        return R.string.greeting == 1;
            |    }
            |}
        '''.stripMargin()

        when:
        BuildResult result = gradleRunner()
                .withGradleVersion(gradleVersion)
                .build()

        then:
        result.task(taskName()).outcome == SUCCESS

        // The report must exist, and not complain on missing classes from liba
        reportFile().exists()
        reportFile().assertContents(containsString('<Errors errors="0" missingClasses="0">'))

        where:
        androidVersion << ANDROID_PLUGIN_VERSIONS
        gradleVersion = gradleVersionForAndroid(androidVersion)
    }

    @Unroll('Android classes are available when using android gradle plugin #androidVersion and gradle #gradleVersion')
    @SuppressWarnings('MethodName')
    void 'Android SDK classes are available'() {
        given:
        writeAndroidBuildFile(androidVersion)
        writeAndroidManifest()
        useEmptySuppressionFilter()
        writeDummyAndroidClass('src/main/java', 'ClassA')
        writeDummyAndroidClass('src/test/java', 'ClassATest')
        writeDummyAndroidClass('src/androidTest/java', 'ClassAAndroidTest')

        when:
        BuildResult result = gradleRunner()
                .withGradleVersion(gradleVersion)
                .build()

        then:
        result.task(taskName()).outcome == SUCCESS

        // The report must exist, and not complain on missing classes (Android SDK, R, BuildConfig)
        ['main', 'test', 'androidTest'].each { String sourceSet ->
            result.task(taskName(sourceSet)).outcome == SUCCESS

            reportFile(sourceSet).exists()
            reportFile(sourceSet).assertContents(containsString('<Errors errors="0" missingClasses="0">'))
        }

        where:
        androidVersion << AndroidLintIntegTest.ANDROID_PLUGIN_VERSIONS
        gradleVersion = gradleVersionForAndroid(androidVersion)
    }

    @Unroll('Android generated classes are not analyzed when using AGP #androidVersion and gradle #gradleVersion')
    @SuppressWarnings('MethodName')
    void 'Android generated classes are not analyzed'() {
        given:
        writeAndroidBuildFile(androidVersion)
        writeAndroidManifest()
        useEmptySuppressionFilter()
        writeDummyAndroidClass('src/main/java', 'ClassA')
        writeDummyAndroidClass('src/test/java', 'ClassATest')
        writeDummyAndroidClass('src/androidTest/java', 'ClassAAndroidTest')

        when:
        BuildResult result = gradleRunner()
            .withGradleVersion(gradleVersion)
            .build()

        then:
        result.task(taskName()).outcome == SUCCESS

        // The report must exist, and not complain on missing classes (Android SDK, R, BuildConfig)
        ['main', 'test', 'androidTest'].each { String sourceSet ->
            result.task(taskName(sourceSet)).outcome == SUCCESS

            reportFile(sourceSet).exists()
            reportFile(sourceSet).assertContents(
                not(matchesPattern('(?s).*<FileStats path="[^"]+\\/R\\.java".*')))
            reportFile(sourceSet).assertContents(
                not(matchesPattern('(?s).*<FileStats path="[^"]+\\/BuildConfig\\.java".*')))
        }

        where:
        androidVersion << AndroidLintIntegTest.ANDROID_PLUGIN_VERSIONS
        gradleVersion = gradleVersionForAndroid(androidVersion)
    }

    @SuppressWarnings(['MethodName', 'LineLength', 'LongLine'])
    @Unroll('multimodule classes are available when using android gradle plugin #androidVersion and gradle #gradleVersion')
    void 'multimodule classes are available'() {
        given:
        setupMultimoduleAndroidProject(androidVersion)

        when:
        BuildResult result = gradleRunner()
                .withGradleVersion(gradleVersion)
                .build()

        then:
        result.task(':libb' + taskName()).outcome == SUCCESS

        // The report must exist, and not complain on missing classes from liba
        TestFile spotbugsReport = file('libb/' + reportFileName(null))
        spotbugsReport.exists()
        spotbugsReport.assertContents(not(containsString('<MissingClass>liba.ClassA</MissingClass>')))

        // make sure nothing is reported
        spotbugsReport.assertContents(containsString('<Errors errors="0" missingClasses="0">'))

        where:
        androidVersion << AndroidLintIntegTest.ANDROID_PLUGIN_VERSIONS
        gradleVersion = gradleVersionForAndroid(androidVersion)
    }

    @SuppressWarnings(['MethodName', 'LineLength', 'LongLine'])
    @Unroll('mixed multimodule classes are available when using android gradle plugin #androidVersion and gradle #gradleVersion')
    void 'mixed multimodule classes are available'() {
        given:
        setupMixedMultimoduleAndroidProject(androidVersion)

        when:
        BuildResult result = gradleRunner()
            .withGradleVersion(gradleVersion)
            .build()

        then:
        result.task(':libb' + taskName()).outcome == SUCCESS

        // The report must exist, and not complain on missing classes from liba
        TestFile finbugsReport = file('libb/' + reportFileName(null))
        finbugsReport.exists()
        finbugsReport.assertContents(not(containsString('<MissingClass>liba.ClassA</MissingClass>')))

        // make sure nothing is reported
        finbugsReport.assertContents(containsString('<Errors errors="0" missingClasses="0">'))

        where:
        androidVersion << AndroidLintIntegTest.ANDROID_PLUGIN_VERSIONS
        gradleVersion = gradleVersionForAndroid(androidVersion)
    }

    @Unroll('AAR classes are available when using android gradle plugin #androidVersion and gradle #gradleVersion')
    @SuppressWarnings('MethodName')
    void 'Dependency AAR classes are available'() {
        given:
        writeAndroidBuildFile(androidVersion) << '''
            |dependencies {
            |    compile 'io.card:android-sdk:5.5.1'
            |}
            |
            |android {
            |    defaultConfig {
            |        minSdkVersion 16
            |    }
            |}
        '''.stripMargin()
        writeAndroidManifest()
        useEmptySuppressionFilter()
        file('src/main/java/com/monits/ClassA.java') << '''
            |package com.monits;
            |
            |import io.card.payment.CardIOActivity;
            |
            |public class ClassA {
            |    public boolean isFoo() {
            |        return new CardIOActivity().isFinishing();
            |    }
            |}
        '''.stripMargin()

        when:
        BuildResult result = gradleRunner()
            .withGradleVersion(gradleVersion)
            .build()

        then:
        result.task(taskName()).outcome == SUCCESS

        // The report must exist, and not complain on missing classes from liba
        reportFile().exists()
        reportFile().assertContents(containsString('<Errors errors="0" missingClasses="0">'))

        where:
        androidVersion << AndroidLintIntegTest.ANDROID_PLUGIN_VERSIONS
        gradleVersion = gradleVersionForAndroid(androidVersion)
    }

    @SuppressWarnings('MethodName')
    void 'dsl allows to override rules per sourceset'() {
        given:
        writeBuildFile() << '''
            |staticCodeAnalysis {
            |    sourceSetConfig {
            |        test {
            |            spotbugsExclude = 'test-spotbugsExclude.xml'
            |        }
            |    }
            |}
        '''.stripMargin()
        file('test-spotbugsExclude.xml') << '''
            |<FindBugsFilter>
            |    <Match>
            |        <Or>
            |            <Bug pattern="UNKNOWN_NULLNESS_OF_PARAMETER" />
            |        </Or>
            |    </Match>
            |</FindBugsFilter>
        '''.stripMargin()
        goodCode()

        when:
        BuildResult result = gradleRunner()
                .build()

        then:
        result.task(taskName()).outcome == SUCCESS

        // Make sure spotbugs reports exist
        reportFile().exists()
        reportFile(TEST_SOURCESET).exists()

        // But results should differ in spite of being very similar code
        reportFile().assertContents(containsString('<BugInstance type="UNKNOWN_NULLNESS_OF_PARAMETER"'))
        reportFile(TEST_SOURCESET)
                .assertContents(not(containsString('<BugInstance type="UNKNOWN_NULLNESS_OF_PARAMETER"')))
    }

    @SuppressWarnings('MethodName')
    void 'reports include just classes from their sourcesets'() {
        given:
        writeAndroidBuildFile(DEFAULT_ANDROID_VERSION)
        writeAndroidManifest()
        useEmptySuppressionFilter()
        goodCode()

        when:
        BuildResult result = gradleRunner()
                .withGradleVersion(gradleVersionForAndroid(DEFAULT_ANDROID_VERSION))
                .build()

        then:
        result.task(taskName()).outcome == SUCCESS

        // The report must exist, analyzed classes must match sourcesets, and not complain of missing classes
        reportFile().exists()
        reportFile()
            .assertContents(containsString('<FileStats path="com/monits/Class1.java" '))
            .assertContents(not(containsString('<FileStats path="com/monits/Class1Test.java" ')))
            .assertContents(containsString('<Errors errors="0" missingClasses="0">'))

        reportFile(TEST_SOURCESET).exists()
        reportFile(TEST_SOURCESET)
            .assertContents(not(containsString('<FileStats path="com/monits/Class1.java" ')))
            .assertContents(containsString('<FileStats path="com/monits/Class1Test.java" '))
            .assertContents(containsString('<Errors errors="0" missingClasses="0">'))
    }

    @SuppressWarnings('MethodName')
    void 'Android project has android suppressions'() {
        given:
        writeAndroidBuildFile(DEFAULT_ANDROID_VERSION)
        writeAndroidManifest()
        goodCode()

        when:
        BuildResult result = gradleRunner()
                .withGradleVersion(gradleVersionForAndroid(DEFAULT_ANDROID_VERSION))
                .build()

        then:
        result.task(taskName()).outcome == SUCCESS

        // Make sure spotbugs suppression file exists and has the proper version
        suppressionFilter('main').exists()
        suppressionFilter('main').assertContents(containsString('<Source name="~.*Activity.java"/>'))
        suppressionFilter('test').exists()
        suppressionFilter('test').assertContents(containsString('<Source name="~.*Activity.java"/>'))
    }

    @SuppressWarnings('MethodName')
    void 'Java project has Java suppressions'() {
        given:
        writeBuildFile()
        goodCode()

        when:
        BuildResult result = gradleRunner()
                .build()

        then:
        result.task(taskName()).outcome == SUCCESS

        // Make sure spotbugs suppression file exists and has the proper version
        suppressionFilter('main').exists()
        suppressionFilter('main').assertContents(not(containsString('<Source name="~.*Activity.java"/>')))
        suppressionFilter('test').exists()
        suppressionFilter('test').assertContents(not(containsString('<Source name="~.*Activity.java"/>')))
    }

    @SuppressWarnings(['MethodName', 'LineLength'])
    void 'old DSL properties still work'() {
        given:
        writeBuildFile([:]) // don't enable spotbugs by default
        buildScriptFile() << '''
            |staticCodeAnalysis {
            |   findbugs = true
            |   findbugsExclude = 'customExclude.xml'
            |   sourceSetConfig {
            |       main {
            |           findbugsExclude = 'customExcludeMain.xml'
            |       }
            |   }
            |}
            |
            |afterEvaluate {
            |    if (staticCodeAnalysis.spotbugs) {
            |        println 'Spotbugs is enabled'
            |    }
            |
            |    Task spotbugsTask = project.tasks.getByPath(':spotbugsMain')
            |    spotbugsTask.onlyIf {
            |        println "Spotbugs main exclude is '" + it.excludeFilter.asFile.get().name + "'"
            |        false // don't really run the task
            |    }
            |
            |    Task spotbugsTestTask = project.tasks.getByPath(':spotbugsTest')
            |    spotbugsTestTask.onlyIf {
            |        println "Spotbugs test exclude is '" + it.excludeFilter.asFile.get().name + "'"
            |        false // don't really run the task
            |    }
            |}
        '''.stripMargin()
        goodCode()

        when:
        BuildResult result = gradleRunner()
            .build()

        then:
        // The tasks must be configured
        assertThat('Spotbugs is not enabled',
            (result.output =~ /Spotbugs is enabled/) as boolean, is(true))
        assertThat('Per-sourceset exclude is not set',
            (result.output =~ /Spotbugs main exclude is 'customExcludeMain.xml'/) as boolean, is(true))
        assertThat('General exclude is not set',
            (result.output =~ /Spotbugs test exclude is 'customExclude.xml'/) as boolean, is(true))

        // Deprecation warnings must be present
        assertThat('Findbugs deprecation warning not present',
            (result.output =~ /Using deprecated 'findbugs' property for Static Code Analysis plugin./) as boolean,
            is(true))
        assertThat('FindbugsExclude deprecation warning not present',
            (result.output =~ /Using deprecated 'findbugsExclude' property for Static Code Analysis plugin./) as boolean,
            is(true))
    }

    @SuppressWarnings('MethodName')
    @Unroll('Old #findbugsTask task should still work')
    void 'old tasks still work'() {
        given:
        writeBuildFile()
        buildScriptFile()
        goodCode()

        when:
        BuildResult result = gradleRunner()
            .withArguments(findbugsTask, '--stacktrace')
            .build()

        then:
        String equivalentSpotbugsTask = findbugsTask.replace('findbugs', 'spotbugs')

        // The equivalent Spotbugs task must have run
        result.task(':' + equivalentSpotbugsTask).outcome == SUCCESS

        // Deprecation warnings must be present
        String deprecationMsg = "Using deprecated ':${findbugsTask}' task. " +
            "Please update to use ':${equivalentSpotbugsTask}' instead"
        assertThat('Findbugs deprecation warning not present',
            (result.output =~ deprecationMsg) as boolean, is(true))

        where:
        findbugsTask << ['findbugsMain', 'findbugsTest', 'findbugs']
    }

    @Override
    String reportFileName(final String sourceSet) {
        "build/reports/spotbugs/spotbugs${sourceSet ? "-${sourceSet}" : '-main'}.xml"
    }

    @Override
    String taskName() {
        ':spotbugs'
    }

    @Override
    String toolName() {
        'spotbugs'
    }

    @SuppressWarnings('GStringExpressionWithinString')
    void useEmptySuppressionFilter() {
        writeEmptySuppressionFilter()

        buildScriptFile() << '''
            |staticCodeAnalysis {
            |    spotbugsExclude = "${project.rootDir}/config/spotbugs/excludeFilter.xml"
            |}
        '''.stripMargin()
    }

    TestFile writeEmptySuppressionFilter(final String sourceSet = null) {
        suppressionFilter(sourceSet) << '''
            |<FindBugsFilter>
            |</FindBugsFilter>
        '''.stripMargin() as TestFile
    }

    TestFile suppressionFilter(final String sourceSet = null) {
        file("config/spotbugs/excludeFilter${sourceSet ? "-${sourceSet}" : ''}.xml")
    }

    TestFile writeDummyAndroidClass(final String sourceDir, final String className) {
        file("${sourceDir}/com/monits/${className}.java") << """
            |package com.monits;
            |
            |import android.view.View;
            |import android.util.Log;
            |import com.monits.staticCodeAnalysis.R;
            |import com.monits.staticCodeAnalysis.BuildConfig;
            |
            |public class ${className} {
            |    public boolean isFoo() {
            |        Log.d("${className}", Integer.toString(R.string.foo));
            |        Log.d("${className}", BuildConfig.foo);
            |        return new View(null).callOnClick();
            |    }
            |}
        """.stripMargin()
    }
}
