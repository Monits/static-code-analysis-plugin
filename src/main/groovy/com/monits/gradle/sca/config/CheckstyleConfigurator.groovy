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
import org.gradle.api.plugins.quality.Checkstyle

class CheckstyleConfigurator implements AnalysisConfigurator {

    @Override
    void applyConfig(final Project project, final StaticCodeAnalysisExtension extension) {
        project.plugins.apply 'checkstyle'

        boolean remoteLocation = isRemoteLocation(extension.getCheckstyleRules());
        File configSource;
        String downloadTaskName = 'downloadCheckstyleXml'
        if (remoteLocation) {
            configSource = createDownloadFileTask(project, extension.getCheckstyleRules(),
                    'checkstyle.xml', downloadTaskName, 'checkstyle');
        } else {
            configSource = new File(extension.getCheckstyleRules());
        }

        project.checkstyle {
            toolVersion = ToolVersions.checkstyleVersion
            ignoreFailures = extension.getIgnoreErrors();
            showViolations = false
            configFile configSource
        }

        project.task('checkstyle', type: Checkstyle) {
            if (remoteLocation) {
                dependsOn project.tasks.findByName(downloadTaskName)
            }
            source 'src'
            include '**/*.java'
            exclude '**/gen/**'
            classpath = project.configurations.compile
        }

        project.tasks.check.dependsOn project.tasks.checkstyle
    }

    @Override
    void applyAndroidConfig(final Project project, final StaticCodeAnalysisExtension extension) {
        applyConfig(project, extension) // no difference at all
    }

    private static boolean isRemoteLocation(String path) {
        return path.startsWith('http://') || path.startsWith('https://');
    }

    private File createDownloadFileTask(Project project, String remotePath, String destination,
                                        String taskName, String plugin) {
        def destPath = "${project.rootDir}/config/${plugin}/"
        def File destFile = project.file(destPath + destination)

        project.task(taskName, type: DownloadTask) {
            directory = project.file(destPath)
            downloadedFile = destFile
            resourceUri = remotePath
        }

        return destFile;
    }
}
