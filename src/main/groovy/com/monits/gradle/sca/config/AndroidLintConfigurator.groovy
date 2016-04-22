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
import com.monits.gradle.sca.task.CleanupAndroidLintTask
import com.monits.gradle.sca.task.ResolveAndroidLintTask
import org.gradle.api.Project;

class AndroidLintConfigurator implements AnalysisConfigurator {
    @Override
    void applyConfig(final Project project, final StaticCodeAnalysisExtension extension) {
        // nothing to do for non-android projects
    }

    @Override
    void applyAndroidConfig(final Project project, final StaticCodeAnalysisExtension extension) {
        def t = project.tasks.findByName('lint');
        if (t == null) {
            return;
        }

        t.dependsOn project.tasks.create('resolveAndroidLint', ResolveAndroidLintTask)
        t.finalizedBy project.tasks.create('cleanupAndroidLint', CleanupAndroidLintTask)
    }
}
