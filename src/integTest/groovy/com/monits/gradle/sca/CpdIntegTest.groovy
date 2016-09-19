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

import com.monits.gradle.sca.fixture.AbstractPluginIntegTestFixture
import org.gradle.testkit.runner.BuildResult
import org.gradle.util.GradleVersion
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.FAILED
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

/**
 * Integration test of CPD tasks.
 */
class CpdIntegTest extends AbstractPluginIntegTestFixture {
    @SuppressWarnings('MethodName')
    @Unroll('CPD should run when using gradle #version')
    void 'cpd is run'() {
        given:
        writeBuildFile()
        goodCode()

        when:
        BuildResult result = gradleRunner()
            .withGradleVersion(version)
            .build()

        then:
        if (GradleVersion.version(version) >= GradleVersion.version('2.5')) {
            // Executed task capture is only available in Gradle 2.5+
            result.task(taskName()).outcome == SUCCESS
        }

        // Make sure report exists
        reportFile().exists()

        where:
        version << TESTED_GRADLE_VERSIONS
    }

    @SuppressWarnings('MethodName')
    void 'cpd runs if there is no code'() {
        given:
        writeBuildFile()

        when:
        BuildResult result = gradleRunner().build()

        then:
        result.task(taskName()).outcome == SUCCESS

        // The report should not exist
        !reportFile().exists()
    }

    @SuppressWarnings('MethodName')
    void 'fails when error found and ignoreErrors is false'() {
        given:
        setupProjectWithViolations(false)

        when:
        BuildResult result = gradleRunner().buildAndFail()

        then:
        result.task(taskName()).outcome == FAILED

        // Make sure the report exist
        reportFile().exists()
    }

    @SuppressWarnings('MethodName')
    void 'does not fail when error found and ignoreErrors is true'() {
        given:
        setupProjectWithViolations(true)

        when:
        BuildResult result = gradleRunner().build()

        then:
        result.task(taskName()).outcome == SUCCESS

        // Make sure the report exist
        reportFile().exists()
    }

    void setupProjectWithViolations(final boolean ignoreErrors) {
        writeBuildFile() << """
            staticCodeAnalysis {
                ignoreErrors = ${ignoreErrors}
            }
        """
        // Write a large chunk of code repeated several times...
        1.upto(5) {
            file("src/main/java/com/monits/Class${it}.java") << """
                package com.monits;

                public class Class${it} {
                    public boolean isFoo(Object arg) {
                        return true;
                    }

                    public boolean isBar(Bar arg) {
                        return arg == null ? false : arg.someMethod();
                    }

                    public String greeting() {
                        return "It's dangerous to go alone! take this.";
                    }

                    public int surprise() {
                        return hashCode() * 2 % getClass().getName().length() ^ 0x0f;
                    }

                    public void doSomethingStupid() {
                        final String msg;
                        if (surprise() > getClass().getName().length()) {
                            msg = this.greeting();
                        } else if (isBar(null)) {
                            msg = "Ok, this is awkward...";
                        } else {
                            msg = "It's a trap! " + toString();
                        }

                        System.out.println(msg);
                    }

                    @Override
                    public String toString() {
                        return "ClassTest{" +
                            getClass().getName() +
                            "}@" + hashCode();
                    }

                    public static class Bar {
                        public boolean someMethod() {
                            return true;
                        }
                    }
                }
            """
        }
    }

    @Override
    String reportFileName(final String sourceSet) {
        "build/reports/pmd/cpd${sourceSet ? "-${sourceSet}" : ''}.xml"
    }

    @Override
    String taskName() {
        ':cpd'
    }

    @Override
    String toolName() {
        'cpd'
    }
}
