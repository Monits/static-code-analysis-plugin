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

import groovy.transform.CompileStatic

import java.util.regex.Matcher
import org.gradle.api.Project
import org.gradle.util.VersionNumber

/**
 * Utility class to help dealing with the Android Gradle Plugin.
 */
@CompileStatic
final class AndroidHelper {
    public static final Set<String> SUPPORTED_PLUGINS = (['com.android.application',
                                           'com.android.library',
                                           'com.android.dynamic-feature'] as Set<String>).asImmutable()

    private static final String ANDROID_SDK_HOME = 'ANDROID_SDK_HOME'
    private static final String ANDROID_ENABLE_CACHE_PROPERTY = 'android.enableBuildCache'
    private static final String ANDROID_CACHE_LOCATION = 'android.buildCacheDir'
    private static final String ANDROID_DEPENDENCY_PATTERN = /com\.android\.tools\.build\/gradle\/([^\/]+)/
    private static final String VERSION_3_5_0 = '3.5.0'

    private static final VersionNumber LINT_HAS_VARIANT_INFO = VersionNumber.parse('1.5.0')
    private static final VersionNumber USES_JAVAC_TASK_OUTPUTS = VersionNumber.parse('3.2.0')
    private static final VersionNumber FLAT_JAVAC_TASK_OUTPUTS = VersionNumber.parse(VERSION_3_5_0)
    private static final VersionNumber GARBAGE_INPUTS = VersionNumber.parse(VERSION_3_5_0)
    private static final VersionNumber STANDALONE_R_JAR = VersionNumber.parse('3.3.0')
    private static final String MAIN_SOURCESET = 'main'
    private static final String DEBUG_SOURCESET = 'debug'

    /**
     * Checks if the current Android build is using the build-cache.
     * Notice this method may return true, but pre-dex caching be disabled due to
     * multidex / proguard / low minSdkVersion. However, exploded-aar caching is still active.
     *
     * @param project The project to analyze
     * @return True if the build is using build-cache, false otherwise
     */
    static boolean usesBuildCache(final Project project) {
        (!project.hasProperty(ANDROID_ENABLE_CACHE_PROPERTY) ||
            project.property(ANDROID_ENABLE_CACHE_PROPERTY) == 'true')
    }

    /**
     * Checks if the current Android build has variant info on the lint task.
     *
     * @param project The project to analyze
     * @return True if the AGP version in use provides variant info on the lint task, false otherwise
     */
    static boolean lintTaskHasVariantInfo(final Project project) {
        getCurrentVersion(project) >= LINT_HAS_VARIANT_INFO
    }

    /**
     * Checks if the current Android build requires lint tasks' inputs to be added
     *
     * @param project The project to analyze
     * @return True if inputs should be added, false otherwise
     */
    static boolean shouldAddLintInputsAndOutputs(final Project project) {
        /*
         * Android 3.5.0 started adding dependency strings such as
         * "annotations.jar (com.google.code.findbugs:annotations:3.0.1)" as inputs, so when caching
         * the task, it would try to fingerprint those inputs / outputs and fail, producing error messages
         * and stacktraces.
         * See https://issuetracker.google.com/issues/141126614
        */
        getCurrentVersion(project) < GARBAGE_INPUTS
    }

    /**
     * Retrieves the location were lint reports are output by AGP.
     *
     * @param project The project to analyze
     * @return The directory in which AGP outputs lint reports
     */
    static String getLintReportDir(final Project project) {
        "${project.buildDir}/reports/"
    }

    static String getCompileOutputDir(final Project project, final String sourceSetName, final String sourceSetPath) {
        VersionNumber currentVersion = getCurrentVersion(project)
        if (currentVersion >= USES_JAVAC_TASK_OUTPUTS) {
            String outputDir = sourceSetName == 'androidTest' ? 'debugAndroidTest' :
                sourceSetName == 'test' ? 'debugUnitTest' :
                    sourceSetName == MAIN_SOURCESET ? DEBUG_SOURCESET : sourceSetName

            if (currentVersion >= FLAT_JAVAC_TASK_OUTPUTS) {
                return project.buildDir.absolutePath +
                    "/intermediates/javac/${outputDir}/classes/"
            }

            return project.buildDir.absolutePath +
                "/intermediates/javac/${outputDir}/compile${outputDir.capitalize()}JavaWithJavac/classes/"
        }

        project.buildDir.absolutePath + '/intermediates/classes/' + sourceSetPath + File.separator
    }

    @SuppressWarnings('ConfusingTernary')
    static String getStandaloneRJarPath(final Project project, final String sourceSetName) {
        VersionNumber currentVersion = getCurrentVersion(project)
        if (currentVersion < STANDALONE_R_JAR) {
            return null
        }

        // main sourceset maps to debug
        String actualSourceSet = sourceSetName != MAIN_SOURCESET ?: DEBUG_SOURCESET

        String intermediatePath = ''
        if (currentVersion.major == 3) {
            if (currentVersion.minor <= 4) {
                intermediatePath = '/generate' + actualSourceSet.capitalize() + 'RFile'
            }
        }

        project.buildDir.absolutePath + '/intermediates/compile_only_not_namespaced_r_class_jar/' +
            actualSourceSet + intermediatePath + '/R.jar'
    }

    /**
     * Retrieves the location of Android's build-cache directory.
     * @param project The project to analyze.
     * @return The location of Android's build-cache directory.
     */
    static String getBuildCacheDir(final Project project) {
        if (project.hasProperty(ANDROID_CACHE_LOCATION)) {
            return project.property(ANDROID_CACHE_LOCATION)
        }

        String home = homeDir
        if (home) {
            return home + '/.android/build-cache/'
        }

        null
    }

    /**
     * Retrieves the current Android Home path, or null if unknown.
     * @return The current Android Home.
     */
    @SuppressWarnings('CouldBeSwitchStatement')
    static String getHomeDir() {
        // Home candidates and order according to http://tools.android.com/tips/lint-custom-rules
        String home = System.getProperty(ANDROID_SDK_HOME)
        if (home == null) {
            home = System.getenv(ANDROID_SDK_HOME)
        }
        if (home == null) {
            home = System.getProperty('user.home')
        }
        if (home == null) {
            home = System.getenv('HOME')
        }
        home
    }

    /**
     * Retrieves the current Android SDK install location
     * @return The current Android SDK install location
     */
    static String getSdkDir() {
        System.getenv('ANDROID_HOME')
    }

    /**
     * Retrieves the current plugin version, if available.
     * @param project The project on which to analyze the used plugin version.
     * @return The version of the used plugin, or {@see VersionNumber#UNKNOWN} if not known.
     */
    // FIXME : Use @Memoized ?
    private static VersionNumber getCurrentVersion(final Project project) {
        File androidDependency = project.buildscript.configurations.getByName('classpath').resolve()
                .find { it =~ ANDROID_DEPENDENCY_PATTERN }
        Matcher matcher = androidDependency =~ ANDROID_DEPENDENCY_PATTERN

        if (!matcher.find()) {
            if (project.parent != null) {
                return getCurrentVersion(project.parent)
            }
            return VersionNumber.UNKNOWN
        }

        VersionNumber.parse(matcher.group(1))
    }
}
