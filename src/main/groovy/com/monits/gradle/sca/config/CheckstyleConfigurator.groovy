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
import groovy.transform.TypeCheckingMode
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Namer
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.plugins.quality.CheckstyleReports
import org.gradle.api.reporting.ConfigurableReport
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.reporting.SingleFileReport
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.util.GUtil
import org.gradle.util.GradleVersion

/**
 * A configurator for Checkstyle tasks.
 */
@CompileStatic
class CheckstyleConfigurator implements AnalysisConfigurator {
    private static final String CHECKSTYLE = 'checkstyle'
    private static final GradleVersion GRADLE4 = GradleVersion.version('4.0.0')
    private static final GradleVersion GRADLE3_3 = GradleVersion.version('3.3')

    private static final String HTML_PROPERTY = 'html'

    private final RemoteConfigLocator configLocator = new RemoteConfigLocator(CHECKSTYLE)

    @Override
    void applyConfig(Project project, StaticCodeAnalysisExtension extension) {
        setupPlugin(project, extension)

        SourceSetContainer sourceSets = project.convention.getPlugin(JavaPluginConvention).sourceSets
        setupTasksPerSourceSet(project, extension, sourceSets) { Checkstyle task, SourceSet sourceSet ->
            // Backport fix from Gradle 3.3
            // https://github.com/gradle/gradle/commit/d2479f58330fb2a360f77b719d336205065159b5
            task.classpath = sourceSet.output + sourceSet.compileClasspath
        }
    }

    @Override
    void applyAndroidConfig(Project project, StaticCodeAnalysisExtension extension) {
        setupPlugin(project, extension)

        setupTasksPerSourceSet(project, extension,
                project['android']['sourceSets'] as NamedDomainObjectContainer) { Checkstyle task, sourceSet ->
            task.source sourceSet['java']['srcDirs']
            task.exclude '**/gen/**'

            // Make sure the config is resolvable... AGP 3 decided to play with this...
            Configuration config = project.configurations[sourceSet['packageConfigurationName'] as String]
            if (GradleVersion.current() >= GRADLE3_3 && config.state == Configuration.State.UNRESOLVED
                    && !config.canBeResolved) {
                config.canBeResolved = true
            }

            task.classpath = config
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
                                               final Closure<?> configuration = null) {
        // Create a phony checkstyle task that just executes all real checkstyle tasks
        Task checkstyleRootTask = project.tasks.maybeCreate(CHECKSTYLE)

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

            Task checkstyleTask = project.tasks.maybeCreate(generateTaskName(sourceSetName), Checkstyle)
                .configure { Checkstyle t ->
                    t.with {
                        if (remoteLocation) {
                            dependsOn project.tasks.findByName(downloadTaskName)
                        }

                        /*
                             * Gradle 4.0 introduced a config property setting by default to config/checkstyle
                             * After any other checkstyle task downloads a new config there,
                             * all other would be invalidated so we manually disable it.
                            */
                        if (GradleVersion.current() >= GRADLE4) {
                            configDir = project.<File> provider { null }
                        }

                        configFile = configSource

                        reports { CheckstyleReports r ->
                            configureXmlReport(r.xml, project, sourceSetName)

                            if (r.hasProperty(HTML_PROPERTY)) { // added in gradle 2.10, but unwanted
                                // use lazy property access, as the return type for getHtml changed in Gradle 5
                                (r[HTML_PROPERTY] as SingleFileReport).enabled = false
                            }
                        }

                        // Setup cache file location per-sourceset
                        configProperties = [
                            'checkstyle.cache.file':"${project.buildDir}/checkstyle-${sourceSetName}.cache" as Object,
                        ]
                    }
                }

            if (configuration) {
                // Add the sourceset as second parameter for configuration closure
                checkstyleTask.configure configuration.rcurry(sourceSet)
            }

            checkstyleRootTask.dependsOn checkstyleTask
        }

        project.tasks.findByName('check').dependsOn checkstyleRootTask
    }

    /*
     * Gradle 4.2 deprecated setDestination(Object) in favor of the new setDestination(File) which didn't exist before
     * Therefore, static compilation against the new method fails on older Gradle versions, but forcing the usage of
     * the old one produces deprecation warnings on 4.2, so we let the runtime decide which method to use
    */
    @CompileStatic(TypeCheckingMode.SKIP)
    private static void configureXmlReport(final ConfigurableReport report, final Project project,
            final String sourceSetName) {
        report.destination = new File(project.extensions.getByType(ReportingExtension).file(CHECKSTYLE),
            "checkstyle-${sourceSetName}.xml")
    }

    private static String generateTaskName(final String taskName = CHECKSTYLE, final String sourceSetName) {
        GUtil.toLowerCamelCase(String.format('%s %s', taskName, sourceSetName))
    }
}
