package com.monits.scabuild.buildquality;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.api.invocation.Gradle;

public class NoResolutionAtConfigurationTimePlugin implements Plugin<Project> {
    private boolean projectsEvaluated;

    @Override
    public void apply(final Project project) {
        projectsEvaluated = false;

        project.getGradle().projectsEvaluated(new Action<Gradle>() {
            @Override
            public void execute(final Gradle gradle) {
                projectsEvaluated = true;
            }
        });

        project.allprojects(new Action<Project>() {
            @Override
            public void execute(final Project project) {
                final String projectName = project.getName();
                project.getConfigurations().all(new Action<Configuration>() {
                    @Override
                    public void execute(final Configuration configuration) {
                        final String configName = configuration.getName();
                        configuration.getIncoming().beforeResolve(new Action<ResolvableDependencies>() {
                            @Override
                            public void execute(final ResolvableDependencies resolvableDeps) {
                                if (!projectsEvaluated) {
                                    throw new RuntimeException("Configuration " + configName + " of project " + projectName + " is being resolved at configuration time.");
                                }
                            }
                        });
                    }
                });
            }
        });
    }
}

