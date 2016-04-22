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
import com.monits.gradle.sca.config.CpdConfigurator
import com.monits.gradle.sca.config.PmdConfigurator
import com.monits.gradle.sca.task.DownloadTask
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.file.FileTree
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.FindBugs
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.util.GradleVersion

class StaticCodeAnalysisPlugin implements Plugin<Project> {
    private final static String LATEST_CHECKSTYLE_VERSION = '6.17'
    private final static String BACKWARDS_CHECKSTYLE_VERSION = '6.7'
    private final static GradleVersion GRADLE_VERSION_CHECKSTYLE = GradleVersion.version('2.7');

    private final static String FINDBUGS_ANNOTATIONS_VERSION = '3.0.0'
    private final static String FINDBUGS_TOOL_VERSION = '3.0.1'
    private final static String FINDBUGS_MONITS_VERSION = '0.2.0-SNAPSHOT'
    private final static String FB_CONTRIB_VERSION = '6.6.1'

    private final static String EXTENSION_NAME = "staticCodeAnalysis";
    private final static String CHECKSTYLE_DEFAULT_RULES = "http://static.monits.com/checkstyle.xml"
    private final static String CHECKSTYLE_BACKWARDS_RULES = "http://static.monits.com/checkstyle-6.7.xml"
    private final static String PMD_DEFAULT_RULES = "http://static.monits.com/pmd.xml";
    private final static String PMD_DEFAULT_ANDROID_RULES = "http://static.monits.com/pmd-android.xml";
    private final static String PMD_BACKWARDS_RULES = "http://static.monits.com/pmd-5.1.3.xml";
    private static final String FINDBUGS_DEFAULT_SUPPRESSION_FILTER = "http://static.monits.com/findbugs-exclusions-android.xml"

    private String currentCheckstyleVersion = LATEST_CHECKSTYLE_VERSION;

    private StaticCodeAnalysisExtension extension;

    private Project project;

