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

import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.util.VersionNumber

/**
 * Utility class to help dealing with the Android Gradle Plugin.
*/
@CompileStatic
final class AndroidHelper {
    private static final String ANDROID_SDK_HOME = 'ANDROID_SDK_HOME'
    private static final String ANDROID_ENABLE_CACHE_PROPERTY = 'android.enableBuildCache'
    private static final String ANDROID_CACHE_LOCATION = 'android.buildCacheDir'
    private static final String ANDROID_GRADLE_VERSION_PROPERTY_NAME = 'androidGradlePluginVersion'
    private static final VersionNumber BUILD_CACHE_ANDROID_GRADLE_VERSION = VersionNumber.parse('2.3.0')
    private static final VersionNumber REPORT_PER_VARIANT_ANDROID_GRADLE_VERSION = VersionNumber.parse('2.0.0')

    /**
     * Retrieves the current plugin version, if available.
     * @param project The project on which to analyze the used plugin version.
     * @return The version of the used plugin, or {@see VersionNumber#UNKNOWN} if not known.
     */
    private static VersionNumber getCurrentVersion(final Project project) {
        Task lintTask = project.tasks.findByName('lint')

        if (!lintTask.hasProperty(ANDROID_GRADLE_VERSION_PROPERTY_NAME)) {
            return VersionNumber.UNKNOWN
        }

        VersionNumber.parse(lintTask.property(ANDROID_GRADLE_VERSION_PROPERTY_NAME) as String)
    }

    /**
     * Checks if the current Android Plugin produces a report per variant or not.
     * @param project The project to analyze.
     * @return True if a report per variant is expected, false otherwise
     */
    public static boolean lintReportPerVariant(final Project project) {
        getCurrentVersion(project) >= REPORT_PER_VARIANT_ANDROID_GRADLE_VERSION
    }

    /**
     * Checks if the current Android build is using the build-cache.
     * Notice this method may return true, but pre-dex caching be disabled due to
     * multidex / proguard / low minSdkVersion. However, exploded-aar caching is still active.
     *
     * @param project The project to analyze
     * @return True if the build is using build-cache, false otherwise
     */
    public static boolean usesBuildCache(final Project project) {
        getCurrentVersion(project) >= BUILD_CACHE_ANDROID_GRADLE_VERSION &&
            (!project.hasProperty(ANDROID_ENABLE_CACHE_PROPERTY) ||
                project.property(ANDROID_ENABLE_CACHE_PROPERTY) == 'true')
    }

    /**
     * Retrieves the location of Android's build-cache directory.
     * @param project The project to analyze.
     * @return The location of Android's build-cache directory.
     */
    public static String getBuildCacheDir(final Project project) {
        if (project.hasProperty(ANDROID_CACHE_LOCATION)) {
            return project.property(ANDROID_CACHE_LOCATION)
        }

        def home = homeDir
        if (home) {
            return home + '/.android/build-cache/'
        }

        null
    }

    /**
     * Retrieves the current Android Home path, or null if unknown.
     * @return The current Android Home.
     */
    public static String getHomeDir() {
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
}
