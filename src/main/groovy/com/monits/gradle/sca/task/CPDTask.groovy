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
package com.monits.gradle.sca.task

import com.monits.gradle.sca.logging.ConsoleRenderer
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.ParallelizableTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.VerificationTask
import org.gradle.util.VersionNumber

/**
 * CPD task.
*/
@CompileStatic
@ParallelizableTask
class CPDTask extends DefaultTask implements VerificationTask {

    private static final String CPD = 'cpd'

    boolean ignoreFailures

    @Input
    String toolVersion

    @InputFiles
    FileCollection inputFiles

    @OutputFile
    File outputFile

    @CompileStatic(TypeCheckingMode.SKIP)
    @TaskAction
    void run() {
        inputFiles.stopExecutionIfEmpty()

        addConfigurations()  // TODO : shouldn't be here
        resolveDependencies()  // TODO : shouldn't be here

        outputFile.parentFile.mkdirs()
        ant.taskdef(name:CPD, classname:'net.sourceforge.pmd.cpd.CPDTask', classpath:project.configurations.cpd.asPath)
        ant.cpd(minimumTokenCount:'100', format:'xml', outputFile:outputFile) {
            inputFiles.addToAntBuilder(ant, 'fileset', FileCollection.AntType.FileSet)
        }

        if (cpdFileHasErrors(outputFile)) {
            String message = 'CPD rule violations were found. See the report at: '
            String reportUrl = new ConsoleRenderer().asClickableFileUrl(outputFile)
            message += reportUrl
            if (ignoreFailures) {
                logger.warn(message)
            } else {
                throw new GradleException(message)
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
    private static boolean cpdFileHasErrors(final File output) {
        BufferedReader br = new BufferedReader(new FileReader(output))
        String line
        br.readLine()
        if ((line = br.readLine()) == null) {
            return false
        }
        br.close()

        line != '<pmd-cpd/>'
    }

    // FIXME : This is copy pasted from AbstractCodeQualityPlugin... it shouldn't
    @SuppressWarnings('DuplicateStringLiteral')
    private void addConfigurations() {
        project.configurations.create(CPD).with {
            visible = false
            transitive = true
            description = 'The cpd libraries to be used for this project.'
            // Don't need these things, they're provided by the runtime
            exclude group:'ant', module:'ant'
            exclude group:'org.apache.ant', module:'ant'
            exclude group:'org.apache.ant', module:'ant-launcher'
            exclude group:'org.slf4j', module:'slf4j-api'
            exclude group:'org.slf4j', module:'jcl-over-slf4j'
            exclude group:'org.slf4j', module:'log4j-over-slf4j'
            exclude group:'commons-logging', module:'commons-logging'
            exclude group:'log4j', module:'log4j'
        }
    }

    // FIXME : This is copy pasted from PmdPlugin... it shouldn't
    private void resolveDependencies() {
        Configuration config = project.configurations[CPD]
        config.incoming.beforeResolve {
            if (config.dependencies.empty) {
                VersionNumber version = VersionNumber.parse(toolVersion)
                String dependency = calculateDefaultDependencyNotation(version)
                config.dependencies.add(project.dependencies.create(dependency))
            }
        }
    }

    // FIXME : This is copy pasted from PmdPlugin... it shouldn't
    protected static String calculateDefaultDependencyNotation(final VersionNumber toolVersion) {
        if (toolVersion < VersionNumber.version(5)) {
            return "pmd:pmd:$toolVersion"
        } else if (toolVersion < VersionNumber.parse('5.2.0')) {
            return "net.sourceforge.pmd:pmd:$toolVersion"
        }
        "net.sourceforge.pmd:pmd-java:$toolVersion"
    }
}
