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
package com.monits.gradle.sca.performance

import com.monits.gradle.sca.performance.fixture.AbstractPluginPerfTestFixture
import com.monits.gradle.sca.performance.io.TestFile
import groovy.transform.CompileStatic
import org.gradle.testkit.runner.GradleRunner

/**
 * Performance test to measure configuration time.
 */
@CompileStatic
class ConfigurationPerfTest extends AbstractPluginPerfTestFixture {

    @Override
    String taskName() {
        'help'
    }

    @Override
    String toolName() {
        'help'
    }

    // For this test we don't want to run check task
    @Override
    GradleRunner gradleRunner() {
        super.gradleRunner().withArguments('help', '--stacktrace')
    }

    // For this test we want to enable all task types
    @Override
    TestFile writeBuildFile(final String pluginVersion = "files($pluginClasspathString)") {
        writeBuildFile(buildAllToolsMap(), pluginVersion)
    }

    // For this test we want to enable all task types
    @Override
    TestFile writeAndroidBuildFile(final String androidVersion,
                                   final String pluginVersion = "files($pluginClasspathString)") {
        Map<String, Object> configMap = buildAllToolsMap()
        configMap.put(ANDROID_VERSION, androidVersion)
        writeAndroidBuildFile(configMap, pluginVersion)
    }

    @SuppressWarnings('FactoryMethodName')
    private Map<String, Object> buildAllToolsMap() {
        Map<String, Object> configMap = [:]
        configMap.put('androidLint', Boolean.TRUE)
        configMap.put('checkstyle', Boolean.TRUE)
        configMap.put('cpd', Boolean.TRUE)
        configMap.put('pmd', Boolean.TRUE)
        configMap.put('spotbugs', Boolean.TRUE)
        configMap
    }
}
