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
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.quality.FindBugs
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.util.GUtil

/**
 * A configurator for Findbugs tasks
*/
class FindbugsConfigurator extends AbstractRemoteConfigLocator implements AnalysisConfigurator, ClasspathAware {
    private static final String FINDBUGS = 'findbugs'

    final String pluginName = FINDBUGS

    @Override
    void applyConfig(final Project project, final StaticCodeAnalysisExtension extension) {
        setupPlugin(project, extension)

        setupTasksPerSourceSet(project, extension, project.sourceSets)
    }

    @Override
    void applyAndroidConfig(final Project project, final StaticCodeAnalysisExtension extension) {
        setupPlugin(project, extension)

        setupTasksPerSourceSet(project, extension, project.android.sourceSets) { FindBugs findbugsTask, sourceSet ->
            /*
             * Android doesn't expose name of the task compiling the sourceset, and names vary
             * widely from version to version of the plugin, plus needs to take flavors into account.
             * This is inefficient, but safer and simpler.
            */
            dependsOn project.tasks.withType(JavaCompile)

            // TODO : Get classes just for the given sourceset, the rest should be in the classpath
            classes = getProjectClassTree(project)

            source sourceSet.java.srcDirs
            exclude '**/gen/**'

            setupAndroidClasspathAwareTask(findbugsTask, project)
        }
    }

    @SuppressWarnings('UnnecessaryGetter')
    private static void setupPlugin(final Project project, final StaticCodeAnalysisExtension extension) {
        project.plugins.apply FINDBUGS

        project.dependencies {
            findbugsPlugins('com.monits:findbugs-plugin:' + ToolVersions.monitsFindbugsVersion) {
                transitive = false
            }
            findbugsPlugins 'com.mebigfatguy.fb-contrib:fb-contrib:' + ToolVersions.fbContribVersion
        }

        project.findbugs {
            toolVersion = ToolVersions.findbugsVersion
            effort = 'max'
            ignoreFailures = extension.getIgnoreErrors()
        }
    }

    @SuppressWarnings('UnnecessaryGetter')
    private void setupTasksPerSourceSet(final Project project, final StaticCodeAnalysisExtension extension,
                                               final NamedDomainObjectContainer<Object> sourceSets,
                                               final Closure<?> configuration = null) {
        // Create a phony findbugs task that just executes all real findbugs tasks
        Task findbugsRootTask = project.tasks.findByName(FINDBUGS) ?: project.task(FINDBUGS)
        sourceSets.all { sourceSet ->
            String sourceSetName = sourceSets.namer.determineName(sourceSet)
            RulesConfig config = extension.sourceSetConfig.maybeCreate(sourceSetName)

            boolean remoteLocation = isRemoteLocation(config.getFindbugsExclude())
            File filterSource
            String downloadTaskName = generateTaskName('downloadFindbugsExcludeFilter', sourceSetName)
            if (remoteLocation) {
                filterSource = makeDownloadFileTask(project, config.getFindbugsExclude(),
                        String.format('excludeFilter-%s.xml', sourceSetName), downloadTaskName)
            } else {
                filterSource = new File(config.getFindbugsExclude())
            }

            Task findbugsTask = getOrCreateTask(project, generateTaskName(sourceSetName)) {
                // most defaults are good enough
                if (remoteLocation) {
                    dependsOn project.tasks.findByName(downloadTaskName)
                }

                excludeFilter = filterSource

                reports {
                    xml {
                        destination = new File(project.extensions.getByType(ReportingExtension).file(FINDBUGS),
                                "findbugs-${sourceSetName}.xml")
                        withMessages = true
                    }
                }
            }

            if (configuration) {
                // Add the sourceset as second parameter for configuration closure
                findbugsTask.configure configuration.rcurry(sourceSet)
            }

            findbugsRootTask.dependsOn findbugsTask
        }

        project.tasks.check.dependsOn findbugsRootTask
    }

    @CompileStatic
    private static String generateTaskName(final String taskName = FINDBUGS, final String sourceSetName) {
        GUtil.toLowerCamelCase(String.format('%s %s', taskName, sourceSetName))
    }

    @CompileStatic
    private static Task getOrCreateTask(final Project project, final String taskName, final Closure closure) {
        Task findbugsTask
        if (project.tasks.findByName(taskName)) {
            findbugsTask = project.tasks.findByName(taskName)
        } else {
            findbugsTask = project.task(taskName, type:FindBugs)
        }

        findbugsTask.configure closure
    }
}
