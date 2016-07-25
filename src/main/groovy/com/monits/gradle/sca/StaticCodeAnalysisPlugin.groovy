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

import com.monits.gradle.sca.config.AnalysisConfigurator
import com.monits.gradle.sca.config.AndroidLintConfigurator
import com.monits.gradle.sca.config.CheckstyleConfigurator
import com.monits.gradle.sca.config.CpdConfigurator
import com.monits.gradle.sca.config.FindbugsConfigurator
import com.monits.gradle.sca.config.PmdConfigurator
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.plugins.JavaBasePlugin

/**
 * Static code analysis plugin for Android and Java projects
*/
@CompileStatic
class StaticCodeAnalysisPlugin implements Plugin<Project> {
    private final static String EXTENSION_NAME = 'staticCodeAnalysis'
    private final static String CHECKSTYLE_DEFAULT_RULES = 'http://static.monits.com/checkstyle.xml'
    private final static String CHECKSTYLE_BACKWARDS_RULES = 'http://static.monits.com/checkstyle-6.7.xml'
    private final static String PMD_DEFAULT_RULES = 'http://static.monits.com/pmd.xml'
    private final static String PMD_DEFAULT_ANDROID_RULES = 'http://static.monits.com/pmd-android.xml'
    private final static String PMD_BACKWARDS_RULES = 'http://static.monits.com/pmd-5.1.3.xml'
    private final static String FINDBUGS_DEFAULT_SUPPRESSION_FILTER =
            'http://static.monits.com/findbugs-exclusions-android.xml'

    private StaticCodeAnalysisExtension extension
    private Project project

