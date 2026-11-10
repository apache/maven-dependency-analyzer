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
     * <p>analyze.</p>
     *
     * @param project a {@link org.apache.maven.api.Project} object
     * @param dependencies the dependencies of the project, as resolved by the dependency resolver
     * @return a {@link org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalysis} object
     * @throws org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalyzerException if any
     */
    default ProjectDependencyAnalysis analyze(Project project, DependencyResolverResult dependencies)
            throws ProjectDependencyAnalyzerException {
        return analyze(project, dependencies, null);
    }

    /**
     * <p>analyze.</p>
     *
     * @param project a {@link org.apache.maven.api.Project} object
     * @param dependencies the dependencies of the project, as resolved by the dependency resolver
     * @param excludedClasses collection of regular expression of classes name to exclude
     * @return a {@link org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalysis} object
     * @throws org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalyzerException if any
     */
    ProjectDependencyAnalysis analyze(
            Project project, DependencyResolverResult dependencies, Collection<String> excludedClasses)
            throws ProjectDependencyAnalyzerException;
}
