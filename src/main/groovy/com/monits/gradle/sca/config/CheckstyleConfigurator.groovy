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

import com.monits.gradle.sca.RulesConfig
import com.monits.gradle.sca.StaticCodeAnalysisExtension
import com.monits.gradle.sca.ToolVersions
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.util.GUtil

/**
 * A configurator for Checkstyle tasks.
 */
class CheckstyleConfigurator extends AbstractRemoteConfigLocator implements AnalysisConfigurator {
    private static final String CHECKSTYLE = 'checkstyle'

    final String pluginName = CHECKSTYLE

    @Override
    void applyConfig(Project project, StaticCodeAnalysisExtension extension) {
        setupPlugin(project, extension)

        setupTasksPerSourceSet(project, extension, project.sourceSets)
    }

    @Override
    void applyAndroidConfig(Project project, StaticCodeAnalysisExtension extension) {
        setupPlugin(project, extension)

        setupTasksPerSourceSet(project, extension, project.android.sourceSets) { task, sourceSet ->
            source 'src'
            include '**/*.java'
            exclude '**/gen/**'
            classpath = project.configurations[sourceSet.packageConfigurationName]
        }
    }

    @SuppressWarnings('UnnecessaryGetter')
    private static void setupPlugin(final Project project, final StaticCodeAnalysisExtension extension) {
        project.plugins.apply CHECKSTYLE

        project.checkstyle {
            toolVersion = ToolVersions.checkstyleVersion
            ignoreFailures = extension.getIgnoreErrors()
            showViolations = false
        }

        if (!ToolVersions.isLatestCheckstyleVersion()) {
            project.logger.warn('Using an outdated Checkstyle version. Update the used Gradle ' +
                    'version to get better analysis results.')
        }
    }

    @SuppressWarnings('UnnecessaryGetter')
    private void setupTasksPerSourceSet(final Project project, final StaticCodeAnalysisExtension extension,
                                               final NamedDomainObjectContainer<Object> sourceSets,
                                               final Closure<?> configuration = null) {
        // Create a phony checkstyle task that just executes all real checkstyle tasks
        Task checkstyleRootTask = project.tasks.findByName(CHECKSTYLE) ?: project.task(CHECKSTYLE)

        sourceSets.all { sourceSet ->
            String sourceSetName = sourceSets.namer.determineName(sourceSet)
            RulesConfig config = extension.sourceSetConfig.maybeCreate(sourceSetName)

            // TODO : Avoid multiple downloads of same file under different names
            boolean remoteLocation = isRemoteLocation(config.getCheckstyleRules())
            File configSource
            String downloadTaskName = generateTaskName('downloadCheckstyleXml', sourceSetName)
            if (remoteLocation) {
                configSource = makeDownloadFileTask(project, config.getCheckstyleRules(),
                        String.format('checkstyle-%s.xml', sourceSetName), downloadTaskName)
            } else {
                configSource = new File(config.getCheckstyleRules())
            }

            Task checkstyleTask = getOrCreateTask(project, generateTaskName(sourceSetName)) {
                if (remoteLocation) {
                    dependsOn project.tasks.findByName(downloadTaskName)
                }

                configFile configSource

                reports {
                    xml.destination = reports.xml.destination.absolutePath - "${sourceSetName}.xml" +
                            "checkstyle-${sourceSetName}.xml"

                    if (it.hasProperty('html')) {
                        html.enabled = false // added in gradle 2.10, but unwanted
                    }
                }
            }

            if (configuration) {
                // Add the sourceset as second parameter for configuration closure
                checkstyleTask.configure configuration.rcurry(sourceSet)
            }

            checkstyleRootTask.dependsOn checkstyleTask
        }

        project.tasks.check.dependsOn checkstyleRootTask
    }

    private static Task getOrCreateTask(final Project project, final String taskName, final Closure closure) {
        Task pmdTask
        if (project.tasks.findByName(taskName)) {
            pmdTask = project.tasks.findByName(taskName)
        } else {
            pmdTask = project.task(taskName, type:Checkstyle)
        }

        pmdTask.configure closure
    }

    private static String generateTaskName(final String taskName = CHECKSTYLE, final String sourceSetName) {
        GUtil.toLowerCamelCase(String.format('%s %s', taskName, sourceSetName))
    }
}
