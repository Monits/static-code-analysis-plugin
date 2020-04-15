/*
 * Copyright 2010-2017 Monits S.A.
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
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.plugins.ExtensionAware

/**
 * Extension to configure the Static code Analysis Plugin.
*/
@CompileStatic
class StaticCodeAnalysisExtension {
    boolean ignoreErrors

    boolean spotbugs
    boolean pmd
    boolean checkstyle
    boolean cpd
    boolean androidLint

    String checkstyleRules
    List<String> pmdRules
    String spotbugsExclude
    String androidLintConfig

    NamedDomainObjectContainer<RulesConfig> sourceSetConfig

    // Needed so we can write sourceSetConfig { test { .... } }
    @SuppressWarnings(['GroovyUnusedDeclaration', 'ConfusingMethodName'])
    NamedDomainObjectContainer<RulesConfig> sourceSetConfig(final Closure<?> config) {
        sourceSetConfig.configure config
    }

    void setFindbugs(final boolean enabled) {
        // TODO : Nag about deprecation
        /*
         * we need to set the property this way, so we get through the Gradle generated proxy
         * and avoid convention mapping from taking place
         */
        setProperty('spotbugs', enabled)
    }

    boolean getFindbugs() {
        // TODO : Nag about deprecation
        getProperty('spotbugs')
    }
}
