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
package com.monits.gradle.sca.config

import com.monits.gradle.sca.ClasspathAware
import com.monits.gradle.sca.StaticCodeAnalysisExtension
import com.monits.gradle.sca.ToolVersions
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.quality.Pmd
import org.gradle.util.GradleVersion

/**
 * A configurator for PMD tasks.
*/
class PmdConfigurator implements AnalysisConfigurator, ClasspathAware {
    private final static GradleVersion GRADLE_VERSION_PMD_CLASSPATH_SUPPORT = GradleVersion.version('2.8')
    private final static String PMD = 'pmd'

    @SuppressWarnings('UnnecessaryGetter')
    @Override
    void applyConfig(final Project project, final StaticCodeAnalysisExtension extension) {
        // prevent applying it twice
        if (project.tasks.findByName(PMD)) {
            return
        }

        boolean supportsClasspath = GRADLE_VERSION_PMD_CLASSPATH_SUPPORT <= GradleVersion.current()

        project.plugins.apply PMD

        project.pmd {
            toolVersion = ToolVersions.pmdVersion
            ignoreFailures = extension.getIgnoreErrors()
            ruleSets = extension.getPmdRules()
        }

        Task pmdTask = project.task(PMD, type:Pmd) {
            source 'src'
            include '**/*.java'
            exclude '**/gen/**'

            reports {
                xml.enabled = true
                html.enabled = false
            }

            if (supportsClasspath) {
                classpath = project.files() // empty by default, will be populated lazily
            }
        }

        if (supportsClasspath) {
            pmdTask.doFirst {
                /*
                 * For best results, PMD needs ALL classes, including Android's SDK.
                 * We do this now that dependent tasks are done to actually find everything
                 */
                configAndroidClasspath(pmdTask, project)
            }
        }

        project.tasks.check.dependsOn pmdTask
    }

    @Override
    void applyAndroidConfig(final Project project, final StaticCodeAnalysisExtension extension) {
        applyConfig(project, extension) // no difference at all
    }
}
