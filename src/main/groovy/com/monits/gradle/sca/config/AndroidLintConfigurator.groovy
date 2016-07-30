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

    @Override
    void applyConfig(final Project project, final StaticCodeAnalysisExtension extension) {
        // nothing to do for non-android projects
    }

    @SuppressWarnings('CatchThrowable') // yes, we REALLY want to be that generic
    @Override
    void applyAndroidConfig(final Project project, final StaticCodeAnalysisExtension extension) {
        Task t = project.tasks.findByName('lint')
        if (t == null) {
            return
        }

        t.dependsOn project.tasks.create('resolveAndroidLint', ResolveAndroidLintTask)
        t.finalizedBy project.tasks.create('cleanupAndroidLint', CleanupAndroidLintTask)

        try {
            configureLintInputsAndOutputs(project, t)
        } catch (Throwable e) {
            // Something went wrong!
            project.logger.warn('Encountered an error trying to set inputs and outputs for Android Lint ' +
                    'tasks, it will be disabled. Please, report this incident in ' +
                    'https://github.com/monits/static-code-analysis-plugin/issues', e)

            // disable up-to-date caching
            t.outputs.upToDateWhen {
                false
            }
        }
    }

    @SuppressWarnings('NoDef') // can't specify a type without depending on Android
    @CompileStatic(TypeCheckingMode.SKIP)
    private void configureLintInputsAndOutputs(final Project project, final Task lintTask) {
        /*
         * Android doesn't define inputs nor outputs for lint tasks, so they will rerun each time.
         * This is an experimental best effort to what I believe it should look like...
         * See: https://code.google.com/p/android/issues/detail?id=209497
         */
        boolean xmlEnabled = project.android.lintOptions.xmlReport
        File xmlOutput = project.android.lintOptions.xmlOutput

        boolean htmlEnabled = project.android.lintOptions.htmlReport
        File htmlOutput = project.android.lintOptions.htmlOutput

        boolean reportFatal = project.android.lintOptions.checkReleaseBuilds

        DomainObjectSet<?> variants = getVariants(project)

        String defaultReportVariant = null
        variants.all {
            if (!defaultReportVariant && it.variantData.variantConfiguration.buildType.isDebuggable() &&
                    !usesJack(it.variantData.variantConfiguration)) {
                defaultReportVariant = it.name

                addReportAsOutput(lintTask, project, xmlEnabled, xmlOutput, defaultReportVariant, 'xml')
                addReportAsOutput(lintTask, project, htmlEnabled, htmlOutput, defaultReportVariant, 'html')
            }

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

            // This logic is copy-pasted from Android's TaskManager.createLintVitalTask
            if (reportFatal && !configuration.buildType.isDebuggable() && !usesJack(configuration)) {
                lintTask.outputs.with {
                    if (xmlEnabled) {
                        file("${project.buildDir}/outputs/lint-results-${variantName}-fatal.xml")
                    }
                    if (htmlEnabled) {
                        file("${project.buildDir}/outputs/lint-results-${variantName}-fatal.hmtl")
                    }
                }
            }
        }
    }

    @SuppressWarnings('NoDef') // can't specify a type without depending on Android
    @CompileStatic(TypeCheckingMode.SKIP)
    private boolean usesJack(final def configuration) {
        (configuration.hasProperty(USE_JACK_PROPERTY_NAME) && configuration.useJack) ||
            (configuration.buildType.hasProperty(USE_JACK_PROPERTY_NAME) && configuration.buildType.useJack) ||
            (configuration.hasProperty('jackOptions') && configuration.jackOptions.enabled)
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    private DomainObjectSet<?> getVariants(final Project project) {
        if (project.android.hasProperty('libraryVariants')) {
            return project.android.libraryVariants
        }

        project.android.applicationVariants
    }

    @SuppressWarnings('ParameterCount')
    private void addReportAsOutput(final Task task, final Project project, final boolean isEnabled,
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

    private boolean lintReportPerVariant(final Task task) {
        if (!task.hasProperty(ANDROID_GRADLE_VERSION_PROPERTY_NAME)) {
            return false
        }

        String versionStr = task.property(ANDROID_GRADLE_VERSION_PROPERTY_NAME) as String
        VersionNumber.parse(versionStr) >= ANDROID_GRADLE_VERSION_2_0_0
    }
}
