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

import com.monits.gradle.sca.AndroidHelper
import com.monits.gradle.sca.dsl.StaticCodeAnalysisExtension
import com.monits.gradle.sca.task.CleanupAndroidLintTask
import com.monits.gradle.sca.task.ResolveAndroidLintTask
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.gradle.api.DomainObjectSet
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.CopySpec
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.provider.ProviderInternal
import org.gradle.api.specs.Specs
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.TaskOutputs
import org.gradle.api.tasks.TaskProvider
import static com.monits.gradle.sca.utils.TaskUtils.registerTask

/**
 * A configurator for Android Lint tasks.
*/
@CompileStatic
class AndroidLintConfigurator implements AnalysisConfigurator {
    private static final String USE_JACK_PROPERTY_NAME = 'useJack'
    private static final String JACK_OPTIONS_PROPERTY_NAME = 'jackOptions'
    private static final String ANDROID = 'android'
    private static final String LINT_OPTIONS = 'lintOptions'
    private static final String GLOBAL_LINT_TASK_NAME = 'lint'
    private static final String XML = 'xml'
    private static final String HTML = 'html'

    private final RemoteConfigLocator configLocator = new RemoteConfigLocator(ANDROID)

    @Override
    void applyConfig(final Project project, final StaticCodeAnalysisExtension extension) {
        // nothing to do for non-android projects
    }

    @SuppressWarnings(['UnnecessaryGetter', 'Instanceof'])
    @Override
    void applyAndroidConfig(final Project project, final StaticCodeAnalysisExtension extension) {
        Class<? extends Task> lintTask = getLintTaskClass(project)

        // Lazily setup all lint tasks without requiring them to be created
        project.tasks.names.each { String taskName ->
            if (taskName != 'lintFix') { // The new lintFix task in AGP 3.5.0 should not be altered
                TaskProvider<Task> tp = project.tasks.named(taskName)
                if (tp instanceof ProviderInternal) {
                    // explicit getter call - see GROOVY-7149
                    if (lintTask.isAssignableFrom(tp.getType())) {
                        setupTasks(tp, project, extension)

                        configureLintTask(project, extension, tp)
                    }
                }
            }
        }
    }

    private static Class<? extends Task> getLintTaskClass(final Project project) {
        try {
            // AGP 3.0+
            return AndroidLintConfigurator.classLoader
                .loadClass('com.android.build.gradle.tasks.LintBaseTask') as Class<? extends Task>
        } catch (ClassNotFoundException ignored) {
            try {
                // Older versions
                return AndroidLintConfigurator.classLoader
                    .loadClass('com.android.build.gradle.tasks.Lint') as Class<? extends Task>
            } catch (ClassNotFoundException e) {
                // Something went wrong!
                warnUnexpectedException(project, 'Encountered an error trying to configure Android Lint tasks.', e)

                // Best effort, get the global lint task class (needs to be configured!)
                project.tasks.getByName(GLOBAL_LINT_TASK_NAME).class
            }
        }
    }

    private static void setupTasks(final TaskProvider<Task> lintTask, final Project project,
                                   final StaticCodeAnalysisExtension extension) {
        // This method is called once a lint task is created and configured, so we can be eager
        TaskProvider<ResolveAndroidLintTask> resolveTask =
            registerTask(project, 'resolveAndroidLint', ResolveAndroidLintTask)
        TaskProvider<CleanupAndroidLintTask> cleanupTask =
            registerTask(project, 'cleanupAndroidLint', CleanupAndroidLintTask)

        lintTask.configure { Task t ->
            t.dependsOn resolveTask
            t.finalizedBy cleanupTask

            // Tasks should be skipped if disabled by extension
            t.onlyIf { extension.androidLint }
        }

        // Tasks should be skipped if disabled by extension
        resolveTask.configure { Task t ->
            t.onlyIf { extension.androidLint }
        }
        cleanupTask.configure { Task t ->
            t.onlyIf { extension.androidLint }
        }
    }

    @SuppressWarnings(['NoDef', 'VariableTypeRequired']) // can't specify a type without depending on Android
    private static void configureLintOptions(final Project project, final StaticCodeAnalysisExtension extension,
                                             final File configSource, final TaskProvider<Task> lintTask) {
        def lintOptions = project[ANDROID][LINT_OPTIONS]

        // update global config
        lintOptions.with { it ->
            // TODO : This won't fail on warnings, just like Checkstyle.
            // See https://issues.gradle.org/browse/GRADLE-2888
            it['abortOnError'] = !extension.ignoreErrors

            // Update global config
            it['lintConfig'] = configSource
        }

        // Make sure the task has the updated global config
        lintTask.configure { Task task ->
            task[LINT_OPTIONS] = lintOptions
        }
    }

