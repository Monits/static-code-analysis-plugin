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

import com.monits.gradle.sca.AndroidHelper
import groovy.io.FileType
import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Base class for Android Lint tasks.
*/
@CompileStatic
abstract class AndroidLintTask extends DefaultTask {
    protected AndroidLintTask() {
        /*
         * This tasks actually depend on the androidLint configuration, but there seems to be now way
         * to configure that as an input...
         */
        outputs.upToDateWhen { false }
    }

    @TaskAction
    abstract void run()

    /**
     * Retrieves a file pointing to the active android lint home, making user it exits.
     *
     * @return A File pointing to the active android lint home.
     */
    @InputDirectory
    @OutputDirectory
    File getAndroidLintHome() {
        String home = AndroidHelper.homeDir

        if (home == null) {
            throw new GradleException('Neither ANDROID_SDK_HOME, nor user.home nor HOME could be found.')
        }

        File f = project.file("${home}/.android/lint/")
        if (!f.exists()) {
            f.mkdirs()
        }

        f
    }

    /**
     * Change the file extension of all files in the given folder from one to another
     *
     * @param dir The directory in which to  find for files to rename
     * @param from The original extension to be changed
     * @param to The new extension to be used
     */
    protected static void changeAllFileExtensions(final File dir, final String from, final String to) {
        dir.eachFileMatch(FileType.FILES, ~/.*${from}$/) {
            it.renameTo(it.absolutePath[0 ..< it.absolutePath.length() - from.length()] + to)
        }
    }
}
