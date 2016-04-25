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

import com.monits.gradle.sca.ClasspathAware
import com.monits.gradle.sca.StaticCodeAnalysisExtension
import com.monits.gradle.sca.ToolVersions
import com.monits.gradle.sca.task.DownloadTask
import org.gradle.api.Project
import org.gradle.api.file.FileTree
import org.gradle.api.plugins.quality.FindBugs
import org.gradle.api.tasks.compile.JavaCompile

class FindbugsConfigurator implements AnalysisConfigurator, ClasspathAware {

    @Override
    void applyConfig(final Project project, final StaticCodeAnalysisExtension extension) {
        // TODO : Not currently working on java projects
        applyAndroidConfig(project, extension)
    }

    @Override
    void applyAndroidConfig(final Project project, final StaticCodeAnalysisExtension extension) {
        // prevent applying it twice
        if (project.tasks.findByName('findbugs')) {
            return
        }

        //FIXME: This is here so that projects that use Findbugs can compile... but it ignores DSL completely
        project.repositories {
            maven {
                url 'http://nexus.monits.com/content/repositories/oss-snapshots'
            }
        }
        project.dependencies {
            provided 'com.google.code.findbugs:annotations:' + ToolVersions.findbugsVersion
        }

        project.plugins.apply 'findbugs'

        project.dependencies {
            findbugs project.configurations.findbugsPlugins.dependencies

            // To keep everything tidy, we set these apart
            findbugsPlugins('com.monits:findbugs-plugin:' + ToolVersions.monitsFindbugsVersion) {
                transitive = false
            }
            findbugsPlugins 'com.mebigfatguy.fb-contrib:fb-contrib:' + ToolVersions.fbContribVersion
        }

        boolean remoteLocation = isRemoteLocation(extension.getFindbugsExclude());
        File filterSource;
        String downloadTaskName = 'downloadFindbugsExcludeFilter'
        if (remoteLocation) {
            filterSource = createDownloadFileTask(project, extension.getFindbugsExclude(),
                    'excludeFilter.xml', downloadTaskName, 'findbugs');
        } else {
            filterSource = new File(extension.getFindbugsExclude());
        }

        project.findbugs {
            toolVersion = ToolVersions.findbugsVersion
            effort = 'max'
            ignoreFailures = extension.getIgnoreErrors()
            excludeFilter = filterSource
        }

        println project.tasks.findByName('findbugs')
        try {
            throw new IOException()
        } catch (IOException e) {
            e.printStackTrace()
        }
        project.task('findbugs', type: FindBugs) {
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
        configAndroidClasspath(project.tasks.findbugs, project);

        project.tasks.check.dependsOn project.tasks.findbugs
    }

    private static boolean isRemoteLocation(String path) {
        return path.startsWith('http://') || path.startsWith('https://');
    }

    private File createDownloadFileTask(Project project, String remotePath, String destination,
                                        String taskName, String plugin) {
        def destPath = "${project.rootDir}/config/${plugin}/"
        def File destFile = project.file(destPath + destination)

        project.task(taskName, type: DownloadTask) {
            directory = project.file(destPath)
            downloadedFile = destFile
            resourceUri = remotePath
        }

        return destFile;
    }

    /**
     * Retrieves a FileTree pointing to all interesting .class files for
     * static code analysis. This ignores for instance, Android's autogenerated classes
     *
     * @param proj The project whose class file tree to obtain.
     * @return FileTree pointing to all interesting .class files
     */
    private static FileTree getProjectClassTree(Project proj) {
        FileTree tree = proj.fileTree(dir: "${proj.buildDir}/intermediates/classes/")

        tree.exclude '**/R.class' //exclude generated R.java
        tree.exclude '**/R$*.class' //exclude generated R.java inner classes
        tree.exclude '**/Manifest.class' //exclude generated Manifest.java
        tree.exclude '**/Manifest$*.class' //exclude generated Manifest.java inner classes
        tree.exclude '**/BuildConfig.class' //exclude generated BuildConfig.java
        tree.exclude '**/BuildConfig$*.class' //exclude generated BuildConfig.java inner classes

        return tree
    }
}
