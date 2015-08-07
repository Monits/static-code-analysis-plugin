import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.util.VersionNumber

class CPDTask extends DefaultTask {
    @Input
    def String toolVersion

    @InputFiles
    def FileCollection inputFiles

    @OutputFile
    def File outputFile

    @TaskAction
    void run() {

        createConfigurations()  // TODO : shouldn't be here
        resolveDependencies()  // TODO : shouldn't be here

        outputFile.parentFile.mkdirs()
        ant.taskdef(name: 'cpd', classname: 'net.sourceforge.pmd.cpd.CPDTask',
            classpath: project.configurations.cpd.asPath)
        ant.cpd(minimumTokenCount: '100', format: 'xml',
                outputFile: outputFile) {
            inputFiles.addToAntBuilder(ant, 'fileset', FileCollection.AntType.FileSet)
        }
    }

    // FIXME : This is copy pasted from AbstractCodeQualityPlugin... it shouldn't
    private void createConfigurations() {
        project.configurations.create('cpd').with {
            visible = false
            transitive = true
            description = "The cpd libraries to be used for this project."
            // Don't need these things, they're provided by the runtime
            exclude group: 'ant', module: 'ant'
            exclude group: 'org.apache.ant', module: 'ant'
            exclude group: 'org.apache.ant', module: 'ant-launcher'
            exclude group: 'org.slf4j', module: 'slf4j-api'
            exclude group: 'org.slf4j', module: 'jcl-over-slf4j'
            exclude group: 'org.slf4j', module: 'log4j-over-slf4j'
            exclude group: 'commons-logging', module: 'commons-logging'
            exclude group: 'log4j', module: 'log4j'
        }
    }

    // FIXME : This is copy pasted from PmdPlugin... it shouldn't
    private void resolveDependencies() {
        def config = project.configurations['cpd']
        config.defaultDependencies { dependencies ->
            VersionNumber version = VersionNumber.parse(getToolVersion())
            String dependency = calculateDefaultDependencyNotation(version)
            dependencies.add(this.project.dependencies.create(dependency))
        }
    }

    // FIXME : This is copy pasted from PmdPlugin... it shouldn't
    protected String calculateDefaultDependencyNotation(VersionNumber toolVersion) {
        if (toolVersion < VersionNumber.version(5)) {
            return "pmd:pmd:$toolVersion"
        } else if (toolVersion < VersionNumber.parse("5.2.0")) {
            return "net.sourceforge.pmd:pmd:$toolVersion"
        }
        return "net.sourceforge.pmd:pmd-java:$toolVersion"
    }
}