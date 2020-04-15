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
package com.monits.gradle.sca.config

import com.monits.gradle.sca.StaticCodeAnalysisExtension
import com.monits.gradle.sca.ToolVersions
import com.monits.gradle.sca.task.CPDTask
import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileTree
import org.gradle.util.VersionNumber

/**
 * A configurator for CPD tasks.
 */
@CompileStatic
class CpdConfigurator implements AnalysisConfigurator {
    private static final String CPD = 'cpd'

    @SuppressWarnings('UnnecessaryGetter')
    @Override
    void applyConfig(final Project project, final StaticCodeAnalysisExtension extension) {
        // prevent applying it twice
        if (project.tasks.findByName(CPD)) {
            return
        }

        // Setup configuration
        addConfigurations(project)
        configureDefaultDependencies(project)

        Task cpdTask = project.task(CPD, type:CPDTask) { CPDTask it ->
            it.ignoreFailures = extension.getIgnoreErrors()

            it.ignoreLiterals = true
            it.ignoreIdentifiers = true

            FileTree srcDir = project.fileTree("$project.projectDir/src/")
            srcDir.include '**/*.java'
            srcDir.exclude '**/gen/**'

            it.inputFiles = srcDir
            it.outputFile = new File("$project.buildDir/reports/pmd/cpd.xml")
        }

        project.tasks.findByName('check').dependsOn cpdTask
    }

    @Override
    void applyAndroidConfig(final Project project, final StaticCodeAnalysisExtension extension) {
        applyConfig(project, extension) // no difference at all
    }

    // FIXME : This is copy pasted from AbstractCodeQualityPlugin... it shouldn't
    @SuppressWarnings('DuplicateStringLiteral')
    private static void addConfigurations(final Project project) {
        project.configurations.create(CPD).with { Configuration c ->
            c.with {
                visible = false
                transitive = true
                description = 'The cpd libraries to be used for this project.'
                // Don't need these things, they're provided by the runtime
                exclude group:'ant', module:'ant'
                exclude group:'org.apache.ant', module:'ant'
                exclude group:'org.apache.ant', module:'ant-launcher'
                exclude group:'org.slf4j', module:'slf4j-api'
                exclude group:'org.slf4j', module:'jcl-over-slf4j'
                exclude group:'org.slf4j', module:'log4j-over-slf4j'
                exclude group:'commons-logging', module:'commons-logging'
                exclude group:'log4j', module:'log4j'
            }
        }
    }

    private static void configureDefaultDependencies(final Project project) {
        Configuration config = project.configurations.getByName(CPD)
        config.incoming.beforeResolve {
            VersionNumber version = VersionNumber.parse(ToolVersions.pmdVersion)
            String dependency = calculateDefaultDependencyNotation(version)
            config.dependencies.add(project.dependencies.create(dependency))
        }
    }

    private static String calculateDefaultDependencyNotation(final VersionNumber toolVersion) {
        if (toolVersion < VersionNumber.version(5)) {
            return "pmd:pmd:$toolVersion"
        } else if (toolVersion < VersionNumber.parse('5.2.0')) {
            return "net.sourceforge.pmd:pmd:$toolVersion"
        }
        "net.sourceforge.pmd:pmd-java:$toolVersion"
    }
}
