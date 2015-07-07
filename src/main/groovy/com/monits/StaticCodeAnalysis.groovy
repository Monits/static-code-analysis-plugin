package com.monits

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileTree
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.FindBugs
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.wrapper.Wrapper

class StaticCodeAnalysis implements Plugin<Project> {

    def void apply(Project project) {

        project.plugins.apply 'checkstyle'
        project.plugins.apply 'findbugs'
        project.plugins.apply 'pmd'

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
        }

        project.dependencies {
            provided 'com.google.code.findbugs:annotations:3.0.0'

            checkstyle 'com.puppycrawl.tools:checkstyle:6.7'

            findbugs 'com.google.code.findbugs:findbugs:3.0.1'
            findbugs project.configurations.findbugsPlugins.dependencies

            // To keep everything tidy, we set these apart
            findbugsPlugins('com.monits:findbugs-plugin:0.2.0-SNAPSHOT') {
                transitive = false
            }
            findbugsPlugins 'com.mebigfatguy.fb-contrib:fb-contrib:6.2.1'
        }

        project.task("sourcesJar", type: Jar) {
            classifier = 'sources'
            from 'src/main/java'
        }

        project.artifacts {
            archives project.tasks.sourcesJar
        }

        project.task("downloadCheckstyleXml") {
            new File("${project.rootDir}/config/checkstyle/").mkdirs()
            ant.get(src: 'http://static.monits.com/checkstyle.xml', dest: "${project.rootDir}/config/checkstyle/checkstyle.xml")
        }

        project.task("checkstyle", type: Checkstyle) {
            dependsOn project.tasks.downloadCheckstyleXml

            configFile project.file("${project.rootDir}/config/checkstyle/checkstyle.xml")

            source 'src'
            include '**/*.java'
            exclude '**/gen/**'

            classpath = project.configurations.compile
        }

        project.task("findbugs", type: FindBugs) {
            dependsOn project.tasks.withType(JavaCompile)
            ignoreFailures = true
            effort = "max"

            FileTree tree = project.fileTree(dir: "${project.buildDir}/intermediates/classes/")

            tree.exclude '**/R.class' //exclude generated R.java
            tree.exclude '**/R$*.class' //exclude generated R.java inner classes
            tree.exclude '**/Manifest.class' //exclude generated Manifest.java
            tree.exclude '**/Manifest$*.class' //exclude generated Manifest.java inner classes
            tree.exclude '**/BuildConfig.class' //exclude generated BuildConfig.java
            tree.exclude '**/BuildConfig$*.class' //exclude generated BuildConfig.java inner classes
            classes = tree

            source 'src'
            include '**/*.java'
            exclude '**/gen/**'

            excludeFilter = project.file("$project.rootProject.projectDir/config/findbugs/excludeFilter.xml")

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
        project.afterEvaluate {
            project.tasks.withType(FindBugs).each {
                def t = project.tasks.findByName('mockableAndroidJar');
                if (t != null) {
                    it.dependsOn project.tasks.findByName('mockableAndroidJar')
                }
                it.classpath = project.configurations.compile + project.configurations.testCompile +
                        project.fileTree(dir: "${project.buildDir}/intermediates/exploded-aar/", include: '**/*.jar') +
                        project.fileTree(dir: "${project.buildDir}/intermediates/", include: 'mockable-android-*.jar')
            }
        }

        // PMD 5.2+ requires gradle 2.4+
        project.task("wrapper", type: Wrapper) {
            gradleVersion = '2.4'
        }

        project.pmd {
             toolVersion = '5.3.2'
        }

        project.task("pmd", type: Pmd) {
            ignoreFailures = true

            source 'src'
            include '**/*.java'
            exclude '**/gen/**'

            reports {
                xml.enabled = true
                html.enabled = false
            }

            ruleSets = ['http://static.monits.com/pmd.xml']
        }

        project.task("cpd") {
            File outDir = new File("$project.buildDir/reports/pmd/")
            outDir.mkdirs()
            ant.taskdef(name: 'cpd', classname: 'net.sourceforge.pmd.cpd.CPDTask',
                    classpath: project.configurations.pmd.asPath)
            ant.cpd(minimumTokenCount: '100', format: 'xml',
                    outputFile: new File(outDir , 'cpd.xml')) {
                fileset(dir: "src") {
                    include(name: '**/*.java')
                    exclude(name: '**/gen/**')
                }
            }
        }

        project.tasks.check.dependsOn project.tasks.checkstyle, project.tasks.findbugs, project.tasks.pmd, project.tasks.cpd
    }
}