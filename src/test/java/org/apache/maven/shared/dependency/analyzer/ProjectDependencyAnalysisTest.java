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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        assertEquals(usedDeclaredArtifacts, analysis.getUsedDeclaredArtifacts());
        assertEquals(usedUndeclaredArtifacts, analysis.getUsedUndeclaredArtifacts());
        assertEquals(unusedDeclaredArtifacts, analysis.getUnusedDeclaredArtifacts());
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

        assertEquals(3, compileOnlyAnalysis.getUsedDeclaredArtifacts().size());
        assertEquals(3, compileOnlyAnalysis.getUsedUndeclaredArtifacts().size());

        assertEquals(1, compileOnlyAnalysis.getUnusedDeclaredArtifacts().size());
        for (Artifact a : compileOnlyAnalysis.getUnusedDeclaredArtifacts()) {
            assertEquals(Artifact.SCOPE_COMPILE, a.getScope());
        }

        assertEquals(3, compileOnlyAnalysis.getTestArtifactsWithNonTestScope().size());
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

        assertEquals(new HashSet<>(Arrays.asList("a.B", "c.D")), new HashSet<>(analysis.getUsedDependencyClasses()));
        assertIterableEquals(
                Arrays.asList(declared, other),
                analysis.getCandidateArtifactsByUsedClass().get("a.B"));
        assertEquals(declared, analysis.getSelectedArtifactByUsedClass().get("a.B"));
        assertIterableEquals(Collections.singletonList("a.B"), analysis.getAmbiguousUsedDependencyClasses());
        assertTrue(analysis.getUnmappedUsedDependencyClasses().isEmpty());
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

        assertIterableEquals(Collections.singletonList("a.B"), analysis.getUnmappedUsedDependencyClasses());
        assertFalse(analysis.getSelectedArtifactByUsedClass().containsKey("a.B"));
    }

    @Test
    void forcingUsageKeepsTheUsagesOfOtherUsedDeclaredDependencies() throws Exception {
        Artifact used = aTestArtifact("used", Artifact.SCOPE_COMPILE);
        Artifact forced = aTestArtifact("forced", Artifact.SCOPE_COMPILE);
        DependencyUsage usage = new DependencyUsage("a.B", "my.App");

        ProjectDependencyAnalysis analysis = new ProjectDependencyAnalysis(
                        Collections.singletonMap(used, Collections.singleton(usage)),
                        Collections.emptyMap(),
                        Collections.singleton(forced),
                        Collections.emptySet())
                .forceDeclaredDependenciesUsage(new String[] {"groupId:forced"});

        Map<Artifact, Set<DependencyUsage>> usages = analysis.getUsedDeclaredArtifactsWithUsages();
        assertEquals(Collections.singleton(usage), usages.get(used));
        assertEquals(Collections.emptySet(), usages.get(forced));
        assertTrue(analysis.getUnusedDeclaredArtifacts().isEmpty());
    }

    private <T> Set<T> asSet(T... items) {
        return new HashSet<>(Arrays.asList(items));
    }

    private Artifact aTestArtifact(String artifactId, String scope) {
        return new DefaultArtifact(
                "groupId", artifactId, VersionRange.createFromVersion("1.0"), scope, "jar", "", null);
    }
}
