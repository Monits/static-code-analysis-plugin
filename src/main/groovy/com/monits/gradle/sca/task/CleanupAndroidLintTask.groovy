/*
 * Copyright 2010-2017 Monits S.A.
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
import groovy.transform.CompileStatic
import org.gradle.api.tasks.TaskAction

/**
 * Task to clean up and restore Android Lint Home after running.
*/
@CompileStatic
class CleanupAndroidLintTask extends AndroidLintTask {

    @TaskAction
    void run() {
        // Remove all the .jar files we introduced
        androidLintHome.eachFileMatch(FileType.FILES, ~/.*\.jar$/) {
            it.delete()
        }

        // Restore .bak files
        changeAllFileExtensions(androidLintHome, '.bak', '.jar')
    }
}
