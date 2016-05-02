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

import org.gradle.api.Named

/**
 * Rules configurations for Static Code Analysis.
*/
class RulesConfig implements Named {
    String name

    String checkstyleRules
    List<String> pmdRules
    String findbugsExclude

    private final StaticCodeAnalysisExtension extension

    RulesConfig(final String name, final StaticCodeAnalysisExtension extension) {
        this.name = name
        this.extension = extension
    }

    // Manually default to the value of the extension. We do it here so we evaluate lazily on demand
    @SuppressWarnings('UnnecessaryGetter')
    String getCheckstyleRules() {
        checkstyleRules ?: extension.getCheckstyleRules()
    }

    @SuppressWarnings('UnnecessaryGetter')
    List<String> getPmdRules() {
        pmdRules ?: extension.getPmdRules()
    }

    @SuppressWarnings('UnnecessaryGetter')
    String getFindbugsExclude() {
        findbugsExclude ?: extension.getFindbugsExclude()
    }
}
