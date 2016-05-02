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

import org.gradle.api.NamedDomainObjectContainer

/**
 * Extension to configure the Static code Analysis Plugin.
*/
class StaticCodeAnalysisExtension {
    boolean ignoreErrors

    boolean findbugs
    boolean pmd
    boolean checkstyle
    boolean cpd

    String checkstyleRules
    List<String> pmdRules
    String findbugsExclude

    NamedDomainObjectContainer<RulesConfig> sourceSetConfig
}
