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

import com.github.spotbugs.snom.SpotBugsExtension
import com.github.spotbugs.snom.SpotBugsPlugin
import com.monits.gradle.sca.ClasspathAware
import com.monits.gradle.sca.dsl.RulesConfig
import com.monits.gradle.sca.dsl.StaticCodeAnalysisExtension
import com.monits.gradle.sca.ToolVersions
import groovy.transform.CompileStatic
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Namer
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.util.GUtil
import com.github.spotbugs.snom.SpotBugsTask
import static com.monits.gradle.sca.utils.TaskUtils.registerTask

/**
 * A configurator for Spotbugs tasks
*/
@CompileStatic
class SpotbugsConfigurator implements AnalysisConfigurator, ClasspathAware {
    private static final String SPOTBUGS = 'spotbugs'
    private static final String SPOTBUGS_PLUGIN_ID = 'com.github.spotbugs'
    private static final String SPOTBUGS_PLUGINS_CONFIGURATION = 'spotbugsPlugins'

    private final RemoteConfigLocator configLocator = new RemoteConfigLocator(SPOTBUGS)

    @Override
    void applyConfig(final Project project, final StaticCodeAnalysisExtension extension) {
        setupPlugin(project, extension)

        SourceSetContainer sourceSets = project.convention.getPlugin(JavaPluginConvention).sourceSets
        setupTasksPerSourceSet(project, extension, sourceSets)
    }

    @SuppressWarnings('DuplicateStringLiteral')
    @Override
    void applyAndroidConfig(final Project project, final StaticCodeAnalysisExtension extension) {
        setupPlugin(project, extension)

        setupTasksPerSourceSet(project, extension,
                project['android']['sourceSets'] as NamedDomainObjectContainer) { SpotBugsTask task, sourceSet ->
            /*
             * Android doesn't expose name of the task compiling the sourceset, and names vary
             * widely from version to version of the plugin, plus needs to take flavors into account.
             * This is inefficient, but safer and simpler.
            */
            task.dependsOn project.tasks.withType(JavaCompile)

            // Filter analyzed classes to just include those that are in the sourceset
            if ((sourceSet['java']['sourceFiles'] as Collection).empty) {
                task.classes = project.files() // empty file collection
            } else {
                task.classes = getProjectClassTree(project, sourceSet['name'] as String)
            }

            task.sourceDirs = project.files(sourceSet['java']['srcDirs'])
        } { TaskProvider<SpotBugsTask> task, sourceSet ->
            setupAndroidClasspathAwareTask(task, project, sourceSet['name'] as String)
        }
    }

    private static void setupPlugin(final Project project, final StaticCodeAnalysisExtension extension) {
        project.plugins.apply SPOTBUGS_PLUGIN_ID

        project.dependencies.with {
            add(SPOTBUGS_PLUGINS_CONFIGURATION,
                    'com.monits:findbugs-plugin:' + ToolVersions.monitsSpotbugsVersion) { ModuleDependency d ->
                d.transitive = false
            }

            add(SPOTBUGS_PLUGINS_CONFIGURATION,
                'com.mebigfatguy.sb-contrib:sb-contrib:' + ToolVersions.sbContribVersion)
        }

        // Needed by com.monits:findbugs-plugin, used to be shipped with Findbugs
        // Added this way so that defaultDependencies (which bring SpotBugs itself) will still apply
        project.configurations.named(SpotBugsPlugin.CONFIG_NAME).configure { Configuration c ->
            c.withDependencies { DependencySet ds ->
                ds.add(project.dependencies.create('commons-lang:commons-lang:2.6'))
            }
        }

        project.extensions.configure(SpotBugsExtension) { SpotBugsExtension it ->
            it.with {
                toolVersion.set(ToolVersions.spotbugsVersion)
                effort = 'max'
                ignoreFailures.set(extension.ignoreErrors)
            }
        }
    }

    private void setupTasksPerSourceSet(final Project project, final StaticCodeAnalysisExtension extension,
                                               final NamedDomainObjectContainer<?> sourceSets,
                                               final Closure<?> configuration = null,
                                               final Closure<?> register = null) {
        // Create a phony spotbugs task that just executes all real spotbugs tasks
        TaskProvider<Task> spotbugsRootTask = registerTask(project, SPOTBUGS)
        sourceSets.all { sourceSet ->
            Namer<Object> namer = sourceSets.namer as Namer<Object>
            String sourceSetName = namer.determineName(sourceSet)
            RulesConfig config = extension.sourceSetConfig.maybeCreate(sourceSetName)

            File filterSource
            boolean remoteLocation
            String downloadTaskName

            // spotbugs exclude is optional
            if (config.spotbugsExclude) {
                remoteLocation = RemoteConfigLocator.isRemoteLocation(config.spotbugsExclude)
                downloadTaskName = generateTaskName('downloadSpotbugsExcludeFilter', sourceSetName)
                if (remoteLocation) {
                    filterSource = configLocator.makeDownloadFileTask(project, config.spotbugsExclude,
                            String.format('excludeFilter-%s.xml', sourceSetName), downloadTaskName)
                } else {
                    filterSource = new File(config.spotbugsExclude)
                }
            }

            TaskProvider<SpotBugsTask> spotbugsTask = registerTask(project,
                generateTaskName(sourceSetName), SpotBugsTask)
            spotbugsTask.configure { SpotBugsTask it ->
                it.with {
                    // most defaults are good enough
                    if (remoteLocation) {
                        dependsOn project.tasks.named(downloadTaskName)
                    }

                    if (filterSource) {
                        excludeFilter.set(filterSource)
                    }

                    reports.create('xml').destination = new File(
                        project.extensions.getByType(ReportingExtension).file(SPOTBUGS),
                        "spotbugs-${sourceSetName}.xml")
                }

                it // make the closure return the task to avoid compiler errors
            }

            if (register) {
                // Allow registering related tasks
                register.call(spotbugsTask, sourceSet)
            }

            if (configuration) {
                // Add the sourceset as second parameter for configuration closure
                spotbugsTask.configure configuration.rcurry(sourceSet)
            }

            // For backwards compatibility, create equivalent findbugs* task
            setupPhonyBackwardsCompatibleFindbugsTask(project, spotbugsTask)

            spotbugsRootTask.configure { Task it ->
                it.dependsOn spotbugsTask
            }
        }

        // For backwards compatibility, create equivalent findbugs* task
        setupPhonyBackwardsCompatibleFindbugsTask(project, spotbugsRootTask)

        project.tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME).configure { Task it ->
            it.dependsOn spotbugsRootTask
        }
    }

    private static String generateTaskName(final String taskName = SPOTBUGS, final String sourceSetName) {
        GUtil.toLowerCamelCase(String.format('%s %s', taskName, sourceSetName))
    }

    private void setupPhonyBackwardsCompatibleFindbugsTask(final Project project,
                                                           final TaskProvider<? extends Task> spotbugsTask) {
        String taskName = spotbugsTask.name.replace(SPOTBUGS, 'findbugs')
        TaskProvider<Task> findbugsTask = registerTask(project, taskName)

        findbugsTask.configure { Task it ->
            it.doFirst { Task t ->
                t.logger.warn("Using deprecated '${t.path}' task. " +
                    "Please update to use '${spotbugsTask.get().path}' instead, this task " +
                    'will be removed in the 4.0.0 release.')
            }
            it.finalizedBy spotbugsTask
        }
    }
}
