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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.versioning.VersionRange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests <code>ProjectDependencyAnalysis</code>.
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @see ProjectDependencyAnalysis
 */
class ProjectDependencyAnalysisTest {
    @Test
    void testConstructor() {
        Set<Artifact> usedDeclaredArtifacts = new HashSet<>();
        Set<Artifact> usedUndeclaredArtifacts = new HashSet<>();
        Set<Artifact> unusedDeclaredArtifacts = new HashSet<>();
        Set<Artifact> testArtifactsWithNonTestScope = new HashSet<>();

        ProjectDependencyAnalysis analysis = new ProjectDependencyAnalysis(
                usedDeclaredArtifacts, usedUndeclaredArtifacts, unusedDeclaredArtifacts, testArtifactsWithNonTestScope);

        assertThat(analysis.getUsedDeclaredArtifacts()).isEqualTo(usedDeclaredArtifacts);
        assertThat(analysis.getUsedUndeclaredArtifacts()).isEqualTo(usedUndeclaredArtifacts);
        assertThat(analysis.getUnusedDeclaredArtifacts()).isEqualTo(unusedDeclaredArtifacts);
    }

    @Test
    void ignoreNonCompileShouldFilterOnlyUnusedDeclare() {
        Artifact artifactCompile = aTestArtifact("test1", Artifact.SCOPE_COMPILE);
        Artifact artifactProvided = aTestArtifact("test2", Artifact.SCOPE_PROVIDED);
        Artifact artifactTest = aTestArtifact("test3", Artifact.SCOPE_TEST);

        ProjectDependencyAnalysis analysis = new ProjectDependencyAnalysis(
                asSet(artifactCompile, artifactProvided, artifactTest),
                asSet(artifactCompile, artifactProvided, artifactTest),
                asSet(artifactCompile, artifactProvided, artifactTest),
                asSet(artifactCompile, artifactProvided, artifactTest));

        ProjectDependencyAnalysis compileOnlyAnalysis = analysis.ignoreNonCompile();

        assertThat(compileOnlyAnalysis.getUsedDeclaredArtifacts()).hasSize(3);
        assertThat(compileOnlyAnalysis.getUsedUndeclaredArtifacts()).hasSize(3);

        assertThat(compileOnlyAnalysis.getUnusedDeclaredArtifacts())
                .hasSize(1)
                .allSatisfy(a -> assertThat(a.getScope()).isEqualTo(Artifact.SCOPE_COMPILE));

        assertThat(compileOnlyAnalysis.getTestArtifactsWithNonTestScope()).hasSize(3);
    }

    @Test
    void shouldExposeUsedClassesAndCandidateArtifacts() {
        Artifact declared = aTestArtifact("declared", Artifact.SCOPE_COMPILE);
        Artifact other = aTestArtifact("other", Artifact.SCOPE_COMPILE);
        Set<String> used = new HashSet<>(Arrays.asList("a.B", "c.D"));
        Map<String, Set<Artifact>> candidates = new java.util.LinkedHashMap<>();
        candidates.put("a.B", new LinkedHashSet<>(Arrays.asList(declared, other)));
        candidates.put("c.D", new LinkedHashSet<>(Collections.singleton(declared)));
        Map<String, Artifact> selected = new java.util.LinkedHashMap<>();
        selected.put("a.B", declared);
        selected.put("c.D", declared);

        ProjectDependencyAnalysis analysis = new ProjectDependencyAnalysis(
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptySet(),
                Collections.emptySet(),
                used,
                candidates,
                selected);

        assertThat(analysis.getUsedDependencyClasses()).containsExactlyInAnyOrder("a.B", "c.D");
        assertThat(analysis.getCandidateArtifactsByUsedClass().get("a.B")).containsExactly(declared, other);
        assertThat(analysis.getSelectedArtifactByUsedClass().get("a.B")).isEqualTo(declared);
        assertThat(analysis.getAmbiguousUsedDependencyClasses()).containsExactly("a.B");
        assertThat(analysis.getUnmappedUsedDependencyClasses()).isEmpty();
    }

    @Test
    void shouldReportUnmappedUsedDependencyClasses() {
        Set<String> used = new HashSet<>(Collections.singletonList("a.B"));
        Map<String, Set<Artifact>> candidates = new java.util.LinkedHashMap<>();
        candidates.put("a.B", Collections.emptySet());

        ProjectDependencyAnalysis analysis = new ProjectDependencyAnalysis(
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptySet(),
                Collections.emptySet(),
                used,
                candidates,
                Collections.emptyMap());

        assertThat(analysis.getUnmappedUsedDependencyClasses()).containsExactly("a.B");
        assertThat(analysis.getSelectedArtifactByUsedClass()).doesNotContainKey("a.B");
    }

    private <T> Set<T> asSet(T... items) {
        return new HashSet<>(Arrays.asList(items));
    }

    private Artifact aTestArtifact(String artifactId, String scope) {
        return new DefaultArtifact(
                "groupId", artifactId, VersionRange.createFromVersion("1.0"), scope, "jar", "", null);
    }
}
