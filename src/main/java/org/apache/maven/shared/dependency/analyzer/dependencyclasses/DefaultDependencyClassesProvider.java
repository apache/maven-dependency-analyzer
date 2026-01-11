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
package org.apache.maven.shared.dependency.analyzer.dependencyclasses;

import javax.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Set;

import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.analyzer.ClassesPatterns;
import org.apache.maven.shared.dependency.analyzer.DependencyAnalyzer;
import org.apache.maven.shared.dependency.analyzer.DependencyClassesProvider;
import org.apache.maven.shared.dependency.analyzer.DependencyUsage;

abstract class DefaultDependencyClassesProvider implements DependencyClassesProvider {

    /**
     * DependencyAnalyzer
     */
    private final DependencyAnalyzer dependencyAnalyzer;

    @Inject
    DefaultDependencyClassesProvider(DependencyAnalyzer dependencyAnalyzer) {
        this.dependencyAnalyzer = dependencyAnalyzer;
    }

    @Override
    public Set<DependencyUsage> getDependencyClasses(MavenProject project, ClassesPatterns excludedClasses)
            throws IOException {
        String classesDirectory = getOutputClassesDirectory(project);
        URL url = new File(classesDirectory).toURI().toURL();

        return dependencyAnalyzer.analyzeUsages(url, excludedClasses);
    }

    protected abstract String getOutputClassesDirectory(MavenProject project);
}
