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
import org.gradle.api.JavaVersion
import org.gradle.util.GradleVersion

/**
 * Utility class to manage versions of tools being used by the plugin.
*/
@CompileStatic
final class ToolVersions {
    private final static String LATEST_PMD_TOOL_VERSION = '6.22.0'
    private final static String BACKWARDS_PMD_TOOL_VERSION = '5.1.3'
    private final static GradleVersion GRADLE_VERSION_PMD = GradleVersion.version('2.4')

    private final static String LATEST_CHECKSTYLE_VERSION = '8.23'
    private final static String LATEST_CHECKSTYLE_VERSION_JAVA_7 = '6.19'

    private final static String SPOTBUGS_TOOL_VERSION = '4.0.1'
    private final static String SPOTBUGS_MONITS_VERSION = '0.2.0'
    private final static String SB_CONTRIB_VERSION = '7.4.7'

    private final static String TOOL_GRADLE = 'Gradle'
    private final static String TOOL_JRE = 'Java'
    private final static String UPDATE_INSTRUCTIONS = 'Update the used %s version to %s or higher to ' +
        'get better (and faster) analysis results.'

    private ToolVersions() {
        throw new AssertionError("ToolVersions can't be instantiated" as Object)
    }

    static String getPmdVersion() {
        if (GradleVersion.current() < GRADLE_VERSION_PMD) {
            return BACKWARDS_PMD_TOOL_VERSION
        }

        LATEST_PMD_TOOL_VERSION
    }

    static boolean isLatestPmdVersion() {
        pmdVersion == LATEST_PMD_TOOL_VERSION
    }

    static String getPmdUpdateInstructions() {
        // Only gradle version affects used PMD version
        String.format(UPDATE_INSTRUCTIONS, TOOL_GRADLE, GRADLE_VERSION_PMD.version)
    }

    static String getCheckstyleVersion() {
        if (JavaVersion.current() < JavaVersion.VERSION_1_8) {
            return LATEST_CHECKSTYLE_VERSION_JAVA_7
        }

        LATEST_CHECKSTYLE_VERSION
    }

    static boolean isLatestCheckstyleVersion(final boolean ignoreJre = false) {
        checkstyleVersion == LATEST_CHECKSTYLE_VERSION ||
            (ignoreJre && checkstyleVersion == LATEST_CHECKSTYLE_VERSION_JAVA_7)
    }

    static String getCheckstyleUpdateInstructions() {
        String.format(UPDATE_INSTRUCTIONS, TOOL_JRE, JavaVersion.VERSION_1_8.majorVersion)
    }

    @SuppressWarnings('GetterMethodCouldBeProperty')
    static String getSpotbugsVersion() {
        SPOTBUGS_TOOL_VERSION
    }

    @SuppressWarnings('GetterMethodCouldBeProperty')
    static String getSbContribVersion() {
        SB_CONTRIB_VERSION
    }

    @SuppressWarnings('GetterMethodCouldBeProperty')
    static String getMonitsSpotbugsVersion() {
        SPOTBUGS_MONITS_VERSION
    }
}
