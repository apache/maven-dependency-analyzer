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

import java.io.File;
import java.io.IOException;
import java.util.Set;

import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;
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
class DefaultMainDependencyClassesProviderTest {

    @Mock
    private DependencyAnalyzer analyzer;

    @InjectMocks
    private DefaultMainDependencyClassesProvider provider;

    @Test
    void mainOutputIsUsed() throws IOException {
        MavenProject project = Mockito.mock(MavenProject.class);
        Build build = Mockito.mock(Build.class);
        when(project.getBuild()).thenReturn(build);
        when(build.getOutputDirectory()).thenReturn("target/classes");

        Set<DependencyUsage> dependencyUsages = provider.getDependencyClasses(project, null);

        assertThat(dependencyUsages).isNotNull();

        verify(analyzer).analyzeUsages(new File("target/classes").toURI().toURL(), null);
    }
}
