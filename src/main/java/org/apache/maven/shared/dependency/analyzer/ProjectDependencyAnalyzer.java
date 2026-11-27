/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.shared.dependency.analyzer;

import java.util.Collection;

import org.apache.maven.api.Project;
import org.apache.maven.api.services.DependencyResolverResult;

/**
 * Analyze a project's declared dependencies and effective classes used to find which artifacts are:
 * <ul>
 * <li>used and declared,</li>
 * <li>used but not declared,</li>
 * <li>not used but declared.</li>
 * <li>used but declared in too broad a scope</li>
 * </ul>
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 */
public interface ProjectDependencyAnalyzer {

    /**
     * Analyzes the dependencies of a project. The project's main and test classes must be compiled and its
     * dependencies resolved in the test scope.
     *
     * @param project the project to analyze
     * @param dependencies the dependencies of the project, as resolved by the dependency resolver
     * @return the analysis result
     * @throws ProjectDependencyAnalyzerException if the classes of the project or of a dependency cannot be read
     */
    default ProjectDependencyAnalysis analyze(Project project, DependencyResolverResult dependencies)
            throws ProjectDependencyAnalyzerException {
        return analyze(project, dependencies, null);
    }

    /**
     * Analyzes the dependencies of a project, leaving out the classes that match {@code excludedClasses} both in the
     * project and in its dependencies.
     *
     * @param project the project to analyze
     * @param dependencies the dependencies of the project, as resolved by the dependency resolver
     * @param excludedClasses regular expressions of class names to exclude, or {@code null} for none
     * @return the analysis result
     * @throws ProjectDependencyAnalyzerException if the classes of the project or of a dependency cannot be read
     */
    ProjectDependencyAnalysis analyze(
            Project project, DependencyResolverResult dependencies, Collection<String> excludedClasses)
            throws ProjectDependencyAnalyzerException;
}
