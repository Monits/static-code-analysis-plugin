import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

class CPDTask extends DefaultTask {
    @InputFiles
    def FileCollection inputFiles

    @OutputFile
    def File outputFile

    @TaskAction
    void run() {
        outputFile.mkdirs()
        ant.taskdef(name: 'cpd', classname: 'net.sourceforge.pmd.cpd.CPDTask',
            classpath: project.configurations.pmd.asPath)
        ant.cpd(minimumTokenCount: '100', format: 'xml',
            outputFile: outputFile) {
            inputFiles.addToAntBuilder(ant, 'fileset', FileCollection.AntType.FileSet)
        }
    }
}