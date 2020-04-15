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
package com.monits.gradle.sca.dsl

import groovy.transform.CompileStatic
import org.gradle.api.NamedDomainObjectContainer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Extension to configure the Static code Analysis Plugin.
*/
@CompileStatic
class StaticCodeAnalysisExtension {
    private static final Logger LOGGER = LoggerFactory.getLogger(StaticCodeAnalysisExtension)

    private static final String SPOTBUGS_PROPERTY_NAME = 'spotbugs'
    private static final String FINDBUGS_PROPERTY_NAME = 'findbugs'

    private static final String SPOTBUGS_EXCLUDE_PROPERTY_NAME = 'spotbugsExclude'
    private static final String FINDBUGS_EXCLUDE_PROPERTY_NAME = 'findbugsExclude'

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
        setDeprecatedProperty(FINDBUGS_PROPERTY_NAME, SPOTBUGS_PROPERTY_NAME, enabled)
    }

    boolean getFindbugs() {
        getDeprecatedProperty(FINDBUGS_PROPERTY_NAME, SPOTBUGS_PROPERTY_NAME)
    }

    void setFindbugsExclude(final String exclude) {
        setDeprecatedProperty(FINDBUGS_EXCLUDE_PROPERTY_NAME, SPOTBUGS_EXCLUDE_PROPERTY_NAME, exclude)
    }

    String getFindbugsExclude() {
        getDeprecatedProperty(FINDBUGS_EXCLUDE_PROPERTY_NAME, SPOTBUGS_EXCLUDE_PROPERTY_NAME)
    }

    private void setDeprecatedProperty(final String usedProperty, final String replacementProperty,
                                       final Object value) {
        nagDeprecatedPropertyUsage(usedProperty, replacementProperty)
        /*
         * we need to set the property this way, so we get through the Gradle generated proxy
         * and avoid convention mapping from taking place
         */
        setProperty(replacementProperty, value)
    }

    private Object getDeprecatedProperty(final String usedProperty, final String replacementProperty) {
        nagDeprecatedPropertyUsage(usedProperty, replacementProperty)
        getProperty(replacementProperty)
    }

    private void nagDeprecatedPropertyUsage(final String usedProperty, final String replacementProperty) {
        LOGGER.warn("Using deprecated '${usedProperty}' property for Static Code Analysis plugin. " +
            "Please update to use '${replacementProperty}', this property will be removed in the 4.0.0 release.")
    }
}
