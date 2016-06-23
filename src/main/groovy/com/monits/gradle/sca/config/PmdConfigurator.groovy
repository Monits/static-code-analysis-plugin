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
import com.monits.gradle.sca.RulesConfig
import com.monits.gradle.sca.StaticCodeAnalysisExtension
import com.monits.gradle.sca.ToolVersions
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.plugins.quality.PmdExtension
import org.gradle.api.plugins.quality.PmdReports
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.util.GUtil
import org.gradle.util.GradleVersion

/**
 * A configurator for PMD tasks.
*/
@CompileStatic
class PmdConfigurator implements AnalysisConfigurator, ClasspathAware {
    private final static GradleVersion GRADLE_VERSION_PMD_CLASSPATH_SUPPORT = GradleVersion.version('2.8')
    private final static String PMD = 'pmd'

    @Override
    void applyConfig(final Project project, final StaticCodeAnalysisExtension extension) {
        setupPlugin(project, extension)

        SourceSetContainer sourceSets = project.convention.getPlugin(JavaPluginConvention).sourceSets
        setupTasksPerSourceSet(project, extension, sourceSets) { Pmd pmdTask, SourceSet sourceSet ->
            boolean supportsClasspath = GRADLE_VERSION_PMD_CLASSPATH_SUPPORT <= GradleVersion.current()

            if (supportsClasspath) {
                // This is the default in Gradle 3.+, we backport it
                pmdTask.classpath = sourceSet.output + sourceSet.compileClasspath
            }
        }
    }

    // DuplicateStringLiteral should be removed once we refactor this
    @CompileStatic(TypeCheckingMode.SKIP)
    @Override
    void applyAndroidConfig(final Project project, final StaticCodeAnalysisExtension extension) {
        setupPlugin(project, extension)

        setupTasksPerSourceSet(project, extension, project.android.sourceSets) { Pmd pmdTask, sourceSet ->
            /*
             * Android doesn't expose name of the task compiling the sourceset, and names vary
             * widely from version to version of the plugin, plus needs to take flavors into account.
             * This is inefficient, but safer and simpler.
            */
            dependsOn project.tasks.withType(JavaCompile)

            source sourceSet.java.srcDirs
            exclude '**/gen/**'

            boolean supportsClasspath = GRADLE_VERSION_PMD_CLASSPATH_SUPPORT <= GradleVersion.current()

            if (supportsClasspath) {
                setupAndroidClasspathAwareTask(pmdTask, project, null)
            }
        }
    }

    @SuppressWarnings('UnnecessaryGetter')
    private static void setupPlugin(final Project project, final StaticCodeAnalysisExtension extension) {
        project.plugins.apply PMD

        project.extensions.configure(PmdExtension) { PmdExtension e ->
            e.toolVersion = ToolVersions.pmdVersion
            e.ignoreFailures = extension.getIgnoreErrors()
        }

        if (!ToolVersions.isLatestPmdVersion()) {
            project.logger.warn('Using an outdated PMD version. Update the used Gradle ' +
                    'version to get better analysis results.')
        }
    }

    @SuppressWarnings('UnnecessaryGetter')
    private static void setupTasksPerSourceSet(final Project project, final StaticCodeAnalysisExtension extension,
                                               final NamedDomainObjectContainer<?> sourceSets,
                                               final Closure<?> configuration = null) {
        // Create a phony pmd task that just executes all real pmd tasks
        Task pmdRootTask = project.tasks.findByName(PMD) ?: project.task(PMD)
        sourceSets.all { sourceSet ->
            String sourceSetName = sourceSets.namer.determineName(sourceSet)
            RulesConfig config = extension.sourceSetConfig.maybeCreate(sourceSetName)

            Task pmdTask = getOrCreateTask(project, generateTaskName(sourceSetName)) { Pmd it ->
                // most defaults are good enough

                // PMD doesn't play ball with relative paths nor file:// URIs
                it.ruleSets = config.getPmdRules().collect { it =~ /https?:\/\// ? it : project.file(it).absolutePath }

                it.reports { PmdReports r ->
                    r.with {
                        xml.enabled = true
                        xml.setDestination(xml.destination.absolutePath - "${sourceSetName}.xml" +
                            "pmd-${sourceSetName}.xml")
                        html.enabled = false
                    }
                }
            }

            if (configuration) {
                // Add the sourceset as second parameter for configuration closure
                pmdTask.configure configuration.rcurry(sourceSet)
            }

            pmdRootTask.dependsOn pmdTask
        }

        project.tasks.findByName('check').dependsOn pmdRootTask
    }

    private static Task getOrCreateTask(final Project project, final String taskName, final Closure closure) {
        Task pmdTask
        if (project.tasks.findByName(taskName)) {
            pmdTask = project.tasks.findByName(taskName)
        } else {
            pmdTask = project.task(taskName, type:Pmd)
        }

        pmdTask.configure closure
    }

    private static String generateTaskName(final String sourceSetName) {
        GUtil.toLowerCamelCase(String.format('%s %s', PMD, sourceSetName))
    }
}
