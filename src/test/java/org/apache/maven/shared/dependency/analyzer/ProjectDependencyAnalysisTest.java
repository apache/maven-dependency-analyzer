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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.versioning.VersionRange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        Artifact first = aTestArtifact("first", Artifact.SCOPE_COMPILE);
        Artifact second = aTestArtifact("second", Artifact.SCOPE_COMPILE);
        Artifact forcedA = aTestArtifact("forced-a", Artifact.SCOPE_COMPILE);
        Artifact forcedB = aTestArtifact("forced-b", Artifact.SCOPE_COMPILE);
        DependencyUsage firstUsage = new DependencyUsage("a.First", "my.App");
        DependencyUsage secondUsage = new DependencyUsage("b.Second", "my.Other");
        Map<Artifact, Set<DependencyUsage>> usedDeclared = new LinkedHashMap<>();
        usedDeclared.put(first, Collections.singleton(firstUsage));
        usedDeclared.put(second, Collections.singleton(secondUsage));
        Set<String> usedClasses = new LinkedHashSet<>(Arrays.asList("a.First", "b.Second"));
        Map<String, Set<Artifact>> candidates = new LinkedHashMap<>();
        candidates.put("a.First", Collections.singleton(first));
        candidates.put("b.Second", Collections.singleton(second));
        Map<String, Artifact> selected = new LinkedHashMap<>();
        selected.put("a.First", first);
        selected.put("b.Second", second);

        ProjectDependencyAnalysis analysis = new ProjectDependencyAnalysis(
                        usedDeclared,
                        Collections.emptyMap(),
                        new LinkedHashSet<>(Arrays.asList(forcedA, forcedB)),
                        Collections.emptySet(),
                        usedClasses,
                        candidates,
                        selected)
                .forceDeclaredDependenciesUsage(new String[] {"groupId:forced-a", "groupId:forced-b"});

        Map<Artifact, Set<DependencyUsage>> usages = analysis.getUsedDeclaredArtifactsWithUsages();
        assertEquals(Collections.singleton(firstUsage), usages.get(first));
        assertEquals(Collections.singleton(secondUsage), usages.get(second));
        assertEquals(Collections.emptySet(), usages.get(forcedA));
        assertEquals(Collections.emptySet(), usages.get(forcedB));
        assertTrue(analysis.getUnusedDeclaredArtifacts().isEmpty());
        assertEquals(usedClasses, analysis.getUsedDependencyClasses());
        assertEquals(candidates, analysis.getCandidateArtifactsByUsedClass());
        assertEquals(selected, analysis.getSelectedArtifactByUsedClass());
    }

    @Test
    void forcingAnAlreadyUsedOrUndeclaredDependencyFails() {
        Artifact used = aTestArtifact("used", Artifact.SCOPE_COMPILE);
        ProjectDependencyAnalysis analysis = new ProjectDependencyAnalysis(
                Collections.singletonMap(used, Collections.singleton(new DependencyUsage("a.B", "my.App"))),
                Collections.emptyMap(),
                Collections.emptySet(),
                Collections.emptySet());

        ProjectDependencyAnalyzerException exception = assertThrows(
                ProjectDependencyAnalyzerException.class,
                () -> analysis.forceDeclaredDependenciesUsage(new String[] {"groupId:used", "groupId:missing"}));
        assertEquals(
                "Trying to force use of dependencies which are not declared: [groupId:missing]"
                        + " and declared but already detected as used: [groupId:used]",
                exception.getMessage());
    }

    @Test
    void classesAndUsagesAreSorted() {
        Artifact undeclared = aTestArtifact("undeclared", Artifact.SCOPE_COMPILE);
        // HashSet iterates these as c.Zeta, a.Alpha, b.Beta
        Set<DependencyUsage> usages = new HashSet<>(Arrays.asList(
                new DependencyUsage("c.Zeta", "my.App"),
                new DependencyUsage("a.Alpha", "my.Zed"),
                new DependencyUsage("a.Alpha", "my.App"),
                new DependencyUsage("b.Beta", "my.App")));
        Set<String> used = new HashSet<>(Arrays.asList("c.Zeta", "a.Alpha", "b.Beta"));
        Map<String, Set<Artifact>> candidates = new java.util.HashMap<>();
        for (String className : used) {
            candidates.put(className, Collections.emptySet());
        }

        ProjectDependencyAnalysis analysis = new ProjectDependencyAnalysis(
                Collections.emptyMap(),
                Collections.singletonMap(undeclared, usages),
                Collections.emptySet(),
                Collections.emptySet(),
                used,
                candidates,
                Collections.emptyMap());

        List<String> sorted = Arrays.asList("a.Alpha", "b.Beta", "c.Zeta");
        assertIterableEquals(sorted, analysis.getUsedDependencyClasses());
        assertIterableEquals(sorted, analysis.getUnmappedUsedDependencyClasses());
        assertIterableEquals(sorted, analysis.getCandidateArtifactsByUsedClass().keySet());
        assertIterableEquals(
                sorted, analysis.getUsedUndeclaredArtifactsWithClasses().get(undeclared));
        assertIterableEquals(
                Arrays.asList(
                        new DependencyUsage("a.Alpha", "my.App"),
                        new DependencyUsage("a.Alpha", "my.Zed"),
                        new DependencyUsage("b.Beta", "my.App"),
                        new DependencyUsage("c.Zeta", "my.App")),
                analysis.getUsedUndeclaredArtifactsWithUsages().get(undeclared));
        // sorted copies still answer null lookups like the hash-based ones did
        assertFalse(analysis.getUsedDependencyClasses().contains(null));
        assertFalse(analysis.getCandidateArtifactsByUsedClass().containsKey(null));
    }

    @Test
    void ignoreNonCompileKeepsTheOrderOfTheUnusedDependencies() {
        List<Artifact> unused = new ArrayList<>();
        for (char c = 'z'; c >= 'a'; c--) {
            unused.add(aTestArtifact(String.valueOf(c), Artifact.SCOPE_COMPILE));
        }

        ProjectDependencyAnalysis analysis = new ProjectDependencyAnalysis(
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        new LinkedHashSet<>(unused),
                        Collections.emptySet())
                .ignoreNonCompile();

        assertIterableEquals(unused, analysis.getUnusedDeclaredArtifacts());
    }

    private <T> Set<T> asSet(T... items) {
        return new HashSet<>(Arrays.asList(items));
    }

    private Artifact aTestArtifact(String artifactId, String scope) {
        return new DefaultArtifact(
                "groupId", artifactId, VersionRange.createFromVersion("1.0"), scope, "jar", "", null);
    }
}
