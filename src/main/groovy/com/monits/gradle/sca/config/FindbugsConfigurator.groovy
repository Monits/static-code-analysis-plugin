/*
 * Copyright 2010-2017 Monits S.A.
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
import org.gradle.api.Namer
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.plugins.quality.FindBugs
import org.gradle.api.plugins.quality.FindBugsExtension
import org.gradle.api.plugins.quality.FindBugsReports
import org.gradle.api.reporting.ConfigurableReport
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.util.GUtil

/**
 * A configurator for Findbugs tasks
*/
@CompileStatic
class FindbugsConfigurator implements AnalysisConfigurator, ClasspathAware {
    private static final String FINDBUGS = 'findbugs'
    private static final String FINBUGS_PLUGINS_CONFIGURATION = 'findbugsPlugins'

    private final RemoteConfigLocator configLocator = new RemoteConfigLocator(FINDBUGS)

    @Override
    void applyConfig(final Project project, final StaticCodeAnalysisExtension extension) {
        setupPlugin(project, extension)

        SourceSetContainer sourceSets = project.convention.getPlugin(JavaPluginConvention).sourceSets
        setupTasksPerSourceSet(project, extension, sourceSets)
    }

    @Override
    void applyAndroidConfig(final Project project, final StaticCodeAnalysisExtension extension) {
        setupPlugin(project, extension)

        setupTasksPerSourceSet(project, extension,
                project['android']['sourceSets'] as NamedDomainObjectContainer) { FindBugs findbugsTask, sourceSet ->
            /*
             * Android doesn't expose name of the task compiling the sourceset, and names vary
             * widely from version to version of the plugin, plus needs to take flavors into account.
             * This is inefficient, but safer and simpler.
            */
            findbugsTask.dependsOn project.tasks.withType(JavaCompile)

            // Filter analyzed classes to just include those that are in the sourceset
            if ((sourceSet['java']['sourceFiles'] as Collection).empty) {
                findbugsTask.classes = project.files() // empty file collection
            } else {
                findbugsTask.classes = getProjectClassTree(project, sourceSet['name'] as String)
            }

            findbugsTask.source sourceSet['java']['srcDirs']
            findbugsTask.exclude '**/gen/**'

            setupAndroidClasspathAwareTask(findbugsTask, project)
        }
    }

    private static void setupPlugin(final Project project, final StaticCodeAnalysisExtension extension) {
        project.plugins.apply FINDBUGS

        project.dependencies.with {
            add(FINBUGS_PLUGINS_CONFIGURATION,
                    'com.monits:findbugs-plugin:' + ToolVersions.monitsFindbugsVersion) { ModuleDependency d ->
                d.transitive = false
            }

            add(FINBUGS_PLUGINS_CONFIGURATION, 'com.mebigfatguy.fb-contrib:fb-contrib:' + ToolVersions.fbContribVersion)
        }

        project.extensions.configure(FindBugsExtension) { FindBugsExtension it ->
            it.with {
                toolVersion = ToolVersions.findbugsVersion
                effort = 'max'
                ignoreFailures = extension.ignoreErrors
            }
        }
    }

    private void setupTasksPerSourceSet(final Project project, final StaticCodeAnalysisExtension extension,
                                               final NamedDomainObjectContainer<?> sourceSets,
                                               final Closure<?> configuration = null) {
        // Create a phony findbugs task that just executes all real findbugs tasks
        Task findbugsRootTask = project.tasks.maybeCreate(FINDBUGS)
        sourceSets.all { sourceSet ->
            Namer<Object> namer = sourceSets.namer as Namer<Object>
            String sourceSetName = namer.determineName(sourceSet)
            RulesConfig config = extension.sourceSetConfig.maybeCreate(sourceSetName)

            File filterSource
            boolean remoteLocation
            String downloadTaskName

            // findbugs exclude is optional
            if (config.findbugsExclude) {
                remoteLocation = RemoteConfigLocator.isRemoteLocation(config.findbugsExclude)
                downloadTaskName = generateTaskName('downloadFindbugsExcludeFilter', sourceSetName)
                if (remoteLocation) {
                    filterSource = configLocator.makeDownloadFileTask(project, config.findbugsExclude,
                            String.format('excludeFilter-%s.xml', sourceSetName), downloadTaskName)
                } else {
                    filterSource = new File(config.findbugsExclude)
                }
            }

            Task findbugsTask = project.tasks.maybeCreate(generateTaskName(sourceSetName), FindBugs)
                .configure { FindBugs it ->
                    it.with {
                        // most defaults are good enough
                        if (remoteLocation) {
                            dependsOn project.tasks.findByName(downloadTaskName)
                        }

                        if (filterSource) {
                            excludeFilter = filterSource
                        }

                        reports { FindBugsReports r ->
                            r.with {
                                configureXmlReport(xml, project, sourceSetName)
                            }
                        }
                    }
                }

            if (configuration) {
                // Add the sourceset as second parameter for configuration closure
                findbugsTask.configure configuration.rcurry(sourceSet)
            }

            findbugsRootTask.dependsOn findbugsTask
        }

        project.tasks.findByName('check').dependsOn findbugsRootTask
    }

    /*
     * Gradle 4.2 deprecated setDestination(Object) in favor of the new setDestination(File) which didn't exist before
     * Therefore, static compilation against the new method fails on older Gradle versions, but forcing the usage of
     * the old one produces deprecation warnings on 4.2, so we let the runtime decide which method to use
    */
    @CompileStatic(TypeCheckingMode.SKIP)
    private static void configureXmlReport(final ConfigurableReport report, final Project project,
            final String sourceSetName) {
        report.destination = new File(project.extensions.getByType(ReportingExtension).file(FINDBUGS),
            "findbugs-${sourceSetName}.xml")
        report.withMessages = true
    }

    private static String generateTaskName(final String taskName = FINDBUGS, final String sourceSetName) {
        GUtil.toLowerCamelCase(String.format('%s %s', taskName, sourceSetName))
    }
}
