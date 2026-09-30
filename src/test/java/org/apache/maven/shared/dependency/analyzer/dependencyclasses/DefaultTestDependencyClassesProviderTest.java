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

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import org.apache.maven.api.Project;
import org.apache.maven.api.ProjectScope;
import org.apache.maven.shared.dependency.analyzer.DependencyAnalyzer;
import org.apache.maven.shared.dependency.analyzer.DependencyUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultTestDependencyClassesProviderTest {

    @Mock
    private DependencyAnalyzer analyzer;

    @InjectMocks
    private DefaultTestDependencyClassesProvider provider;

    @Test
    void testOutputIsUsed() throws IOException {
        Project project = Mockito.mock(Project.class);
        Path outputDirectory = Paths.get("target/test-classes");
        when(project.getOutputDirectory(ProjectScope.TEST)).thenReturn(outputDirectory);

        Set<DependencyUsage> dependencyUsages = provider.getDependencyClasses(project, null);

        assertThat(dependencyUsages).isNotNull();

        verify(analyzer).analyzeUsages(outputDirectory.toUri().toURL(), null);
    }
}
