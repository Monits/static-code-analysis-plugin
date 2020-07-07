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

import com.monits.gradle.sca.dsl.RulesConfig
import com.monits.gradle.sca.dsl.StaticCodeAnalysisExtension
import com.monits.gradle.sca.ToolVersions
import groovy.transform.CompileStatic
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Namer
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.plugins.quality.CheckstyleReports
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.util.GUtil
import org.gradle.util.GradleVersion
import static com.monits.gradle.sca.utils.TaskUtils.registerTask

/**
 * A configurator for Checkstyle tasks.
 */
@CompileStatic
class CheckstyleConfigurator implements AnalysisConfigurator {
    private static final String CHECKSTYLE = 'checkstyle'
    private static final GradleVersion GRADLE7 = GradleVersion.version('7.0.0')

    private final RemoteConfigLocator configLocator = new RemoteConfigLocator(CHECKSTYLE)

    @Override
    void applyConfig(Project project, StaticCodeAnalysisExtension extension) {
        setupPlugin(project, extension)

        SourceSetContainer sourceSets = project.convention.getPlugin(JavaPluginConvention).sourceSets
        setupTasksPerSourceSet(project, extension, sourceSets)
    }

    @Override
    void applyAndroidConfig(Project project, StaticCodeAnalysisExtension extension) {
        setupPlugin(project, extension)

        setupTasksPerSourceSet(project, extension,
                project['android']['sourceSets'] as NamedDomainObjectContainer) { Checkstyle task, sourceSet ->
            task.source sourceSet['java']['srcDirs']
            task.exclude '**/gen/**'

            task.classpath = project.configurations[sourceSet['packageConfigurationName'] as String]
        } { TaskProvider<Checkstyle> task, sourceSet ->
            // Make sure the config is resolvable... AGP 3 decided to play with this...
            Configuration config = project.configurations[sourceSet['packageConfigurationName'] as String]
            if (config.state == Configuration.State.UNRESOLVED && !config.canBeResolved) {
                config.canBeResolved = true
            }
        }
    }

    @SuppressWarnings('UnnecessaryGetter')
    private static void setupPlugin(final Project project, final StaticCodeAnalysisExtension extension) {
        project.plugins.apply CHECKSTYLE

        project.extensions.configure(CheckstyleExtension) { CheckstyleExtension e ->
            e.with {
                toolVersion = ToolVersions.checkstyleVersion
                ignoreFailures = extension.getIgnoreErrors()
                showViolations = false
            }
        }

        if (!ToolVersions.isLatestCheckstyleVersion()) {
            project.logger.warn('Using an outdated Checkstyle version. ' + ToolVersions.checkstyleUpdateInstructions)
        }
    }

    @SuppressWarnings('UnnecessaryGetter')
    private void setupTasksPerSourceSet(final Project project, final StaticCodeAnalysisExtension extension,
                                               final NamedDomainObjectContainer<?> sourceSets,
                                               final Closure<?> configuration = null,
                                               final Closure<?> register = null) {
        // Create a phony checkstyle task that just executes all real checkstyle tasks
        TaskProvider<Task> checkstyleRootTask = registerTask(project, CHECKSTYLE)

        sourceSets.all { sourceSet ->
            Namer<Object> namer = sourceSets.namer as Namer<Object>
            String sourceSetName = namer.determineName(sourceSet)
            RulesConfig config = extension.sourceSetConfig.maybeCreate(sourceSetName)

            boolean remoteLocation = RemoteConfigLocator.isRemoteLocation(config.getCheckstyleRules())
            File configSource
            String downloadTaskName = generateTaskName('downloadCheckstyleXml', sourceSetName)
            if (remoteLocation) {
                configSource = configLocator.makeDownloadFileTask(project, config.getCheckstyleRules(),
                        String.format('checkstyle-%s.xml', sourceSetName), downloadTaskName)
            } else {
                configSource = new File(config.getCheckstyleRules())
            }

            TaskProvider<Checkstyle> checkstyleTask = registerTask(project, generateTaskName(sourceSetName), Checkstyle)
            checkstyleTask.configure { Checkstyle t ->
                if (remoteLocation) {
                    t.dependsOn project.tasks.named(downloadTaskName)
                }

                if (GradleVersion.current() < GRADLE7) {
                    t.configDir = project.<File> provider { null }
                }

                t.configFile = configSource

                t.reports { CheckstyleReports r ->
                    r.xml.destination = new File(
                        project.extensions.getByType(ReportingExtension).file(CHECKSTYLE),
                        "checkstyle-${sourceSetName}.xml")

                    r.html.enabled = false
                }

                // Setup cache file location per-sourceset
                t.configProperties = [
                    'checkstyle.cache.file':"${project.buildDir}/checkstyle-${sourceSetName}.cache" as Object,
                ]

                t // make the closure return the task to avoid compiler errors
            }

            if (configuration) {
                // Add the sourceset as second parameter for configuration closure
                checkstyleTask.configure configuration.rcurry(sourceSet)
            }

            checkstyleRootTask.configure { Task it ->
                it.dependsOn checkstyleTask
            }

            if (register) {
                // Allow registering related tasks
                register.call(checkstyleRootTask, sourceSet)
            }
        }

        project.tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME).configure { Task it ->
            it.dependsOn checkstyleRootTask
        }
    }

    private static String generateTaskName(final String taskName = CHECKSTYLE, final String sourceSetName) {
        GUtil.toLowerCamelCase(String.format('%s %s', taskName, sourceSetName))
    }
}