    def void apply(Project project) {
        this.project = project
        extension = project.extensions.create(EXTENSION_NAME, StaticCodeAnalysisExtension)

        createConfigurations()
        configureExtensionRule()

        //FIXME: This is here so that projects that use Findbugs can compile... but it ignores DSL completely
        project.repositories {
            maven {
                url 'http://nexus.monits.com/content/repositories/oss-snapshots'
            }
        }

        project.dependencies {
            provided 'com.google.code.findbugs:annotations:' + FINDBUGS_ANNOTATIONS_VERSION
        }

        // Apply Android Lint configuration
        withAndroidPlugins AndroidLintConfigurator

        project.afterEvaluate {
            // Populate scaconfig
            addDepsButModulesToScaconfig(project.configurations.compile)
            addDepsButModulesToScaconfig(project.configurations.testCompile)

            // Make sure versions and config are ok
            checkVersions();

            if (extension.getFindbugs()) {
                findbugs();
            }

            if (extension.getCheckstyle()) {
                checkstyle();
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

    private void createConfigurations() {
        project.configurations {
            archives {
                extendsFrom project.configurations.default
            }
            provided {
                dependencies.all { dep ->
                    project.configurations.default.exclude group: dep.group, module: dep.name
                }
            }
            compile.extendsFrom provided
            scaconfig // Custom configuration for static code analysis
            androidLint { // Configuration used for android linters
                transitive = false
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
            checkstyleRules = { CHECKSTYLE_DEFAULT_RULES }
            findbugsExclude = { FINDBUGS_DEFAULT_SUPPRESSION_FILTER }
            pmdRules = {
                if (ToolVersions.isLatestPmdVersion()) {
                    return [PMD_DEFAULT_RULES, PMD_DEFAULT_ANDROID_RULES]
                } else {
                    return [PMD_BACKWARDS_RULES, PMD_DEFAULT_ANDROID_RULES]
                }
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
            if (it instanceof ModuleDependency && it.group.equals(project.rootProject.name)) {
                addDepsButModulesToScaconfig(project.rootProject.findProject(':' + it.name).configurations[it.getConfiguration()])
            } else {
                project.dependencies.scaconfig it
            }
        }
    }

    private void checkVersions() {
        project.task("checkstyleVersionCheck") {
            if (GradleVersion.current() < GRADLE_VERSION_CHECKSTYLE) {
                currentCheckstyleVersion = BACKWARDS_CHECKSTYLE_VERSION;
                /*
                    If checkstyleRules are equal to "http://static.monits.com/checkstyle.xml",
                    that means the user has not defined its own rules. So its the plugins
                    responsibility to check for compatible ones.
                */
                if (extension.getCheckstyleRules().equals(CHECKSTYLE_DEFAULT_RULES)) {
                    extension.setCheckstyleRules(CHECKSTYLE_BACKWARDS_RULES);
                }
            } else {
                currentCheckstyleVersion = LATEST_CHECKSTYLE_VERSION;
            }
        }
    }

    private boolean isRemoteLocation(String path) {
        return path.startsWith("http://") || path.startsWith("https://");
    }

    private File createDownloadFileTask(String remotePath, String destination, String taskName, String plugin) {
        def destPath = "${project.rootDir}/config/${plugin}/"
        def File destFile = project.file(destPath + destination)

        project.task(taskName, type: DownloadTask) {
            directory = project.file(destPath)
            downloadedFile = destFile
            resourceUri = remotePath
        }

        return destFile;
    }

    private void checkstyle() {
        project.plugins.apply 'checkstyle'

        boolean remoteLocation = isRemoteLocation(extension.getCheckstyleRules());
        File configSource;
        String downloadTaskName = "downloadCheckstyleXml"
        if (remoteLocation) {
            configSource = createDownloadFileTask(extension.getCheckstyleRules(), "checkstyle.xml",
                    downloadTaskName, "checkstyle");
        } else {
            configSource = new File(extension.getCheckstyleRules());
        }

        project.checkstyle {
            toolVersion = currentCheckstyleVersion
            ignoreFailures = extension.getIgnoreErrors();
            showViolations = false
            configFile configSource
        }

        project.task("checkstyle", type: Checkstyle) {
            dependsOn project.tasks.checkstyleVersionCheck

            if (remoteLocation) {
                dependsOn project.tasks.findByName(downloadTaskName)
            }
            source 'src'
            include '**/*.java'
            exclude '**/gen/**'
            classpath = project.configurations.compile
        }

        project.tasks.check.dependsOn project.tasks.checkstyle
    }

    private void findbugs() {

        project.plugins.apply 'findbugs'

        project.dependencies {
            findbugs project.configurations.findbugsPlugins.dependencies

            // To keep everything tidy, we set these apart
            findbugsPlugins('com.monits:findbugs-plugin:' + FINDBUGS_MONITS_VERSION) {
                transitive = false
            }
            findbugsPlugins 'com.mebigfatguy.fb-contrib:fb-contrib:' + FB_CONTRIB_VERSION
        }

        boolean remoteLocation = isRemoteLocation(extension.getFindbugsExclude());
        File filterSource;
        String downloadTaskName = "downloadFindbugsExcludeFilter"
        if (remoteLocation) {
            filterSource = createDownloadFileTask(extension.getFindbugsExclude(), "excludeFilter.xml",
                    downloadTaskName, "findbugs");
        } else {
            filterSource = new File(extension.getFindbugsExclude());
        }

        project.findbugs {
            toolVersion = FINDBUGS_TOOL_VERSION
            effort = "max"
            ignoreFailures = extension.getIgnoreErrors()
            excludeFilter = filterSource
        }

        project.task("findbugs", type: FindBugs) {
            dependsOn project.tasks.withType(JavaCompile)
            if (remoteLocation) {
                dependsOn project.tasks.findByName(downloadTaskName)
            }

            classes = getProjectClassTree(project)

            source 'src'
            include '**/*.java'
            exclude '**/gen/**'

            reports {
                xml {
                    destination "$project.buildDir/reports/findbugs/findbugs.xml"
                    xml.withMessages true
                }
            }

            pluginClasspath = project.configurations.findbugsPlugins
        }

        /*
         * For best results, Findbugs needs ALL classes, including Android's SDK,
         * but the task is created dynamically, so we need to set it afterEvaluate
         */
        configTaskClasspath(FindBugs);

        project.tasks.check.dependsOn project.tasks.findbugs
    }

    private void configTaskClasspath(task) {
        project.tasks.withType(task).each {
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

            it.classpath = project.configurations.scaconfig +
                    project.fileTree(dir: "${project.buildDir}/intermediates/exploded-aar/", include: '**/*.jar',
                        exclude: "${project.rootProject.name}/*/unspecified/jars/classes.jar") +
                    project.fileTree(dir: "${project.buildDir}/intermediates/", include: 'mockable-android-*.jar') +
                    classTree
        }
    }

    /**
     * Retrieves a FileTree pointing to all interesting .class files for
     * static code analysis. This ignores for instance, Android's autogenerated classes
     *
     * @param path The path to the project whose class file tree to obtain.
     * @return FileTree pointing to all interesting .class files
    */
    private FileTree getProjectClassTree(path) {
        return getProjectClassTree(project.rootProject.findProject(':' + path));
    }

    /**
     * Retrieves a FileTree pointing to all interesting .class files for
     * static code analysis. This ignores for instance, Android's autogenerated classes
     *
     * @param proj The project whose class file tree to obtain.
     * @return FileTree pointing to all interesting .class files
     */
    private FileTree getProjectClassTree(Project proj) {
        FileTree tree = project.fileTree(dir: "${proj.buildDir}/intermediates/classes/")

        tree.exclude '**/R.class' //exclude generated R.java
        tree.exclude '**/R$*.class' //exclude generated R.java inner classes
        tree.exclude '**/Manifest.class' //exclude generated Manifest.java
        tree.exclude '**/Manifest$*.class' //exclude generated Manifest.java inner classes
        tree.exclude '**/BuildConfig.class' //exclude generated BuildConfig.java
        tree.exclude '**/BuildConfig$*.class' //exclude generated BuildConfig.java inner classes

        return tree
    }

    private withAndroidPlugins(Class<AnalysisConfigurator> configClass) {
        def configurator = configClass.newInstance();
        def configureAction = { configurator.applyAndroidConfig(project, extension) }

        withOptionalPlugin('com.android.build.gradle.AppPlugin', configureAction)
        withOptionalPlugin('com.android.build.gradle.LibraryPlugin', configureAction)
    }

    private withPlugin(Class<? extends Plugin> pluginClass, Class<AnalysisConfigurator> configClass) {
        def configurator = configClass.newInstance();
        def configureAction = { configurator.applyConfig(project, extension) }

        withPlugin(pluginClass, configureAction)
    }

    private withPlugin(Class<? extends Plugin> pluginClass, Action<? extends Plugin> configureAction) {
        project.plugins.withType(pluginClass, configureAction)
    }

    private withOptionalPlugin(String pluginClassName, Action<? extends Plugin> configureAction) {
        try {
            // Will most likely throw a ClassNotFoundException
            def pluginClass = Class.forName(pluginClassName)
            withPlugin(pluginClass, configureAction)
        } catch (ClassNotFoundException e) {
            // do nothing
        }
    }
}
