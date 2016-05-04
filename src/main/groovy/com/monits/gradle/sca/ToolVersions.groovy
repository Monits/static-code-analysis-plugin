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

import org.gradle.util.GradleVersion

/**
 * Utility class to manage versions of tools being used by the plugin.
*/
final class ToolVersions {
    private final static String LATEST_PMD_TOOL_VERSION = '5.4.1'
    private final static String BACKWARDS_PMD_TOOL_VERSION = '5.1.3'
    private final static GradleVersion GRADLE_VERSION_PMD = GradleVersion.version('2.4')

    private final static String LATEST_CHECKSTYLE_VERSION = '6.18'
    private final static String BACKWARDS_CHECKSTYLE_VERSION = '6.7'
    private final static GradleVersion GRADLE_VERSION_CHECKSTYLE = GradleVersion.version('2.7')

    private final static String FINDBUGS_TOOL_VERSION = '3.0.1'
    private final static String FINDBUGS_MONITS_VERSION = '0.2.0-SNAPSHOT'
    private final static String FB_CONTRIB_VERSION = '6.6.1'

    private ToolVersions() {
        throw new AssertionError("ToolVersions can't be instantiated")
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

    static String getCheckstyleVersion() {
        if (GradleVersion.current() < GRADLE_VERSION_CHECKSTYLE) {
            return BACKWARDS_CHECKSTYLE_VERSION
        }

        LATEST_CHECKSTYLE_VERSION
    }

    static boolean  isLatestCheckstyleVersion() {
        checkstyleVersion == LATEST_CHECKSTYLE_VERSION
    }

    @SuppressWarnings('GetterMethodCouldBeProperty')
    static String getFindbugsVersion() {
        FINDBUGS_TOOL_VERSION
    }

    @SuppressWarnings('GetterMethodCouldBeProperty')
    static String getFbContribVersion() {
        FB_CONTRIB_VERSION
    }

    @SuppressWarnings('GetterMethodCouldBeProperty')
    static String getMonitsFindbugsVersion() {
        FINDBUGS_MONITS_VERSION
    }
}