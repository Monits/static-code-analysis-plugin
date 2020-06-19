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
package com.monits.gradle.sca.utils

import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider

/**
 * Utility class to manage versions of tools being used by the plugin.
*/
@CompileStatic
final class TaskUtils {
    private TaskUtils() {
        throw new AssertionError("TaskUtils can't be instantiated" as Object)
    }

    static TaskProvider<Task> registerTask(final Project project, final String taskName) {
        registerTask(project, taskName, Task)
    }

    static <T extends Task> TaskProvider<T> registerTask(final Project project, final String taskName, Class<T> taskType) {
        if (project.tasks.names.contains(taskName)) {
            project.tasks.named(taskName, taskType)
        } else {
            project.tasks.register(taskName, taskType)
        }
    }
}
