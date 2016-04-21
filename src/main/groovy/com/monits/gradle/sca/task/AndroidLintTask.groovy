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

import groovy.io.FileType
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException

abstract class AndroidLintTask extends DefaultTask {

    /**
     * Retrieves a file pointing to the active android lint home, making usre it exits.
     *
     * @return A File pointint to the active android lint home.
     */
    protected File getAndroidLintHome() {
        // Home candidates and order according to http://tools.android.com/tips/lint-custom-rules
        String home = System.getProperty('ANDROID_SDK_HOME');
        if (home == null) {
            home = System.getenv('ANDROID_SDK_HOME');
        }
        if (home == null) {
            home = System.getProperty('user.home');
        }
        if (home == null) {
            home = System.getenv('HOME');
        }

        if (home == null) {
            throw new GradleException("Neither ANDROID_SDK_HOME, nor user.home nor HOME could be found.");
        }

        File f = project.file("${home}/.android/lint/");
        if (!f.exists()) {
            f.mkdirs();
        }

        return f;
    }

    /**
     * Change the file extension of all files in the given folder from one to another
     *
     * @param dir The diectory in which to  find for files to rename
     * @param from The original extension to be changed
     * @param to The new extension to be used
     */
    protected void changeAllFileExtensions(File dir, String from, String to) {
        dir.eachFileMatch(FileType.FILES, ~/.*${from}$/, {
            it.renameTo(it.getAbsolutePath()[0 ..< it.getAbsolutePath().length()-from.length()] + to)
        });
    }
}