    @CompileStatic(TypeCheckingMode.SKIP)
    @SuppressWarnings('UnnecessaryGetter')
    @Override
    void apply(Project project) {
        this.project = project
        extension = project.extensions.create(EXTENSION_NAME, StaticCodeAnalysisExtension)

        defineConfigurations()
        defineFindbugsAnnotationDependencies()
        configureExtensionRule()

        project.afterEvaluate {
            addDepsButModulesToScaconfig project.configurations.compile
            addDepsButModulesToScaconfig project.configurations.testCompile

            // Apply Android Lint configuration
            // must be done in `afterEvaluate` for compatibility with android plugin [1.0, 1.3)
            withAndroidPlugins AndroidLintConfigurator

            if (extension.getFindbugs()) {
                withAndroidPlugins FindbugsConfigurator
                withPlugin(JavaBasePlugin, FindbugsConfigurator)
            }

            if (extension.getCheckstyle()) {
                withAndroidPlugins CheckstyleConfigurator
                withPlugin(JavaBasePlugin, CheckstyleConfigurator)
            }

            if (extension.getPmd()) {
                withAndroidPlugins PmdConfigurator
                withPlugin(JavaBasePlugin, PmdConfigurator)
            }

            if (extension.getCpd()) {
                withAndroidPlugins CpdConfigurator
                withPlugin(JavaBasePlugin, CpdConfigurator)
            }
        }
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    private void defineConfigurations() {
        project.configurations {
            archives {
                extendsFrom project.configurations.default
            }
            provided {
                description = 'Compile only dependencies'
                dependencies.all { Dependency dep ->
                    project.configurations.default.exclude group:dep.group, module:dep.name
                }
            }
            compile.extendsFrom provided
            scaconfig { // Custom configuration for static code analysis
                description = 'Configuraton used for Static Code Analysis'
            }
            androidLint { // Configuration used for android linters
                transitive = false
                description = 'Extra Android lint rules to be used'
            }
        }
    }

    // This should be done when actually configuring Findbugs, but can't be inside an afterEvaluate
    // See: https://code.google.com/p/android/issues/detail?id=208474
    @CompileStatic(TypeCheckingMode.SKIP)
    private void defineFindbugsAnnotationDependencies() {
        project.repositories {
            maven {
                url 'http://nexus.monits.com/content/repositories/oss-snapshots'
            }
        }
        project.dependencies {
            provided('com.google.code.findbugs:annotations:' + ToolVersions.findbugsVersion) {
                /*
                 * This jar both includes and depends on jcip and jsr-305. One is enough
                 * See https://github.com/findbugsproject/findbugs/issues/94
                 */
                transitive = false
            }
        }
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    private void configureExtensionRule() {
        extension.conventionMapping.with {
            ignoreErrors = { true }
            findbugs = { true }
            pmd = { true }
            checkstyle = { true }
            cpd = { true }
            checkstyleRules = {
                if (ToolVersions.isLatestCheckstyleVersion()) {
                    return CHECKSTYLE_DEFAULT_RULES
                }

                CHECKSTYLE_BACKWARDS_RULES
            }
            pmdRules = {
                if (ToolVersions.isLatestPmdVersion()) {
                    return [PMD_DEFAULT_RULES, PMD_DEFAULT_ANDROID_RULES]
                }

                [PMD_BACKWARDS_RULES, PMD_DEFAULT_ANDROID_RULES]
            }
        }

        // default suppression filter for findbugs for Android
        withAndroidPlugins {
            extension.conventionMapping.with {
                findbugsExclude = { FINDBUGS_DEFAULT_SUPPRESSION_FILTER }
            }
        }

        extension.sourceSetConfig = project.container(RulesConfig) { String name ->
            new RulesConfig(name, extension)
        }
    }

    /**
     * Adds all dependencies except modules from given config to scaconfig.
     *
     * Modules are skipped, but transient dependencies are added
     * (and transient modules skipped).
     *
     * @param config The config whose dependencies are to be added to scaconfig
     */
    @CompileStatic(TypeCheckingMode.SKIP)
    private void addDepsButModulesToScaconfig(final Configuration config) {
        // support lazy dependency configuration
        config.allDependencies.all {
            if (it in ProjectDependency && it.group == project.rootProject.name) {
                // support lazy configuration creation
                project.rootProject.findProject(':' + it.name).configurations.all { c ->
                    if (c.name == it.configuration) {
                        addDepsButModulesToScaconfig(c)
                    }
                }
            } else {
                // TODO : This includes @aar packages that aren't understood by our tools. Filter them?
                project.dependencies.scaconfig it
            }
        }
    }

    private withAndroidPlugins(final Class<AnalysisConfigurator> configClass) {
        AnalysisConfigurator configurator = configClass.newInstance()
        Action<? extends Plugin> configureAction = { configurator.applyAndroidConfig(project, extension) }

        withAndroidPlugins configureAction
    }

    private withPlugin(final Class<? extends Plugin> pluginClass, final Class<AnalysisConfigurator> configClass) {
        AnalysisConfigurator  configurator = configClass.newInstance()
        Action<? extends Plugin> configureAction = { configurator.applyConfig(project, extension) }

        withPlugin(pluginClass, configureAction)
    }

    private withPlugin(final Class<? extends Plugin> pluginClass, final Action<? extends Plugin> configureAction) {
        project.plugins.withType(pluginClass, configureAction)
    }

    private withAndroidPlugins(final Action<? extends Plugin> configureAction) {
        withOptionalPlugin('com.android.build.gradle.AppPlugin', configureAction)
        withOptionalPlugin('com.android.build.gradle.LibraryPlugin', configureAction)
    }

    @SuppressWarnings(['ClassForName', 'EmptyCatchBlock'])
    private withOptionalPlugin(final String pluginClassName, final Action<? extends Plugin> configureAction) {
        try {
            // Will most likely throw a ClassNotFoundException
            Class<?> pluginClass = Class.forName(pluginClassName)
            if (Plugin.isAssignableFrom(pluginClass)) {
                withPlugin(pluginClass as Class<? extends Plugin>, configureAction)
            }
        } catch (ClassNotFoundException e) {
            // do nothing
        }
    }
}
