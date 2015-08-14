package com.monits
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.FindBugs
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.tasks.compile.JavaCompile

class StaticCodeAnalysisPlugin implements Plugin<Project> {

    private final static String LATEST_PMD_TOOL_VERSION = '5.3.3'
    private final static String BACKWARDS_PMD_TOOL_VERSION = '5.1.2'
    private final static String GRADLE_VERSION_PMD = '2.4'
    private final static String CHECKSTYLE_VERSION = '6.7'
    private final static String FINDBUGS_ANNOTATIONS_VERSION = '3.0.0'
    private final static String FINDBUGS_TOOL_VERSION = '3.0.1'
    private final static String FINDBUGS_MONITS_VERSION = '0.2.0-SNAPSHOT'
    private final static String FB_CONTRIB_VERSION = '6.2.1'

    private String currentGradleVersion = GRADLE_VERSION_PMD;
    private String currentPmdVersion = LATEST_PMD_TOOL_VERSION;

    private boolean ignoreErrors;

    private String checkstyleRules;
    private List<String> pmdRules;
    private File findbugsExclude;

    private StaticCodeAnalysisExtension extension;

    private Project project;

    def void apply(Project project) {
        this.project = project
        currentGradleVersion = project.gradle.gradleVersion;

        project.task("pmdVersionCheck") {
            if (currentGradleVersion < GRADLE_VERSION_PMD) {
                currentPmdVersion = BACKWARDS_PMD_TOOL_VERSION;
            } else {
                currentPmdVersion = LATEST_PMD_TOOL_VERSION;
            }
        }

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
        }

        //FIXME: This is here so that projects that use Findbugs can compile... but it ignores DSL completely

        project.dependencies {
            provided 'com.google.code.findbugs:annotations:' + FINDBUGS_ANNOTATIONS_VERSION
        }

        project.afterEvaluate {

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

        project.tasks.check.dependsOn project.tasks.pmd
    }

    private void checkstyle() {
        project.plugins.apply 'checkstyle'

        boolean remoteLocation;
        File configSource;
        if (checkstyleRules.startsWith("http://")
                || checkstyleRules.startsWith("https://")) {

            remoteLocation = true;
            project.task("downloadCheckstyleXml") {
                File directory = new File("${project.rootDir}/config/checkstyle/");
                directory.mkdirs();
                configSource = new File(directory, "checkstyle.xml");
                ant.get(src: checkstyleRules, dest: configSource.getAbsolutePath());
            }
        } else {
            remoteLocation = false;
            configSource = new File(checkstyleRules);
            configSource.parentFile.mkdirs();
        }

        project.checkstyle {
            toolVersion = CHECKSTYLE_VERSION
            ignoreFailures = ignoreErrors;
            showViolations = false
            configFile configSource
        }

        project.task("checkstyle", type: Checkstyle) {
            if (remoteLocation) {
                dependsOn project.tasks.downloadCheckstyleXml
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

        project.findbugs {
            toolVersion = FINDBUGS_TOOL_VERSION
            effort = "max"
            ignoreFailures = ignoreErrors
            excludeFilter = findbugsExclude
        }

        project.task("findbugs", type: FindBugs) {
            dependsOn project.tasks.withType(JavaCompile)

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
        project.tasks.withType(FindBugs).each {
            def t = project.tasks.findByName('mockableAndroidJar');
            if (t != null) {
                it.dependsOn project.tasks.findByName('mockableAndroidJar')
            }
            it.classpath = project.configurations.compile + project.configurations.testCompile +
                    project.fileTree(dir: "${project.buildDir}/intermediates/exploded-aar/", include: '**/*.jar') +
                    project.fileTree(dir: "${project.buildDir}/intermediates/", include: 'mockable-android-*.jar')
        }

        project.tasks.check.dependsOn project.tasks.findbugs
    }

}