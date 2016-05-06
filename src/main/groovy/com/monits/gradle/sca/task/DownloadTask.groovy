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
package com.monits.gradle.sca.task

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.ParallelizableTask
import org.gradle.api.tasks.TaskAction
import org.gradle.logging.ConsoleRenderer
import org.gradle.util.GradleVersion

/**
 * A task to download a remote file.
*/
@ParallelizableTask
class DownloadTask extends DefaultTask {

    @Input
    String resourceUri

    @Input
    File directory

    @OutputFile
    File downloadedFile

    DownloadTask() {
        // this task should never be up to date, we need to always check for changes on the remote server
        outputs.upToDateWhen { false }
    }

    @SuppressWarnings('UnnecessaryGetter')
    @TaskAction
    void run() {
        directory.mkdirs()

        try {
            // Honor --offline
            if (project.gradle.startParameter.isOffline()) {
                String cachedResource = new ConsoleRenderer().asClickableFileUrl(downloadedFile)
                if (downloadedFile.exists()) {
                    logger.warn('Running in offline mode. ' +
                            "Using a possibly outdated version of ${cachedResource}")
                } else {
                    throw new GradleException('Running in offline mode, ' +
                            "but there is no cached version of ${cachedResource}")
                }
            }

            Map<String, Serializable> options =
                    [src:resourceUri, dest:downloadedFile.absolutePath, usetimestamp:true]

            // Gradle 2.13 includes ant 1.9.6 which supports gzip
            if (GradleVersion.current() > GradleVersion.version('2.13')) {
                options['tryGzipEncoding'] = true
            }

            ant.get(options)
        } catch (SocketException | UnknownHostException e) {
            // network is unreachable, if there is a local file, warn the user, but use that instead of failing
            if (downloadedFile.exists()) {
                String cachedResource = new ConsoleRenderer().asClickableFileUrl(downloadedFile)
                logger.warn("Couldn't download ${resourceUri}, network seems to be unreachable. " +
                        "Using a possibly outdated version of ${cachedResource}")
            } else {
                throw e
            }
        }
    }
}
