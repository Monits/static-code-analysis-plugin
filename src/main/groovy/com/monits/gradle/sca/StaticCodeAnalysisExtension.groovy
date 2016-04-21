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

import org.gradle.api.Project

class StaticCodeAnalysisExtension {

    static final String NAME = "staticCodeAnalysis";
    static final String CHECKSTYLE_DEFAULT_RULES = "http://static.monits.com/checkstyle.xml"
    static final String CHECKSTYLE_BACKWARDS_RULES = "http://static.monits.com/checkstyle-6.7.xml"
    static final String PMD_DEFAULT_RULES = "http://static.monits.com/pmd.xml";
    static final String PMD_DEFAULT_ANDROID_RULES = "http://static.monits.com/pmd-android.xml";
    static final String PMD_BACKWARDS_RULES = "http://static.monits.com/pmd-5.1.3.xml";
    private static final String FINDBUGS_DEFAULT_SUPPRESSION_FILTER = "http://static.monits.com/findbugs-exclusions-android.xml"


    boolean ignoreErrors;

    boolean findbugs;
    boolean pmd;
    boolean checkstyle;
    boolean cpd;

    String checkstyleRules;
    List<String> pmdRules;
    String findbugsExclude;

    StaticCodeAnalysisExtension(Project project) {
        ignoreErrors = true;
        findbugs = true;
        pmd = true;
        checkstyle = true;
        cpd = true;
        checkstyleRules = CHECKSTYLE_DEFAULT_RULES;
        findbugsExclude = FINDBUGS_DEFAULT_SUPPRESSION_FILTER
        pmdRules = [ PMD_DEFAULT_RULES, PMD_DEFAULT_ANDROID_RULES ]
    }
}