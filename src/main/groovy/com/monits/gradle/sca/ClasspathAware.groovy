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
package com.monits.gradle.sca

import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider

import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Trait for configuring classpath aware tasks.
*/
@CompileStatic
trait ClasspathAware {

    private static final String DEBUG_SOURCESET = 'debug'
    private static final String MAIN_SOURCESET = 'main'
    private static final String TEST_SOURCESET = 'test'
    private static final String ANDROID_TEST_SOURCESET = 'androidTest'
    private static final String AAR_EXTENSTION = '.aar'

    private static final String SCACONFIG = 'scaconfig'

    void setupAndroidClasspathAwareTask(final TaskProvider<? extends Task> taskToConfigure,
                                        final Project project, final String sourceSetName) {
        ClasspathAware cpa = this

        /*
         * For best results, this task needs ALL classes, including Android's SDK,
         * but we need that configure before execution to be considered in up-to-date check.
         * We do it in a separate task, executing AFTER all other needed tasks are done
         */
        TaskProvider<Task> cpTask = project.tasks.register(
                'configureClasspathFor' + taskToConfigure.name.capitalize())
        cpTask.configure { Task self ->
            // we need all other task to be done first
            self.dependsOn taskToConfigure.get().dependsOn.findAll { it != cpTask } // avoid cycles
            self.doLast {
                cpa.configAndroidClasspath(taskToConfigure.get(), project, sourceSetName)
            }
        }

        taskToConfigure.configure { Task t ->
            t.dependsOn cpTask
        }
    }

    void configAndroidClasspath(final Task task, final Project project, final String sourceSetName) {
        // Manually add classes of project dependencies
        FileCollection dependantModuleClasses = project.files()
        project.configurations.getByName('scaconfigModules').dependencies.all { ProjectDependency dependency ->
            Project proj = dependency.dependencyProject

            // is it an Android plugin?
            if (AndroidHelper.SUPPORTED_PLUGINS.any { proj.plugins.hasPlugin(it) }) {
                // TODO : is it okay to always use debug?
                dependantModuleClasses = project.files(pathToCompiledClasses(proj, DEBUG_SOURCESET)) +
                        dependantModuleClasses // Using += produces a runtime exception
            } else if (proj.plugins.hasPlugin('java')) {
                // TODO : is it okay to always use all sourcesets?
                proj.convention.getPlugin(JavaPluginConvention).sourceSets.all { SourceSet ss ->
                    dependantModuleClasses += ss.output
                }
            } else {
                project.logger.warn("The project depends on ${proj.path} which is neither an Android nor" +
                    ' a Java project, ignoring it')
            }
        }

        // as of 3.2.0+ the mockableAndroidJar task is no more… attempt to access directly
        FileCollection androidJar = project.files(
            AndroidHelper.sdkDir + "/platforms/${project['android']['compileSdkVersion']}/android.jar",
            // and javax.accessibility.Accessible is systematically missed for some reason
            System.getenv('JAVA_HOME') + '/jre/lib/rt.jar')

        // as of 3.3.0+ R is built directly into a jar
        FileCollection standaloneRJar
        String pathToStandaloneRJar = AndroidHelper.getStandaloneRJarPath(project, sourceSetName)
        if (pathToStandaloneRJar != null) {
            standaloneRJar = project.files(pathToStandaloneRJar)
        } else {
            standaloneRJar = project.files()
        }

        // test sourcesets require their main counterparts
        FileCollection otherDependantSourceSet
        if (sourceSetName.contains(TEST_SOURCESET) || sourceSetName.contains(ANDROID_TEST_SOURCESET)) {
            // testRelease depends on release, androidTestDebug on debug… and test on main
            String sourceSetSuffix = (sourceSetName - TEST_SOURCESET - ANDROID_TEST_SOURCESET).uncapitalize()
            String mainCounterpartSourceSet = sourceSetSuffix.empty ? 'main' : sourceSetSuffix

            otherDependantSourceSet = project.files(pathToCompiledClasses(project, mainCounterpartSourceSet))
        } else {
            otherDependantSourceSet = project.files()
        }

        String propertyName = task.hasProperty('classpath') ? 'classpath' : 'auxClassPaths'
        task.setProperty(propertyName,
                project.files(pathToCompiledClasses(project, sourceSetName)) +
                otherDependantSourceSet +
                standaloneRJar +
                androidJar +
                dependantModuleClasses +
                getJarsForAarDependencies(project) +
                project.files(project.configurations.getByName(SCACONFIG).files
                    .findAll { File it -> !it.name.endsWith(AAR_EXTENSTION) })
            )
    }