    @SuppressWarnings('CatchThrowable') // yes, we REALLY want to be that generic
    private void configureLintTask(final Project project, final StaticCodeAnalysisExtension extension,
                                    final TaskProvider<Task> lintTask) {
        File config = obtainLintRules(project, extension, lintTask)

        configureLintOptions(project, extension, config, lintTask)

        try {
            /*
             * Android 3.5.0 started adding dependency strings such as
             * "annotations.jar (com.google.code.findbugs:annotations:3.0.1)" as inputs, so when caching
             * the task, it would try to fingerprint those inputs / outputs and fail, producing error messages
             * and stacktraces.
             * See https://issuetracker.google.com/issues/141126614
             */
            if (AndroidHelper.shouldAddLintInputsAndOutputs(project)) {
                lintTask.configure { Task t ->
                    configureLintInputsAndOutputs(project, t)

                    // Allow to cache task result on Gradle 3+!
                    t.outputs.cacheIf(Specs.SATISFIES_ALL)
                }
            }
        } catch (Throwable e) {
            // Something went wrong!
            warnUnexpectedException(project, 'Encountered an error trying to set inputs and outputs for Android Lint ' +
                    'tasks, it will be disabled.', e)

            // disable up-to-date caching
            lintTask.configure { Task t ->
                t.outputs.upToDateWhen {
                    false
                }
            }
        }

        if (lintTask.name == GLOBAL_LINT_TASK_NAME) {
            // Change output location for consistency with other plugins
            // we copy as to not tamper with other lint tasks
            TaskProvider<Copy> copyLintReportTask = project.tasks.register('copyLintReport', Copy)
            copyLintReportTask.configure { Copy it ->
                FileCollection xmlFiles = xmlOutputs(lintTask.get())

                it.from(xmlFiles.singleFile.parent) { CopySpec cs ->
                    cs.include '*.xml'
                }
                it.into project.file("${project.buildDir}/reports/android/")
                it.rename '.*', 'lint-results.xml'

                it.onlyIf { extension.androidLint }
            }

            lintTask.configure { Task t ->
                FileCollection xmlFiles = xmlOutputs(t)
                if (!xmlFiles.empty) {
                    t.finalizedBy copyLintReportTask
                }
            }
        }
    }

    private FileCollection xmlOutputs(Task task) {
        FileCollection files = task.outputs.files.filter { File f -> f.name.endsWith('.xml') }
        if (!files.empty) {
            return files
        }

        task.project.files(AndroidHelper.getLintReportDir(task.project) + 'lint-results.xml')
    }

    private static void warnUnexpectedException(final Project project, final String message, final Throwable e) {
        project.logger.warn(message + ' Please, report this incident at ' +
            'https://github.com/monits/static-code-analysis-plugin/issues', e)
    }

    private File obtainLintRules(final Project project, final StaticCodeAnalysisExtension config,
                                    final TaskProvider<Task> lintTask) {
        boolean remoteLocation = RemoteConfigLocator.isRemoteLocation(config.androidLintConfig)
        File configSource

        if (remoteLocation) {
            String downloadTaskName = 'downloadAndroidLintConfig'
            configSource = configLocator.makeDownloadFileTask(project, config.androidLintConfig,
                String.format('android-lint-%s.xml', project.name), downloadTaskName)

            lintTask.configure { Task t ->
                t.dependsOn project.tasks.named(downloadTaskName)
            }
        } else {
            configSource = new File(config.androidLintConfig)
        }

        configSource
    }

