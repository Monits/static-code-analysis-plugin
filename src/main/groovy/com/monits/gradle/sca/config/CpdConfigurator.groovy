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
import com.monits.gradle.sca.task.CPDTask
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree

class CpdConfigurator implements AnalysisConfigurator {
    @Override
    void applyConfig(final Project project, final StaticCodeAnalysisExtension extension) {
        project.plugins.apply 'pmd'

        project.task('cpd', type: CPDTask) {
            ignoreFailures = extension.getIgnoreErrors()

            FileTree srcDir = project.fileTree("$project.projectDir/src/");
            srcDir.include '**/*.java'
            srcDir.exclude '**/gen/**'

            FileCollection collection = project.files(srcDir.getFiles());

            toolVersion = ToolVersions.pmdVersion
            inputFiles = collection
            outputFile = new File("$project.buildDir/reports/pmd/cpd.xml")
        }

        project.tasks.check.dependsOn project.tasks.cpd
    }

    @Override
    void applyAndroidConfig(final Project project, final StaticCodeAnalysisExtension extension) {
        applyConfig(project, extension) // no difference at all
    }
}
