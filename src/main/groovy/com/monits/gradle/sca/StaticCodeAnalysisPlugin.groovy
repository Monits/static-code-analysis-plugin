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
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.plugins.JavaBasePlugin

/**
 * Static code analysis plugin for Android and Java projects
*/
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

    @SuppressWarnings('UnnecessaryGetter')
    @Override
    void apply(Project project) {
        this.project = project
        extension = project.extensions.create(EXTENSION_NAME, StaticCodeAnalysisExtension)

        defineConfigurations()
        configureExtensionRule()

        // Apply Android Lint configuration
        withAndroidPlugins AndroidLintConfigurator

        project.afterEvaluate {
            // Populate scaconfig
            addDepsButModulesToScaconfig(project.configurations.compile)
            addDepsButModulesToScaconfig(project.configurations.testCompile)

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

    private void defineConfigurations() {
        project.configurations {
            archives {
                extendsFrom project.configurations.default
            }
            provided {
                dependencies.all { dep ->
                    project.configurations.default.exclude group:dep.group, module:dep.name
                }
            }
            compile.extendsFrom provided
            scaconfig // Custom configuration for static code analysis
            androidLint { // Configuration used for android linters
                transitive = false
                description = 'Extra Android lint rules to be used'
            }
        }
    }

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
            findbugsExclude = { FINDBUGS_DEFAULT_SUPPRESSION_FILTER }
            pmdRules = {
                if (ToolVersions.isLatestPmdVersion()) {
                    return [PMD_DEFAULT_RULES, PMD_DEFAULT_ANDROID_RULES]
                }

                [PMD_BACKWARDS_RULES, PMD_DEFAULT_ANDROID_RULES]
            }
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
    private void addDepsButModulesToScaconfig(config) {
        config.allDependencies.each {
            if (it in ModuleDependency && it.group == project.rootProject.name) {
                addDepsButModulesToScaconfig(
                    project.rootProject.findProject(':' + it.name).configurations[it.configuration])
            } else {
                project.dependencies.scaconfig it
            }
        }
    }

    private withAndroidPlugins(Class<AnalysisConfigurator> configClass) {
        AnalysisConfigurator configurator = configClass.newInstance()
        Action<? extends Plugin> configureAction = { configurator.applyAndroidConfig(project, extension) }

        withOptionalPlugin('com.android.build.gradle.AppPlugin', configureAction)
        withOptionalPlugin('com.android.build.gradle.LibraryPlugin', configureAction)
    }

    private withPlugin(Class<? extends Plugin> pluginClass, Class<AnalysisConfigurator> configClass) {
        AnalysisConfigurator  configurator = configClass.newInstance()
        Action<? extends Plugin> configureAction = { configurator.applyConfig(project, extension) }

        withPlugin(pluginClass, configureAction)
    }

    private withPlugin(Class<? extends Plugin> pluginClass, Action<? extends Plugin> configureAction) {
        project.plugins.withType(pluginClass, configureAction)
    }

    @SuppressWarnings(['ClassForName', 'EmptyCatchBlock'])
    private withOptionalPlugin(String pluginClassName, Action<? extends Plugin> configureAction) {
        try {
            // Will most likely throw a ClassNotFoundException
            Class<?> pluginClass = Class.forName(pluginClassName)
            withPlugin(pluginClass, configureAction)
        } catch (ClassNotFoundException e) {
            // do nothing
        }
    }
}
