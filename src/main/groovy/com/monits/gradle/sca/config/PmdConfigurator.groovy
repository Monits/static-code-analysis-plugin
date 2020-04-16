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

import com.monits.gradle.sca.ClasspathAware
import com.monits.gradle.sca.dsl.RulesConfig
import com.monits.gradle.sca.dsl.StaticCodeAnalysisExtension
import com.monits.gradle.sca.ToolVersions
import groovy.transform.CompileStatic
import org.gradle.api.InvalidUserDataException
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Namer
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.plugins.quality.PmdExtension
import org.gradle.api.plugins.quality.PmdReports
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.util.GUtil
import org.gradle.util.GradleVersion
import org.gradle.util.VersionNumber

import java.util.regex.Matcher

/**
 * A configurator for PMD tasks.
*/
@CompileStatic
class PmdConfigurator implements AnalysisConfigurator, ClasspathAware {
    private final static GradleVersion GRADLE_VERSION_PMD_INCREMENTAL_SUPPORT = GradleVersion.version('5.6.0')

    // Prior to 6.18.0 the cache went nuts with XPath rules, see https://github.com/pmd/pmd/pull/1992
    private static final VersionNumber PMD_VERSION_INCREMENTAL_SUPPORT = VersionNumber.parse('6.18.0')

    private final static String PMD = 'pmd'

    private final RemoteConfigLocator configLocator = new RemoteConfigLocator(PMD)

    @Override
    void applyConfig(final Project project, final StaticCodeAnalysisExtension extension) {
        setupPlugin(project, extension)

        SourceSetContainer sourceSets = project.convention.getPlugin(JavaPluginConvention).sourceSets
        //noinspection GroovyMissingReturnStatement
        setupTasksPerSourceSet(project, extension, sourceSets)
    }

    @Override
    void applyAndroidConfig(final Project project, final StaticCodeAnalysisExtension extension) {
        setupPlugin(project, extension)

        //noinspection GroovyAssignabilityCheck
        setupTasksPerSourceSet(project, extension,
                project['android']['sourceSets'] as NamedDomainObjectContainer) { Pmd pmdTask, sourceSet ->
            /*
             * Android doesn't expose name of the task compiling the sourceset, and names vary
             * widely from version to version of the plugin, plus needs to take flavors into account.
             * This is inefficient, but safer and simpler.
            */
            pmdTask.dependsOn project.tasks.withType(JavaCompile)

            pmdTask.source sourceSet['java']['srcDirs']
            pmdTask.exclude '**/gen/**'

            setupAndroidClasspathAwareTask(pmdTask, project)
        }
    }

    private static void setupPlugin(final Project project, final StaticCodeAnalysisExtension extension) {
        project.plugins.apply PMD

        project.extensions.configure(PmdExtension) { PmdExtension e ->
            e.toolVersion = ToolVersions.pmdVersion
            e.ignoreFailures = extension.ignoreErrors

            if (GradleVersion.current() >= GRADLE_VERSION_PMD_INCREMENTAL_SUPPORT) {
                e.incrementalAnalysis.set(
                    VersionNumber.parse(ToolVersions.pmdVersion) >= PMD_VERSION_INCREMENTAL_SUPPORT)
            }
        }

        if (!ToolVersions.latestPmdVersion) {
            project.logger.warn('Using an outdated PMD version. ' + ToolVersions.pmdUpdateInstructions)
        }
    }

    @SuppressWarnings(['UnnecessaryGetter', 'DuplicateStringLiteral'])
    private void setupTasksPerSourceSet(final Project project, final StaticCodeAnalysisExtension extension,
                                               final NamedDomainObjectContainer<?> sourceSets,
                                               final Closure<?> configuration = null) {
        // Create a phony pmd task that just executes all real pmd tasks
        Task pmdRootTask = project.tasks.maybeCreate(PMD)
        sourceSets.all { sourceSet ->
            Namer<Object> namer = sourceSets.namer as Namer<Object>
            String sourceSetName = namer.determineName(sourceSet)
            RulesConfig config = extension.sourceSetConfig.maybeCreate(sourceSetName)

            List<Task> downloadTasks = []
            List<String> rulesets = []

            for (String ruleset : config.getPmdRules()) {
                boolean remoteLocation = RemoteConfigLocator.isRemoteLocation(ruleset)
                File configSource
                if (remoteLocation) {
                    Matcher filenameMatcher = ruleset =~ /\/([^\/]*)\.[^.]*$/
                    filenameMatcher.find()
                    String filename = filenameMatcher.group(1).replaceAll '[^a-zA-Z0-9]', ' '
                    String downloadTaskName

                    /*
                     * Names may have collisions if the same file is downloaded from different domains..
                     * add suffixes as needed
                     */
                    int attempts = 0
                    String suffix = ''
                    while (configSource == null) {
                        try {
                            downloadTaskName = generateTaskName('downloadPmdXml', sourceSetName, filename) + suffix
                            configSource = configLocator.makeDownloadFileTask(project, ruleset,
                                String.format('pmd-%s-%s.xml', sourceSetName, filename.replaceAll(/ /, '-') + suffix),
                                downloadTaskName)
                        } catch (InvalidUserDataException ignored) {
                            attempts++
                            suffix = attempts
                        }
                    }
                    downloadTasks.add(project.tasks.findByName(downloadTaskName))
                } else {
                    configSource = project.file(ruleset)
                }

                // PMD doesn't play ball with relative paths nor file:// URIs
                rulesets.add(configSource.absolutePath)
            }

            Task pmdTask = project.tasks.maybeCreate(generateTaskName(sourceSetName), Pmd).configure { Pmd it ->
                // most defaults are good enough
                if (!downloadTasks.empty) {
                    it.dependsOn downloadTasks
                }

                it.ruleSets = rulesets

                it.reports { PmdReports r ->
                    r.with {
                        xml.enabled = true
                        xml.destination = new File(project.extensions.getByType(ReportingExtension).file(PMD),
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

    private static String generateTaskName(final String taskName = PMD, final String sourceSetName,
                                           final String classifier = '') {
        GUtil.toLowerCamelCase(String.format('%s %s %s', taskName, sourceSetName, classifier))
    }
}
