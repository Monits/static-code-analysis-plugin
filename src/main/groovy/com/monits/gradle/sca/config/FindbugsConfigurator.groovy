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
import com.monits.gradle.sca.task.DownloadTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.quality.FindBugs
import org.gradle.api.tasks.compile.JavaCompile

/**
 * A configurator for Findbugs tasks
*/
class FindbugsConfigurator implements AnalysisConfigurator, ClasspathAware {
    private static final String FINDBUGS = 'findbugs'

    @Override
    void applyConfig(Project project, StaticCodeAnalysisExtension extension) {
        // TODO : Not currently working on java projects
        applyAndroidConfig(project, extension)
    }

    @SuppressWarnings('UnnecessaryGetter')
    @Override
    void applyAndroidConfig(Project project, StaticCodeAnalysisExtension extension) {
        // prevent applying it twice
        if (project.tasks.findByName(FINDBUGS)) {
            return
        }

        project.plugins.apply FINDBUGS

        project.dependencies {
            findbugs project.configurations.findbugsPlugins.dependencies

            // To keep everything tidy, we set these apart
            findbugsPlugins('com.monits:findbugs-plugin:' + ToolVersions.monitsFindbugsVersion) {
                transitive = false
            }
            findbugsPlugins 'com.mebigfatguy.fb-contrib:fb-contrib:' + ToolVersions.fbContribVersion
        }

        boolean remoteLocation = isRemoteLocation(extension.getFindbugsExclude())
        File filterSource
        String downloadTaskName = 'downloadFindbugsExcludeFilter'
        if (remoteLocation) {
            filterSource = makeDownloadFileTask(project, extension.getFindbugsExclude(),
                    'excludeFilter.xml', downloadTaskName, FINDBUGS)
        } else {
            filterSource = new File(extension.getFindbugsExclude())
        }

        project.findbugs {
            toolVersion = ToolVersions.findbugsVersion
            effort = 'max'
            ignoreFailures = extension.getIgnoreErrors()
            excludeFilter = filterSource
        }

        Task findbugsTask = project.task(FINDBUGS, type:FindBugs) {
            dependsOn project.tasks.withType(JavaCompile)

            Task t = project.tasks.findByName('mockableAndroidJar')
            if (t != null) {
                dependsOn t
            }

            if (remoteLocation) {
                dependsOn project.tasks.findByName(downloadTaskName)
            }

            classes = getProjectClassTree(project)
            classpath = project.files() // empty by default, will be populated lazily

            source 'src'
            include '**/*.java'
            exclude '**/gen/**'

            reports {
                xml {
                    destination "$project.buildDir/reports/findbugs/findbugs.xml"
                    xml.withMessages true
                }
            }

            pluginClasspath = project.configurations.findbugsPlugins
        }

        findbugsTask.doFirst {
            /*
             * For best results, Findbugs needs ALL classes, including Android's SDK.
             * We do this now that dependent tasks are done to actually find everything
             */
            configAndroidClasspath(findbugsTask, project)
        }

        project.tasks.check.dependsOn findbugsTask
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
}
