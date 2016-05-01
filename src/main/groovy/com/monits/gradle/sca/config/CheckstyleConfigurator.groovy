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

import com.monits.gradle.sca.StaticCodeAnalysisExtension
import com.monits.gradle.sca.ToolVersions
import com.monits.gradle.sca.task.DownloadTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.tasks.SourceSet
import org.gradle.util.GUtil
/**
 * A confiurator for Checkstyle tasks.
 */
class CheckstyleConfigurator implements AnalysisConfigurator {

    private static final String CHECKSTYLE = 'checkstyle'

    @SuppressWarnings('UnnecessaryGetter')
    @Override
    void applyConfig(Project project, StaticCodeAnalysisExtension extension) {
        project.plugins.apply CHECKSTYLE

        boolean remoteLocation = isRemoteLocation(extension.getCheckstyleRules())
        File configSource
        String downloadTaskName = 'downloadCheckstyleXml'
        if (remoteLocation) {
            configSource = makeDownloadFileTask(project, extension.getCheckstyleRules(),
                    'checkstyle.xml', downloadTaskName, CHECKSTYLE)
        } else {
            configSource = new File(extension.getCheckstyleRules())
        }

        project.checkstyle {
            toolVersion = ToolVersions.checkstyleVersion
            ignoreFailures = extension.getIgnoreErrors()
            showViolations = false
            configFile configSource
        }

        // Create a phony pmd task that just executes all real pmd tasks
        Task checkstyleRootTask = project.tasks.findByName(CHECKSTYLE) ?: project.task(CHECKSTYLE)
        project.sourceSets.all { SourceSet sourceSet ->
            Task checkstyleTask = getOrCreateTask(project, sourceSet.getTaskName(CHECKSTYLE, null)) {
                if (remoteLocation) {
                    dependsOn project.tasks.findByName(downloadTaskName)
                }

                reports {
                    xml.destination = reports.xml.destination.absolutePath - "${sourceSet.name}.xml" +
                            "checkstyle-${sourceSet.name}.xml"

                    if (hasProperty('html')) {
                        html.enabled = false // added in gradle 2.10, but unwanted
                    }
                }
            }

            checkstyleRootTask.dependsOn checkstyleTask
        }

        project.tasks.check.dependsOn checkstyleRootTask
    }

    @Override
    void applyAndroidConfig(Project project, StaticCodeAnalysisExtension extension) {
        project.plugins.apply CHECKSTYLE

        boolean remoteLocation = isRemoteLocation(extension.getCheckstyleRules())
        File configSource
        String downloadTaskName = 'downloadCheckstyleXml'
        if (remoteLocation) {
            configSource = makeDownloadFileTask(project, extension.getCheckstyleRules(),
                    'checkstyle.xml', downloadTaskName, CHECKSTYLE)
        } else {
            configSource = new File(extension.getCheckstyleRules())
        }

        project.checkstyle {
            toolVersion = ToolVersions.checkstyleVersion
            ignoreFailures = extension.getIgnoreErrors()
            showViolations = false
            configFile configSource
        }

        // Create a phony pmd task that just executes all real pmd tasks
        Task checkstyleRootTask = project.tasks.findByName(CHECKSTYLE) ?: project.task(CHECKSTYLE)
        project.android.sourceSets.all { sourceSet ->
            Task checkstyleTask = getOrCreateTask(project, getTaskName(sourceSet.name)) {
                if (remoteLocation) {
                    dependsOn project.tasks.findByName(downloadTaskName)
                }
                source 'src'
                include '**/*.java'
                exclude '**/gen/**'
                classpath = project.configurations.compile

                reports {
                    xml.destination = reports.xml.destination.absolutePath - "${sourceSet.name}.xml" +
                            "checkstyle-${sourceSet.name}.xml"

                    if (hasProperty('html')) {
                        html.enabled = false // added in gradle 2.10, but unwanted
                    }
                }
            }

            checkstyleRootTask.dependsOn checkstyleTask
        }

        project.tasks.check.dependsOn checkstyleRootTask
    }

    private static boolean isRemoteLocation(String path) {
        path.startsWith('http://') || path.startsWith('https://')
    }

    private File makeDownloadFileTask(Project project, String remotePath, String destination,
                                      String taskName, String plugin) {
        GString destPath = "${project.rootDir}/config/${plugin}/"
        File destFile = project.file(destPath + destination)

        project.task(taskName, type:DownloadTask) {
            directory = project.file(destPath)
            downloadedFile = destFile
            resourceUri = remotePath
        }

        destFile
    }

    private static Task getOrCreateTask(final Project project, final String taskName, final Closure closure) {
        Task pmdTask;
        if (project.tasks.findByName(taskName)) {
            pmdTask = project.tasks.findByName(taskName)
        } else {
            pmdTask = project.task(taskName, type:Checkstyle)
        }

        pmdTask.configure closure
    }

    private static String getTaskName(final String sourceSetName) {
        GUtil.toLowerCamelCase(String.format('%s %s', CHECKSTYLE, sourceSetName))
    }
}
