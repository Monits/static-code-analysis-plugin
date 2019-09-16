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

import groovy.transform.CompileDynamic
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.TaskAction

/**
 * Task to resolve AndroidLint dependencies and move them to the Android Lint Home directory.
*/
@CompileDynamic // files.each had a signature change
class ResolveAndroidLintTask extends AndroidLintTask {

    @TaskAction
    void run() {
        Configuration androidLintConfig = project.configurations['androidLint']

        // Resolve all artifacts
        androidLintConfig.resolve()

        // Prevent any "undesired" lints from being applied
        changeAllFileExtensions(androidLintHome, '.jar', '.bak')

        // Manually copy all artifacts to the corresponding location
        androidLintConfig.files.each {
            File target = project.file(androidLintHome.absolutePath + File.separator + it.name)
            InputStream input = it.newDataInputStream()
            OutputStream output = target.newDataOutputStream()

            output << input

            input.close()
            output.close()
        }
    }
}