    /**
     * Retrieves a FileTree pointing to all interesting .class files for
     * static code analysis.
     *
     * @param proj The project whose class file tree to obtain.
     * @param sourceSetName Optional. The name of the sourceset whose classes to obtain.
     * @return FileCollection pointing to all interesting .class files
     */
    FileCollection getProjectClassTree(final Project proj, final String sourceSetName) {
        proj.files(pathToCompiledClasses(proj, sourceSetName))
    }

    private static FileTree getJarsForAarDependencies(final Project project) {
        if (!AndroidHelper.usesBuildCache(project)) {
            return project.fileTree(
                dir:"${project.buildDir}/intermediates/exploded-aar/",
                include:'**/*.jar',
                exclude:"${project.rootProject.name}/*/unspecified/jars/classes.jar",)
        }

        String cacheDir = AndroidHelper.getBuildCacheDir(project)
        project.files(project.configurations.getByName(SCACONFIG).files
            .findAll { File it -> it.name.endsWith AAR_EXTENSTION }
            .collect { File it ->
                MessageDigest sha1 = MessageDigest.getInstance('SHA1')
                String inputFile = 'COMMAND=PREPARE_LIBRARY\n' +
                    "FILE_PATH=${it.absolutePath}\n" +
                    "FILE_SIZE=${it.length()}\n" +
                    "FILE_TIMESTAMP=${it.lastModified()}"
                String hash = new BigInteger(1, sha1.digest(inputFile.bytes)).toString(16)
                File cacheFile = new File(cacheDir + hash + File.separator + 'output/jars/classes.jar')

                // If it doesn't exist, create it - (AGP 3.+ no longer creates them)
                if (!cacheFile.exists()) {
                    ZipFile aarFile = new ZipFile(it)
                    ZipEntry classesEntry = aarFile.getEntry('classes.jar')

                    if (classesEntry != null) {
                        cacheFile.parentFile.mkdirs()
                        cacheFile.createNewFile()
                        cacheFile << aarFile.getInputStream(classesEntry)
                    }
                }

                cacheFile
            }.findAll { File it -> it.exists() }
        ).asFileTree
    }

    /**
     * Retrieves the path to the location of compiled classes for the given sourceset under android
     *
     * @param project The project under which the sourceset exists
     * @param sourceSetName The name of the sourceset being compiled
     * @return The path to the directory were compiled classes can be found for this sourceset
     */
    private String pathToCompiledClasses(final Project project, final String sourceSetName) {
        String sourceSetOutputPath

        if (sourceSetName == ANDROID_TEST_SOURCESET) {
            sourceSetOutputPath = 'androidTest/debug'
        } else if (sourceSetName == TEST_SOURCESET) {
            sourceSetOutputPath = 'test/debug'
        } else {
            // generate output path for classes. 'main' is filtered, since those map directly to debug / release
            sourceSetOutputPath = camelToWords(sourceSetName)*.toLowerCase()
                .findAll { String it -> it != MAIN_SOURCESET }.join(File.separator)

            if (sourceSetOutputPath.empty) {
                sourceSetOutputPath = DEBUG_SOURCESET
            }
        }

        AndroidHelper.getCompileOutputDir(project, sourceSetName, sourceSetOutputPath)
    }

    /**
     * Converts a camel case string into an array of words. Casing is not changed for each word
     *
     * @param camelCase The camel case sring to be split
     * @return The split words
     */
    private static String[] camelToWords(final String camelCase) {
        camelCase.split(
            String.format('%s|%s|%s',
                '(?<=[A-Z])(?=[A-Z][a-z])',
                '(?<=[^A-Z])(?=[A-Z])',
                '(?<=[A-Za-z])(?=[^A-Za-z])'
            )
        )
    }
}