    @SuppressWarnings(['NoDef', 'VariableTypeRequired']) // can't specify a type without depending on Android
    @CompileStatic(TypeCheckingMode.SKIP)
    private static void configureLintInputsAndOutputs(final Project project, final Task lintTask) {
        def lintOptions = project.android.lintOptions

        /*
         * Android doesn't define inputs nor outputs for lint tasks, so they will rerun each time.
         * This is an experimental best effort to what I believe it should look like...
         * See: https://code.google.com/p/android/issues/detail?id=209497
         */
        boolean xmlEnabled = lintOptions.xmlReport
        File xmlOutput = lintOptions.xmlOutput

        boolean htmlEnabled = lintOptions.htmlReport
        File htmlOutput = lintOptions.htmlOutput

        DomainObjectSet<?> variants = getVariants(project)

        String variantName = AndroidHelper.lintTaskHasVariantInfo(project) && lintTask.name != GLOBAL_LINT_TASK_NAME ?
            lintTask.variantName : (lintTask.name.toLowerCase() - GLOBAL_LINT_TASK_NAME)

        // Older plugins didn't setup input files, so up-to-date checks were futile
        if (lintTask.inputs.files.empty) {
            variants.matching { it.name == variantName || variantName == null || variantName.empty }.all {
                def configuration = it.variantData.variantConfiguration
                String variantDirName = configuration.dirName

                lintTask.inputs.with {
                    dir("${project.buildDir}/intermediates/classes/${variantDirName}/")
                    dir("${project.buildDir}/intermediates/assets/${variantDirName}/")
                    dir("${project.buildDir}/intermediates/manifests/full/${variantDirName}/")
                    dir("${project.buildDir}/intermediates/res/merged/${variantDirName}/")
                    dir("${project.buildDir}/intermediates/shaders/${variantDirName}/")
                    dir("${project.buildDir}/intermediates/rs/${variantDirName}/")
                }
            }
        }

        // And none up to this date setup outputs for up-to-date checks and cache
        if (variantName == null || variantName.empty) {
            boolean configFound = false
            variants.all {
                def configuration = it.variantData.variantConfiguration
                if (!configFound && configuration.buildType.debuggable && !usesJack(configuration)) {
                    configFound = true

                    addReportAsOutput(lintTask, project, xmlEnabled, xmlOutput, it.name, XML)
                    addReportAsOutput(lintTask, project, htmlEnabled, htmlOutput, it.name, HTML)
                }
            }
        } else {
            addReportAsOutput(lintTask, project, xmlEnabled, xmlOutput, variantName, XML)
            addReportAsOutput(lintTask, project, htmlEnabled, htmlOutput, variantName, HTML)
        }
    }

    @SuppressWarnings(['NoDef', 'MethodParameterTypeRequired']) // can't specify a type without depending on Android
    @CompileStatic(TypeCheckingMode.SKIP)
    private static boolean usesJack(final def configuration) {
        // Newer plugin versions have a merged jack config on the config
        if (configuration.hasProperty(JACK_OPTIONS_PROPERTY_NAME) && configuration.jackOptions.enabled != null) {
            return configuration.jackOptions.enabled
        }

        // Any flavors?
        if (configuration.hasFlavors()) {
            for (def pf : configuration.productFlavors) {
                if (pf.hasProperty(JACK_OPTIONS_PROPERTY_NAME) && pf.jackOptions.enabled != null) {
                    return pf.jackOptions.enabled
                }
            }
        }

        // default config?
        if (configuration.defaultConfig.hasProperty(JACK_OPTIONS_PROPERTY_NAME) &&
                configuration.defaultConfig.jackOptions.enabled != null) {
            return configuration.defaultConfig.jackOptions.enabled
        }

        // Fallback for older versions, use old property
        if (configuration.hasProperty(USE_JACK_PROPERTY_NAME) && configuration.useJack != null) {
            return configuration.useJack
        }

        if (configuration.buildType.hasProperty(USE_JACK_PROPERTY_NAME) && configuration.buildType.useJack != null) {
            return configuration.buildType.useJack
        }

        // default is false, plugin is too old or too new to know anything about jack
        false
    }

    @SuppressWarnings('DuplicateStringLiteral')
    private static DomainObjectSet<?> getVariants(final Project project) {
        if (project[ANDROID].hasProperty('libraryVariants')) {
            return project[ANDROID]['libraryVariants'] as DomainObjectSet
        }

        project[ANDROID]['applicationVariants'] as DomainObjectSet
    }

    @SuppressWarnings('ParameterCount')
    private static void addReportAsOutput(final Task task, final Project project, final boolean isEnabled,
                                          final File output, final String variantName, final String extension) {
        if (isEnabled) {
            File definiteOutput = output
            if (!output) {
                // Convention naming changed along the way
                if (variantName) {
                    definiteOutput = project.file(
                        AndroidHelper.getLintReportDir(project) + "lint-results-${variantName}.${extension}")
                } else {
                    definiteOutput = project.file(AndroidHelper.getLintReportDir(project) + "lint-results.${extension}")
                }
            }
            task.outputs.file definiteOutput
        }
    }
}
