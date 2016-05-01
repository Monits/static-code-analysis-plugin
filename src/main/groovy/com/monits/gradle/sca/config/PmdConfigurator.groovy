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
import org.gradle.api.tasks.SourceSet
import org.gradle.util.GUtil
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
        project.plugins.apply PMD

        project.pmd {
            toolVersion = ToolVersions.pmdVersion
            ignoreFailures = extension.getIgnoreErrors()
            ruleSets = extension.getPmdRules()
        }

        // Create a phony pmd task that just executes all real pmd tasks
        Task pmdRootTask = project.tasks.findByName(PMD) ?: project.task(PMD)
        project.sourceSets.all { SourceSet sourceSet ->
            Task pmdTask = getOrCreateTask(project, sourceSet.getTaskName(PMD, null)) {
                // most defaults are good enough
                reports {
                    xml.enabled = true
                    xml.destination = xml.destination.absolutePath - "${sourceSet.name}.xml" +
                            "pmd-${sourceSet.name}.xml"
                    html.enabled = false
                }
            }

            pmdRootTask.dependsOn pmdTask
        }

        project.tasks.check.dependsOn pmdRootTask
    }

    @Override
    void applyAndroidConfig(final Project project, final StaticCodeAnalysisExtension extension) {
        boolean supportsClasspath = GRADLE_VERSION_PMD_CLASSPATH_SUPPORT <= GradleVersion.current()

        project.plugins.apply PMD

        project.pmd {
            toolVersion = ToolVersions.pmdVersion
            ignoreFailures = extension.getIgnoreErrors()
            ruleSets = extension.getPmdRules()
        }

        // Create a phony pmd task that just executes all real pmd tasks
        Task pmdRootTask = project.tasks.findByName(PMD) ?: project.task(PMD)
        project.android.sourceSets.all { sourceSet ->
            Task pmdTask = getOrCreateTask(project, getTaskName(sourceSet.name)) {
                source sourceSet.java.srcDirs
                exclude '**/gen/**'

                reports {
                    xml.enabled = true
                    xml.destination = xml.destination.absolutePath - "${sourceSet.name}.xml" +
                            "pmd-${sourceSet.name}.xml"
                    html.enabled = false
                }
            }

            if (supportsClasspath) {
                setupAndroidClasspathAwareTask(pmdTask, project)
            }

            pmdRootTask.dependsOn pmdTask
        }

        project.tasks.check.dependsOn pmdRootTask
    }

    private static Task getOrCreateTask(final Project project, final String taskName, final Closure closure) {
        Task pmdTask;
        if (project.tasks.findByName(taskName)) {
            pmdTask = project.tasks.findByName(taskName)
        } else {
            pmdTask = project.task(taskName, type:Pmd)
        }

        pmdTask.configure closure
    }

    private static String getTaskName(final String sourceSetName) {
        GUtil.toLowerCamelCase(String.format('%s %s', PMD, sourceSetName))
    }
}
