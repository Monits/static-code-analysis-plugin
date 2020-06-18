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
import org.gradle.api.tasks.TaskProvider

/**
 * Creates download tasks for remote config.
 */
@CompileStatic
class RemoteConfigLocator {
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

        if (!project.tasks.names.contains(taskName)) {
            TaskProvider<DownloadTask> downloadTask = project.tasks.register(taskName, DownloadTask)
            downloadTask.configure { DownloadTask it ->
                it.downloadedFile = destFile
                it.resourceUri = configLocation
            }
        }

        destFile
    }

    private String getDestinationDirectory(final Project project) {
        "${project.rootDir}/config/${pluginName}/"
    }

    private File getDestinationFile(final Project project, final String localFileName) {
        new File(getDestinationDirectory(project), localFileName)
    }
}
