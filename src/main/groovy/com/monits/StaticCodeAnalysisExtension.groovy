package com.monits

import org.gradle.api.Project

class StaticCodeAnalysisExtension {

    static final String NAME = "staticCodeAnalysis";

    boolean ignoreErrors;

    boolean findbugs;
    boolean pmd;
    boolean checkstyle;
    boolean cpd;

    String checkstyleRules;
    List<String> pmdRules;
    File findbugsExclude;

    StaticCodeAnalysisExtension(Project project) {
        ignoreErrors = true;
        findbugs = true;
        pmd = true;
        checkstyle = true;
        cpd = true;
        checkstyleRules = "http://static.monits.com/checkstyle.xml"
        findbugsExclude = new File("$project.rootProject.projectDir/config/findbugs/excludeFilter.xml");
        pmdRules = [ "http://static.monits.com/pmd.xml", "http://static.monits.com/pmd-android.xml" ]
    }
}