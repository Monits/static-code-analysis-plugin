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
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.logging.ConsoleRenderer
import org.gradle.util.VersionNumber

class CPDTask extends DefaultTask {

    boolean ignoreFailures;

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

        if (cpdFileHasErrors(outputFile)) {
            String message = "CPD rule violations were found. See the report at: ";
            def reportUrl = new ConsoleRenderer().asClickableFileUrl(outputFile);
            message += reportUrl;
            if (!ignoreFailures) {
                throw new GradleException(message);
            } else {
                logger.warn(message);
            }
        }

    }
    /*
        When no errors are found, CPD generates an xml report with this exact
        content:
        <?xml version="1.0" encoding="UTF-8" standalone="no"?>
        <pmd-cpd/>
        So naturally, if the second line of the outputFile is '<pmd-cpd/>', it
        means that cpd didn't find errors.
     */
    private boolean cpdFileHasErrors(File output) {
        BufferedReader br = new BufferedReader(new FileReader(output));
        String line;
        br.readLine();
        if ((line = br.readLine()) == null) {
            return false;
        } else {
            if (line.equals("<pmd-cpd/>")) {
                return false;
            }
        }
        return true;
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
        config.incoming.beforeResolve {
            if (config.dependencies.empty) {
                VersionNumber version = VersionNumber.parse(getToolVersion())
                String dependency = calculateDefaultDependencyNotation(version)
                config.dependencies.add(project.dependencies.create(dependency))
            }
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