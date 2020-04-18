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

import com.monits.gradle.sca.task.DownloadTask
import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.tasks.Copy

/**
 * Abstract class that can efficiently creates download tasks for remote config.
 */
@CompileStatic
class RemoteConfigLocator {
    private static final Map<String, String> DOWNLOAD_TASKS = [:]
    private final String pluginName

    RemoteConfigLocator(final String pluginName) {
        this.pluginName = pluginName
    }

    static boolean isRemoteLocation(final String path) {
        path.startsWith('http://') || path.startsWith('https://')
    }

    /**
     * Creates a task to download a remote config to a local directory. The task may just make a copy
     * from a file if it detects it's already being downloaded by previously created task.
     *
     * @param project The project for which this task is
     * @param configLocation The remote location where the config is
     * @param localFileName The local name we want the file to have within the output directory
     * @param taskName The intended name of the download task
     * @return The {@see File} where the local copy of the file will be
     */
    File makeDownloadFileTask(final Project project, final String configLocation,
                                       final String localFileName, final String taskName) {
        File destFile = getDestinationFile(project, localFileName)

        // Make sure it doesn't already exist in the current project
        if (project.tasks.findByName(taskName) == null) {
            DownloadTask download = getDownloadTaskIfExists(project, configLocation)

            if (download) {
                if (download.downloadedFile.absolutePath == destFile.absolutePath) {
                    // Same file, so we create a NOOP task
                    project.task(taskName, dependsOn:download.path)
                } else {
                    // Already downloading, just wait for it to finish and copy it
                    project.task(taskName, type:Copy) { Copy it ->
                        it.from download.downloadedFile.parentFile
                        it.into getDestinationDirectory(project)
                        it.include download.downloadedFile.name
                        it.rename download.downloadedFile.name, localFileName
                        it.dependsOn download.path
                    }
                }
            } else {
                download = project.task(taskName, type:DownloadTask) { DownloadTask it ->
                    it.downloadedFile = destFile
                    it.resourceUri = configLocation
                } as DownloadTask

                DOWNLOAD_TASKS[configLocation] = download.path
            }
        }

        destFile
    }

    private static DownloadTask getDownloadTaskIfExists(final Project project, final String configLocation) {
        if (!DOWNLOAD_TASKS.containsKey(configLocation)) {
            return null
        }

        project.rootProject.tasks.findByPath(DOWNLOAD_TASKS[configLocation]) as DownloadTask
    }

    private String getDestinationDirectory(final Project project) {
        "${project.rootDir}/config/${pluginName}/"
    }

    private File getDestinationFile(final Project project, final String localFileName) {
        new File(getDestinationDirectory(project), localFileName)
    }
}
