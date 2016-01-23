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
package com.monits

import groovy.io.FileType
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.FindBugs
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.tasks.compile.JavaCompile

class StaticCodeAnalysisPlugin implements Plugin<Project> {

    private final static String LATEST_PMD_TOOL_VERSION = '5.4.1'
    private final static String BACKWARDS_PMD_TOOL_VERSION = '5.1.3'
    private final static String GRADLE_VERSION_PMD = '2.4'

    private final static String LATEST_CHECKSTYLE_VERSION = '6.14.1'
    private final static String BACKWARDS_CHECKSTYLE_VERSION = '6.7'
    private final static String GRADLE_VERSION_CHECKSTYLE = '2.7'

    private final static String FINDBUGS_ANNOTATIONS_VERSION = '3.0.0'
    private final static String FINDBUGS_TOOL_VERSION = '3.0.1'
    private final static String FINDBUGS_MONITS_VERSION = '0.2.0-SNAPSHOT'
    private final static String FB_CONTRIB_VERSION = '6.4.1'

    private final static String GRADLE_VERSION_PMD_CLASSPATH_SUPPORT = '2.8'

    private String currentGradleVersion = GRADLE_VERSION_PMD;
    private String currentPmdVersion = LATEST_PMD_TOOL_VERSION;
    private String currentCheckstyleVersion = LATEST_CHECKSTYLE_VERSION;

    private boolean ignoreErrors;

    private String checkstyleRules;
    private List<String> pmdRules;
    private String findbugsExclude;

    private StaticCodeAnalysisExtension extension;

    private Project project;

