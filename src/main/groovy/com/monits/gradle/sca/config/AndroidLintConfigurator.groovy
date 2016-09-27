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
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.gradle.api.DomainObjectSet
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.util.VersionNumber

/**
 * A configurator for Android Lint tasks.
*/
@CompileStatic
class AndroidLintConfigurator implements AnalysisConfigurator {
    private static final String ANDROID_GRADLE_VERSION_PROPERTY_NAME = 'androidGradlePluginVersion'
    private static final VersionNumber ANDROID_GRADLE_VERSION_2_0_0 = VersionNumber.parse('2.0.0')
    private static final String USE_JACK_PROPERTY_NAME = 'useJack'
    private static final String JACK_OPTIONS_PROPERTY_NAME = 'jackOptions'

    private final RemoteConfigLocator configLocator = new RemoteConfigLocator('android')

    @Override
    void applyConfig(final Project project, final StaticCodeAnalysisExtension extension) {
        // nothing to do for non-android projects
    }

    @Override
    void applyAndroidConfig(final Project project, final StaticCodeAnalysisExtension extension) {
        Task t = project.tasks.findByName('lint')
        if (t == null) {
            return
        }

        t.dependsOn project.tasks.create('resolveAndroidLint', ResolveAndroidLintTask)
        t.finalizedBy project.tasks.create('cleanupAndroidLint', CleanupAndroidLintTask)

        configureLintTask(project, extension, t)
    }

    @SuppressWarnings(['UnnecessaryGetter', 'CatchThrowable']) // yes, we REALLY want to be that generic
    @CompileStatic(TypeCheckingMode.SKIP)
    private void configureLintTask(final Project project, final StaticCodeAnalysisExtension extension,
                                    final Task lintTask) {
        // TODO : This won't fail on warnings, just like Checkstyle. See https://issues.gradle.org/browse/GRADLE-2888
        project.android.lintOptions.abortOnError = !extension.getIgnoreErrors()

        // Change output location for consistency with other plugins
        project.android.lintOptions.xmlOutput = project.file("${project.buildDir}/reports/android/lint-results.xml")

        configureLintRules(project, extension, lintTask)

        // Tasks should be skipped if disabled by extension
        lintTask.onlyIf { extension.getAndroidLint() }
        lintTask.dependsOn.find { it in ResolveAndroidLintTask }.onlyIf { extension.getAndroidLint() }
        lintTask.finalizedBy.getDependencies(lintTask)
            .find { it in CleanupAndroidLintTask }.onlyIf { extension.getAndroidLint() }

        try {
            configureLintInputsAndOutputs(project, lintTask)
        } catch (Throwable e) {
            // Something went wrong!
            project.logger.warn('Encountered an error trying to set inputs and outputs for Android Lint ' +
                    'tasks, it will be disabled. Please, report this incident in ' +
                    'https://github.com/monits/static-code-analysis-plugin/issues', e)

            // disable up-to-date caching
            lintTask.outputs.upToDateWhen {
                false
            }
        }
    }

    @SuppressWarnings('UnnecessaryGetter')
    @CompileStatic(TypeCheckingMode.SKIP)
    private void configureLintRules(final Project project, final StaticCodeAnalysisExtension config,
                                    final Task lintTask) {
        boolean remoteLocation = RemoteConfigLocator.isRemoteLocation(config.getAndroidLintConfig())
        File configSource

        if (remoteLocation) {
            String downloadTaskName = 'downloadAndroidLintConfig'
            configSource = configLocator.makeDownloadFileTask(project, config.getAndroidLintConfig(),
                String.format('android-lint-%s.xml', project.name), downloadTaskName)

            lintTask.dependsOn project.tasks.findByName(downloadTaskName)
        } else {
            configSource = new File(config.getAndroidLintConfig())
        }

        // Update global config
        project.android.lintOptions.lintConfig configSource

        // Make sure the task has the updated global config
        lintTask.lintOptions = project.android.lintOptions
    }

    @SuppressWarnings('NoDef') // can't specify a type without depending on Android
    @CompileStatic(TypeCheckingMode.SKIP)
    private static void configureLintInputsAndOutputs(final Project project, final Task lintTask) {
        /*
         * Android doesn't define inputs nor outputs for lint tasks, so they will rerun each time.
         * This is an experimental best effort to what I believe it should look like...
         * See: https://code.google.com/p/android/issues/detail?id=209497
         */
        boolean xmlEnabled = project.android.lintOptions.xmlReport
        File xmlOutput = project.android.lintOptions.xmlOutput

        boolean htmlEnabled = project.android.lintOptions.htmlReport
        File htmlOutput = project.android.lintOptions.htmlOutput

        DomainObjectSet<?> variants = getVariants(project)

        String defaultReportVariant = null
        variants.all {
            def configuration = it.variantData.variantConfiguration
            String variantName = it.name
            String variantDirName = configuration.dirName

            lintTask.inputs.with {
                dir("${project.buildDir}/intermediates/classes/${variantDirName}/")
                dir("${project.buildDir}/intermediates/assets/${variantDirName}/")
                dir("${project.buildDir}/intermediates/manifests/full/${variantDirName}/")
                dir("${project.buildDir}/intermediates/res/merged/${variantDirName}/")
                dir("${project.buildDir}/intermediates/shaders/${variantDirName}/")
                dir("${project.buildDir}/intermediates/rs/${variantDirName}/")
            }

            if (!defaultReportVariant && configuration.buildType.isDebuggable() && !usesJack(configuration)) {
                defaultReportVariant = variantName

                addReportAsOutput(lintTask, project, xmlEnabled, xmlOutput, defaultReportVariant, 'xml')
                addReportAsOutput(lintTask, project, htmlEnabled, htmlOutput, defaultReportVariant, 'html')
            }
        }
    }

    @SuppressWarnings('NoDef') // can't specify a type without depending on Android
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

        // default is false, plugin is too old to know anything about jack
        false
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    private static DomainObjectSet<?> getVariants(final Project project) {
        if (project.android.hasProperty('libraryVariants')) {
            return project.android.libraryVariants
        }

        project.android.applicationVariants
    }

    /*
     * The signature of TaskInputs.file(Object) changed, we need to skip @CompileStatic for backwards compatibility
     * with Gradle 2.x. Remove it once we drop support for 2.x.
     */
    @SuppressWarnings('ParameterCount')
    @CompileStatic(TypeCheckingMode.SKIP)
    private static void addReportAsOutput(final Task task, final Project project, final boolean isEnabled,
                                          final File output, final String variantName, final String extension) {
        if (isEnabled) {
            File definiteOutput = output
            if (!output) {
                // Convention naming changed along the way
                if (lintReportPerVariant(task)) {
                    definiteOutput = project.file(
                            "${project.buildDir}/outputs/lint-results-${variantName}.${extension}")
                } else {
                    definiteOutput = project.file("${project.buildDir}/outputs/lint-results.${extension}")
                }
            }
            task.outputs.file definiteOutput
        }
    }

    private static boolean lintReportPerVariant(final Task task) {
        if (!task.hasProperty(ANDROID_GRADLE_VERSION_PROPERTY_NAME)) {
            return false
        }

        String versionStr = task.property(ANDROID_GRADLE_VERSION_PROPERTY_NAME) as String
        VersionNumber.parse(versionStr) >= ANDROID_GRADLE_VERSION_2_0_0
    }
}
