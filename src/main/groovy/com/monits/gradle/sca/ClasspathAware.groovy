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
package com.monits.gradle.sca

import org.gradle.api.Project
import org.gradle.api.Task

trait ClasspathAware {
    void configAndroidClasspath(final Task task, final Project project) {
        def t = project.tasks.findByName('mockableAndroidJar');
        if (t != null) {
            it.dependsOn t
        }

        // Manually add classes of module dependencies
        def classTree = project.files()
        project.fileTree(dir: "${project.buildDir}/intermediates/exploded-aar/${project.rootProject.name}/", include: "*/unspecified/").visit({
            if (!it.isDirectory()) return;
            if (it.path.contains('/')) return;
            classTree += getProjectClassTree(it.path)
        })

        task.classpath = project.configurations.scaconfig +
                project.fileTree(dir: "${project.buildDir}/intermediates/exploded-aar/", include: '**/*.jar',
                        exclude: "${project.rootProject.name}/*/unspecified/jars/classes.jar") +
                project.fileTree(dir: "${project.buildDir}/intermediates/", include: 'mockable-android-*.jar') +
                classTree
    }
}