    def void apply(Project project) {
        this.project = project

        checkVersions();

        extension = new StaticCodeAnalysisExtension(project);
        project.extensions.add(StaticCodeAnalysisExtension.NAME, extension);

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

        //FIXME: This is here so that projects that use Findbugs can compile... but it ignores DSL completely
        project.repositories {
            maven {
                url 'http://nexus.monits.com/content/repositories/oss-snapshots'
            }
        }

        project.dependencies {
            provided 'com.google.code.findbugs:annotations:' + FINDBUGS_ANNOTATIONS_VERSION
        }

        project.afterEvaluate {
            // Populate scaconfig
            addDepsButModulesToScaconfig(project.configurations.compile)
            addDepsButModulesToScaconfig(project.configurations.testCompile)

            // Take data from extension
            ignoreErrors = extension.ignoreErrors;

            checkstyleRules = extension.checkstyleRules;
            pmdRules = extension.pmdRules;
            findbugsExclude = extension.findbugsExclude;

            if (extension.findbugs) {
                findbugs();
            }

            if (extension.checkstyle) {
                checkstyle();
            }

            if (extension.pmd) {
                pmd();
            }

            if (extension.cpd) {
                cpd();
            }

            androidLint();
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
        currentGradleVersion = project.gradle.gradleVersion;

        project.task("pmdVersionCheck") {
            if (currentGradleVersion < GRADLE_VERSION_PMD) {
                currentPmdVersion = BACKWARDS_PMD_TOOL_VERSION;
            } else {
                currentPmdVersion = LATEST_PMD_TOOL_VERSION;
            }
        }

        project.task("checkstyleVersionCheck") {
            if (currentGradleVersion < GRADLE_VERSION_CHECKSTYLE) {
                currentCheckstyleVersion = BACKWARDS_CHECKSTYLE_VERSION;
                /*
                    If checkstyleRules are equal to "http://static.monits.com/checkstyle.xml",
                    that means the user has not defined its own rules. So its the plugins
                    responsibility to check for compatible ones.
                */
                if (checkstyleRules.equals(StaticCodeAnalysisExtension.CHECKSTYLE_DEFAULT_RULES)) {
                    checkstyleRules = StaticCodeAnalysisExtension.CHECKSTYLE_BACKWARDS_RULES;
                }
            } else {
                currentCheckstyleVersion = LATEST_CHECKSTYLE_VERSION;
            }
        }
    }

    private void cpd() {
        project.plugins.apply 'pmd'

        project.task("cpd", type: CPDTask) {

            ignoreFailures = ignoreErrors

            dependsOn project.tasks.pmdVersionCheck

            FileTree srcDir = project.fileTree("$project.projectDir/src/");
            srcDir.include '**/*.java'
            srcDir.exclude '**/gen/**'

            FileCollection collection = project.files(srcDir.getFiles());

            toolVersion = currentPmdVersion
            inputFiles = collection
            outputFile = new File("$project.buildDir/reports/pmd/cpd.xml")
        }

        project.tasks.check.dependsOn project.tasks.cpd
    }

    private void pmd() {

        project.plugins.apply 'pmd'

        project.pmd {
            toolVersion = currentPmdVersion
            ignoreFailures = ignoreErrors;
            ruleSets = pmdRules
        }

        project.task("pmd", type: Pmd) {
            dependsOn project.tasks.pmdVersionCheck

            source 'src'
            include '**/*.java'
            exclude '**/gen/**'

            reports {
                xml.enabled = true
                html.enabled = false
            }
        }

        if (GRADLE_VERSION_PMD_CLASSPATH_SUPPORT <= currentGradleVersion) {
            /*
             * For best results, PMD needs ALL classes, including Android's SDK,
             * but the task is created dynamically, so we need to set it afterEvaluate
             */
            configTaskClasspath(Pmd);
        }

        project.tasks.check.dependsOn project.tasks.pmd
    }

    private boolean isRemoteLocation(String path) {
        return path.startsWith("http://") || path.startsWith("https://");
    }

    private File createDownloadFileTask(String remotePath, String destination, String taskName, String plugin) {
        File downloadedFile;
        project.task(taskName) {
            File directory = new File("${project.rootDir}/config/" + plugin + "/");
            downloadedFile = new File(directory, destination);
            doFirst {
                directory.mkdirs();
                ant.get(src: remotePath, dest: downloadedFile.getAbsolutePath());
            }
        }

        return downloadedFile;
    }

    private void checkstyle() {
        project.plugins.apply 'checkstyle'

        boolean remoteLocation = isRemoteLocation(checkstyleRules);
        File configSource;
        String downloadTaskName = "downloadCheckstyleXml"
        if (remoteLocation) {
            configSource = createDownloadFileTask(checkstyleRules,"checkstyle.xml",
                    downloadTaskName, "checkstyle");
        } else {
            configSource = new File(checkstyleRules);
        }

        project.checkstyle {
            toolVersion = currentCheckstyleVersion
            ignoreFailures = ignoreErrors;
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

        boolean remoteLocation = isRemoteLocation(findbugsExclude);
        File filterSource;
        String downloadTaskName = "downloadFindbugsExcludeFilter"
        if (remoteLocation) {
            filterSource = createDownloadFileTask(findbugsExclude,"excludeFilter.xml",
                    downloadTaskName, "findbugs");
        } else {
            filterSource = new File(findbugsExclude);
        }

        project.findbugs {
            toolVersion = FINDBUGS_TOOL_VERSION
            effort = "max"
            ignoreFailures = ignoreErrors
            excludeFilter = filterSource
        }

        project.task("findbugs", type: FindBugs) {
            dependsOn project.tasks.withType(JavaCompile)
            if (remoteLocation) {
                dependsOn project.tasks.findByName(downloadTaskName)
            }

            classes = getProjectClassTree(project.name)

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
        def proj = project.rootProject.findProject(':' + path)
        FileTree tree = project.fileTree(dir: "${proj.buildDir}/intermediates/classes/")

        tree.exclude '**/R.class' //exclude generated R.java
        tree.exclude '**/R$*.class' //exclude generated R.java inner classes
        tree.exclude '**/Manifest.class' //exclude generated Manifest.java
        tree.exclude '**/Manifest$*.class' //exclude generated Manifest.java inner classes
        tree.exclude '**/BuildConfig.class' //exclude generated BuildConfig.java
        tree.exclude '**/BuildConfig$*.class' //exclude generated BuildConfig.java inner classes

        return tree
    }

    private void androidLint() {
        def t = project.tasks.findByName('lint');
        if (t == null) {
            return;
        }

        project.task('resolveAndroidLint') {
            outputs.upToDateWhen({ false }) // never!
        } << {
            // Resolve all artifacts
            project.configurations.androidLint.resolve();

            def f = getAndroidLintHome();

            // Prevent any "undersired" lints from being applied
            changeAllFileExtensions(f, ".jar", ".bak");

            // Manually copy all artifacts to the corresponding location
            project.configurations.androidLint.getFiles().each {
                def target = project.file(f.getAbsolutePath() + File.separator + it.name)
                def input = it.newDataInputStream()
                def output = target.newDataOutputStream()

                output << input 

                input.close()
                output.close()
            }
        }

        project.task('cleanupAndroidLint') {
            outputs.upToDateWhen({ false }) // never!
        } << {
            def f = getAndroidLintHome();

            // Remove all the .jar files we introduced
            f.eachFileMatch(FileType.FILES, ~/.*\.jar$/, { it.delete(); });

            // Restore .bak files
            changeAllFileExtensions(f, ".bak", ".jar");
        }

        t.dependsOn project.tasks.findByName('resolveAndroidLint');
        t.finalizedBy project.tasks.findByName('cleanupAndroidLint');
    }

    /**
     * Retrieves a file pointing to the active android lint home, making usre it exits.
     *
     * @return A File pointint to the active android lint home.
    */
    private File getAndroidLintHome() {
        // Home candidates and order according to http://tools.android.com/tips/lint-custom-rules
        String home = System.getProperty('ANDROID_SDK_HOME');
        if (home == null) {
            home = System.getenv('ANDROID_SDK_HOME');
        }
        if (home == null) {
            home = System.getProperty('user.home');
        }
        if (home == null) {
            home = System.getenv('HOME');
        }

        if (home == null) {
            throw new GradleException("Neither ANDROID_SDK_HOME, nor user.home nor HOME could be found.");
        }

        File f = project.file("${home}/.android/lint/");
        if (!f.exists()) {
            f.mkdirs();
        }

        return f;
    }

    /**
     * Change the file extension of all files in the given folder from one to another
     *
     * @param dir The diectory in which to  find for files to rename
     * @param from The original extension to be changed
     * @param to The new extension to be used
    */
    private void changeAllFileExtensions(File dir, String from, String to) {
        dir.eachFileMatch(FileType.FILES, ~/.*${from}$/, {
            it.renameTo(it.getAbsolutePath()[0 ..< it.getAbsolutePath().length()-from.length()] + to)
        });
    }
}
